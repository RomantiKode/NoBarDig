package com.agooseangsa.Donghub

import android.util.Base64
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TrailerData
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class Donghub : MainAPI() {
    override var mainUrl = DEFAULT_MAIN_URL
    override var name = "Donghub"
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    /**
     * All three rows are verified sections of Home.txt.
     * Their target snapshots do not prove pagination, therefore page > 1 is intentionally empty.
     */
    override val mainPage = mainPageOf(
        HOME_POPULAR to "Popular Today",
        HOME_LATEST to "Latest Release",
        HOME_RECOMMENDATION to "Recommendation",
    )

    private val requestHeaders = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    )

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
        ensureMainUrl()

        val document = fetchDocument("$mainUrl/")
        val selector = when (request.data) {
            HOME_POPULAR -> ".releases.hothome + .listupd.popularslider article.bs"
            HOME_LATEST -> ".releases.latesthome + .listupd.normal article.bs"
            HOME_RECOMMENDATION -> ".series-gen .listupd article.bs"
            else -> ".releases.latesthome + .listupd.normal article.bs"
        }
        return newHomePageResponse(request.name, parseCards(document, selector))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        return parseCards(fetchDocument("$mainUrl/?s=$encoded"))
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureMainUrl()

        val initialDocument = fetchDocument(url)
        val parentUrl = findParentDetailUrl(initialDocument, url)
        val pageUrl = parentUrl ?: url
        val document = if (parentUrl != null) fetchDocument(parentUrl, url) else initialDocument

        val title = document.selectFirst("h1.entry-title, .bigcontent h1, .entry-title")
            ?.text()
            ?.cleanTitle()
            ?.takeIf(String::isNotBlank)
            ?: return null

        val poster = document.selectFirst(
            ".bigcontent .thumb img, .thumb img, .seriesthumb img, img.wp-post-image, .poster img"
        )?.imageUrl(pageUrl)

        val plot = extractPlot(document, title)
        val genres = document.select(".genxed a[href], a[rel=tag][href*=/genres/]")
            .map { it.text().trim() }
            .filter(String::isNotBlank)
            .distinct()

        val informationText = document.selectFirst(".spe")?.text().orEmpty()
        val year = parseYear(informationText)
        val duration = parseDurationMinutes(informationText)
        val status = parseStatus(informationText)
        val trailer = findTrailerUrl(document, pageUrl)

        return if (isMovie(document)) {
            val movieData = parseEpisodes(document, pageUrl).firstOrNull()?.data ?: pageUrl
            newMovieLoadResponse(title, pageUrl, TvType.Movie, movieData) {
                posterUrl = poster
                this.plot = plot
                tags = genres
                this.year = year
                this.duration = duration
                trailer?.let {
                    trailers = mutableListOf(TrailerData(it, pageUrl, false))
                }
            }
        } else {
            val episodes = parseEpisodes(document, pageUrl)
            newTvSeriesLoadResponse(title, pageUrl, TvType.Anime, episodes) {
                posterUrl = poster
                this.plot = plot
                tags = genres
                this.year = year
                this.duration = duration
                showStatus = status
                trailer?.let {
                    trailers = mutableListOf(TrailerData(it, pageUrl, false))
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        ensureMainUrl()
        val document = fetchDocument(data)
        val embedUrls = linkedSetOf<String>()
        val directUrls = linkedSetOf<String>()

        // Current/default server visible after the site play action.
        document.select(
            "#embed_holder iframe, #pembed iframe, iframe#video-player, " +
                ".player-embed iframe, .video-content iframe"
        ).forEach { iframe ->
            iframe.firstUrlAttribute()?.let {
                classifyPlayerUrl(it, data, embedUrls, directUrls)
            }
        }

        document.select(
            "#embed_holder source[src], #pembed source[src], .player-embed source[src], " +
                ".video-content source[src], .video-content video[src]"
        ).forEach { media ->
            media.firstUrlAttribute()?.let {
                classifyPlayerUrl(it, data, embedUrls, directUrls)
            }
        }

        // Important: every mirror is decoded independently. A broken default server must not
        // prevent working alternatives from being emitted to Cloudstream.
        document.select("select.mirror option[value], .mirror option[value], .mobius option[value]")
            .forEach { option ->
                decodeMirrorValue(option.attr("value"), data)
                    .forEach { classifyPlayerUrl(it, data, embedUrls, directUrls) }
            }

        var emittedCount = 0
        val trackedCallback: (ExtractorLink) -> Unit = { link ->
            emittedCount += 1
            callback(link)
        }

        directUrls.forEach { streamUrl ->
            val isHls = streamUrl.contains(".m3u8", ignoreCase = true) ||
                streamUrl.contains("/hls/", ignoreCase = true)
            trackedCallback(
                newExtractorLink(
                    source = name,
                    name = "$name Direct",
                    url = streamUrl,
                    type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                ) {
                    referer = data
                    quality = Qualities.Unknown.value
                }
            )
        }

        embedUrls.forEach { embedUrl ->
            val before = emittedCount
            try {
                loadExtractor(
                    embedUrl,
                    data,
                    subtitleCallback,
                    trackedCallback,
                )
            } catch (_: Throwable) {
                // Isolate server failures so the next mirror still gets a chance.
            }

            if (emittedCount == before && shouldUseWebViewFallback(embedUrl)) {
                resolvePlayerWithWebView(embedUrl, data)?.let(trackedCallback)
            }
        }

        return emittedCount > 0
    }

    /** Template v6 three-layer domain resolver. */
    private suspend fun ensureMainUrl() {
        if (mainUrlResolved) return

        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock

            val remoteCandidates = runCatching {
                JSONObject(app.get(MAIN_URL_JSON).text).readMainUrlCandidates()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidates + DEFAULT_MAIN_URL)
                .mapNotNull(::normalizeHttpBaseUrl)
                .distinct()

            for (candidate in candidates) {
                val response = runCatching { app.get(candidate) }.getOrNull() ?: continue
                if (!response.isSuccessful) continue

                val resolved = normalizeHttpBaseUrl(response.url) ?: continue
                mainUrl = resolved
                mainUrlResolved = true
                return@withLock
            }

            mainUrl = DEFAULT_MAIN_URL
        }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(REMOTE_CONFIG_KEY) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index) }
            .mapNotNull(::normalizeHttpBaseUrl)
            .distinct()
    }

    private fun normalizeHttpBaseUrl(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf(String::isNotBlank) ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun fetchDocument(url: String, referer: String = mainUrl): Document {
        ensureMainUrl()
        val response = app.get(url, headers = requestHeaders + ("Referer" to referer))
        normalizeHttpBaseUrl(response.url)?.let { mainUrl = it }
        return response.document
    }

    private fun parseCards(document: Document, selector: String? = null): List<SearchResponse> {
        val cards = if (selector != null) {
            document.select(selector)
        } else {
            val preferred = document.select(".postbody .listupd article.bs, .listupd.normal article.bs")
            if (preferred.isNotEmpty()) preferred else document.select(".listupd article.bs")
        }

        return cards.mapNotNull { card ->
            val anchor = card.selectFirst(".bsx > a[href], a.tip[href], a[href]")
                ?: return@mapNotNull null
            val href = resolveUrl(anchor.attr("href"), mainUrl)
                ?.takeUnless(::isBlockedPlayerUrl)
                ?: return@mapNotNull null

            val canonicalTitle = card.selectFirst(".eggtitle")?.text()?.trim()
            val visibleTitle = card.selectFirst(".tt h2, h2[itemprop=headline], h2")?.text()?.trim()
                ?: anchor.attr("title").trim()
            val title = normalizeListTitle(visibleTitle, canonicalTitle)
                ?.cleanCardTitle(canonicalTitle)
                ?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null

            val poster = card.selectFirst("img")?.imageUrl(href)
            val typeText = card.selectFirst(".eggtype, .typez")?.text().orEmpty()

            if (typeText.contains("movie", ignoreCase = true)) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.Anime) {
                    posterUrl = poster
                }
            }
        }.distinctBy { it.url }
    }

    private fun findParentDetailUrl(document: Document, sourceUrl: String): String? {
        val allEpisodes = document.selectFirst(".naveps .nvsc a[href][aria-label*=Episode], .naveps .nvsc a[href]")
        val breadcrumbParent = document.select(".ts-breadcrumb a[href]").getOrNull(1)
        val candidates = listOfNotNull(allEpisodes, breadcrumbParent)

        return candidates.asSequence()
            .mapNotNull { resolveUrl(it.attr("href"), sourceUrl) }
            .filterNot(::isBlockedPlayerUrl)
            .firstOrNull { normalizeUrl(it) != normalizeUrl(sourceUrl) }
    }

    private fun parseEpisodes(document: Document, pageUrl: String): List<Episode> {
        return document.select(".eplister ul li a[href], .eplister li a[href]")
            .mapNotNull { anchor ->
                val episodeUrl = resolveUrl(anchor.attr("href"), pageUrl)
                    ?.takeUnless(::isBlockedPlayerUrl)
                    ?: return@mapNotNull null

                val episodeTitle = anchor.selectFirst(".epl-title")
                    ?.text()?.trim()?.takeIf(String::isNotBlank)
                    ?: anchor.attr("title").trim().takeIf(String::isNotBlank)
                    ?: anchor.text().trim()

                val episodeNumber = anchor.selectFirst(".epl-num")
                    ?.text()?.trim()?.toIntOrNull()
                    ?: episodeTitle.firstEpisodeNumber()

                newEpisode(episodeUrl) {
                    name = episodeTitle
                    episode = episodeNumber
                }
            }
            .distinctBy { it.data }
            .sortedWith(compareBy<Episode> { it.episode ?: Int.MAX_VALUE }.thenBy { it.name })
    }

    private fun isMovie(document: Document): Boolean {
        val informationText = document.selectFirst(".spe")?.text().orEmpty()
        return Regex("(?i)\\bType\\s*:\\s*Movie\\b").containsMatchIn(informationText)
    }

    private fun extractPlot(document: Document, title: String): String? {
        val candidates = listOf(
            document.selectFirst(".bixbox.synp .entry-content")?.text(),
            document.selectFirst(".entry-content[itemprop=description]")?.text(),
            document.selectFirst(".desc.mindes, .desc, .mindes")?.text(),
            document.selectFirst("meta[name=description]")?.attr("content"),
            document.selectFirst("meta[property=og:description]")?.attr("content"),
        )

        return candidates.asSequence()
            .mapNotNull { it?.trim()?.replace(WHITESPACE, " ")?.takeIf(String::isNotBlank) }
            .firstOrNull { !it.equals(title, ignoreCase = true) }
    }

    private fun parseYear(informationText: String): Int? {
        val released = Regex("(?i)Released\\s*:\\s*[^0-9]*(20\\d{2}|19\\d{2})")
            .find(informationText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return released ?: Regex("\\b(19|20)\\d{2}\\b")
            .find(informationText)?.value?.toIntOrNull()
    }

    private fun parseDurationMinutes(informationText: String): Int? {
        Regex("(?i)Duration\\s*:\\s*(\\d+)\\s*:\\s*(\\d+)")
            .find(informationText)?.let { match ->
                val hours = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
                val minutes = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                return hours * 60 + minutes
            }

        return Regex("(?i)Duration\\s*:\\s*(\\d+)\\s*(?:min|menit)")
            .find(informationText)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun parseStatus(informationText: String): ShowStatus? = when {
        informationText.contains("completed", ignoreCase = true) -> ShowStatus.Completed
        informationText.contains("ongoing", ignoreCase = true) -> ShowStatus.Ongoing
        else -> null
    }

    /**
     * The supplied target snapshots contain no trailer element. This helper is deliberately
     * semantic: it only returns a URL when the page itself marks an anchor/iframe as trailer.
     */
    private fun findTrailerUrl(document: Document, pageUrl: String): String? {
        return document.select("a[href], iframe[src]").asSequence().mapNotNull { element ->
            val marker = buildString {
                append(element.id()).append(' ')
                append(element.className()).append(' ')
                append(element.text())
            }
            if (!marker.contains("trailer", ignoreCase = true)) return@mapNotNull null
            val raw = element.attr("href").ifBlank { element.attr("src") }
            resolveUrl(raw, pageUrl)
        }.firstOrNull()
    }

    private fun decodeMirrorValue(value: String, baseUrl: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("//")) {
            return listOfNotNull(resolveUrl(trimmed, baseUrl))
        }

        val decoded = runCatching {
            String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()?.trim().orEmpty()
        if (decoded.isEmpty()) return emptyList()

        val parsed = Jsoup.parse(decoded, baseUrl)
        val urls = parsed.select("iframe[src], iframe[data-src], source[src], video[src]")
            .mapNotNull { it.firstUrlAttribute() }
            .mapNotNull { resolveUrl(it, baseUrl) }
            .toMutableList()

        if (urls.isEmpty() && (
                decoded.startsWith("http://") ||
                    decoded.startsWith("https://") ||
                    decoded.startsWith("//")
            )
        ) {
            resolveUrl(decoded, baseUrl)?.let(urls::add)
        }
        return urls
    }

    private fun classifyPlayerUrl(
        rawUrl: String,
        baseUrl: String,
        embeds: MutableSet<String>,
        direct: MutableSet<String>,
    ) {
        val url = resolveUrl(rawUrl, baseUrl) ?: return
        if (isBlockedPlayerUrl(url)) return
        if (
            url.contains(".m3u8", ignoreCase = true) ||
            url.contains(".mp4", ignoreCase = true) ||
            url.contains("/hls/", ignoreCase = true)
        ) {
            direct += url
        } else {
            embeds += url
        }
    }

    private suspend fun resolvePlayerWithWebView(embedUrl: String, referer: String): ExtractorLink? {
        val response = try {
            app.get(
                embedUrl,
                referer = referer,
                interceptor = WebViewResolver(MEDIA_URL_REGEX),
            )
        } catch (_: Throwable) {
            return null
        }

        val mediaUrl = response.url
        if (!MEDIA_URL_REGEX.containsMatchIn(mediaUrl)) return null
        val isHls = mediaUrl.contains(".m3u8", ignoreCase = true)
        return newExtractorLink(
            source = name,
            name = "$name WebView",
            url = mediaUrl,
            type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
        ) {
            this.referer = embedUrl
            quality = Qualities.Unknown.value
        }
    }

    private fun shouldUseWebViewFallback(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return host == "odysee.com" || host.endsWith(".odysee.com") || host == "play.d.tube"
    }

    private fun Element.firstUrlAttribute(): String? {
        return listOf("src", "data-src", "data-litespeed-src", "data-lazy-src")
            .asSequence()
            .map { attr(it).trim() }
            .firstOrNull(String::isNotBlank)
    }

    private fun Element.imageUrl(baseUrl: String): String? {
        val raw = listOf("data-src", "data-lazy-src", "data-original", "src")
            .asSequence()
            .map { attr(it).trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("data:image") }
            ?: attr("srcset").substringBefore(',').trim().substringBefore(' ')
                .takeIf(String::isNotBlank)
        return raw?.let { resolveUrl(it, baseUrl) }
    }

    private fun resolveUrl(rawUrl: String, baseUrl: String): String? {
        val cleaned = rawUrl.trim().replace("\\/", "/")
        if (
            cleaned.isEmpty() ||
            cleaned.startsWith("javascript:", ignoreCase = true) ||
            cleaned.startsWith('#')
        ) {
            return null
        }
        return runCatching {
            when {
                cleaned.startsWith("//") -> "https:$cleaned"
                cleaned.startsWith("http://") || cleaned.startsWith("https://") -> cleaned
                else -> URI(baseUrl).resolve(cleaned).toString()
            }
        }.getOrNull()
    }

    private fun isBlockedPlayerUrl(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return BLOCKED_AD_HOST_PARTS.any { host.contains(it) }
    }

    private fun normalizeListTitle(visibleTitle: String?, vararg canonicalTitles: String?): String? {
        val visible = visibleTitle?.trim()?.takeIf(String::isNotBlank) ?: return null
        val stripped = visible.replaceFirst(NONTON_PREFIX, "").trim()
        if (stripped == visible) return visible

        val confirmed = canonicalTitles
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .firstOrNull { it.equals(stripped, ignoreCase = true) }
        return confirmed ?: visible
    }

    private fun String.cleanCardTitle(canonicalTitle: String?): String {
        val canonical = canonicalTitle?.trim()?.takeIf(String::isNotBlank)
        return (canonical ?: this)
            .removeSuffix(" - Donghub")
            .replace(WHITESPACE, " ")
            .trim()
    }

    private fun String.cleanTitle(): String = trim()
        .removeSuffix(" - Donghub")
        .replace(WHITESPACE, " ")

    private fun String.firstEpisodeNumber(): Int? {
        return Regex("(?i)\\b(?:episode|ep)\\s*0*(\\d+)")
            .find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun normalizeUrl(url: String): String = url.substringBefore('#').trimEnd('/')

    companion object {
        private const val DEFAULT_MAIN_URL = "https://donghub.vip"
        private const val REMOTE_CONFIG_KEY = "Donghub"
        private const val MAIN_URL_JSON =
            "https://raw.githubusercontent.com/mj1Per127/agoosecloudstream/main/Website.json"

        private const val HOME_POPULAR = "home:popular"
        private const val HOME_LATEST = "home:latest"
        private const val HOME_RECOMMENDATION = "home:recommendation"

        private val BLOCKED_AD_HOST_PARTS = setOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googletagmanager.com",
            "google-analytics.com",
            "dtscout.com",
            "histats.com",
            "onesignal.com",
            "vafrousredware.cyou",
        )

        private val NONTON_PREFIX = Regex("^Nonton\\s+", RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex("\\s+")
        private val MEDIA_URL_REGEX = Regex(
            "(?i)(?:\\.m3u8(?:[?#].*)?$|\\.mp4(?:[?#].*)?$)"
        )
    }
}
