package com.drakorkita

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
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
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

class DrakorKita : MainAPI() {
    override var mainUrl = ENTRY_URL
    override var name = "DrakorKita"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries, TvType.Movie)

    /**
     * ENTRY_URL is a stable redirector. activeBaseUrl is updated from the page's
     * canonical/og:url metadata whenever the site rotates to a new mirror.
     */
    private var activeBaseUrl = ENTRY_URL

    override val mainPage = mainPageOf(
        "all?media_type=movie" to "Movie",
        "all?media_type=tv" to "Series",
        "all?status=returning%20series" to "Ongoing",
        "all?status=ended" to "Complete",
        "all" to "All"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = getDocument(buildPagedUrl(request.data, page))
        val items = document.toSearchResults(request.name)

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = items.isNotEmpty() && hasNextPage(document, page)
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val encoded = encode(query)
        val document = getDocument(buildPagedUrl("all?q=$encoded", page))
        return document
            .toSearchResults("Search")
            .distinctBy { it.url }
            .toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = getDocument(url)
        val pageVars = parsePageVariables(document.html())
        val canonicalUrl = document.pickCanonicalUrl() ?: rebaseToActive(url)
        val title = pageVars["movie_title"]
            .orEmpty()
            .ifBlank { document.pickTitle() }
            .ifBlank { slugTitle(canonicalUrl) }
        val cleanTitle = cleanDetailTitle(title)
        val poster = document.pickPoster()
        val plot = document.pickDescription()
        val tags = document.pickTags()
        val year = Regex("""\((\d{4})\)""")
            .find(title)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        val configs = parseApiConfigs(
            document = document,
            detailUrl = canonicalUrl,
            cleanTitle = cleanTitle,
            poster = poster,
            pageVars = pageVars
        )

        // The target page already exposes every episode through data-* attributes.
        // Parse those first to avoid an unnecessary AJAX request.
        val inlineEpisodes = configs.firstOrNull()
            ?.let { parseInlineEpisodes(document, it) }
            .orEmpty()

        val episodes = if (inlineEpisodes.isNotEmpty()) {
            inlineEpisodes
        } else {
            configs.take(1)
                .flatMap { fetchEpisodes(it) }
                .distinctBy { it.data }
                .sortedBy { it.episode ?: Int.MAX_VALUE }
        }

        val infoText = document.select("ul.anf, .anf").text()
        val isSeries = episodes.size > 1 ||
            infoText.contains("TV Series", ignoreCase = true) ||
            title.contains("Season", ignoreCase = true) ||
            title.contains(Regex("""Episode\s+\d+\s*-\s*\d+""", RegexOption.IGNORE_CASE))

        return if (isSeries) {
            val finalEpisodes = if (episodes.isNotEmpty()) {
                episodes
            } else {
                configs.firstOrNull()?.let { config ->
                    listOf(
                        newEpisode(config.toPayloadJson()) {
                            this.name = "Episode 1"
                            this.episode = 1
                            this.posterUrl = poster
                        }
                    )
                }.orEmpty()
            }

            newTvSeriesLoadResponse(cleanTitle, canonicalUrl, TvType.AsianDrama, finalEpisodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        } else {
            val movieData = episodes.firstOrNull()?.data
                ?: configs.firstOrNull()?.toPayloadJson()
                ?: canonicalUrl

            newMovieLoadResponse(cleanTitle, canonicalUrl, TvType.Movie, movieData) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var handled = false
        val payload = parsePayload(data)

        if (payload != null) {
            handled = DrakorKitaResolver.resolveApiPlayback(
                providerName = name,
                mainUrl = activeBaseUrl,
                payload = payload,
                subtitleCallback = subtitleCallback,
                callback = callback
            ) || handled
        }

        val pageUrl = payload?.detailUrl ?: data
        runCatching {
            val document = getDocument(pageUrl)
            DrakorKitaResolver.extractSubtitles(document, activeBaseUrl)
                .forEach(subtitleCallback)

            // The detail HTML initially contains the player for the active/first episode.
            // Do not reuse that iframe for another selected episode when API resolution fails.
            val pageEpisodeId = document
                .selectFirst("#episode_lists a.current[data-epid], #episode_lists a.active[data-epid], #episode_lists a[data-epid]")
                ?.attr("data-epid")
                .orEmpty()
            val canUsePagePlayer = payload == null ||
                payload.episodeId.isBlank() ||
                pageEpisodeId.isBlank() ||
                payload.episodeId == pageEpisodeId

            if (canUsePagePlayer) {
                handled = DrakorKitaResolver.resolveCandidates(
                    providerName = name,
                    mainUrl = activeBaseUrl,
                    pageUrl = document.pickCanonicalUrl() ?: rebaseToActive(pageUrl),
                    candidates = DrakorKitaResolver.extractEmbedCandidates(document, activeBaseUrl),
                    subtitleCallback = subtitleCallback,
                    callback = callback
                ) || handled
            }
        }

        return handled
    }

    private fun parseInlineEpisodes(document: Document, config: ApiConfig): List<Episode> {
        return document
            .select("#episode_lists a[data-epid], a.btn-svr[data-epid]")
            .mapIndexedNotNull { index, element ->
                val episodeId = element.attr("data-epid").trim()
                if (episodeId.isBlank()) return@mapIndexedNotNull null

                val label = element.text().trim()
                val number = label.toIntOrNull()
                    ?: Regex("""\d+""").find(label)?.value?.toIntOrNull()
                    ?: index + 1

                val payload = DrakorKitaResolver.ApiPayload(
                    detailUrl = config.detailUrl,
                    title = config.cleanTitle,
                    movieId = element.attr("data-movieid").ifBlank { config.movieId },
                    episodeId = episodeId,
                    serverXid = element.attr("data-server_xid").ifBlank { "f1" },
                    tag = element.attr("data-cat").ifBlank { config.tag },
                    c = config.c,
                    t = config.t,
                    ver = element.attr("data-tag").ifBlank { config.ver },
                    cApiHost = config.cApiHost,
                    isMob = config.isMob,
                    isUc = config.isUc,
                    mediaType = "web"
                )

                newEpisode(payload.toPayloadJson()) {
                    this.name = "Episode $number"
                    this.episode = number
                    this.posterUrl = config.poster
                }
            }
            .distinctBy { it.data }
            .sortedBy { it.episode ?: Int.MAX_VALUE }
    }

    private suspend fun fetchEpisodes(config: ApiConfig): List<Episode> {
        val candidateApiHosts = linkedSetOf<String>().apply {
            config.cApiHost.takeIf { it.isNotBlank() }?.let(::add)
            add("${originOf(config.detailUrl)}/c_api")
            add("${activeBaseUrl.trimEnd('/')}/c_api")
            add(DEFAULT_C_API)
        }

        for (cApiHost in candidateApiHosts) {
            val endpoint = "${cApiHost.trimEnd('/')}/episode_mob.php" +
                "?is_mob=${encode(config.isMob)}" +
                "&is_uc=${encode(config.isUc)}" +
                "&movie_id=${encode(config.movieId)}" +
                "&tag=${encode(config.tag)}" +
                "&c=${encode(config.c)}" +
                "&t=${encode(config.t)}" +
                "&ver=${encode(config.ver)}"

            val json = getJson(endpoint, config.detailUrl) ?: continue
            val episodes = parseEpisodeJson(json, config, cApiHost)
            if (episodes.isNotEmpty()) return episodes
        }

        return emptyList()
    }

    private fun parseEpisodeJson(
        json: JSONObject,
        config: ApiConfig,
        cApiHost: String
    ): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val defaultServerXid = json.optString("server_xid").ifBlank { "f1" }
        val episodeHtml = json.optString("episode_lists")

        if (episodeHtml.isNotBlank()) {
            Jsoup.parse(episodeHtml)
                .select("a.btn-svr, a[data-epid]")
                .forEachIndexed { index, button ->
                    val episodeId = button.attr("data-epid")
                        .ifBlank { button.attr("data-id") }
                    if (episodeId.isBlank()) return@forEachIndexed

                    val label = button.text().trim()
                    val number = label.toIntOrNull()
                        ?: Regex("""\d+""").find(label)?.value?.toIntOrNull()
                        ?: index + 1

                    val payload = DrakorKitaResolver.ApiPayload(
                        detailUrl = config.detailUrl,
                        title = config.cleanTitle,
                        movieId = button.attr("data-movieid").ifBlank { config.movieId },
                        episodeId = episodeId,
                        serverXid = button.attr("data-server_xid").ifBlank { defaultServerXid },
                        tag = button.attr("data-cat").ifBlank { config.tag },
                        c = config.c,
                        t = config.t,
                        ver = button.attr("data-tag").ifBlank { config.ver },
                        cApiHost = cApiHost,
                        isMob = config.isMob,
                        isUc = config.isUc,
                        mediaType = "web"
                    )

                    episodes += newEpisode(payload.toPayloadJson()) {
                        this.name = "Episode $number"
                        this.episode = number
                        this.posterUrl = config.poster
                    }
                }
        }

        if (episodes.isNotEmpty()) return episodes.distinctBy { it.data }

        val array = json.optJSONArray("episode") ?: return emptyList()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val episodeId = item.optString("id")
            if (episodeId.isBlank()) continue

            val number = item.optString("eps_no").toIntOrNull() ?: index + 1
            val payload = DrakorKitaResolver.ApiPayload(
                detailUrl = config.detailUrl,
                title = config.cleanTitle,
                movieId = config.movieId,
                episodeId = episodeId,
                serverXid = item.optString("server_xid").ifBlank { defaultServerXid },
                tag = config.tag,
                c = config.c,
                t = config.t,
                ver = config.ver,
                cApiHost = cApiHost,
                isMob = config.isMob,
                isUc = config.isUc,
                mediaType = "web"
            )

            episodes += newEpisode(payload.toPayloadJson()) {
                this.name = item.optString("name").ifBlank { "Episode $number" }
                this.episode = number
                this.posterUrl = config.poster
            }
        }

        return episodes.distinctBy { it.data }
    }

    private fun parseApiConfigs(
        document: Document,
        detailUrl: String,
        cleanTitle: String,
        poster: String?,
        pageVars: Map<String, String>
    ): List<ApiConfig> {
        val c = pageVars["c"].orEmpty()
        val t = pageVars["t"].orEmpty()
        val isMob = pageVars["is_mob"].orEmpty().ifBlank { "0" }
        val isUc = pageVars["is_uc"].orEmpty().ifBlank { "0" }
        val cApiHost = pageVars["c_api_host"]
            .orEmpty()
            .ifBlank { pageVars["api_host"].orEmpty().replace(Regex("""/api/?$"""), "/c_api") }
            .ifBlank { DEFAULT_C_API }
            .trimEnd('/')

        val configs = mutableListOf<ApiConfig>()

        document.select("a[onclick*='loadEpisode'], button[onclick*='loadEpisode']")
            .forEach { element ->
                val (movieId, tag, ver) = parseEpisodeInitializer(element.attr("onclick"))
                if (movieId.isNotBlank()) {
                    configs += ApiConfig(
                        detailUrl,
                        cleanTitle,
                        poster,
                        movieId,
                        tag.ifBlank { "hs" },
                        c,
                        t,
                        ver.ifBlank { "ind" },
                        cApiHost,
                        isMob,
                        isUc
                    )
                }
            }

        Regex("""initEpisodeList\s*\(\s*['\"]([^'\"]+)['\"]\s*,\s*['\"]([^'\"]+)['\"]\s*,\s*['\"]([^'\"]+)['\"]""")
            .find(document.html())
            ?.let { match ->
                configs += ApiConfig(
                    detailUrl,
                    cleanTitle,
                    poster,
                    match.groupValues[1],
                    match.groupValues[2].ifBlank { "hs" },
                    c,
                    t,
                    match.groupValues[3].ifBlank { "ind" },
                    cApiHost,
                    isMob,
                    isUc
                )
            }

        document.selectFirst("#episode_lists a[data-movieid], a.btn-svr[data-movieid]")
            ?.let { element ->
                val movieId = element.attr("data-movieid")
                if (movieId.isNotBlank()) {
                    configs += ApiConfig(
                        detailUrl,
                        cleanTitle,
                        poster,
                        movieId,
                        element.attr("data-cat").ifBlank { "hs" },
                        c,
                        t,
                        element.attr("data-tag").ifBlank { "ind" },
                        cApiHost,
                        isMob,
                        isUc
                    )
                }
            }

        return configs.distinctBy { "${it.movieId}|${it.tag}|${it.ver}" }
    }

    private fun parseEpisodeInitializer(onclick: String): Triple<String, String, String> {
        val match = Regex("""(?:loadEpisode|initEpisodeList)\s*\(\s*['\"]([^'\"]+)['\"]\s*,\s*['\"]([^'\"]+)['\"]\s*,\s*['\"]([^'\"]+)['\"]""")
            .find(onclick)
            ?: return Triple("", "", "")
        return Triple(match.groupValues[1], match.groupValues[2], match.groupValues[3])
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

    private fun parsePayload(data: String): DrakorKitaResolver.ApiPayload? {
        val trimmed = data.trim()
        if (!trimmed.startsWith("{")) return null

        return runCatching {
            val json = JSONObject(trimmed)
            DrakorKitaResolver.ApiPayload(
                detailUrl = json.optString("detailUrl"),
                title = json.optString("title"),
                movieId = json.optString("movieId"),
                episodeId = json.optString("episodeId"),
                serverXid = json.optString("serverXid"),
                tag = json.optString("tag"),
                c = json.optString("c"),
                t = json.optString("t"),
                ver = json.optString("ver"),
                cApiHost = json.optString("cApiHost"),
                isMob = json.optString("isMob", "0"),
                isUc = json.optString("isUc", "0"),
                mediaType = json.optString("mediaType")
            )
        }.getOrNull()
    }

    private fun Document.toSearchResults(sectionName: String): List<SearchResponse> {
        val cards = select("a.poster[href*='/detail/']")
        val candidates = if (cards.isNotEmpty()) cards else select("a[href*='/detail/']")

        return candidates.mapNotNull { link ->
            val fullUrl = link.resolveUrl("href", this)
            if (!isValidContentUrl(fullUrl)) return@mapNotNull null

            val container = link.selectFirst(".bungkus") ?: link
            val titleElement = container.selectFirst(".titit, .title, .entry-title, h2, h3, h4, .name")
            val rawTitle = titleElement?.ownText()
                ?.ifBlank { titleElement.text() }
                ?.ifBlank { null }
                ?: link.attr("title").ifBlank { link.text() }
            val cleanTitle = cleanDetailTitle(rawTitle)
            if (cleanTitle.isBlank()) return@mapNotNull null

            val image = container.selectFirst("img.poster, img[src*='tmdb'], img:not([src*='flagsapi']):not([src*='flag'])")
            val poster = image?.firstNonBlankAttribute("data-src", "src", "data-lazy-src")
                ?.let { resolveUrl(it, baseUri()) }
                ?.takeUnless(::isFlagImage)

            val typeText = container.selectFirst(".type")?.text().orEmpty()
            val isMovie = typeText.equals("Movie", ignoreCase = true) ||
                sectionName.equals("Movie", ignoreCase = true)

            if (isMovie) {
                newMovieSearchResponse(cleanTitle, fullUrl, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(cleanTitle, fullUrl, TvType.AsianDrama) {
                    this.posterUrl = poster
                }
            }
        }.distinctBy { it.url }
    }

    private fun isValidContentUrl(url: String): Boolean {
        return url.startsWith("http", ignoreCase = true) &&
            runCatching { URI(url).path.orEmpty().contains("/detail/") }.getOrDefault(false)
    }

    private fun hasNextPage(document: Document, currentPage: Int): Boolean {
        if (document.selectFirst("a.next, a.next-page, a[rel=next], li.next a") != null) return true
        return document.select(".pagination a, .pagination-index a, .nav-links a")
            .mapNotNull { Regex("""\d+""").find(it.text())?.value?.toIntOrNull() }
            .any { it > currentPage }
    }

    private fun buildPagedUrl(path: String, page: Int): String {
        val base = activeBaseUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        if (page <= 1) return "$base/$cleanPath".trimEnd('/')

        return if (cleanPath.contains('?')) {
            val parts = cleanPath.split('?', limit = 2)
            "$base/${parts[0].trimEnd('/')}/page/$page?${parts[1]}"
        } else {
            "$base/${cleanPath.trimEnd('/')}/page/$page"
        }
    }

    private fun Document.pickTitle(): String {
        return selectFirst(".breadcrumb_last, h1[itemprop=headline], h1.entry-title, h1.title, h1.name, h1")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank { selectFirst("meta[property=og:title]")?.attr("content").orEmpty().trim() }
            .ifBlank { title().trim() }
    }

    private fun Document.pickPoster(): String? {
        val image = selectFirst(".bigcontent .thumb img, .bigcover img, img[src*='tmdb']")
        val raw = image?.firstNonBlankAttribute("data-src", "src", "data-lazy-src")
            .orEmpty()
            .ifBlank { selectFirst("meta[property=og:image]")?.attr("content").orEmpty() }
        return resolveUrl(raw, baseUri()).takeUnless { it.isBlank() || isFlagImage(it) }
    }

    private fun Document.pickDescription(): String? {
        return selectFirst(".sinopsis .desc-wrap, .sinopsis p, .mv-description .desc-wrap, .entry-content p")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: selectFirst("meta[name=description]")
                ?.attr("content")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: selectFirst("meta[property=og:description]")
                ?.attr("content")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
    }

    private fun Document.pickTags(): List<String> {
        return select(".gnr a, a[href*='genre=']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun Document.pickCanonicalUrl(): String? {
        val raw = selectFirst("link[rel=canonical]")?.attr("href")
            .orEmpty()
            .ifBlank { selectFirst("meta[property=og:url]")?.attr("content").orEmpty() }
        val resolved = resolveUrl(raw, baseUri())
        return resolved.takeIf { it.startsWith("http") && isLikelySiteHost(it) }
    }

    private fun cleanDetailTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)^\s*(nonton|streaming)\s+(drama\s+korea\s+)?"""), "")
            .replace(Regex("""(?i)\s+season\s+\d+\s*\[?episode\s+\d+(?:\s*-\s*\d+)?\]?"""), "")
            .replace(Regex("""(?i)\s+episode\s+\d+(?:\s*-\s*\d+)?"""), "")
            .replace(Regex("""(?i)\s+(?:2160p|1080p|720p|480p|360p)(?:\s*,\s*(?:2160p|1080p|720p|480p|360p))*.*$"""), "")
            .replace(Regex("""(?i)\s+(subtitle\s+indonesia|sub\s*indo|softsub|hardsub).*$"""), "")
            .replace(Regex("""(?i)\s+-\s+DrakorKita\s*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '|')
    }

    private suspend fun getDocument(requestedUrl: String): Document {
        val pathAndQuery = pathAndQuery(requestedUrl)
        val candidates = linkedSetOf<String>().apply {
            if (requestedUrl.startsWith("http://") || requestedUrl.startsWith("https://")) {
                add(requestedUrl)
            }
            listOf(activeBaseUrl, ENTRY_URL).plus(KNOWN_MIRRORS).forEach { origin ->
                add(joinOriginAndPath(origin, pathAndQuery))
            }
        }

        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                val origin = originOf(candidate)
                val response = app.get(
                    url = candidate,
                    headers = sourceHeaders(origin),
                    referer = "$origin/"
                )
                if (!response.isSuccessful || response.text.isBlank()) continue

                val initial = Jsoup.parse(response.text, candidate)
                if (!initial.looksLikeDrakorKita()) continue

                val detectedOrigin = initial.pickCanonicalUrl()
                    ?.let(::originOf)
                    ?.takeIf { isLikelySiteHost(it) }
                    ?: origin

                activeBaseUrl = detectedOrigin.trimEnd('/')
                mainUrl = activeBaseUrl
                return Jsoup.parse(response.text, "$activeBaseUrl/")
            } catch (error: Throwable) {
                lastError = error
            }
        }

        throw lastError ?: IllegalStateException("DrakorKita tidak dapat diakses melalui domain yang tersedia")
    }

    private suspend fun getJson(url: String, referer: String): JSONObject? {
        return runCatching {
            val response = app.get(
                url = url,
                headers = ajaxHeaders(originOf(referer)),
                referer = referer
            )
            if (!response.isSuccessful) return@runCatching null
            JSONObject(response.text)
        }.getOrNull()
    }

    private fun Document.looksLikeDrakorKita(): Boolean {
        val identity = listOf(
            title(),
            selectFirst("meta[name=publisher]")?.attr("content").orEmpty(),
            selectFirst("meta[property=og:site_name]")?.attr("content").orEmpty()
        ).joinToString(" ")

        return identity.contains("DrakorKita", ignoreCase = true) ||
            select("a[href*='/detail/']").isNotEmpty() ||
            selectFirst("#episode_lists") != null
    }

    private fun sourceHeaders(origin: String): Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "Referer" to "$origin/"
    )

    private fun ajaxHeaders(origin: String): Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "application/json,text/plain,*/*",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "Origin" to origin,
        "X-Requested-With" to "XMLHttpRequest"
    )

    private fun Element.resolveUrl(attribute: String, document: Document): String {
        return resolveUrl(attr(attribute), document.baseUri())
    }

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

    private fun rebaseToActive(url: String): String = joinOriginAndPath(activeBaseUrl, pathAndQuery(url))

    private fun joinOriginAndPath(origin: String, pathAndQuery: String): String {
        return origin.trimEnd('/') + "/" + pathAndQuery.trimStart('/')
    }

    private fun pathAndQuery(url: String): String {
        return runCatching {
            val uri = URI(url)
            val path = uri.rawPath.orEmpty().ifBlank { "/" }
            path + uri.rawQuery?.let { "?$it" }.orEmpty()
        }.getOrElse {
            "/" + url.substringAfter("//", url).substringAfter('/', "").trimStart('/')
        }
    }

    private fun originOf(url: String): String {
        return runCatching {
            val uri = URI(url)
            if (uri.scheme.isNullOrBlank() || uri.rawAuthority.isNullOrBlank()) ENTRY_URL
            else "${uri.scheme}://${uri.rawAuthority}"
        }.getOrDefault(ENTRY_URL)
    }

    private fun isLikelySiteHost(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "drakor.kita.mobi" ||
            host.contains("drakor") ||
            host.endsWith(".kita.mom") ||
            host.endsWith(".kita.baby") ||
            host.endsWith(".kita.mobi")
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

    data class ApiConfig(
        val detailUrl: String,
        val cleanTitle: String,
        val poster: String?,
        val movieId: String,
        val tag: String,
        val c: String,
        val t: String,
        val ver: String,
        val cApiHost: String,
        val isMob: String,
        val isUc: String
    ) {
        fun toPayloadJson(): String = JSONObject().apply {
            put("detailUrl", detailUrl)
            put("title", cleanTitle)
            put("movieId", movieId)
            put("tag", tag)
            put("c", c)
            put("t", t)
            put("ver", ver)
            put("cApiHost", cApiHost)
            put("isMob", isMob)
            put("isUc", isUc)
        }.toString()
    }

    private fun DrakorKitaResolver.ApiPayload.toPayloadJson(): String = JSONObject().apply {
        put("detailUrl", detailUrl)
        put("title", title)
        put("movieId", movieId)
        put("episodeId", episodeId)
        put("serverXid", serverXid)
        put("tag", tag)
        put("c", c)
        put("t", t)
        put("ver", ver)
        put("cApiHost", cApiHost)
        put("isMob", isMob)
        put("isUc", isUc)
        put("mediaType", mediaType)
    }.toString()

    companion object {
        private const val ENTRY_URL = "https://drakor.kita.mobi"
        private const val DEFAULT_C_API = "https://api.nonton.bid/c_api"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        private val KNOWN_MIRRORS = listOf(
            "https://drakorkita77.kita.mom",
            "https://xdrakor84.kita.baby",
            "https://drakor94.kita.mom"
        )
    }
}
