package com.agooseangsa.DrakorKita

import com.lagradost.cloudstream3.Episode
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
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

class DrakorKita : MainAPI() {
    override var mainUrl = DEFAULT_MAIN_URL
    override var name = "Drakor Kita"
    override var lang = "id"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries, TvType.Movie)

    /**
     * Info.txt leaves Homepage Sources / Order / Hide empty, so the provider auto-discovers
     * the three native rows that are present in the supplied homepage snapshot.
     */
    override val mainPage = mainPageOf(
        "/#latest" to "Eps Terbaru",
        "/#movies" to "Movie Terbaru",
        "/#series" to "Serie Terbaru",
    )

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false
    private var activeBaseUrl = DEFAULT_MAIN_URL
    private var remoteCandidatesCache = emptyList<String>()
    private var knownFamilyCandidatesCache = emptyList<String>()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) {
            return newHomePageResponse(
                list = HomePageList(request.name, emptyList(), false),
                hasNext = false,
            )
        }

        val document = getDocument("/")
        val heading = document.select("h4.heading1")
            .firstOrNull { it.text().trim().startsWith(request.name, ignoreCase = true) }
        val row = heading?.nextElementSibling()
        val items = row
            ?.select("a.poster[href*='/detail/']")
            ?.mapNotNull { it.toSearchResult(document) }
            ?.distinctBy { it.url }
            .orEmpty()

        return newHomePageResponse(
            list = HomePageList(request.name, items, false),
            hasNext = false,
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = getDocument("/all?q=${encode(query)}")
        return document.select("a.poster[href*='/detail/']")
            .mapNotNull { it.toSearchResult(document) }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = getDocument(url)
        val pageVars = parsePageVariables(document.html())
        val canonicalUrl = document.pickCanonicalUrl() ?: rebaseToActive(url)
        val title = pageVars["movie_title"].orEmpty()
            .ifBlank { document.pickTitle() }
            .ifBlank { slugTitle(canonicalUrl) }
        val cleanTitle = cleanDetailTitle(title)
        val poster = document.pickPoster()
        val plot = document.pickDescription()
        val genres = document.pickGenres()
        val info = document.pickInfoMap()
        val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val duration = parseDurationMinutes(info["video length"])
        val season = info["season"]?.filter { it.isDigit() }?.toIntOrNull()

        val calls = parseVideoCalls(document)
        val defaultCall = calls.firstOrNull()
        val initializer = parseEpisodeInitializer(document.html())
        val common = DrakorKitaResolver.ApiPayload(
            detailUrl = canonicalUrl,
            title = cleanTitle,
            movieId = initializer.movieId,
            episodeId = "",
            serverXid = defaultCall?.serverXid.orEmpty().ifBlank { "f1" },
            category = defaultCall?.category.orEmpty().ifBlank { initializer.category },
            language = defaultCall?.language.orEmpty().ifBlank { initializer.language },
            c = pageVars["c"].orEmpty(),
            t = pageVars["t"].orEmpty(),
            cApiHost = pageVars["c_api_host"].orEmpty()
                .ifBlank { pageVars["api_host"].orEmpty().replace(Regex("""/api/?$"""), "/c_api") }
                .ifBlank { DEFAULT_C_API },
            isMob = pageVars["is_mob"].orEmpty().ifBlank { "0" },
            isUc = pageVars["is_uc"].orEmpty().ifBlank { "0" },
            mediaType = defaultCall?.mediaType.orEmpty().ifBlank { "web" },
            servers = calls.map { "${it.kind}:${it.quality}" }.distinct().joinToString("|"),
        )

        val episodes = parseInlineEpisodes(document, common, poster, season)
        val typeText = info["type"].orEmpty()
        val isSeries = typeText.contains("TV", ignoreCase = true) || episodes.size > 1

        return if (isSeries) {
            val finalEpisodes = if (episodes.isNotEmpty()) {
                episodes
            } else {
                listOf(
                    newEpisode(common.toPayloadJson()) {
                        this.name = "Episode 1"
                        this.episode = 1
                        this.season = season
                        this.posterUrl = poster
                    }
                )
            }

            newTvSeriesLoadResponse(cleanTitle, canonicalUrl, TvType.AsianDrama, finalEpisodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
                this.duration = duration
            }
        } else {
            val directCall = defaultCall
            val moviePayload = if (directCall != null) {
                common.copy(
                    episodeId = directCall.episodeId,
                    serverXid = directCall.serverXid,
                    category = directCall.category,
                    language = directCall.language,
                    mediaType = directCall.mediaType,
                )
            } else {
                common
            }

            newMovieLoadResponse(cleanTitle, canonicalUrl, TvType.Movie, moviePayload.toPayloadJson()) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
                this.duration = duration
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val payload = DrakorKitaResolver.parsePayload(data)

        if (payload != null) {
            val apiHandled = DrakorKitaResolver.resolveApiPlayback(
                providerName = name,
                mainUrl = activeBaseUrl,
                payload = payload,
                subtitleCallback = subtitleCallback,
                callback = callback,
            )
            if (apiHandled) return true
        }

        val pageUrl = payload?.detailUrl ?: data
        return runCatching {
            val document = getDocument(pageUrl)
            DrakorKitaResolver.extractSubtitles(document, activeBaseUrl).forEach(subtitleCallback)

            // Detail HTML contains the currently active player. Never reuse the first-episode
            // iframe for another selected serial episode if API playback for that episode failed.
            val pageEpisodeId = document
                .selectFirst("#episode_lists a.current[data-epid], #episode_lists a.active[data-epid], #episode_lists a[data-epid]")
                ?.attr("data-epid")
                .orEmpty()
            val canUsePagePlayer = payload == null ||
                payload.episodeId.isBlank() ||
                pageEpisodeId.isBlank() ||
                payload.episodeId == pageEpisodeId

            if (!canUsePagePlayer) return@runCatching false

            DrakorKitaResolver.resolveCandidates(
                providerName = name,
                mainUrl = activeBaseUrl,
                pageUrl = document.pickCanonicalUrl() ?: rebaseToActive(pageUrl),
                candidates = DrakorKitaResolver.extractEmbedCandidates(document, activeBaseUrl),
                subtitleCallback = subtitleCallback,
                callback = callback,
            )
        }.getOrDefault(false)
    }

    private fun parseInlineEpisodes(
        document: Document,
        common: DrakorKitaResolver.ApiPayload,
        poster: String?,
        season: Int?,
    ): List<Episode> {
        return document.select("#episode_lists a[data-epid], a.btn-svr[data-epid]")
            .mapIndexedNotNull { index, element ->
                val episodeId = element.attr("data-epid").trim()
                if (episodeId.isBlank()) return@mapIndexedNotNull null

                val label = element.text().trim()
                val number = label.toIntOrNull()
                    ?: Regex("""\d+""").find(label)?.value?.toIntOrNull()
                    ?: index + 1

                val payload = common.copy(
                    movieId = element.attr("data-movieid").ifBlank { common.movieId },
                    episodeId = episodeId,
                    serverXid = element.attr("data-server_xid").ifBlank { common.serverXid },
                    category = element.attr("data-cat").ifBlank { common.category },
                    language = element.attr("data-tag").ifBlank { common.language },
                )

                newEpisode(payload.toPayloadJson()) {
                    this.name = "Episode $number"
                    this.episode = number
                    this.season = season
                    this.posterUrl = poster
                }
            }
            .distinctBy { it.data }
            .sortedBy { it.episode ?: Int.MAX_VALUE }
    }

    private fun parseVideoCalls(document: Document): List<VideoCall> {
        return document.select("#server_lists a[onclick*='loadVideo'], a.btn_play[onclick*='loadVideo']")
            .mapNotNull { element ->
                val onclick = element.attr("onclick")
                val kind = Regex("""loadVideo(P2P|HYDRAX|SB)\s*\(""", RegexOption.IGNORE_CASE)
                    .find(onclick)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.lowercase()
                    ?: return@mapNotNull null
                val args = Regex("""['\"]([^'\"]*)['\"]""")
                    .findAll(onclick)
                    .map { it.groupValues[1] }
                    .toList()
                if (args.size < 6 || args[0].isBlank()) return@mapNotNull null

                VideoCall(
                    kind = kind,
                    episodeId = args[0],
                    mediaType = args.getOrElse(1) { "web" },
                    quality = args.getOrElse(2) { "720" },
                    serverXid = args.getOrElse(3) { "f1" },
                    category = args.getOrElse(4) { "hs" },
                    language = args.getOrElse(5) { "" },
                )
            }
            .distinctBy { "${it.kind}|${it.quality}|${it.episodeId}" }
    }

    private fun parseEpisodeInitializer(html: String): EpisodeInitializer {
        val match = Regex(
            """initEpisodeList\s*\(\s*['\"]([^'\"]+)['\"]\s*,\s*['\"]([^'\"]*)['\"]\s*,\s*['\"]([^'\"]*)['\"]""",
            RegexOption.IGNORE_CASE,
        ).find(html)

        return EpisodeInitializer(
            movieId = match?.groupValues?.getOrNull(1).orEmpty(),
            category = match?.groupValues?.getOrNull(2).orEmpty(),
            language = match?.groupValues?.getOrNull(3).orEmpty(),
        )
    }

    private fun parsePageVariables(html: String): Map<String, String> {
        return decodeScriptVariables(html) + extractJsVariables(html)
    }

    private fun decodeScriptVariables(html: String): Map<String, String> {
        val encoded = Regex("""[A-Za-z0-9_$]+\s*=\s*['\"]([A-Za-z0-9+/=]{10,}(?:\.[A-Za-z0-9+/=]{10,})+)['\"]""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyMap()

        val decoded = buildString {
            encoded.split('.').forEach { chunk ->
                if (chunk.isBlank()) return@forEach
                val padded = chunk + "=".repeat((4 - chunk.length % 4) % 4)
                runCatching {
                    val raw = String(Base64.getDecoder().decode(padded), Charsets.ISO_8859_1)
                    raw.replace(Regex("""\D"""), "")
                        .toIntOrNull()
                        ?.toChar()
                        ?.let(::append)
                }
            }
        }
        return extractJsVariables(decoded)
    }

    private fun extractJsVariables(script: String): Map<String, String> {
        val variables = mutableMapOf<String, String>()
        Regex("""(?:var|let|const)\s+([A-Za-z0-9_$]+)\s*=\s*['\"]([^'\"]*)['\"]""")
            .findAll(script)
            .forEach { match -> variables[match.groupValues[1]] = match.groupValues[2] }
        return variables
    }

    private fun Element.toSearchResult(document: Document): SearchResponse? {
        val fullUrl = resolveUrl(attr("href"), document.baseUri())
        if (!isValidContentUrl(fullUrl)) return null

        val container = selectFirst(".bungkus") ?: this
        val titleElement = container.selectFirst(".titit, .title, .entry-title, h2, h3, h4, .name")
        val title = titleElement?.ownText()?.trim().orEmpty()
            .ifBlank { attr("title").cleanCardAttributeTitle() }
            .ifBlank { text().trim() }
        if (title.isBlank()) return null

        val image = container.selectFirst("img.poster, img[src*='tmdb'], img:not([src*='flagsapi']):not([src*='flag'])")
        val poster = image?.firstNonBlankAttribute("data-src", "src", "data-lazy-src")
            ?.let { resolveUrl(it, document.baseUri()) }
            ?.takeUnless(::isFlagImage)

        val typeClasses = container.selectFirst(".type")?.classNames()?.map { it.lowercase() }.orEmpty()
        return if ("movie" in typeClasses) {
            newMovieSearchResponse(title, fullUrl, TvType.Movie) { this.posterUrl = poster }
        } else {
            newTvSeriesSearchResponse(title, fullUrl, TvType.AsianDrama) { this.posterUrl = poster }
        }
    }

    private fun String.cleanCardAttributeTitle(): String {
        return replace(Regex("""(?i)^\s*(streaming|nonton)\s+(drama\s+korea\s+)?"""), "")
            .replace(Regex("""(?i)\s+subtitle\s+indonesia\s*$"""), "")
            .trim()
    }

    private fun Document.pickTitle(): String {
        return selectFirst(".infox h1, .bigcontent h1, h1[itemprop=headline], h1.entry-title, h1.title, h1")
            ?.text()?.trim().orEmpty()
            .ifBlank { selectFirst("meta[property=og:title]")?.attr("content").orEmpty().trim() }
            .ifBlank { title().trim() }
    }

    private fun Document.pickPoster(): String? {
        val image = selectFirst(".bigcontent .thumb img, .thumb img, .bigcover img, img[src*='tmdb']")
        return image?.firstNonBlankAttribute("data-src", "src", "data-lazy-src")
            ?.let { resolveUrl(it, baseUri()) }
            ?.takeIf { it.startsWith("http") }
    }

    private fun Document.pickDescription(): String? {
        return selectFirst(".mv-description .desc-wrap, .sinopsis .desc-wrap, .desc-wrap")
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: selectFirst("meta[name=description]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
            ?: selectFirst("meta[property=og:description]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun Document.pickGenres(): List<String> {
        return select(".gnr a, a[href*='genre=']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun Document.pickInfoMap(): Map<String, String> {
        val result = linkedMapOf<String, String>()
        select("ul.anf li, .anf li").forEach { item ->
            val text = item.text().trim()
            val split = text.split(':', limit = 2)
            if (split.size == 2) {
                result[split[0].trim().lowercase()] = split[1].trim()
            }
        }
        return result
    }

    private fun Document.pickCanonicalUrl(): String? {
        val raw = selectFirst("link[rel=canonical]")?.attr("href").orEmpty()
            .ifBlank { selectFirst("meta[property=og:url]")?.attr("content").orEmpty() }
        val resolved = resolveUrl(raw, baseUri())
        return resolved.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun cleanDetailTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)^\s*(nonton|streaming)\s+(drama\s+korea\s+)?"""), "")
            .replace(Regex("""(?i)\s+-\s+DrakorKita\s*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '|')
    }

    private fun parseDurationMinutes(value: String?): Int? {
        val parts = value?.trim()?.split(':')?.mapNotNull { it.toIntOrNull() } ?: return null
        return when (parts.size) {
            3 -> parts[0] * 60 + parts[1] + if (parts[2] >= 30) 1 else 0
            2 -> parts[0] + if (parts[1] >= 30) 1 else 0
            1 -> parts[0]
            else -> null
        }
    }

    private suspend fun ensureMainUrl() {
        if (mainUrlResolved) return
        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock

            remoteCandidatesCache = runCatching {
                val json = JSONObject(app.get(MAIN_URL_JSON).text)
                val array = json.optJSONArray(REMOTE_CONFIG_KEY)
                if (array == null) emptyList() else (0 until array.length())
                    .map { index -> array.optString(index) }
                    .mapNotNull(::normalizeHttpBaseUrl)
                    .distinct()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidatesCache + CURRENT_TARGET_MIRROR + DEFAULT_MAIN_URL + knownFamilyCandidatesCache + INFO_MIRRORS)
                .mapNotNull(::normalizeHttpBaseUrl)
                .distinct()

            for (candidate in candidates) {
                val response = runCatching {
                    app.get(candidate, headers = sourceHeaders(candidate), referer = "$candidate/")
                }.getOrNull() ?: continue
                if (!response.isSuccessful || response.text.isBlank()) continue

                val document = Jsoup.parse(response.text, response.url)
                if (!document.looksLikeDrakorKita()) continue

                rememberKnownFamilyOrigins(document, response.url)

                val resolved = originOf(response.url)
                activeBaseUrl = resolved
                mainUrl = resolved
                mainUrlResolved = true
                return@withLock
            }

            mainUrl = DEFAULT_MAIN_URL
            activeBaseUrl = DEFAULT_MAIN_URL
        }
    }

    private suspend fun getDocument(requestedUrl: String): Document {
        ensureMainUrl()
        val requestedPath = pathAndQuery(requestedUrl)
        val candidates = linkedSetOf<String>().apply {
            if (requestedUrl.startsWith("http://") || requestedUrl.startsWith("https://")) add(requestedUrl)
            (listOf(activeBaseUrl, mainUrl, CURRENT_TARGET_MIRROR, DEFAULT_MAIN_URL) + remoteCandidatesCache + knownFamilyCandidatesCache + INFO_MIRRORS)
                .forEach { origin -> add(joinOriginAndPath(origin, requestedPath)) }
        }

        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                val origin = originOf(candidate)
                val response = app.get(
                    url = candidate,
                    headers = sourceHeaders(origin),
                    referer = "$origin/",
                )
                if (!response.isSuccessful || response.text.isBlank()) continue

                val responseDocument = Jsoup.parse(response.text, response.url)
                if (!responseDocument.looksLikeDrakorKita()) continue

                rememberKnownFamilyOrigins(responseDocument, response.url)

                // The target intentionally rotates through arbitrary subdomains/domains.
                // Once the HTML is validated as DrakorKita, trust the final response origin
                // instead of restricting it to a hard-coded host suffix.
                val responseOrigin = normalizeHttpBaseUrl(response.url)
                val canonicalOrigin = responseDocument.pickCanonicalUrl()?.let(::normalizeHttpBaseUrl)
                val detectedOrigin = responseOrigin ?: canonicalOrigin ?: origin

                activeBaseUrl = detectedOrigin.trimEnd('/')
                mainUrl = activeBaseUrl
                mainUrlResolved = true
                return Jsoup.parse(response.text, response.url)
            } catch (error: Throwable) {
                lastError = error
            }
        }

        mainUrlResolved = false
        throw lastError ?: IllegalStateException("DrakorKita tidak dapat diakses melalui domain yang tersedia")
    }

    /**
     * Recognizes the historical rotating domain families without turning them into an
     * allowlist. A future parent-domain outside these suffixes is still accepted after
     * DrakorKita HTML identity validation.
     *
     * This deliberately does not synthesize random subdomains. The supplied evidence
     * demonstrates rotating hosts, but does not prove wildcard DNS behavior.
     */
    private fun isKnownSiteFamily(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank()) return false
        return KNOWN_SITE_SUFFIXES.any { suffix ->
            host == suffix.removePrefix(".") || host.endsWith(suffix)
        }
    }

    /**
     * Keeps newly observed hosts from known families as lightweight fallback hints.
     * Origins may come from the final response URL, canonical/OG metadata, or regular
     * absolute links. Unknown families are intentionally not cached here; they remain
     * supported through validated redirect-origin synchronization.
     */
    private fun rememberKnownFamilyOrigins(document: Document, responseUrl: String) {
        val discovered = buildList {
            add(responseUrl)
            document.selectFirst("link[rel=canonical][href]")?.attr("href")?.let { add(it) }
            document.selectFirst("meta[property=og:url][content]")?.attr("content")?.let { add(it) }
            document.select("a[href^=http]").forEach { element -> add(element.attr("href")) }
        }
            .mapNotNull(::normalizeHttpBaseUrl)
            .filter(::isKnownSiteFamily)
            .distinct()

        if (discovered.isEmpty()) return
        knownFamilyCandidatesCache = (knownFamilyCandidatesCache + discovered)
            .distinct()
            .takeLast(MAX_KNOWN_FAMILY_CANDIDATES)
    }

    private fun Document.looksLikeDrakorKita(): Boolean {
        val identity = listOf(
            title(),
            selectFirst("meta[property=og:site_name]")?.attr("content").orEmpty(),
        ).joinToString(" ")
        return identity.contains("DrakorKita", ignoreCase = true) ||
            select("a[href*='/detail/']").isNotEmpty() ||
            selectFirst("#server_lists") != null
    }

    private fun normalizeHttpBaseUrl(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.rawAuthority}"
            } else null
        }.getOrNull()
    }

    private fun sourceHeaders(origin: String): Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "Referer" to "${origin.trimEnd('/')}/",
    )

    private fun Element.firstNonBlankAttribute(vararg names: String): String? {
        return names.firstNotNullOfOrNull { name -> attr(name).takeIf { it.isNotBlank() } }
    }

    private fun resolveUrl(raw: String, base: String): String {
        val value = raw.trim().replace("\\/", "/")
        if (value.isBlank()) return ""
        return runCatching {
            when {
                value.startsWith("//") -> "https:$value"
                value.startsWith("http://") || value.startsWith("https://") -> value
                else -> URI(base.ifBlank { "$activeBaseUrl/" }).resolve(value).toString()
            }
        }.getOrDefault("")
    }

    private fun isValidContentUrl(url: String): Boolean {
        return url.startsWith("http", ignoreCase = true) &&
            runCatching { URI(url).path.orEmpty().contains("/detail/") }.getOrDefault(false)
    }

    private fun rebaseToActive(url: String): String = joinOriginAndPath(activeBaseUrl, pathAndQuery(url))

    private fun joinOriginAndPath(origin: String, pathAndQuery: String): String {
        return origin.trimEnd('/') + "/" + pathAndQuery.trimStart('/')
    }

    private fun pathAndQuery(url: String): String {
        val clean = url.substringBefore('#')
        return runCatching {
            val uri = URI(clean)
            val path = uri.rawPath.orEmpty().ifBlank { "/" }
            path + uri.rawQuery?.let { "?$it" }.orEmpty()
        }.getOrElse {
            "/" + clean.substringAfter("//", clean).substringAfter('/', "").trimStart('/')
        }
    }

    private fun originOf(url: String): String {
        return runCatching {
            val uri = URI(url)
            if (uri.scheme.isNullOrBlank() || uri.rawAuthority.isNullOrBlank()) DEFAULT_MAIN_URL
            else "${uri.scheme}://${uri.rawAuthority}"
        }.getOrDefault(DEFAULT_MAIN_URL)
    }

    private fun isFlagImage(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("flagsapi") || lower.contains("/flag")
    }

    private fun slugTitle(url: String): String {
        return runCatching {
            URI(url).path.orEmpty().trim('/').substringAfterLast('/').replace('-', ' ')
        }.getOrDefault("DrakorKita")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private data class VideoCall(
        val kind: String,
        val episodeId: String,
        val mediaType: String,
        val quality: String,
        val serverXid: String,
        val category: String,
        val language: String,
    )

    private data class EpisodeInitializer(
        val movieId: String,
        val category: String,
        val language: String,
    )

    companion object {
        private const val DEFAULT_MAIN_URL = "https://drakor.kita.mobi"
        private const val REMOTE_CONFIG_KEY = "DrakorKita"
        private const val MAIN_URL_JSON =
            "https://raw.githubusercontent.com/mj1Per127/agoosecloudstream/main/Website.json"
        private const val DEFAULT_C_API = "https://api.nonton.bid/c_api"
        private const val CURRENT_TARGET_MIRROR = "https://xdrakor88.kita.mom"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private const val MAX_KNOWN_FAMILY_CANDIDATES = 12

        // Historical rotating families from Info.txt. These are hints, not an allowlist.
        // Do not reject a validated future DrakorKita domain merely because its suffix
        // is absent from this list.
        private val KNOWN_SITE_SUFFIXES = listOf(
            ".kita.mom",
            ".kita.baby",
            ".nicewap.sbs",
        )

        private val INFO_MIRRORS = listOf(
            "https://drakorkita77.kita.mom",
            "https://xdrakor84.kita.baby",
            "https://drakor94.kita.mom",
            "https://drakorkita14.kita.mom",
            "https://drakorindo73.kita.mom",
            "https://drakorindo33.kita.baby",
            "https://drakorindo96.kita.baby",
            "https://xdrakor33.nicewap.sbs",
            "https://xdrakor88.nicewap.sbs",
        )
    }
}
