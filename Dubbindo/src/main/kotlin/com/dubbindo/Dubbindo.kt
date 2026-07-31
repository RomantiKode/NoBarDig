package com.dubbindo

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
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

    // Seluruh kategori utama sudah tersedia dalam satu dokumen homepage.
    override val mainPage = mainPageOf(mainUrl to "Dubbindo")

    private val invisibleTextRegex = Regex(
        "[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF]"
    )
    private val whitespaceRegex = Regex("\\s+")
    private val yearRegex = Regex("""\b(?:19|20)\d{2}\b""")
    private val episodeRegex = Regex("""(?i)\b(?:episode|eps?\.?|ep\.?)\s*[-_:]?\s*(\d{1,4})\b""")
    private val qualityRegex = Regex("""(?i)\b(2160|1440|1080|720|480|360|240|144)p?\b""")
    private val urlQualityRegex = Regex("""(?i)(?:^|[_-])(2160|1440|1080|720|480|360|240|144)p(?:[_\-.]|$)""")
    private val animeMovieCategoryRegex = Regex("""/videos/category/4(?:$|[/?])""")

    // Hanya array sumber milik player yang dipindai. Script iklan lain diabaikan.
    private val updateSrcRegex = Regex("""(?is)(?:player\s*\.\s*)?updateSrc\s*\(\s*\[(.*?)]\s*\)""")
    private val sourceObjectRegex = Regex("""(?s)\{(.*?)}""")
    private val jsSourceUrlRegex = Regex("""(?i)\bsrc\s*:\s*['"]([^'"]+)['"]""")
    private val jsLabelRegex = Regex("""(?i)\blabel\s*:\s*['"]([^'"]+)['"]""")
    private val jsResolutionRegex = Regex("""(?i)\bres\s*:\s*['"]?(\d{3,4})['"]?""")

    private val blockedSchemes = listOf("javascript:", "data:", "blob:", "about:")

    private fun String.cleanDisplayText(): String =
        replace(invisibleTextRegex, "")
            .replace(whitespaceRegex, " ")
            .trim()

    /**
     * Jsoup hanya mengurai HTML dan tidak menjalankan JavaScript. Pembersihan ini
     * mencegah selector provider menyentuh modal umur, banner cookie, VAST/IMA,
     * tracker, dan elemen popup yang ada di HTML sumber.
     *
     * Script inline tetap dipertahankan karena kualitas video berada di
     * player.updateSrc([...]). Script eksternal tidak diperlukan oleh provider.
     */
    private fun Document.removeNoise(): Document = apply {
        select(
            "#pop_up_18, .cc-window, #header_ad_, " +
                ".ads-placment, .ad-container, .video-ads, .video-player-ads, " +
                ".ima-ad-container, .ads-overlay-info, .video_js_skip_ad, " +
                ".vjs-ad-playing, [id*=ad-container], [id*=adContainer], " +
                "iframe[src*=doubleclick], iframe[src*=googlesyndication], " +
                "iframe[src*=adservice], iframe[src*=histats]"
        ).remove()
        select("script[src], noscript").remove()
    }

    private fun normalizeUrl(raw: String?, baseUrl: String = mainUrl): String? {
        val clean = raw
            ?.trim()
            ?.replace("\\/", "/")
            ?.replace("&amp;", "&")
            ?.takeIf(String::isNotBlank)
            ?: return null

        if (blockedSchemes.any { clean.startsWith(it, ignoreCase = true) }) return null

        return runCatching {
            when {
                clean.startsWith("//") -> "https:$clean"
                clean.startsWith("http://", true) || clean.startsWith("https://", true) -> clean
                else -> URI(baseUrl).resolve(clean).toString()
            }
        }.getOrNull()
    }

    private fun hostOf(url: String): String? = runCatching {
        URI(url).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()

    private fun isSameHost(url: String): Boolean {
        val sourceHost = hostOf(mainUrl) ?: return false
        return hostOf(url) == sourceHost
    }

    private fun isContentUrl(url: String): Boolean {
        if (!isSameHost(url)) return false
        val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        return path.startsWith("/watch/") || path.startsWith("/shorts/")
    }

    private fun Element.contentAnchor(): Element? = select(
        ".video-list-title a[href], " +
            ".ra-title .video-title a[href], " +
            ".video-title a[href], " +
            ".video-list-image a[href], " +
            ".ra-thumb a[href]"
    ).firstOrNull { anchor ->
        normalizeUrl(anchor.attr("href"))?.let(::isContentUrl) == true
    }

    private fun Element.imageUrl(): String? = listOf(
        attr("data-src"),
        attr("data-original"),
        attr("data-lazy-src"),
        attr("src")
    ).firstOrNull { value ->
        value.startsWith("http://", true) ||
            value.startsWith("https://", true) ||
            value.startsWith("//") ||
            value.startsWith("/")
    }

    private fun Element.categoryType(fallback: TvType = TvType.Movie): TvType {
        val isAnimeMovie = select("a[href*='/videos/category/4']")
            .any { animeMovieCategoryRegex.containsMatchIn(it.attr("href").lowercase()) }
        return if (isAnimeMovie) TvType.AnimeMovie else fallback
    }

    private fun Element.toSearchResult(fallbackType: TvType = TvType.Movie): SearchResponse? {
        val anchor = contentAnchor() ?: return null
        val href = normalizeUrl(anchor.attr("href"))?.takeIf(::isContentUrl) ?: return null

        val titleElement = selectFirst(
            ".video-list-title h4[title], .video-list-title h4, " +
                ".ra-title .video-title a, .video-title a, h4[title], h4"
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
        val year = yearRegex.findAll(title).lastOrNull()?.value?.toIntOrNull()

        return newMovieSearchResponse(title, href, categoryType(fallbackType)) {
            posterUrl = poster
            this.year = year
        }
    }

    private fun Element.sectionName(): String {
        val heading = selectFirst(".title h4, .title h3, h4, h3") ?: return "Video"
        val copy = heading.clone()
        copy.select(".view_more_link, svg, i").remove()
        return copy.text().cleanDisplayText().ifBlank { "Video" }
    }

    private fun sectionType(name: String): TvType =
        if (name.contains("anime movie", ignoreCase = true)) TvType.AnimeMovie else TvType.Movie

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(emptyList(), hasNext = false)

        val document = app.get(request.data).document.removeNoise()
        val lists = document.select("div.home-page-categories")
            .mapNotNull { section ->
                val sectionName = section.sectionName()
                val items = section.select("div.video-list")
                    .mapNotNull { it.toSearchResult(sectionType(sectionName)) }
                    .distinctBy { it.url }
                if (items.isEmpty()) null else HomePageList(sectionName, items, true)
            }

        val finalLists = if (lists.isNotEmpty()) {
            lists
        } else {
            val items = document.select("div.video-list")
                .mapNotNull { it.toSearchResult() }
                .distinctBy { it.url }
            if (items.isEmpty()) emptyList() else listOf(HomePageList("Video terbaru", items, true))
        }

        return newHomePageResponse(finalLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cleanQuery = query.cleanDisplayText()
        if (cleanQuery.isBlank()) return emptyList()

        val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
        val document = app.get("$mainUrl/search?keyword=$encoded").document.removeNoise()

        return document.select("div.video-list, div.related-video-wrapper")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    private fun Document.effectivePageUrl(requestedUrl: String): String {
        val canonical = selectFirst("link[rel=canonical][href]")
            ?.attr("href")
            ?.let { normalizeUrl(it, requestedUrl) }
            ?.takeIf(::isContentUrl)
        return canonical ?: requestedUrl
    }

    /**
     * Hanya kategori yang melekat pada metadata video yang diperiksa. Menu global
     * juga berisi tautan Anime Movie pada setiap halaman dan tidak boleh dipakai
     * untuk menentukan tipe konten.
     */
    private fun Document.pageType(): TvType {
        val categoryUrls = select(
            ".video-published a[href*='/videos/category/'], " +
                ".video-published a[href*='/category/']"
        ).map { it.attr("href").lowercase() }
        return if (categoryUrls.any(animeMovieCategoryRegex::containsMatchIn)) {
            TvType.AnimeMovie
        } else {
            TvType.Movie
        }
    }

    private fun Document.pageTitle(): String? = listOf(
        selectFirst(".video-big-title h1")?.text(),
        selectFirst("meta[property='og:title']")?.attr("content"),
        selectFirst("meta[name=title]")?.attr("content"),
        title()
    ).firstNotNullOfOrNull { it?.cleanDisplayText()?.takeIf(String::isNotBlank) }

    private fun Document.pageDescription(): String? = listOf(
        selectFirst(".watch-video-description p")?.text(),
        selectFirst("meta[property='og:description']")?.attr("content"),
        selectFirst("meta[name=description]")?.attr("content")
    ).firstNotNullOfOrNull { it?.cleanDisplayText()?.takeIf(String::isNotBlank) }

    private fun Document.pagePoster(pageUrl: String): String? = listOf(
        selectFirst("meta[property='og:image']")?.attr("content"),
        selectFirst("meta[name=thumbnail]")?.attr("content"),
        selectFirst("meta[name='twitter:image']")?.attr("content"),
        selectFirst(".video-player video[poster]")?.attr("poster"),
        selectFirst("video#my-video[poster]")?.attr("poster")
    ).firstNotNullOfOrNull { normalizeUrl(it, pageUrl) }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document.removeNoise()
        val pageUrl = document.effectivePageUrl(url)
        val title = document.pageTitle() ?: return null
        val description = document.pageDescription()
        val poster = document.pagePoster(pageUrl)
        val year = yearRegex.findAll(title).lastOrNull()?.value?.toIntOrNull()
        val episode = episodeRegex.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val tags = document.select(
            ".video-published a[href*='/videos/category/'], " +
                ".video-published a[href*='/category/']"
        ).map { it.text().cleanDisplayText() }
            .filter(String::isNotBlank)
            .distinct()

        val recommendations = document.select("div.related-video-wrapper")
            .mapNotNull { it.toSearchResult() }
            .filter { it.url != url && it.url != pageUrl }
            .distinctBy { it.url }
            .take(30)

        return newMovieLoadResponse(title, url, document.pageType(), pageUrl) {
            posterUrl = poster
            plot = description
            this.year = year
            this.tags = tags
            this.recommendations = recommendations
            if (episode != null && plot.isNullOrBlank()) plot = "Episode $episode"
        }
    }

    private fun isDirectMedia(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        return path.endsWith(".mp4") || path.endsWith(".m3u8") ||
            path.endsWith(".mkv") || path.endsWith(".avi") || path.endsWith(".webm")
    }

    private fun qualityFromText(text: String?): Int =
        qualityRegex.find(text.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value

    private fun qualityFromUrl(url: String): Int =
        urlQualityRegex.find(runCatching { URI(url).path.orEmpty() }.getOrDefault(url))
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value

    private fun normalizedLabel(url: String, declaredLabel: String?): String? {
        val urlQuality = qualityFromUrl(url)
        return if (urlQuality > Qualities.Unknown.value) "${urlQuality}p" else declaredLabel
    }

    private fun sourceType(url: String): ExtractorLinkType =
        if (runCatching { URI(url).path.orEmpty().endsWith(".m3u8", true) }.getOrDefault(false)) {
            ExtractorLinkType.M3U8
        } else {
            ExtractorLinkType.VIDEO
        }

    private data class MediaSource(val url: String, val label: String? = null)

    private fun MutableMap<String, MediaSource>.putBest(source: MediaSource) {
        val current = this[source.url]
        val currentQuality = qualityFromText(current?.label)
        val newQuality = qualityFromText(source.label)
        if (current == null || newQuality > currentQuality || current.label.isNullOrBlank()) {
            this[source.url] = source
        }
    }

    private fun Document.extractSources(pageUrl: String): List<MediaSource> {
        val sources = linkedMapOf<String, MediaSource>()

        select(
            ".video-player video source[src], .video-player video source[data-src], " +
                ".video-player video[src], .video-player video[data-src], " +
                "video#my-video source[src], video#my-video source[data-src], " +
                "video#my-video[src], video#my-video[data-src]"
        ).forEach { element ->
            val rawUrl = element.attr("src").ifBlank { element.attr("data-src") }
            val mediaUrl = normalizeUrl(rawUrl, pageUrl)?.takeIf(::isDirectMedia)
                ?: return@forEach
            val resolution = element.attr("res")
                .ifBlank { element.attr("size") }
                .ifBlank { element.attr("data-res") }
            val label = resolution.toIntOrNull()?.let { "${it}p" }
                ?: element.attr("data-quality")
                    .ifBlank { element.attr("title") }
                    .ifBlank { element.attr("label") }
                    .cleanDisplayText()
                    .takeIf(String::isNotBlank)
            sources.putBest(MediaSource(mediaUrl, normalizedLabel(mediaUrl, label)))
        }

        select("script:not([src])").forEach { script ->
            val body = script.data().replace("\\/", "/")
            updateSrcRegex.findAll(body).forEach { arrayMatch ->
                sourceObjectRegex.findAll(arrayMatch.groupValues[1]).forEach sourceLoop@{ objectMatch ->
                    val block = objectMatch.groupValues[1]
                    val rawUrl = jsSourceUrlRegex.find(block)?.groupValues?.getOrNull(1)
                        ?: return@sourceLoop
                    val mediaUrl = normalizeUrl(rawUrl, pageUrl)?.takeIf(::isDirectMedia)
                        ?: return@sourceLoop
                    val resolution = jsResolutionRegex.find(block)?.groupValues?.getOrNull(1)
                    val label = resolution?.let { "${it}p" }
                        ?: jsLabelRegex.find(block)?.groupValues?.getOrNull(1)?.cleanDisplayText()
                    sources.putBest(MediaSource(mediaUrl, normalizedLabel(mediaUrl, label)))
                }
            }
        }

        return sources.values.sortedWith(
            compareByDescending<MediaSource> { qualityFromText(it.label) }
                .thenBy { it.url }
        )
    }

    private fun Document.extractSubtitles(pageUrl: String): List<SubtitleFile> =
        select(
            ".video-player track[kind=subtitles][src], " +
                ".video-player track[kind=captions][src], " +
                "video#my-video track[kind=subtitles][src], " +
                "video#my-video track[kind=captions][src]"
        ).mapNotNull { track ->
            val subtitleUrl = normalizeUrl(track.attr("src"), pageUrl) ?: return@mapNotNull null
            val language = track.attr("label")
                .ifBlank { track.attr("srclang") }
                .ifBlank { "Subtitle" }
                .cleanDisplayText()
            SubtitleFile(language, subtitleUrl)
        }.distinctBy { it.url }

    private fun Document.internalEmbed(pageUrl: String): String? =
        select(
            ".pt_embed_playr iframe[src*='/embed/'], " +
                ".video-player iframe[src*='/embed/'], iframe[src*='/embed/']"
        ).firstNotNullOfOrNull { frame ->
            normalizeUrl(frame.attr("src"), pageUrl)?.takeIf { embedUrl ->
                isSameHost(embedUrl) && runCatching {
                    URI(embedUrl).path.orEmpty().startsWith("/embed/")
                }.getOrDefault(false)
            }
        }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pageUrl = normalizeUrl(data)?.takeIf(::isContentUrl) ?: return false
        val document = app.get(pageUrl).document.removeNoise()

        for (subtitle in document.extractSubtitles(pageUrl)) subtitleCallback(subtitle)

        var sources = document.extractSources(pageUrl)
        var refererUrl = pageUrl

        // Fallback hanya mengizinkan satu embed internal dan satu request tambahan.
        if (sources.isEmpty()) {
            val embedUrl = document.internalEmbed(pageUrl)
            if (embedUrl != null) {
                val embedDocument = app.get(embedUrl, referer = pageUrl).document.removeNoise()
                sources = embedDocument.extractSources(embedUrl)
                refererUrl = embedUrl
            }
        }

        val emitted = linkedSetOf<String>()
        for (media in sources) {
            if (!emitted.add(media.url)) continue
            val label = media.label?.cleanDisplayText()?.takeIf(String::isNotBlank)
            callback(
                newExtractorLink(
                    source = name,
                    name = label?.let { "$name $it" } ?: name,
                    url = media.url,
                    type = sourceType(media.url)
                ) {
                    referer = refererUrl
                    headers = mapOf("Referer" to refererUrl, "Origin" to mainUrl)
                    quality = qualityFromText(label)
                }
            )
        }

        return emitted.isNotEmpty()
    }
}
