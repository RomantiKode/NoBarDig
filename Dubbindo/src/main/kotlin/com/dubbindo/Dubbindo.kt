package com.dubbindo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

class Dubbindo : MainAPI() {
    override var mainUrl = "https://www.dubbindo.site"
    override var name = "Dubbindo"
    override var lang = "id"

    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        "$mainUrl/videos/latest" to "Latest Videos",
        "$mainUrl/videos/trending" to "Trending Videos",
        "$mainUrl/videos/top" to "Top Videos",
        "$mainUrl/videos/category/1" to "Film Movie",
        "$mainUrl/videos/category/3" to "TV Series (per episode)",
        "$mainUrl/videos/category/5" to "Anime Series (per episode)",
        "$mainUrl/videos/category/4" to "Anime Movie",
        "$mainUrl/videos/category/other" to "Other",
        "$mainUrl/videos/category/790" to "Shorts",
        "$mainUrl/videos/category/791" to "Uncategory"
    )

    private val invisibleTextRegex = Regex(
        "[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF]"
    )

    private val whitespaceRegex = Regex("\\s+")
    private val yearRegex = Regex("""\b(19|20)\d{2}\b""")
    private val qualityRegex = Regex(
        """(?i)(2160|1440|1080|720|480|360|240|144)(?:p|\b)"""
    )
    private val animeMovieCategoryRegex = Regex(
        """/videos/category/4(?:$|[/?])"""
    )

    private val directMediaRegex = Regex(
        """https?://[^\s\"'<>]+?\.(?:mp4|m3u8|mkv|avi)(?:\?[^\s\"'<>]*)?""",
        RegexOption.IGNORE_CASE
    )

    private val sourceObjectRegex = Regex(
        """\{[^{}]*?\bsrc\s*:\s*['\"]([^'\"]+?\.(?:mp4|m3u8|mkv|avi)(?:\?[^'\"]*)?)['\"][^{}]*?}""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val sourceLabelRegex = Regex(
        """\blabel\s*:\s*['\"]([^'\"]+)['\"]""",
        RegexOption.IGNORE_CASE
    )

    private val sourceResolutionRegex = Regex(
        """\bres\s*:\s*['\"]?(\d{3,4})['\"]?""",
        RegexOption.IGNORE_CASE
    )

    private fun String.cleanDisplayText(): String {
        return replace(invisibleTextRegex, "")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    /**
     * Struktur aktif situs:
     * - Home/kategori/search: div.video-list
     * - Rekomendasi detail: div.related-video-wrapper
     * - div.video-wrapper dipertahankan sebagai fallback tema lama.
     */
    private fun Document.videoCards(): List<Element> {
        val selectors = listOf(
            "div.video-list",
            "div.related-video-wrapper",
            "div.video-wrapper"
        )

        selectors.forEach { selector ->
            val cards = select(selector)
                .filter { it.contentAnchor() != null }
                .distinctBy { it.contentAnchor()?.attr("href") }

            if (cards.isNotEmpty()) return cards
        }

        return emptyList()
    }

    private fun Element.contentAnchor(): Element? {
        return selectFirst(
            ".video-list-title a[href*='/watch/'], " +
                ".video-list-title a[href*='/shorts/'], " +
                ".ra-title .video-title a[href*='/watch/'], " +
                ".ra-title .video-title a[href*='/shorts/'], " +
                ".video-title a[href*='/watch/'], " +
                ".video-title a[href*='/shorts/'], " +
                ".video-list-image a[href*='/watch/'], " +
                ".video-list-image a[href*='/shorts/'], " +
                ".ra-thumb a[href*='/watch/'], " +
                ".ra-thumb a[href*='/shorts/'], " +
                "a[href^='/watch/'], " +
                "a[href^='/shorts/'], " +
                "a[href^='$mainUrl/watch/'], " +
                "a[href^='$mainUrl/shorts/']"
        )
    }

    private fun Element.imageUrl(): String? {
        return listOf(
            attr("data-src"),
            attr("data-original"),
            attr("data-lazy-src"),
            attr("src")
        ).firstOrNull {
            it.startsWith("http://") ||
                it.startsWith("https://") ||
                it.startsWith("//") ||
                it.startsWith("/")
        }
    }

    private fun normalizeUrl(raw: String?, baseUrl: String = mainUrl): String? {
        val clean = raw
            ?.trim()
            ?.replace("\\/", "/")
            ?.replace("&amp;", "&")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        if (clean.startsWith("data:", ignoreCase = true) ||
            clean.startsWith("javascript:", ignoreCase = true) ||
            clean.equals("about:blank", ignoreCase = true)
        ) return null

        return runCatching {
            when {
                clean.startsWith("//") -> "https:$clean"
                clean.startsWith("http://") || clean.startsWith("https://") -> clean
                else -> URI(baseUrl).resolve(clean).toString()
            }
        }.getOrNull()
    }

    private fun qualityFromText(text: String?): Int {
        val clean = text.orEmpty()
        val direct = clean.trim().toIntOrNull()
        if (direct != null) return direct

        val resolution = qualityRegex
            .find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return resolution ?: Qualities.Unknown.value
    }

    private fun Element.categoryType(fallback: TvType = TvType.Movie): TvType {
        val categoryUrls = select("a[href*='/videos/category/']")
            .map { it.attr("href").lowercase() }

        return if (categoryUrls.any {
                animeMovieCategoryRegex.containsMatchIn(it)
            }
        ) {
            TvType.AnimeMovie
        } else {
            fallback
        }
    }

    private fun Element.toSearchResult(fallbackType: TvType = TvType.Movie): SearchResponse? {
        val anchor = contentAnchor() ?: return null
        val href = normalizeUrl(anchor.attr("href")) ?: return null

        val titleElement = selectFirst(
            ".video-list-title h4[title], " +
                ".video-list-title h4, " +
                ".ra-title .video-title a, " +
                ".video-title a, " +
                "h4[title], h4"
        )

        val title = titleElement
            ?.attr("title")
            ?.cleanDisplayText()
            .orEmpty()
            .ifBlank { titleElement?.text()?.cleanDisplayText().orEmpty() }
            .ifBlank { anchor.attr("title").cleanDisplayText() }
            .ifBlank { selectFirst("img[alt]")?.attr("alt")?.cleanDisplayText().orEmpty() }
            .ifBlank { anchor.text().cleanDisplayText() }

        if (title.isBlank()) return null

        val poster = selectFirst("img")?.imageUrl()?.let { normalizeUrl(it, href) }
        val year = yearRegex
            .findAll(title)
            .lastOrNull()
            ?.value
            ?.toIntOrNull()
        val type = categoryType(fallbackType)

        return newMovieSearchResponse(title, href, type) {
            posterUrl = poster
            this.year = year
        }
    }

    private fun Document.hasNextPage(page: Int): Boolean {
        val nextPage = page + 1

        return select(
            "a[rel=next], " +
                ".pagination a[href], " +
                ".pagination a[data-page], " +
                "a[href*='page_id=']"
        ).any { link ->
            val text = link.text().cleanDisplayText()
            val href = link.attr("href")
            val dataPage = link.attr("data-page").toIntOrNull()

            text.equals("next", ignoreCase = true) ||
                text == "›" || text == "»" ||
                dataPage == nextPage ||
                href.contains("page_id=$nextPage") ||
                href.contains("/page/$nextPage/")
        }
    }

    private fun typeHintFromUrl(url: String): TvType {
        return if (url.contains("/videos/category/4")) {
            TvType.AnimeMovie
        } else {
            TvType.Movie
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val separator = if (request.data.contains("?")) "&" else "?"
        val url = "${request.data}${separator}page_id=$page"
        val document = app.get(url).document
        val typeHint = typeHintFromUrl(request.data)
        val items = document.videoCards()
            .mapNotNull { it.toSearchResult(typeHint) }
            .distinctBy { it.url }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = true
            ),
            hasNext = items.isNotEmpty() && document.hasNextPage(page)
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val cleanQuery = query.cleanDisplayText()
        if (cleanQuery.isBlank()) return newSearchResponseList(emptyList(), hasNext = false)

        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val urls = buildList {
            add("$mainUrl/search?keyword=$encoded&page_id=$page")
            if (page > 1) add("$mainUrl/page/$page/search?keyword=$encoded")
        }

        var lastDocument: Document? = null
        for (url in urls) {
            val document = app.get(url).document
            lastDocument = document
            val results = document.videoCards()
                .mapNotNull { it.toSearchResult() }
                .distinctBy { it.url }

            if (results.isNotEmpty() || page == 1) {
                return newSearchResponseList(
                    results,
                    hasNext = results.isNotEmpty() && document.hasNextPage(page)
                )
            }
        }

        return newSearchResponseList(
            emptyList(),
            hasNext = lastDocument?.hasNextPage(page) == true
        )
    }

    private fun parseDurationMinutes(raw: String?): Int? {
        val clean = raw?.cleanDisplayText()?.takeIf { it.isNotBlank() } ?: return null
        val parts = clean.split(":").mapNotNull { it.trim().toIntOrNull() }

        return when (parts.size) {
            3 -> parts[0] * 60 + parts[1] + if (parts[2] >= 30) 1 else 0
            2 -> parts[0] + if (parts[1] >= 30) 1 else 0
            else -> null
        }
    }

    private fun Document.pageJsonData(): PageJsonData? {
        val element = selectFirst("#json-data") ?: return null
        val raw = element.attr("value")
            .ifBlank { element.data() }
            .ifBlank { element.text() }
            .takeIf { it.isNotBlank() }
            ?: return null

        return tryParseJson<PageJsonData>(raw)
    }

    private fun Document.detailType(): TvType {
        return if (select(
                ".video-published a[href*='/videos/category/4']"
            ).isNotEmpty()
        ) {
            TvType.AnimeMovie
        } else {
            TvType.Movie
        }
    }

    private fun Document.extractSources(
        pageUrl: String,
        includeEmbedFallback: Boolean = true
    ): List<MediaSource> {
        val sources = linkedMapOf<String, MediaSource>()

        select(
            ".video-player video source[src], " +
                ".video-player video source[data-src], " +
                ".video-player video[src], " +
                ".video-player video[data-src], " +
                "video#my-video source[src], " +
                "video#my-video source[data-src], " +
                "video#my-video[src], " +
                "video#my-video[data-src]"
        ).forEach { element ->
            val rawUrl = element.attr("src").ifBlank { element.attr("data-src") }
            val url = normalizeUrl(rawUrl, pageUrl) ?: return@forEach
            val label = element.attr("data-quality")
                .ifBlank { element.attr("title") }
                .ifBlank { element.attr("label") }
                .ifBlank { element.attr("size") }
                .ifBlank { element.attr("res") }
                .ifBlank { element.attr("data-res") }
            val type = element.attr("type")

            // Assignment, bukan putIfAbsent: <source> berlabel dapat memperbaiki
            // data dari atribut src pada elemen <video> yang tidak memiliki label.
            sources[url] = MediaSource(url, label, type)
        }

        // Pada source asli, pilihan kualitas juga ditulis dalam
        // player.updateSrc([{ src, label, res }, ...]). Hanya script player
        // ini yang dipindai agar URL iklan tidak ikut dianggap sebagai video.
        select("script").forEach { script ->
            val body = script.data().replace("\\/", "/")
            if (!body.contains("updateSrc", ignoreCase = true)) return@forEach

            sourceObjectRegex.findAll(body).forEach sourceMatch@{ match ->
                val block = match.value
                val url = normalizeUrl(match.groupValues[1], pageUrl) ?: return@sourceMatch
                val label = sourceLabelRegex.find(block)?.groupValues?.getOrNull(1)
                    ?: sourceResolutionRegex.find(block)?.groupValues?.getOrNull(1)?.let { "${it}p" }

                sources.putIfAbsent(url, MediaSource(url, label, null))
            }

            // Fallback jika struktur objek JavaScript berubah, tetap dibatasi
            // hanya pada script yang mengandung updateSrc.
            directMediaRegex.findAll(body).forEach fallbackMatch@{ match ->
                val url = normalizeUrl(match.value, pageUrl) ?: return@fallbackMatch
                sources.putIfAbsent(url, MediaSource(url, null, null))
            }
        }

        // Embed resmi hanya dipakai bila halaman tidak menyediakan direct
        // source. Ini menghindari request extractor tambahan ketika MP4/M3U8
        // sudah tersedia, sekaligus tetap menyediakan fallback untuk halaman
        // yang hanya berisi iframe internal.
        if (sources.isEmpty() && includeEmbedFallback) {
            select(
                ".pt_embed_playr iframe[src*='/embed/'], " +
                    "iframe[src^='$mainUrl/embed/']"
            ).forEach { frame ->
                val url = normalizeUrl(frame.attr("src"), pageUrl) ?: return@forEach
                sources.putIfAbsent(url, MediaSource(url, "Dubbindo Embed", "embed"))
            }
        }

        return sources.values.sortedWith(
            compareByDescending<MediaSource> {
                qualityFromText(it.label).takeIf { quality ->
                    quality != Qualities.Unknown.value
                } ?: qualityFromText(it.url)
            }.thenBy { it.url }
        )
    }

    private fun Document.extractSubtitles(pageUrl: String): List<SubtitleData> {
        return select(
            ".video-player track[kind=subtitles][src], " +
                ".video-player track[kind=subtitles][data-src], " +
                ".video-player track[kind=captions][src], " +
                ".video-player track[kind=captions][data-src], " +
                "video#my-video track[kind=subtitles][src], " +
                "video#my-video track[kind=captions][src]"
        ).mapNotNull { track ->
            val rawUrl = track.attr("src").ifBlank { track.attr("data-src") }
            val url = normalizeUrl(rawUrl, pageUrl) ?: return@mapNotNull null
            val language = track.attr("label")
                .ifBlank { track.attr("srclang") }
                .ifBlank { "Subtitle" }
                .cleanDisplayText()
            SubtitleData(language, url)
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val pageJson = document.pageJsonData()
        val canonicalUrl = document.selectFirst("link[rel=canonical][href]")
            ?.attr("href")
            ?.let { normalizeUrl(it, url) }
            ?.takeUnless {
                url.contains("/shorts/") && it.trimEnd('/') == mainUrl.trimEnd('/')
            }
        val effectivePageUrl = pageJson?.url
            ?.let { normalizeUrl(it, url) }
            ?: canonicalUrl
            ?: url

        val title = document.selectFirst(".video-big-title h1")
            ?.text()
            ?.cleanDisplayText()
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:title]")
                ?.attr("content")
                ?.cleanDisplayText()
                ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[name=title]")
                ?.attr("content")
                ?.cleanDisplayText()
                ?.takeIf { it.isNotBlank() }
            ?: pageJson?.title?.cleanDisplayText()?.takeIf { it.isNotBlank() }
            ?: return null

        val poster = listOfNotNull(
            document.selectFirst("meta[property=og:image]")?.attr("content"),
            document.selectFirst("meta[name=thumbnail]")?.attr("content"),
            document.selectFirst("meta[name=twitter:image]")?.attr("content"),
            document.selectFirst(".video-player video[poster]")?.attr("poster"),
            document.selectFirst("video#my-video[poster]")?.attr("poster")
        ).firstNotNullOfOrNull { normalizeUrl(it, effectivePageUrl) }

        val description = document.selectFirst(".watch-video-description p")
            ?.text()
            ?.cleanDisplayText()
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:description]")
                ?.attr("content")
                ?.cleanDisplayText()
                ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[name=description]")
                ?.attr("content")
                ?.cleanDisplayText()
                ?.takeIf { it.isNotBlank() }
            ?: pageJson?.description?.cleanDisplayText()?.takeIf { it.isNotBlank() }

        val categoryTags = document.select(
            ".video-published a[href*='/videos/category/'], " +
                ".video-published a[href*='/category/']"
        ).map { it.text().cleanDisplayText() }
            .filter { it.isNotBlank() }

        val keywordTags = (
            document.selectFirst("meta[name=keywords]")
                ?.attr("content")
                ?.split(",")
                .orEmpty() +
                pageJson?.keyword
                    ?.split(",")
                    .orEmpty()
            ).map { it.cleanDisplayText() }
            .filter { it.isNotBlank() }

        val tags = (categoryTags + keywordTags).distinct()

        // Jangan memakai .video-duration global karena elemen tersebut milik
        // daftar rekomendasi. Bila durasi player belum dirender, biarkan null.
        val duration = parseDurationMinutes(
            document.selectFirst(
                ".video-player .vjs-duration-display, " +
                    ".video-player .mejs__duration, " +
                    ".video-player .mejs-duration"
            )?.text()
        )

        val recommendations = document.select("div.related-video-wrapper")
            .mapNotNull { it.toSearchResult() }
            .filter { it.url != url && it.url != effectivePageUrl }
            .distinctBy { it.url }

        val payload = LinkData(
            pageUrl = effectivePageUrl,
            sources = document.extractSources(effectivePageUrl),
            subtitles = document.extractSubtitles(effectivePageUrl)
        )

        val releaseYear = yearRegex
            .findAll(title)
            .lastOrNull()
            ?.value
            ?.toIntOrNull()

        return newMovieLoadResponse(title, url, document.detailType(), payload.toJson()) {
            posterUrl = poster
            plot = description
            this.tags = tags
            this.year = releaseYear
            this.duration = duration
            this.recommendations = recommendations
        }
    }

    private fun isDirectMedia(url: String): Boolean {
        val path = url.substringBefore("?").lowercase()
        return path.endsWith(".mp4") ||
            path.endsWith(".m3u8") ||
            path.endsWith(".mkv") ||
            path.endsWith(".avi")
    }

    private fun isInternalEmbed(url: String): Boolean {
        return url.startsWith("$mainUrl/embed/")
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parsed = tryParseJson<LinkData>(data)
        val pageUrl = parsed?.pageUrl ?: data

        val sourceDocument = if (parsed == null || parsed.sources.isEmpty()) {
            app.get(pageUrl).document
        } else {
            null
        }
        val sources = parsed?.sources
            ?.takeIf { it.isNotEmpty() }
            ?: sourceDocument?.extractSources(pageUrl)
                .orEmpty()
        val subtitles = parsed?.subtitles
            ?.takeIf { it.isNotEmpty() }
            ?: sourceDocument?.extractSubtitles(pageUrl)
                .orEmpty()

        subtitles.forEach { subtitle ->
            subtitleCallback(SubtitleFile(subtitle.language, subtitle.url))
        }

        val emitted = linkedSetOf<String>()
        val uniqueCallback: (ExtractorLink) -> Unit = { link ->
            if (emitted.add(link.url)) callback(link)
        }

        suspend fun emitMedia(media: MediaSource, refererUrl: String) {
            val mediaUrl = normalizeUrl(media.url, refererUrl) ?: return

            try {
                if (isDirectMedia(mediaUrl)) {
                    val type = if (
                        mediaUrl.substringBefore("?").endsWith(".m3u8", ignoreCase = true)
                    ) {
                        ExtractorLinkType.M3U8
                    } else {
                        ExtractorLinkType.VIDEO
                    }
                    val quality = qualityFromText(media.label).takeIf {
                        it != Qualities.Unknown.value
                    } ?: qualityFromText(mediaUrl)
                    val displayName = media.label
                        ?.cleanDisplayText()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "$name $it" }
                        ?: name

                    uniqueCallback(
                        newExtractorLink(
                            source = name,
                            name = displayName,
                            url = mediaUrl,
                            type = type
                        ) {
                            referer = refererUrl
                            headers = mapOf(
                                "Referer" to refererUrl,
                                "Origin" to mainUrl
                            )
                            this.quality = quality
                        }
                    )
                } else if (isInternalEmbed(mediaUrl)) {
                    val embedDocument = app.get(mediaUrl, referer = refererUrl).document
                    embedDocument.extractSources(
                        pageUrl = mediaUrl,
                        includeEmbedFallback = false
                    ).forEach { embedded ->
                        emitMedia(embedded, mediaUrl)
                    }
                } else {
                    loadExtractor(
                        mediaUrl,
                        refererUrl,
                        subtitleCallback,
                        uniqueCallback
                    )
                }
            } catch (_: Exception) {
                // Satu source gagal tidak menghentikan source lain.
            }
        }

        sources.distinctBy { it.url }.forEach { media ->
            emitMedia(media, pageUrl)
        }

        return emitted.isNotEmpty()
    }

    data class LinkData(
        val pageUrl: String,
        val sources: List<MediaSource> = emptyList(),
        val subtitles: List<SubtitleData> = emptyList()
    )

    data class MediaSource(
        val url: String,
        val label: String? = null,
        val type: String? = null
    )

    data class SubtitleData(
        val language: String,
        val url: String
    )

    data class PageJsonData(
        val title: String? = null,
        val description: String? = null,
        val keyword: String? = null,
        val page: String? = null,
        val url: String? = null,
        val is_movie: Boolean? = null
    )
}
