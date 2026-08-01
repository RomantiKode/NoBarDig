package com.nimegami

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.net.URI
import java.net.URLEncoder
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Nimegami : MainAPI() {
    override var mainUrl = "https://nimegami.id"
    override var name = "Nimegami"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private val blockedAdHosts =
        setOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googletagmanager.com",
            "google-analytics.com",
            "monetag.com",
            "popads.net",
            "popcash.net",
            "propellerads.com",
        )

    private val directStreamHosts = setOf("stordl.halahgan.com")

    override val mainPage =
        mainPageOf(
            "" to "Updated Anime",
            "type/drama-movie" to "Drama Movie",
            "type/drama-series" to "Drama Series",
            "type/live" to "Live",
            "type/live-action" to "Live Action",
            "type/tv" to "Anime",
            "type/movie" to "Movie",
            "type/ona" to "ONA",
            "type/ova" to "OVA",
            "type/ova/special" to "OVA Special",
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(buildPageUrl(request.data, page)).document
        val items =
            document.select("div.post-article article, div.archive article")
                .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(request.name, items, isHorizontalImages = false),
            hasNext = document.selectFirst("a.next.page-numbers") != null,
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        if (encodedQuery.isBlank()) return emptyList()

        val results = mutableListOf<SearchResponse>()
        for (page in 1..2) {
            val document = app.get(
                "$mainUrl/page/$page/?s=$encodedQuery&post_type=post"
            ).document

            results.addAll(
                document
                    .select("div.archive article, div.post-article article")
                    .mapNotNull { element -> element.toSearchResult() }
            )
        }

        return results.distinctBy { result -> result.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.infoText("Judul")
                ?: document.selectFirst("h1.title")?.text()?.cleanPageTitle()
                ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.cleanPageTitle()
                ?: throw ErrorLoadingException("Judul tidak ditemukan")

        val pageTitle = document.selectFirst("h1.title")?.text().orEmpty()
        val type = getType(document.infoText("Type").orEmpty())
        val poster = document.posterFrom("div.coverthumbnail img")
        val backgroundPoster =
            document.posterFrom("div.thumbnail-a img")
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.toAbsoluteUrl()
        val year =
            Regex("""\b(?:19|20)\d{2}\b""")
                .find(document.infoText("Musim / Rilis").orEmpty())
                ?.value
                ?.toIntOrNull()
        val tags = document.infoTags("Kategori")
        val description =
            document.select("#Sinopsis p")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .ifBlank {
                    document.selectFirst("meta[property=og:description], meta[name=description]")
                        ?.attr("content")
                        .orEmpty()
                        .trim()
                }
        val trailer = document.selectFirst("#Trailer iframe[src]")?.attr("src")?.toAbsoluteUrl()

        val episodes =
            document.select("div.list_eps_stream li.select-eps[data]")
                .mapNotNull { element ->
                    val data = element.attr("data").trim().takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val episodeNumber = element.extractEpisodeNumber()
                    newEpisode(data) {
                        this.name = element.text().trim().takeIf { it.isNotBlank() }
                        this.episode = episodeNumber
                    }
                }
                .sortedBy { it.episode ?: Int.MAX_VALUE }

        return newAnimeLoadResponse(title, url, type) {
            engName = title
            posterUrl = poster
            backgroundPosterUrl = backgroundPoster
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = getStatus(pageTitle)
            plot = description.takeIf { it.isNotBlank() }
            this.tags = tags
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        // Atribut data pada tombol episode sudah berisi JSON Base64. Membacanya langsung
        // menghindari eksekusi JavaScript, overlay, redirect, dan popup iklan halaman.
        val decoded = runCatching { base64Decode(data.trim()) }.getOrNull() ?: return false
        val sourceGroups = tryParseJson<ArrayList<Sources>>(decoded).orEmpty()
        val candidates =
            sourceGroups.flatMap { group ->
                group.url.orEmpty()
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .filter(::isUsableStreamUrl)
                    .distinct()
                    .map { url -> StreamCandidate(url, group.format) }
                    .toList()
            }.distinctBy { it.url }

        candidates.amap { candidate ->
            loadFixedExtractor(
                url = candidate.url,
                quality = candidate.quality,
                referer = "$mainUrl/",
                subtitleCallback = subtitleCallback,
                callback = callback,
            )
        }

        return candidates.isNotEmpty()
    }

    private suspend fun loadFixedExtractor(
        url: String,
        quality: String?,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val qualityValue = getQualityFromName(quality)

        if (isDirectStream(url)) {
            callback(
                newExtractorLink(name, "$name ${quality.orEmpty()}".trim(), url) {
                    this.referer = referer
                    this.quality = qualityValue
                }
            )
            return
        }

        loadExtractor(url, referer, subtitleCallback) { link ->
            link.quality = qualityValue
            callback(link)
        }
    }

    private fun buildPageUrl(path: String, page: Int): String {
        val normalizedPath = path.trim('/')
        return when {
            normalizedPath.isBlank() && page <= 1 -> "$mainUrl/"
            normalizedPath.isBlank() -> "$mainUrl/page/$page/"
            page <= 1 -> "$mainUrl/$normalizedPath/"
            else -> "$mainUrl/$normalizedPath/page/$page/"
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val anchor = selectFirst("h2 a[href], a[href]") ?: return null
        val href = anchor.attr("href").trim().takeIf { it.isNotBlank() }?.let(::fixUrl)
            ?: return null
        val title =
            selectFirst("h2 a")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: anchor.attr("title").cleanPageTitle().takeIf { it.isNotBlank() }
                ?: return null
        val poster = posterUrl()
        val itemType = getType(select("div.bot-post a, div.post-2-complete").text())
        val episode =
            select("li")
                .firstOrNull { it.text().contains("Episode", ignoreCase = true) }
                ?.text()
                ?.let { Regex("""(?i)Episode\s*:?\s*(\d+)""").find(it)?.groupValues?.getOrNull(1) }
                ?.toIntOrNull()

        return newAnimeSearchResponse(title, href, itemType) {
            posterUrl = poster
            addSub(episode)
        }
    }

    private fun Element.posterUrl(): String? {
        val image = selectFirst("img") ?: return null
        return sequenceOf("data-src", "data-lazy-src", "src")
            .map { image.attr(it).trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("data:") }
            ?.let(::fixUrl)
    }

    private fun Document.posterFrom(selector: String): String? =
        selectFirst(selector)?.let { image ->
            sequenceOf("data-src", "data-lazy-src", "src")
                .map { image.attr(it).trim() }
                .firstOrNull { it.isNotBlank() && !it.startsWith("data:") }
                ?.toAbsoluteUrl()
        }

    private fun Document.infoRow(label: String): Element? {
        val wanted = label.normalizeLabel()
        return select("#Info tr").firstOrNull { row ->
            row.selectFirst("th, td")?.text()?.normalizeLabel() == wanted
        }
    }

    private fun Document.infoText(vararg labels: String): String? =
        labels.firstNotNullOfOrNull { label ->
            infoRow(label)
                ?.select("th, td")
                ?.getOrNull(1)
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }

    private fun Document.infoTags(label: String): List<String> {
        val valueCell = infoRow(label)?.select("th, td")?.getOrNull(1) ?: return emptyList()
        val linkedTags = valueCell.select("a").map { it.text().trim() }.filter { it.isNotBlank() }
        return if (linkedTags.isNotEmpty()) {
            linkedTags.distinct()
        } else {
            valueCell.text().split(',').map(String::trim).filter(String::isNotBlank).distinct()
        }
    }

    private fun Element.extractEpisodeNumber(): Int? {
        val patterns =
            listOf(
                Regex("""(?i)play[_\s-]*eps[_\s-]*(\d+)"""),
                Regex("""(?i)Episode\s*(\d+)"""),
            )
        val values = listOf(attr("id"), attr("title"), text())
        return values.firstNotNullOfOrNull { value ->
            patterns.firstNotNullOfOrNull { regex ->
                regex.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
        }
    }

    private fun getType(raw: String): TvType =
        when {
            raw.contains("Movie", ignoreCase = true) -> TvType.AnimeMovie
            raw.contains("OVA", ignoreCase = true) || raw.contains("Special", ignoreCase = true) ->
                TvType.OVA
            else -> TvType.Anime
        }

    private fun getStatus(pageTitle: String): ShowStatus =
        if (Regex("""(?i)\b(?:End|Complete|Completed)\b""").containsMatchIn(pageTitle)) {
            ShowStatus.Completed
        } else {
            ShowStatus.Ongoing
        }

    private fun isUsableStreamUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme !in setOf("http", "https")) return false
        val host = uri.host?.lowercase().orEmpty()
        if (host.isBlank()) return false
        return blockedAdHosts.none { blocked -> host == blocked || host.endsWith(".$blocked") }
    }

    private fun isDirectStream(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        return host in directStreamHosts ||
            Regex("""(?i)\.(?:mp4|mkv|m3u8)(?:$|[?&#])""").containsMatchIn(url)
    }

    private fun String.cleanPageTitle(): String =
        replace(Regex("""(?i)\s*-\s*Nimegami\s*$"""), "")
            .replace(Regex("""(?i)\s+Sub\s+Indo(?:\s*:.*)?$"""), "")
            .trim()

    private fun String.normalizeLabel(): String = trim().trimEnd(':').trim().lowercase()

    private fun String.toAbsoluteUrl(): String? {
        val value = trim()
        if (value.isBlank() || value.startsWith("data:")) return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") || value.startsWith("https://") -> value
            else -> runCatching { URI(mainUrl).resolve(value).toString() }.getOrNull()
        }
    }

    data class Sources(
        @JsonProperty("format") val format: String? = null,
        @JsonProperty("url") val url: ArrayList<String>? = arrayListOf(),
    )

    private data class StreamCandidate(val url: String, val quality: String?)
}
