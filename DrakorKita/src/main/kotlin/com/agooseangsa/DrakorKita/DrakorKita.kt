package com.agooseangsa.DrakorKita

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class DrakorKita : MainAPI() {
    override var mainUrl = ENTRY_MAIN_URL
    override var name = _q9("Hf3URpNHeHqTOuE=")
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val hasMainPage = true
    override val mainPage = listOf(
        MainPageData(HOMEPAGE_EPS, HOMEPAGE_EPS),
        MainPageData(HOMEPAGE_MOVIE, HOMEPAGE_MOVIE),
        MainPageData(HOMEPAGE_SERIES, HOMEPAGE_SERIES),
    )

    private val blockedCategoryKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_CATEGORIES.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    private val blockedTagKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_TAGS.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    private suspend fun _b0(): List<String> = runCatching {
        JSONObject(app.get(MAIN_URL_JSON).text).readMainUrlCandidates()
    }.getOrDefault(emptyList())

    private fun _b6(responseUrl: String?) {
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
            if ((scheme == _q9("MfvBXQ==") || scheme == _q9("MfvBXY8=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else {
                null
            }
        }.getOrNull()
    }

    private fun normalizeTaxonomyName(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)

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
            throw ErrorLoadingException(_q9("EuDbWZlbeFWTLOyBWcXq0FTL01/yvrl8617ahz79vbN5/8dCilw8VIg="))
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request, emptyList(), false)

        val (_, document) = _b1(mainUrl, _q9("MbubRZlUPFiUKbE="))

        val heading = document.select(_q9("MbubRZlUPFiUKbE="))
            .firstOrNull { _a0(it.text()) == request.data }
        val row = heading?.nextElementSibling()
        val results = row?.select(_q9("OKHFQo9BPUOhJvKLVPE="))
            ?.mapNotNull(::_a1)
            ?.distinctBy { it.url }
            .orEmpty()

        return newHomePageResponse(request, results, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, _q9("DNvzAMQ="))
        val (_, document) = _b1("$mainUrl/all?q=$encoded")
        return document.select(_q9("OKHFQo9BPUOhJvKLVPE="))
            .mapNotNull(::_a1)
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val (detailUrl, document) = _b1(url, _q9("MbybRZlUPFiUKbE="))

        val (websiteTitle, websiteYear) = _a6(document)
        val websiteType = _a2(document, _q9("DfbFSA=="))
        val isTv = when {
            websiteType.equals(_q9("DdmVfplHMVSJ"), ignoreCase = true) -> true
            websiteType.equals(_q9("FODDRJk="), ignoreCase = true) -> false
            else -> throw ErrorLoadingException(_q9("DebFSNxeN1+OK+7OVsnskVLLlkO7sbd5rVPUmSnyr7Ywr9FMjlx4RZs854tG"))
        }

        val websiteGenres = document.select(_q9("d+jbX9xU"))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        enforceContentAllowed(categories = websiteGenres)

        val websitePlot = document.selectFirst(_q9("d/zcQ5NFK1iJbq6KV9/73UzV10fypQ=="))
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
        val websitePoster = document.selectFirst(_q9("d+3cSp9aNkWfIPTOHNjwhVbFll6/so1h/1Tg"))
            ?.attr(_q9("Kv3W"))?.trim()?.takeIf { it.isNotBlank() }
        val websiteBackground = document.selectFirst(_q9("d+3cSp9aLlSIbumDVffrglj6"))
            ?.attr(_q9("Kv3W"))?.trim()?.takeIf { it.isNotBlank() }
        val websiteDuration = _a3(_a2(document, _q9("D+bRSJMVFFSUKfSG")))
        val originalTitle = document.selectFirst(_q9("d+7ZWZlH"))
            ?.text()?.trim()?.takeIf { it.isNotBlank() && !it.equals(websiteTitle, ignoreCase = true) }
        val iframe = document.selectFirst(_q9("ev/ZQp1RPUPaJ+acU8H9q0jV1Wo="))
            ?.attr(_q9("Kv3W"))?.trim()?.takeIf { it.isNotBlank() }

        val tmdb = fetchAgooseTmdbMetadata(
            AgooseTmdbIdentity(
                originalTitle = originalTitle,
                displayTitle = websiteTitle,
                year = websiteYear,
                isTv = isTv,
            ),
        )

        val finalTitle = tmdb?.localizedTitle?.takeIf { it.isNotBlank() } ?: websiteTitle
        val finalPlot = tmdb?.overview?.takeIf { it.isNotBlank() } ?: websitePlot
        val finalPoster = tmdb?.posterPath?.let { "$TMDB_IMAGE_W500$it" } ?: websitePoster
        val finalBackground = tmdb?.backdropPath?.let { "$TMDB_IMAGE_ORIGINAL$it" } ?: websiteBackground
        val finalYear = tmdb?.year ?: websiteYear
        val finalGenres = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: websiteGenres
        val finalDuration = tmdb?.runtimeMinutes ?: websiteDuration

        return if (!isTv) {
            val streamData = _a4(
                pageUrl = detailUrl,
                iframe = iframe,
                mediaType = _q9("NODDRJk="),
            )
            newMovieLoadResponse(finalTitle, detailUrl, TvType.Movie, streamData) {
                posterUrl = finalPoster
                backgroundPosterUrl = finalBackground
                plot = finalPlot
                year = finalYear
                tags = finalGenres
                duration = finalDuration
            }
        } else {
            val seasonNumber = _a2(document, _q9("CurUXpNb"))?.toIntOrNull()
            val episodes = document.select(_q9("eurFRI9aPFSlIumdRt+4kWDD10Oz+LNi5FPg")).mapNotNull { episodeElement ->
                val number = episodeElement.text().trim().toIntOrNull() ?: return@mapNotNull null
                val exactActiveIframe = iframe.takeIf { episodeElement.hasClass(_q9("OOzBRIpQ")) }
                val category = episodeElement.attr(_q9("Pe7BTNFWOUU=")).takeIf { it.isNotBlank() }
                val tag = episodeElement.attr(_q9("Pe7BTNFBOVY=")).takeIf { it.isNotBlank() }
                val episodePageUrl = _b8(
                    detailUrl = detailUrl,
                    category = category,
                    tag = tag,
                    episodeNumber = number,
                )
                val data = _a4(
                    pageUrl = detailUrl,
                    iframe = exactActiveIframe,
                    mediaType = "tv",
                    episodePageUrl = episodePageUrl,
                    episodeNumber = number,
                    episodeId = episodeElement.attr(_q9("Pe7BTNFQKFie")).takeIf { it.isNotBlank() },
                    movieId = episodeElement.attr(_q9("Pe7BTNFYN0eTK+mK")).takeIf { it.isNotBlank() },
                    category = category,
                    tag = tag,
                    server = episodeElement.attr(_q9("Pe7BTNFGPUOMK/I=")).takeIf { it.isNotBlank() },
                    serverXid = episodeElement.attr(_q9("Pe7BTNFGPUOMK/KxSsX8")).takeIf { it.isNotBlank() },
                )
                newEpisode(data) {
                    name = "Episode $number"
                    season = seasonNumber
                    episode = number
                }
            }

            if (episodes.isEmpty()) {
                throw ErrorLoadingException(_q9("He7TWZ1HeFSKJ/OBVsm4hFrV0VKm9aJ76VbW0ij1ur80+t5Mkg=="))
            }

            newTvSeriesLoadResponse(finalTitle, detailUrl, TvType.TvSeries, episodes) {
                posterUrl = finalPoster
                backgroundPosterUrl = finalBackground
                plot = finalPlot
                year = finalYear
                tags = finalGenres
                duration = finalDuration
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val payload = runCatching { JSONObject(data) }.getOrNull() ?: return false
        val pageUrl = payload.optString(_q9("Ke7SSKlHNA==")).trim().takeIf { it.isNotBlank() }

        val discoveredIframe = payload.optString(_q9("MOnHTJFQ")).trim().takeIf { it.isNotBlank() }
        if (_b7(
                iframe = discoveredIframe,
                referer = pageUrl,
                subtitleCallback = subtitleCallback,
                callback = callback,
            )
        ) {
            return true
        }

        if (payload.optString(_q9("NOrRRJ1hIUGf")).equals("tv", ignoreCase = true)) {
            val episodePageUrl = payload.optString(_q9("PP/cXpNRPWGbKeW7QMA="))
                .trim()
                .takeIf { it.isNotBlank() }
            if (episodePageUrl != null) {
                val (resolvedEpisodeUrl, episodeDocument) = runCatching {
                    _b1(episodePageUrl, _q9("ev/ZQp1RPUPaJ+acU8H9q0jV1Wo="))
                }.getOrNull() ?: return false

                val episodeIframe = episodeDocument.selectFirst(_q9("ev/ZQp1RPUPaJ+acU8H9q0jV1Wo="))
                    ?.attr(_q9("Kv3W"))
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                if (_b7(
                        iframe = episodeIframe,
                        referer = resolvedEpisodeUrl,
                        subtitleCallback = subtitleCallback,
                        callback = callback,
                    )
                ) {
                    return true
                }
            }
        }

        return false
    }

    private suspend fun _b7(
        iframe: String?,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val playerUrl = iframe?.trim()?.takeIf { it.isNotBlank() } ?: return false
        var emittedLink = false
        val extractorMatched = loadExtractor(playerUrl, referer, subtitleCallback) { link ->
            emittedLink = true
            callback(link)
        }
        return extractorMatched && emittedLink
    }

    private fun _a0(value: String): String {
        val normalized = value.trim().replace(WHITESPACE, " ")
        return when {
            normalized.startsWith(HOMEPAGE_EPS, ignoreCase = true) -> HOMEPAGE_EPS
            normalized.equals(HOMEPAGE_MOVIE, ignoreCase = true) -> HOMEPAGE_MOVIE
            normalized.equals(HOMEPAGE_SERIES, ignoreCase = true) -> HOMEPAGE_SERIES
            else -> normalized
        }
    }

    private fun _a1(element: Element): SearchResponse? {
        val typeClasses = element.selectFirst(_q9("Kv/UQ9JBIUGf"))?.classNames().orEmpty()
        val isMovie = typeClasses.any { it.equals(_q9("FODDRJk="), ignoreCase = true) }
        val isTv = typeClasses.any { it.equals("TV", ignoreCase = true) }
        if (!isMovie && !isTv) return null

        val href = element.attr(_q9("Mf3QSw==")).trim().takeIf { it.isNotBlank() } ?: return null
        val title = element.selectFirst(_q9("Kv/UQ9JBMUWTOg=="))
            ?.ownText()?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = element.selectFirst(_q9("MOLSA4xaK0WfPNudQM/F3BvO21CJpqRx0A=="))
            ?.attr(_q9("Kv3W"))?.trim()?.takeIf { it.isNotBlank() }

        val absoluteHref = element.absUrl(_q9("Mf3QSw==")).trim().takeIf { it.isNotBlank() }
        val fixedUrl = when {
            href.startsWith(_q9("MfvBXcYadw==")) || href.startsWith(_q9("MfvBXY8Pdx4=")) -> href
            absoluteHref != null -> absoluteHref
            else -> "$mainUrl/${href.trimStart('/')}"
        }
        val fixedPoster = poster?.let {
            val absolutePoster = element.selectFirst(_q9("MOLSA4xaK0WfPNudQM/F3BvO21CJpqRx0A=="))
                ?.absUrl(_q9("Kv3W"))?.trim()?.takeIf { value -> value.isNotBlank() }
            when {
                it.startsWith(_q9("MfvBXcYadw==")) || it.startsWith(_q9("MfvBXY8Pdx4=")) -> it
                absolutePoster != null -> absolutePoster
                else -> "$mainUrl/${it.trimStart('/')}"
            }
        }

        return if (isMovie) {
            newMovieSearchResponse(title, fixedUrl, TvType.Movie) {
                posterUrl = fixedPoster
            }
        } else {
            newTvSeriesSearchResponse(title, fixedUrl, TvType.TvSeries) {
                posterUrl = fixedPoster
            }
        }
    }

    private fun _a6(document: Document): Pair<String, Int?> {
        val heading = document.selectFirst(_q9("MbybRZlUPFiUKbHOQdz5ng=="))
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_q9("EerUSZVbPxGJJ+6BQt/xgxvD00OzvLoy+V7Zkye8qrMt6thYl1Q2"))
        val withoutPrefix = heading.replaceFirst(SYNOPSIS_PREFIX, "").removeSuffix(":").trim()
        val yearMatch = TRAILING_YEAR.find(withoutPrefix)
        val year = yearMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        val title = if (yearMatch != null) {
            withoutPrefix.substring(0, yearMatch.range.first).trim()
        } else {
            withoutPrefix
        }
        if (title.isBlank()) throw ErrorLoadingException(_q9("E/rRWJAVPFSOL+mCEtj5glzCwhe5uqV941A="))
        return title to year
    }

    private fun _a2(document: Document, label: String): String? {
        val prefix = "$label :"
        return document.select(_q9("LOObTJJTeA/aIuk="))
            .asSequence()
            .map { it.text().trim().replace(WHITESPACE, " ") }
            .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun _a3(value: String?): Int? {
        val parts = value?.trim()?.split(":")?.mapNotNull { it.toIntOrNull() } ?: return null
        return when (parts.size) {
            3 -> (parts[0] * 60 + parts[1]).takeIf { it > 0 }
            2 -> parts[0].takeIf { it > 0 }
            else -> null
        }
    }

    private suspend fun _b1(
        url: String,
        requiredSelector: String? = null,
    ): Pair<String, Document> {
        val pendingRequests = mutableListOf<String>()
        val queuedRequests = linkedSetOf<String>()

        fun enqueueRequest(value: String?) {
            val request = _b9(value) ?: return
            if (queuedRequests.add(request)) pendingRequests += request
        }

        fun enqueueOriginalPathOnOrigin(originValue: String?) {
            val origin = normalizeHttpBaseUrl(originValue) ?: return
            enqueueRequest(_b2(url, origin))
        }

        enqueueRequest(url)

        enqueueOriginalPathOnOrigin(mainUrl)
        enqueueOriginalPathOnOrigin(ENTRY_MAIN_URL)
        _b0().forEach(::enqueueOriginalPathOnOrigin)

        var index = 0
        var attempts = 0
        while (index < pendingRequests.size && attempts < MAX_ORIGIN_ATTEMPTS) {
            val requestUrl = pendingRequests[index++]
            val response = runCatching { app.get(requestUrl) }.getOrNull() ?: continue
            attempts += 1

            val document = response.document
            val discoveredUrls = _b3(response.url, document)

            discoveredUrls.forEach { discovered ->
                enqueueRequest(discovered)
                enqueueOriginalPathOnOrigin(discovered)
            }

            if (!response.isSuccessful || !_b5(document)) continue

            val providerPageUrl = _b4(document)
                ?: response.url
                ?: requestUrl
            _b6(providerPageUrl)

            if (requiredSelector != null && document.selectFirst(requiredSelector) == null) continue

            val resolvedPageUrl = response.url?.takeIf { it.isNotBlank() } ?: requestUrl
            return resolvedPageUrl to document
        }

        mainUrl = ENTRY_MAIN_URL
        throw ErrorLoadingException(_q9("Ee7ZTJFUNhGKPO+YW8j9ghvT31OzvvZ27Efchmz4p6os49xFl1Q2EYov5I8SyPedWs7YF7O+onvr"))
    }

    private fun _b3(responseUrl: String?, document: Document): List<String> {
        val results = linkedSetOf<String>()

        fun add(value: String?) {
            _b9(value, responseUrl)?.let(results::add)
        }

        add(responseUrl)
        add(_b4(document))
        add(
            document.selectFirst(_q9("NOrBTKdFKl6KK/KaS5H3lwHSxFuPjrV940PYnDjB"))
                ?.attr(_q9("OuDbWZlbLA=="))?.trim()?.takeIf { it.isNotBlank() },
        )
        add(
            document.selectFirst(_q9("OKHbTIpXOUPXLPKPXMjDmEnC0GnvvaJm/WqR0iL9uPo41N1fmVMGDJI69J5vgLjeWdXTVra2pGfgVZ2TF/S8vz/RiEWIQShs"))
                ?.attr(_q9("Mf3QSw=="))?.trim()?.takeIf { it.isNotBlank() },
        )

        document.select(_q9("NOrBTKddLEWKY+WfR8XurQ==")).forEach { meta ->
            if (!meta.attr(_q9("MfvBXdFQKUSTOA==")).equals(_q9("K+rTX5lGMA=="), ignoreCase = true)) return@forEach
            META_REFRESH_URL.find(meta.attr(_q9("OuDbWZlbLA==")))
                ?.groupValues?.getOrNull(1)
                ?.trim()?.trim('\'', '"')
                ?.let(::add)
        }

        document.select(_q9("KuzHRIxBYl+VOqi1Qd77rRI=")).forEach { script ->
            SCRIPT_REDIRECT_URL.findAll(script.data()).forEach { match ->
                val candidate = match.groupValues.getOrNull(1)?.trim()
                val normalizedCandidate = _b9(candidate, responseUrl)
                if (_c0(normalizedCandidate)) add(normalizedCandidate)
            }
        }

        return results.toList()
    }

    private fun _b4(document: Document): String? =
        document.selectFirst(_q9("NebbRqdHPV3HLeGAXcLxk1rL62y6p7N00A=="))
            ?.attr(_q9("Mf3QSw=="))?.trim()?.takeIf { it.isNotBlank() }

    private fun _b5(document: Document): Boolean {
        val hasProviderMarker =
            document.selectFirst(_q9("OKHbTIpXOUPXLPKPXMjDgl7Li1+9uLNPoReegSnuuL8r0NlEj0ErHdomtMBayfmUUsnRBv71viGjX9iTKPWgvWg=")) != null
        val canonical = _b4(document)
        return hasProviderMarker && (
            normalizeHttpBaseUrl(canonical) != null ||
                document.selectFirst(_q9("NOrBTKdFKl6KK/KaS5H3lwHU30O3irhz4FLgqS/zoK484cEHwXEqUJEh8qVb2Pmt")) != null
            )
    }

    private fun _b9(url: String?, baseUrl: String? = null): String? {
        val value = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val raw = URI(value)
            val uri = when {
                raw.isAbsolute -> raw
                value.startsWith("//") -> URI(baseUrl ?: ENTRY_MAIN_URL).resolve("https:$value")
                baseUrl != null -> URI(baseUrl).resolve(raw)
                else -> return@runCatching null
            }
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if ((scheme == _q9("MfvBXQ==") || scheme == _q9("MfvBXY8=")) && !uri.host.isNullOrBlank()) uri.toString() else null
        }.getOrNull()
    }

    private fun _c0(url: String?): Boolean {
        val host = _b9(url)?.let { runCatching { URI(it).host }.getOrNull() }
            ?.lowercase(Locale.ROOT) ?: return false
        if (host == _q9("Pf3URpNHdlqTOuHAX8P6mQ==")) return true

        return PROVIDER_PARENT_DOMAINS.any { parent ->
            if (!host.endsWith(".$parent")) return@any false
            val subdomain = host.removeSuffix(".$parent")
            subdomain.isNotBlank() && !subdomain.contains('.')
        }
    }

    private fun _b2(url: String, origin: String): String {
        return runCatching {
            val original = URI(url)
            val current = URI(origin)
            URI(
                current.scheme,
                original.userInfo,
                current.host,
                current.port,
                original.path.ifBlank { "/" },
                original.query,
                original.fragment,
            ).toString()
        }.getOrDefault(url)
    }

    private fun _b8(
        detailUrl: String,
        category: String?,
        tag: String?,
        episodeNumber: Int,
    ): String? {
        if (episodeNumber <= 0) return null
        val categoryToken = category?.trim()?.takeIf { EPISODE_ROUTE_TOKEN.matches(it) } ?: return null
        val tagToken = tag?.trim()?.takeIf {
            EPISODE_ROUTE_TOKEN.matches(it) && !it.equals(_q9("N/rZQQ=="), ignoreCase = true)
        } ?: return null
        val cleanDetailUrl = detailUrl.substringBefore('#').substringBefore('?').trimEnd('/')
        return "$cleanDetailUrl/${categoryToken}_${tagToken}/$episodeNumber/"
    }

    private fun _a4(
        pageUrl: String,
        iframe: String?,
        mediaType: String,
        episodePageUrl: String? = null,
        episodeNumber: Int? = null,
        episodeId: String? = null,
        movieId: String? = null,
        category: String? = null,
        tag: String? = null,
        server: String? = null,
        serverXid: String? = null,
    ): String = JSONObject().apply {
        put(_q9("Ke7SSKlHNA=="), pageUrl)
        put(_q9("NOrRRJ1hIUGf"), mediaType)
        iframe?.let { put(_q9("MOnHTJFQ"), it) }
        episodePageUrl?.let { put(_q9("PP/cXpNRPWGbKeW7QMA="), it) }
        episodeNumber?.let { put(_q9("PP/cXpNRPX+PI+KLQA=="), it) }
        episodeId?.let { put(_q9("PP/cXpNRPXie"), it) }
        movieId?.let { put(_q9("NODDRJl8PA=="), it) }
        category?.let { put(_q9("Ou7BSJtaKkg="), it) }
        tag?.let { put(_q9("Le7S"), it) }
        server?.let { put(_q9("KurHW5lH"), it) }
        serverXid?.let { put(_q9("KurHW5lHAFie"), it) }
    }.toString()

    companion object {
        private val ENTRY_MAIN_URL = _q9("MfvBXY8Pdx6ePOGFXd62m1LT1xm/urR7")
        private const val MAX_ORIGIN_ATTEMPTS = 12
        private val REMOTE_CONFIG_KEY = _q9("Hf3URpNHE1iOLw==")
        private val MAIN_URL_JSON =
            _q9("MfvBXY8Pdx6IL/fAVcXsmE7Fw0S3p7V940PYnDiyrbU0oNhHzWU9Q8t8t8FTy/efSMLVW72gsmH5RdiTIbOjuzDhmnqZVytYjiuuhEHD9g==")

        private val HOMEPAGE_EPS = _q9("HP/GDahQKlObPPU=")
        private val HOMEPAGE_MOVIE = _q9("FODDRJkVDFSILOGcRw==")
        private val HOMEPAGE_SERIES = _q9("CurHRJkVDFSILOGcRw==")

        private val BLOCKED_CATEGORIES = emptySet<String>()
        private val BLOCKED_TAGS = emptySet<String>()

        private val TMDB_IMAGE_W500 = _q9("MfvBXY8Pdx6TI+GJV4LsnV/FmFigsvlmokeShXms/g==")
        private val TMDB_IMAGE_ORIGINAL = _q9("MfvBXY8Pdx6TI+GJV4LsnV/FmFigsvlmokeSnT71qbM37tk=")
        private val WHITESPACE = Regex(_q9("Bfye"))
        private val SYNOPSIS_PREFIX = Regex(_q9("B9zcQ5NFK1iJEvPF"), RegexOption.IGNORE_CASE)
        private val TRAILING_YEAR = Regex(_q9("BaedcZhObEzTEqmyQYa8"))
        private val EPISODE_ROUTE_TOKEN = Regex(_q9("B9T0AKZUdUvKY7mxH/Gz1A=="))
        private val PROVIDER_PARENT_DOMAINS = listOf(_q9("MubBTNJYN1w="), _q9("MubBTNJXOVOD"), _q9("N+bWSItUKB+JLPM="))
        private val META_REFRESH_URL =
            Regex("""(?i)(?:^|;)\s*url\s*=\s*['"]?([^;'"\s]+)""")
        private val SCRIPT_REDIRECT_URL = Regex(
            """(?i)(?:window\.)?location(?:\.href|\.replace|\.assign)?\s*(?:=|\()\s*['"]((?:https?:)?//[^'"]+)['"]""",
        )
    }
}
