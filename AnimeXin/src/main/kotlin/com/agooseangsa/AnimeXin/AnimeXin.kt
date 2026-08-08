package com.agooseangsa.AnimeXin

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class AnimeXin : MainAPI() {
    override var mainUrl = DEFAULT_MAIN_URL
    override var name = "Anime Xin"
    override var lang = "id"
    override val hasMainPage = true
    override val loadLinksTimeoutMs: Long = LOAD_LINKS_TIMEOUT_MS

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.Anime,
        TvType.OVA,
    )

    override val mainPage = mainPageOf(
        "anime/?status=&type=&order=update" to "Latest Release",
        "anime/?status=&type=Movie&order=update" to "New Movie",
    )

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false

    private val blockedCategoryKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_CATEGORIES.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    private val blockedTagKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_TAGS.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        val pageUrl = buildPagedUrl(request.data, page)
        val response = app.get(pageUrl, headers = browserHeaders())
        syncMainUrl(response.url)
        return newHomePageResponse(
            request,
            parseCards(response.document),
            hasNextPage(response.document),
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val response = app.get("$mainUrl/?s=$encoded", headers = browserHeaders())
        syncMainUrl(response.url)
        return parseCards(response.document)
    }

    override suspend fun load(url: String): LoadResponse? {
        ensureMainUrl()

        val initialResponse = app.get(updateUrl(url), headers = browserHeaders())
        syncMainUrl(initialResponse.url)

        var document = initialResponse.document
        var detailUrl = initialResponse.url
        var playerPageUrl = initialResponse.url

        val initialType = siteType(document)
        if (initialType == SiteType.MOVIE) {
            // Movie cards commonly point to the series/detail page, while the actual
            // player and mirror selector live on the single Movie episode page.
            // Keep metadata from the series page, but send loadLinks() to that episode.
            playerPageUrl = findMoviePlayerPageUrl(document, detailUrl) ?: detailUrl
        } else {
            val allEpisodesUrl = findAllEpisodesUrl(document)
            if (allEpisodesUrl != null && !samePage(allEpisodesUrl, detailUrl)) {
                val seriesResponse = app.get(updateUrl(allEpisodesUrl), headers = browserHeaders())
                if (seriesResponse.isSuccessful) {
                    syncMainUrl(seriesResponse.url)
                    document = seriesResponse.document
                    detailUrl = seriesResponse.url
                }
            }
        }

        val title = parseTitle(document) ?: return null
        val canonicalUrl = document.selectFirst("link[rel=canonical][href]")
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?.let(::absoluteUrl)
            ?: detailUrl

        val categories = parseTaxonomy(document, "category")
        val tags = parseTaxonomy(document, "tag")
        enforceContentAllowed(categories, tags)

        val poster = parsePoster(document)
        val plot = parsePlot(document)
        val genres = document.select(".genxed a[href]")
            .map { it.text().cleanText() }
            .filter { it.isNotBlank() }
            .distinct()
        val metadata = parseMetadata(document)
        val rating = parseRating(document)
        val year = YEAR_REGEX.find(metadata["released"].orEmpty())?.value?.toIntOrNull()
        val duration = parseDurationMinutes(metadata["duration"])
        val showStatus = parseShowStatus(metadata["status"])
        val trailers = parseTrailerUrls(document)
        val recommendations = parseRecommendations(document)

        return when (siteType(document)) {
            SiteType.MOVIE -> newMovieLoadResponse(
                title,
                canonicalUrl,
                TvType.Movie,
                playerPageUrl,
            ) {
                posterUrl = poster
                this.plot = plot
                this.tags = genres
                this.year = year
                this.duration = duration
                if (!rating.isNullOrBlank()) addScore(rating)
                if (trailers.isNotEmpty()) addTrailer(trailers)
                this.recommendations = recommendations
            }

            SiteType.OVA -> newAnimeLoadResponse(
                title,
                canonicalUrl,
                TvType.OVA,
                comingSoonIfNone = false,
            ) {
                posterUrl = poster
                this.plot = plot
                this.tags = genres
                this.year = year
                this.duration = duration
                this.showStatus = showStatus
                if (!rating.isNullOrBlank()) addScore(rating)
                if (trailers.isNotEmpty()) addTrailer(trailers)
                this.recommendations = recommendations
                addEpisodes(DubStatus.Subbed, parseEpisodes(document))
            }

            SiteType.ANIME -> newAnimeLoadResponse(
                title,
                canonicalUrl,
                TvType.Anime,
                comingSoonIfNone = false,
            ) {
                posterUrl = poster
                this.plot = plot
                this.tags = genres
                this.year = year
                this.duration = duration
                this.showStatus = showStatus
                if (!rating.isNullOrBlank()) addScore(rating)
                if (trailers.isNotEmpty()) addTrailer(trailers)
                this.recommendations = recommendations
                addEpisodes(DubStatus.Subbed, parseEpisodes(document))
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

        var response = app.get(updateUrl(data), headers = browserHeaders())
        syncMainUrl(response.url)

        var servers = parseAllowedServers(response.document)

        // Compatibility/self-heal path: cached Movie responses or direct Movie series
        // URLs may still point to the detail page. If no mirror selector exists there,
        // follow the actual Movie episode page and enumerate mirrors from that page.
        if (servers.isEmpty() && siteType(response.document) == SiteType.MOVIE) {
            val moviePlayerUrl = findMoviePlayerPageUrl(response.document, response.url)
            if (moviePlayerUrl != null && !samePage(moviePlayerUrl, response.url)) {
                val playerResponse = app.get(updateUrl(moviePlayerUrl), headers = browserHeaders())
                if (playerResponse.isSuccessful) {
                    syncMainUrl(playerResponse.url)
                    response = playerResponse
                    servers = parseAllowedServers(response.document)
                }
            }
        }

        val pageUrl = response.url

        // Resolve all mirrors concurrently with a per-host timeout. A broken/default
        // host must never keep Cloudstream inside loadLinks() long enough to prevent
        // the player/source selector from opening for healthy mirrors.
        val results = coroutineScope {
            servers.map { (serverUrl, label) ->
                async { extractServerWithTimeout(serverUrl, label, pageUrl) }
            }.map { it.await() }
        }

        // Preserve website mirror order while emitting only completed extraction results.
        results.flatMap { it.subtitles }.forEach(subtitleCallback)
        results.flatMap { it.links }.forEach(callback)

        // Important: Cloudstream loadExtractor() returns true when an extractor matches
        // the host even if extractor.getUrl() fails and emits zero links. Therefore this
        // provider only reports success when at least one real ExtractorLink was emitted.
        return results.any { it.links.isNotEmpty() }
    }

    private suspend fun extractServerWithTimeout(
        serverUrl: String,
        label: String,
        pageUrl: String,
    ): ServerExtractionResult {
        val links = mutableListOf<ExtractorLink>()
        val subtitles = mutableListOf<SubtitleFile>()

        try {
            withTimeoutOrNull(SERVER_EXTRACT_TIMEOUT_MS) {
                loadExtractor(
                    serverUrl,
                    pageUrl,
                    { subtitle -> subtitles += subtitle },
                ) { link ->
                    links += ExtractorLink(
                        source = "$name | ${link.source}",
                        name = "$label | ${link.name}",
                        url = link.url,
                        referer = link.referer,
                        quality = link.quality,
                        headers = link.headers,
                        extractorData = link.extractorData,
                        type = link.type,
                        audioTracks = link.audioTracks,
                    )
                }
            }
        } catch (error: CancellationException) {
            // Preserve Cloudstream/global coroutine cancellation; per-server timeout is
            // already converted to null by withTimeoutOrNull.
            throw error
        } catch (_: Throwable) {
            // A single malformed/broken extractor must not cancel healthy mirrors.
        }

        // Keep any real callback already emitted before a timeout; only the boolean
        // return from loadExtractor() is ignored because it is not proof of a link.
        return ServerExtractionResult(links, subtitles)
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val cards = document.select(".listupd article.bs").takeIf { it.isNotEmpty() }
            ?: document.select("article.bs").takeIf { it.isNotEmpty() }
            ?: document.select(".listupd .bsx")

        return cards.mapNotNull(::parseCard).distinctBy { it.url }
    }

    private fun parseCard(element: Element): SearchResponse? {
        val link = element.selectFirst(".bsx a[href], a[href]") ?: return null
        val href = link.attr("href").takeIf { it.isNotBlank() }?.let(::absoluteUrl) ?: return null

        val titleNode = element.selectFirst(".tt")
        val visibleTitle = titleNode?.ownText()?.cleanText()
            ?.takeIf { it.isNotBlank() }
            ?: element.selectFirst(".tt h2, h2, h3")?.text()?.cleanText()
            ?: link.attr("title").cleanText()
        val canonicalTitle = element.selectFirst(".tt h2, h2")?.text()?.cleanText()
        val title = normalizeListTitle(visibleTitle, canonicalTitle)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val poster = element.selectFirst("img")?.imageUrl()
        val episode = EPISODE_NUMBER_REGEX.find(
            element.selectFirst(".epx")?.text().orEmpty(),
        )?.groupValues?.getOrNull(1)?.toIntOrNull()

        return when (cardSiteType(element)) {
            SiteType.MOVIE -> newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
            }

            SiteType.OVA -> newAnimeSearchResponse(title, href, TvType.OVA) {
                posterUrl = poster
                addSub(episode)
            }

            SiteType.ANIME -> newAnimeSearchResponse(title, href, TvType.Anime) {
                posterUrl = poster
                addSub(episode)
            }
        }
    }

    private fun parseEpisodes(document: Document): List<Episode> {
        return document.select(".eplister li a[href]")
            .mapNotNull { link ->
                val href = link.attr("href").takeIf { it.isNotBlank() }?.let(::absoluteUrl)
                    ?: return@mapNotNull null
                val number = link.selectFirst(".epl-num")?.text()?.cleanText()?.toIntOrNull()
                    ?: EPISODE_NUMBER_REGEX.find(link.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
                val episodeTitle = link.selectFirst(".epl-title")?.text()?.cleanText()
                    ?.takeIf { it.isNotBlank() }
                    ?: number?.let { "Episode $it" }
                    ?: link.text().cleanText()

                newEpisode(href) {
                    name = episodeTitle
                    episode = number
                }
            }
            .distinctBy { it.data }
            .sortedWith(compareBy<Episode> { it.episode ?: Int.MAX_VALUE }.thenBy { it.name.orEmpty() })
    }

    private fun parseRecommendations(document: Document): List<SearchResponse> {
        val box = document.select(".bixbox").firstOrNull { candidate ->
            candidate.selectFirst(".releases")
                ?.text()
                ?.contains("Recommended Series", ignoreCase = true) == true
        } ?: return emptyList()

        val cards = box.select("article.bs").takeIf { it.isNotEmpty() } ?: box.select(".bsx")
        return cards.mapNotNull(::parseCard).distinctBy { it.url }
    }

    private fun parseTitle(document: Document): String? {
        return document.selectFirst(".infox h1, .infox h2, h1.entry-title")
            ?.text()
            ?.cleanText()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parsePoster(document: Document): String? {
        return document.selectFirst(".thumbook .thumb img, .bigcontent .thumb img, .infox .thumb img, .thumb img")
            ?.imageUrl()
            ?: document.selectFirst("meta[property=og:image][content]")
                ?.attr("content")
                ?.takeIf { it.isNotBlank() }
                ?.let(::absoluteUrl)
    }

    private fun parsePlot(document: Document): String? {
        return document.selectFirst(".synp .entry-content, .bigcontent .desc, .infox .desc, .desc")
            ?.text()
            ?.cleanText()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseRating(document: Document): String? {
        val content = document.selectFirst(
            ".thumbook .rating-prc meta[itemprop=ratingValue][content], " +
                ".bigcontent .rating-prc meta[itemprop=ratingValue][content]",
        )?.attr("content")?.cleanText()
        if (!content.isNullOrBlank()) return content

        return RATING_REGEX.find(
            document.selectFirst(".thumbook .rating strong, .bigcontent .rating strong")
                ?.text()
                .orEmpty(),
        )?.groupValues?.getOrNull(1)
    }

    private fun parseMetadata(document: Document): Map<String, String> {
        return document.select(".spe span")
            .mapNotNull { span ->
                val text = span.text().cleanText()
                val separator = text.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                val key = text.substring(0, separator).trim().lowercase(Locale.ROOT)
                val value = text.substring(separator + 1).trim()
                key.takeIf { it.isNotBlank() && value.isNotBlank() }?.let { it to value }
            }
            .toMap()
    }

    private fun parseTrailerUrls(document: Document): List<String> {
        return document.select(
            ".trailer iframe[src], #trailer iframe[src], .bixbox.trailer iframe[src], " +
                ".trailer a[href], a.trailer[href]",
        ).mapNotNull { element ->
            val raw = element.attr("src").ifBlank { element.attr("href") }
            raw.takeIf { it.isNotBlank() }?.let(::absoluteUrl)
        }.filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
    }

    private fun parseTaxonomy(document: Document, taxonomy: String): List<String> {
        val needle = "/$taxonomy/"
        return document.select("article.hentry a[href], .bigcontent a[href]")
            .filter { it.attr("href").contains(needle, ignoreCase = true) }
            .map { it.text().cleanText() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun cardSiteType(element: Element): SiteType {
        return siteTypeFromText(
            element.selectFirst(".typez, .type")?.text(),
        )
    }

    private fun siteType(document: Document): SiteType {
        return siteTypeFromText(parseMetadata(document)["type"])
    }

    private fun siteTypeFromText(value: String?): SiteType {
        val normalized = value?.cleanText()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            normalized.contains("movie") -> SiteType.MOVIE
            normalized.contains("ova") || normalized.contains("special") -> SiteType.OVA
            else -> SiteType.ANIME
        }
    }

    private fun parseShowStatus(value: String?): ShowStatus? {
        return when (value?.cleanText()?.lowercase(Locale.ROOT)) {
            "ongoing" -> ShowStatus.Ongoing
            "completed" -> ShowStatus.Completed
            else -> null
        }
    }

    private fun parseDurationMinutes(value: String?): Int? {
        val text = value?.cleanText()?.takeIf { it.isNotBlank() } ?: return null
        DURATION_CLOCK_REGEX.find(text)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: return@let
            val minutes = match.groupValues[2].toIntOrNull() ?: return@let
            return hours * 60 + minutes
        }
        return DURATION_MINUTES_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun findAllEpisodesUrl(document: Document): String? {
        return document.select(".naveps a[href]")
            .firstOrNull { it.text().contains("All Episodes", ignoreCase = true) }
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?.let(::absoluteUrl)
    }

    private fun findMoviePlayerPageUrl(document: Document, currentUrl: String): String? {
        if (hasMirrorSelector(document)) return currentUrl

        return document.select(
            ".eplister li a[href], .episodelist li a[href], " +
                "#singlepisode .episodelist li a[href], .lastend .inepcx a[href]",
        ).asSequence()
            .map { it.attr("href") }
            .filter { it.isNotBlank() }
            .map(::absoluteUrl)
            .firstOrNull { !samePage(it, currentUrl) }
    }

    private fun hasMirrorSelector(document: Document): Boolean {
        return document.selectFirst(
            ".mobius select.mirror option[value], .mobius select option[value], select.mirror option[value]",
        ) != null
    }

    private fun parseAllowedServers(document: Document): LinkedHashMap<String, String> {
        val servers = linkedMapOf<String, String>()
        document
            .select(".mobius select.mirror option[value], .mobius select option[value], select.mirror option[value]")
            .forEach { option ->
                val label = option.text().cleanText()
                if (!isAllowedServer(label)) return@forEach

                val serverUrl = decodeMirrorUrl(option.attr("value")) ?: return@forEach
                servers.putIfAbsent(serverUrl, label)
            }
        return servers
    }

    private fun hasNextPage(document: Document): Boolean {
        return document.selectFirst("a.next.page-numbers[href], .pagination a.next[href], link[rel=next][href]") != null
    }

    private fun buildPagedUrl(source: String, page: Int): String {
        val base = if (source.startsWith("http://") || source.startsWith("https://")) {
            source
        } else {
            "$mainUrl/${source.trimStart('/')}"
        }
        if (page <= 1) return base
        val separator = if ('?' in base) "&" else "?"
        return "$base${separator}page=$page"
    }

    private fun Element.imageUrl(): String? {
        for (attribute in IMAGE_ATTRIBUTES) {
            val value = attr(attribute).trim()
            if (value.isNotBlank() && !value.startsWith("data:image", ignoreCase = true)) {
                return absoluteUrl(value)
            }
        }

        val srcset = attr("srcset")
        if (srcset.isNotBlank()) {
            val candidate = srcset.substringBefore(',').trim().substringBefore(' ').trim()
            if (candidate.isNotBlank()) return absoluteUrl(candidate)
        }
        return null
    }

    private fun isAllowedServer(label: String): Boolean {
        return SERVER_LABEL_REGEX.containsMatchIn(label)
    }

    private fun decodeMirrorUrl(rawValue: String?): String? {
        val value = rawValue?.trim()?.takeIf { it.isNotBlank() } ?: return null
        normalizePlayableUrl(value)?.let { return it }

        val decoded = runCatching {
            String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null

        val parsed = Jsoup.parse(decoded)
        val rawUrl = parsed.selectFirst("iframe[src], [src]")?.attr("src")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return normalizePlayableUrl(rawUrl)
    }

    private fun normalizePlayableUrl(rawUrl: String): String? {
        val value = rawUrl.trim()
        val normalized = when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") || value.startsWith("https://") -> value
            else -> return null
        }
        return normalized.takeIf {
            runCatching {
                val uri = URI(it)
                (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
            }.getOrDefault(false)
        }
    }

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
                val response = runCatching { app.get(candidate, headers = browserHeaders(candidate)) }
                    .getOrNull()
                    ?: continue
                if (!response.isSuccessful) continue

                val resolved = normalizeHttpBaseUrl(response.url) ?: continue
                mainUrl = resolved
                mainUrlResolved = true
                return@withLock
            }

            mainUrl = DEFAULT_MAIN_URL
        }
    }

    private fun syncMainUrl(responseUrl: String?) {
        normalizeHttpBaseUrl(responseUrl)?.let { mainUrl = it }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(REMOTE_CONFIG_KEY) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index) }
            .mapNotNull(::normalizeHttpBaseUrl)
            .distinct()
    }

    private fun normalizeHttpBaseUrl(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() } ?: return null
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

    private fun absoluteUrl(rawUrl: String): String {
        val value = rawUrl.trim()
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") || value.startsWith("https://") -> value
            else -> fixUrl(value)
        }
    }

    private fun samePage(first: String, second: String): Boolean {
        return runCatching {
            val a = URI(first)
            val b = URI(second)
            a.path.trimEnd('/') == b.path.trimEnd('/') && a.query == b.query
        }.getOrDefault(first.trimEnd('/') == second.trimEnd('/'))
    }

    private fun browserHeaders(referer: String = mainUrl): Map<String, String> {
        return mapOf(
            "Referer" to referer,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        )
    }

    private fun normalizeListTitle(
        visibleTitle: String?,
        vararg canonicalTitles: String?,
    ): String? {
        val visible = visibleTitle?.cleanText()?.takeIf { it.isNotBlank() } ?: return null
        val stripped = visible.replaceFirst(NONTON_PREFIX, "").trim()
        if (stripped == visible) return visible

        val confirmed = canonicalTitles
            .mapNotNull { it?.cleanText()?.takeIf(String::isNotBlank) }
            .firstOrNull { it.equals(stripped, ignoreCase = true) }

        return confirmed ?: visible
    }

    private fun shouldBlockContent(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ): Boolean {
        if (categories.asSequence().mapNotNull(::normalizeTaxonomyName).any { it in blockedCategoryKeys }) {
            return true
        }
        return tags.asSequence().mapNotNull(::normalizeTaxonomyName).any { it in blockedTagKeys }
    }

    private fun enforceContentAllowed(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ) {
        if (shouldBlockContent(categories, tags)) {
            throw ErrorLoadingException("Konten diblokir oleh konfigurasi provider")
        }
    }

    private fun normalizeTaxonomyName(value: String?): String? {
        return value
            ?.cleanText()
            ?.takeIf { it.isNotBlank() }
            ?.lowercase(Locale.ROOT)
    }

    private fun String.cleanText(): String = trim().replace(WHITESPACE, " ")

    private data class ServerExtractionResult(
        val links: List<ExtractorLink>,
        val subtitles: List<SubtitleFile>,
    )

    private enum class SiteType {
        MOVIE,
        ANIME,
        OVA,
    }

    companion object {
        private const val DEFAULT_MAIN_URL = "https://animexin.dev"
        private const val SERVER_EXTRACT_TIMEOUT_MS = 15_000L
        private const val LOAD_LINKS_TIMEOUT_MS = 30_000L
        // Case-sensitive: preserve Website JSON Key capitalization exactly from Info.txt.
        private const val REMOTE_CONFIG_KEY = "AnimeXin"
        private const val MAIN_URL_JSON =
            "https://raw.githubusercontent.com/mj1Per127/agoosecloudstream/main/Website.json"

        private val BLOCKED_CATEGORIES = emptySet<String>()
        private val BLOCKED_TAGS = emptySet<String>()

        private val IMAGE_ATTRIBUTES = listOf(
            "data-src",
            "data-lazy-src",
            "data-lazy",
            "data-cfsrc",
            "data-original",
            "src",
        )

        private val SERVER_LABEL_REGEX =
            Regex("(?:Indonesia|\\bIndo\\b|All\\s*Sub)", RegexOption.IGNORE_CASE)
        private val EPISODE_NUMBER_REGEX =
            Regex("(?:Ep(?:isode)?\\s*)?(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        private val RATING_REGEX = Regex("([0-9]+(?:\\.[0-9]+)?)")
        private val YEAR_REGEX = Regex("\\b(?:19|20)\\d{2}\\b")
        private val DURATION_CLOCK_REGEX = Regex("\\b(\\d{1,2}):(\\d{2})\\b")
        private val DURATION_MINUTES_REGEX = Regex("\\b(\\d{1,3})\\b")
        private val NONTON_PREFIX = Regex("^Nonton\\s+", RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex("\\s+")
    }
}
