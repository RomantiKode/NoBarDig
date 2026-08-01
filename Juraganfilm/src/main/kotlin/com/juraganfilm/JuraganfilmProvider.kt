package com.juraganfilm

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class JuraganfilmProvider : MainAPI() {
    override var mainUrl = "https://tv48.juragan.film"
    override var name = "Juraganfilm"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "Film Terbaru",
        "film-seri/" to "Serial TV",
        "kategori-film/kelas-bintang/" to "Kelas Bintang",
        "kategori-film/jav-hd/" to "JAV HD",
        "kategori-film/semi/" to "Semi",
        "kategori-film/anime/" to "Animasi"
    )

    private data class MediaSource(
        val label: String,
        val url: String,
        val type: String? = null
    )

    private data class SourceItem(
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("src") val src: String? = null
    ) {
        fun mediaUrl(): String? = link ?: file ?: src
    }

    private fun cleanTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""(?i)^\s*nonton(?:\s+film)?\s*"""), "")
            .replace(Regex("""(?i)\s+sub\s*indo(?:\s+.*)?$"""), "")
            .replace(Regex("""(?i)\s*(?:[-|:]\s*)?juraganfilm.*$"""), "")
            .trim(' ', '-', '|', ':')
    }

    private fun Element.posterUrl(): String? {
        val image = selectFirst("img") ?: return null
        return listOf("data-src", "data-lazy-src", "data-original", "src")
            .asSequence()
            .map { image.attr(it).trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("data:image", ignoreCase = true) }
            ?.let(::fixUrlNull)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = selectFirst(".content-thumbnail a[href], .other-content-thumbnail a[href], a[href]")
            ?: return null
        val href = fixUrlNull(anchor.attr("href")) ?: return null
        if (!href.contains(mainUrl.substringAfter("://").substringBefore('/'), ignoreCase = true)) return null

        val rawTitle = selectFirst(".entry-title, .gmr-slide-title, h2, h3")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank { anchor.attr("title").trim() }
            .ifBlank { anchor.selectFirst("img")?.attr("alt")?.trim().orEmpty() }

        val title = cleanTitle(rawTitle)
        if (title.isBlank()) return null

        val poster = posterUrl()
        val isSeries = href.contains("/film-seri/", ignoreCase = true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
            }
        }
    }

    private fun buildPageUrl(path: String, page: Int): String {
        val base = if (path.isBlank()) "$mainUrl/" else "$mainUrl/${path.trimStart('/')}"
        val normalized = if (base.endsWith('/')) base else "$base/"
        return if (page <= 1) normalized else "${normalized}page/$page/"
    }

    private fun Document.collectCards(): List<SearchResponse> {
        val primaryCards = select(".gmr-grid .item, article.item")
        val cards = if (primaryCards.isNotEmpty()) primaryCards else select(".other-content-thumbnail")
        return cards.mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(buildPageUrl(request.data, page), timeout = 30).document
        val items = document.collectCards()
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        return app.get("$mainUrl/?s=$encodedQuery", timeout = 30)
            .document
            .collectCards()
    }

    private fun Document.metaContent(selector: String): String? =
        selectFirst(selector)?.attr("content")?.trim()?.takeIf { it.isNotBlank() }

    private fun Document.extractPlot(): String? {
        val synopsis = select(".gmr-moviedata").firstNotNullOfOrNull { block ->
            val heading = block.selectFirst("strong")?.text()?.trim().orEmpty()
            if (heading.startsWith("Sinopsis", ignoreCase = true)) {
                block.selectFirst(".entry-content-single, [itemprop=description]")
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            } else null
        }

        return synopsis
            ?: metaContent("meta[name=description]")
            ?: metaContent("meta[property=og:description]")
    }

    private fun Document.extractPoster(): String? {
        val imageUrl = selectFirst(".gmr-movie-data img.wp-post-image, .content-moviedata img, img.wp-post-image")
            ?.let { image ->
                listOf("data-src", "data-lazy-src", "data-original", "src")
                    .asSequence()
                    .map { image.attr(it).trim() }
                    .firstOrNull { it.isNotBlank() && !it.startsWith("data:image", ignoreCase = true) }
            }
            ?: metaContent("meta[property=og:image]")

        return imageUrl?.let(::fixUrlNull)
    }

    private fun Document.extractYear(): Int? {
        val yearText = selectFirst(
            ".gmr-movie-data a[href*='/tahun/'], .content-moviedata a[href*='/tahun/'], " +
                ".gmr-movie-data time[datetime], .content-moviedata time[datetime]"
        )?.let { element ->
            element.attr("datetime").ifBlank { element.text() }
        }.orEmpty()

        return Regex("""\b(19|20)\d{2}\b""").find(yearText)?.value?.toIntOrNull()
    }

    private fun Document.extractGenres(): List<String> {
        return select(
            ".gmr-movie-data a[rel~=category], " +
                ".content-moviedata a[href*='/kategori-film/']"
        ).map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun Document.extractEpisodes(currentUrl: String): List<Episode> {
        val episodes = select(".jf-eps-wrap .post-page-numbers")
            .mapNotNull { element ->
                val number = Regex("""\d+""").find(element.text())?.value?.toIntOrNull()
                    ?: return@mapNotNull null
                val episodeUrl = if (element.tagName().equals("a", ignoreCase = true)) {
                    fixUrlNull(element.attr("href"))
                } else {
                    currentUrl
                } ?: return@mapNotNull null

                newEpisode(episodeUrl) {
                    name = "Episode $number"
                    episode = number
                }
            }
            .distinctBy { it.episode }
            .sortedBy { it.episode }

        return episodes.ifEmpty {
            listOf(newEpisode(currentUrl) {
                name = "Episode 1"
                episode = 1
            })
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, timeout = 30).document
        val rawTitle = document.selectFirst(".gmr-movie-data .entry-title, h1.entry-title, h1")
            ?.text()
            ?.trim()
            ?: document.metaContent("meta[property=og:title]")
            ?: document.title().takeIf { it.isNotBlank() }
            ?: return null

        val title = cleanTitle(rawTitle)
        val poster = document.extractPoster()
        val plot = document.extractPlot()
        val year = document.extractYear()
        val genres = document.extractGenres()
        val isSeries = url.contains("/film-seri/", ignoreCase = true) ||
            document.selectFirst(".jf-eps-wrap .post-page-numbers") != null

        return if (isSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, document.extractEpisodes(url)) {
                posterUrl = poster
                this.plot = plot
                this.year = year
                tags = genres
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
                this.plot = plot
                this.year = year
                tags = genres
            }
        }
    }

    private fun normalizeMediaUrl(rawUrl: String): String {
        return rawUrl
            .trim()
            .replace("\\/", "/")
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("&amp;", "&")
            .let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun isDirectMediaUrl(url: String): Boolean {
        val normalized = url.substringBefore('#').lowercase()
        return normalized.contains(".m3u8") ||
            normalized.contains(".mp4") ||
            normalized.contains("hotfile.my.id") ||
            normalized.contains("/original/direct/")
    }

    private fun qualityFromLabel(label: String): Int {
        val resolution = Regex("""\b\d{3,4}x(\d{3,4})\b""")
            .find(label)
            ?.groupValues
            ?.getOrNull(1)
            ?: Regex("""\b(2160|1440|1080|720|480|360|240)p?\b""")
                .find(label)
                ?.groupValues
                ?.getOrNull(1)
        return getQualityFromName(resolution ?: label)
    }

    private suspend fun emitDirectSource(
        source: MediaSource,
        referer: String,
        emitted: MutableSet<String>,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val link = normalizeMediaUrl(source.url)
        if (!isDirectMediaUrl(link) || !emitted.add(link)) return false

        val isHls = source.type.equals("hls", ignoreCase = true) ||
            link.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        val label = source.label.ifBlank { if (isHls) "HLS" else "Direct" }

        callback(
            newExtractorLink(
                source = name,
                name = "$name - $label",
                url = link,
                type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.referer = referer
                this.quality = qualityFromLabel(label)
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to referer
                )
            }
        )
        return true
    }

    private fun Document.directSources(): List<MediaSource> {
        val downloadSources = select("a.jf-server-dl[href]").map { anchor ->
            val title = anchor.attr("title")
                .substringAfter("Unduh Video", anchor.text())
                .trim()
                .ifBlank { "Direct" }
            MediaSource(title, anchor.attr("href"))
        }

        val videoSources = select("video[src], video source[src]").map { element ->
            MediaSource(
                label = element.attr("label").ifBlank { "Direct" },
                url = element.attr("src"),
                type = element.attr("type")
            )
        }

        return (downloadSources + videoSources)
            .filter { it.url.isNotBlank() }
            .distinctBy { normalizeMediaUrl(it.url) }
    }

    private fun parseScriptSources(html: String): List<SourceItem> {
        val sourceArrayRegex = Regex(
            """(?:const|let|var)\s+SOURCES\s*=\s*(\[[\s\S]*?])\s*;""",
            RegexOption.IGNORE_CASE
        )
        return sourceArrayRegex.findAll(html)
            .mapNotNull { match -> tryParseJson<List<SourceItem>>(match.groupValues[1]) }
            .firstOrNull()
            .orEmpty()
    }

    private fun fallbackMediaUrls(html: String): List<String> {
        return Regex(
            """https?:\\?/\\?/[^\s\"'<>]+?(?:\.m3u8|\.mp4)(?:\?[^\s\"'<>]*)?""",
            RegexOption.IGNORE_CASE
        ).findAll(html)
            .map { normalizeMediaUrl(it.value) }
            .distinct()
            .toList()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false

        val document = app.get(data, timeout = 30).document
        val emitted = mutableSetOf<String>()
        var found = false

        // Prioritas utama: tautan unduhan/direct sudah tersedia di HTML luar.
        // Ini menghindari klik tombol player, WebView, popup, dan script iklan.
        document.directSources().forEach { source ->
            found = emitDirectSource(source, data, emitted, callback) || found
        }
        if (found) return true

        val iframeSrc = document.selectFirst(
            "iframe[name=juraganfilm][src], iframe[id^=jf-frame-][src], .gmr-embed-responsive iframe[src]"
        )?.attr("src")?.takeIf { it.isNotBlank() } ?: return false

        val iframeUrl = fixUrl(iframeSrc)
        val iframeText = app.get(
            iframeUrl,
            headers = mapOf("User-Agent" to USER_AGENT, "Referer" to data),
            timeout = 30
        ).text
        val iframeDocument = org.jsoup.Jsoup.parse(iframeText, iframeUrl)

        iframeDocument.directSources().forEach { source ->
            found = emitDirectSource(source, iframeUrl, emitted, callback) || found
        }

        parseScriptSources(iframeText).forEach { item ->
            val link = item.mediaUrl() ?: return@forEach
            val source = MediaSource(item.label ?: "Server", link, item.type)
            if (isDirectMediaUrl(normalizeMediaUrl(link))) {
                found = emitDirectSource(source, iframeUrl, emitted, callback) || found
            } else {
                found = loadExtractor(link, iframeUrl, subtitleCallback, callback) || found
            }
        }

        if (!found) {
            fallbackMediaUrls(iframeText).forEach { link ->
                found = emitDirectSource(
                    MediaSource("Direct", link),
                    iframeUrl,
                    emitted,
                    callback
                ) || found
            }
        }

        return found
    }
}
