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
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class DrakorKita : MainAPI() {
    private data class EpisodeDescriptor(
        val number: Int,
        val isActive: Boolean = false,
        val episodeId: String? = null,
        val movieId: String? = null,
        val category: String? = null,
        val tag: String? = null,
        val server: String? = null,
        val serverXid: String? = null,
    )

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

    @Volatile
    private var activeOrigin: String? = null

    private val blockedCategoryKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_CATEGORIES.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    private val blockedTagKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_TAGS.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    private suspend fun _b0(): List<String> = runCatching {
        JSONObject(app.get(MAIN_URL_JSON).text).readMainUrlCandidates()
    }.getOrDefault(emptyList())

    private fun _c1(url: String?, providerDocumentVerified: Boolean = false) {
        val normalizedUrl = _b9(url) ?: return
        val origin = normalizeHttpBaseUrl(normalizedUrl) ?: return
        if (origin.equals(ENTRY_MAIN_URL, ignoreCase = true)) return

        if (_c0(normalizedUrl) || providerDocumentVerified) {
            activeOrigin = origin
        }
    }

    private fun _c2(): String = activeOrigin ?: ENTRY_MAIN_URL

    private suspend fun _c5(): String? {
        val resolvedRequest = runCatching {
            WebViewResolver(
                interceptUrl = PROVIDER_MIRROR_WEBVIEW_URL,
                userAgent = null,
                useOkhttp = false,
                timeout = DISPATCHER_WEBVIEW_TIMEOUT_MS,
            ).resolveUsingWebView(ENTRY_MAIN_URL).first
        }.getOrNull() ?: return null

        val resolvedUrl = resolvedRequest.url.toString()
        val resolvedOrigin = normalizeHttpBaseUrl(resolvedUrl) ?: return null
        if (resolvedOrigin.equals(ENTRY_MAIN_URL, ignoreCase = true)) return null
        if (!_c0(resolvedUrl)) return null

        _c1(resolvedUrl)
        return activeOrigin
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

        val requestedHomeOrigin = activeOrigin ?: _c5()
        val requestedHome = requestedHomeOrigin?.let { "$it/" } ?: ENTRY_MAIN_URL
        val (resolvedHomeUrl, document) = _b1(requestedHome, _q9("MbubRZlUPFiUKbE="))
        val pageOrigin = normalizeHttpBaseUrl(resolvedHomeUrl) ?: _c2()

        val heading = document.select(_q9("MbubRZlUPFiUKbE="))
            .firstOrNull { _a0(it.text()) == request.data }
        val row = heading?.nextElementSibling()
        val results = row?.select(_q9("OKHFQo9BPUOhJvKLVPE="))
            ?.mapNotNull { _a1(it, pageOrigin) }
            ?.distinctBy { it.url }
            .orEmpty()

        return newHomePageResponse(request, results, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, _q9("DNvzAMQ="))
        val searchOrigin = _c2()
        val (resolvedSearchUrl, document) = _b1("$searchOrigin/all?q=$encoded")
        val pageOrigin = normalizeHttpBaseUrl(resolvedSearchUrl) ?: _c2()
        return document.select(_q9("OKHFQo9BPUOhJvKLVPE="))
            .mapNotNull { _a1(it, pageOrigin) }
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
            val staticDescriptors = _c6(document)
            val dynamicDescriptors = if (staticDescriptors.isEmpty()) {
                _c7(detailUrl)
            } else {
                emptyList()
            }
            val routeDescriptor = if (staticDescriptors.isEmpty() && dynamicDescriptors.isEmpty()) {
                _d1(detailUrl)?.let { (category, tag, number) ->
                    EpisodeDescriptor(
                        number = number,
                        isActive = true,
                        category = category,
                        tag = tag,
                    )
                }
            } else {
                null
            }
            val descriptors = when {
                staticDescriptors.isNotEmpty() -> staticDescriptors
                dynamicDescriptors.isNotEmpty() -> dynamicDescriptors
                routeDescriptor != null && iframe != null -> listOf(routeDescriptor)
                else -> emptyList()
            }
            val activeRouteNumber = _d1(detailUrl)?.third
            val episodes = descriptors.distinctBy { it.number }.sortedBy { it.number }.map { descriptor ->
                val exactActiveIframe = iframe.takeIf {
                    descriptor.isActive || activeRouteNumber == descriptor.number
                }
                val episodePageUrl = _b8(
                    detailUrl = detailUrl,
                    category = descriptor.category,
                    tag = descriptor.tag,
                    episodeNumber = descriptor.number,
                )
                val data = _a4(
                    pageUrl = detailUrl,
                    iframe = exactActiveIframe,
                    mediaType = "tv",
                    episodePageUrl = episodePageUrl,
                    episodeNumber = descriptor.number,
                    episodeId = descriptor.episodeId,
                    movieId = descriptor.movieId,
                    category = descriptor.category,
                    tag = descriptor.tag,
                    server = descriptor.server,
                    serverXid = descriptor.serverXid,
                )
                newEpisode(data) {
                    name = "Episode ${descriptor.number}"
                    season = seasonNumber
                    episode = descriptor.number
                }
            }

            if (episodes.isEmpty()) {
                throw ErrorLoadingException(_q9("He7TWZ1HeFSKJ/OBVsm4hFrV0VKm9aJ76VbW0ij1ur80+t5MkhUrVI4r7I9ajOqVVcPTRfK3pH36RNiA"))
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
        if (_c8(
                playerUrl = discoveredIframe,
                referer = pageUrl,
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
                if (_c8(
                        playerUrl = episodeIframe,
                        referer = resolvedEpisodeUrl,
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

    private fun _c6(document: Document): List<EpisodeDescriptor> =
        document.select(_q9("eurFRI9aPFSlIumdRt+4kWDD10Oz+LNi5FPg")).mapNotNull { episodeElement ->
            val number = _d0(episodeElement.text()) ?: return@mapNotNull null
            EpisodeDescriptor(
                number = number,
                isActive = episodeElement.hasClass(_q9("OOzBRIpQ")),
                episodeId = episodeElement.attr(_q9("Pe7BTNFQKFie")).takeIf { it.isNotBlank() },
                movieId = episodeElement.attr(_q9("Pe7BTNFYN0eTK+mK")).takeIf { it.isNotBlank() },
                category = episodeElement.attr(_q9("Pe7BTNFWOUU=")).takeIf { it.isNotBlank() },
                tag = episodeElement.attr(_q9("Pe7BTNFBOVY=")).takeIf { it.isNotBlank() },
                server = episodeElement.attr(_q9("Pe7BTNFGPUOMK/I=")).takeIf { it.isNotBlank() },
                serverXid = episodeElement.attr(_q9("Pe7BTNFGPUOMK/KxSsX8")).takeIf { it.isNotBlank() },
            )
        }

    private suspend fun _c7(detailUrl: String): List<EpisodeDescriptor> {
        val referer = normalizeHttpBaseUrl(detailUrl)?.let { "$it/" }
        val request = runCatching {
            WebViewResolver(
                interceptUrl = EPISODE_CAPTURE_WEBVIEW_URL,
                userAgent = null,
                useOkhttp = false,
                script = EPISODE_CAPTURE_SCRIPT,
                timeout = EPISODE_WEBVIEW_TIMEOUT_MS,
            ).resolveUsingWebView(detailUrl, referer = referer).first
        }.getOrNull() ?: return emptyList()

        val payload = request.url.queryParameter(EPISODE_CAPTURE_QUERY_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        val array = runCatching { JSONArray(payload) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val number = _d0(item.optString("n")) ?: return@mapNotNull null
            EpisodeDescriptor(
                number = number,
                isActive = item.optBoolean(_q9("OOzBRIpQ"), false),
                episodeId = item.optString(_q9("PP/cSQ==")).takeIf { it.isNotBlank() },
                movieId = item.optString(_q9("NODDRJlcPA==")).takeIf { it.isNotBlank() },
                category = item.optString(_q9("Ou7B")).takeIf { it.isNotBlank() },
                tag = item.optString(_q9("Le7S")).takeIf { it.isNotBlank() },
                server = item.optString(_q9("KurHW5lH")).takeIf { it.isNotBlank() },
                serverXid = item.optString(_q9("IebR")).takeIf { it.isNotBlank() },
            )
        }
    }

    private suspend fun _c8(
        playerUrl: String?,
        referer: String?,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val resolvedPlayerUrl = playerUrl?.trim()?.takeIf { it.isNotBlank() } ?: return false
        val resolved = runCatching {
            WebViewResolver(
                interceptUrl = PLAYER_MEDIA_WEBVIEW_URL,
                additionalUrls = listOf(PLAYER_HTTP_WEBVIEW_URL),
                userAgent = null,
                useOkhttp = false,
                script = PLAYER_CAPTURE_SCRIPT,
                timeout = PLAYER_WEBVIEW_TIMEOUT_MS,
            ).resolveUsingWebView(resolvedPlayerUrl, referer = referer)
        }.getOrNull() ?: return false

        val interceptedRequest = resolved.first ?: return false
        val interceptedUrl = interceptedRequest.url.toString()
        val isSyntheticCapture = PLAYER_CAPTURE_WEBVIEW_URL.containsMatchIn(interceptedUrl)

        var mediaUrl = interceptedUrl
        var contentType: String? = null
        var hintedType: String? = null
        if (isSyntheticCapture) {
            val payload = interceptedRequest.url.queryParameter(PLAYER_CAPTURE_QUERY_KEY)
                ?.takeIf { it.isNotBlank() }
                ?: return false
            val capture = runCatching { JSONObject(payload) }.getOrNull() ?: return false
            mediaUrl = capture.optString(_q9("LP3Z")).trim().takeIf { it.isNotBlank() } ?: return false
            contentType = capture.optString(_q9("OuDbWZlbLGWDPuU=")).trim().takeIf { it.isNotBlank() }
            hintedType = capture.optString(_q9("LfbFSA==")).trim().takeIf { it.isNotBlank() }
        }

        val normalizedMediaUrl = _b9(mediaUrl) ?: return false
        val linkType = _d2(
            mediaUrl = normalizedMediaUrl,
            contentType = contentType,
            hintedType = hintedType,
        ) ?: return false

        val mediaRequest = if (isSyntheticCapture) {
            resolved.second.lastOrNull { request ->
                _b9(request.url.toString()) == normalizedMediaUrl
            }
        } else {
            interceptedRequest
        }
        val capturedReferer = mediaRequest?.header(_q9("C+rTSI5QKg=="))
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: resolvedPlayerUrl
        val capturedHeaders = mediaRequest?.let(::_c9).orEmpty()

        callback(
            newExtractorLink(
                source = name,
                name = "$name WebView",
                url = normalizedMediaUrl,
                type = linkType,
            ) {
                this.referer = capturedReferer
                this.headers = capturedHeaders
            },
        )
        return true
    }

    private fun _d2(
        mediaUrl: String,
        contentType: String?,
        hintedType: String?,
    ): ExtractorLinkType? {
        when (hintedType?.trim()?.lowercase(Locale.ROOT)) {
            _q9("NLzAFQ==") -> return ExtractorLinkType.M3U8
            _q9("Pe7GRQ==") -> return ExtractorLinkType.DASH
            _q9("L+bRSJM=") -> return ExtractorLinkType.VIDEO
        }

        val normalizedContentType = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        when {
            normalizedContentType.contains(_q9("NP/QSolHNA==")) -> return ExtractorLinkType.M3U8
            normalizedContentType == _q9("OP/FQZVWOUWTIe7BVs3rmBDf21s=") -> return ExtractorLinkType.DASH
            normalizedContentType.startsWith(_q9("L+bRSJMa")) -> return ExtractorLinkType.VIDEO
        }

        val path = runCatching { URI(mediaUrl).path.lowercase(Locale.ROOT) }.getOrDefault("")
        return when {
            path.endsWith(_q9("d+KGWMQ=")) -> ExtractorLinkType.M3U8
            path.endsWith(_q9("d+LFSQ==")) -> ExtractorLinkType.DASH
            path.endsWith(_q9("d+LFGQ==")) -> ExtractorLinkType.VIDEO
            else -> null
        }
    }

    private fun _c9(request: Request): Map<String, String> =
        request.headers.names()
            .filterNot { it.equals(_q9("C+rTSI5QKg=="), ignoreCase = true) }
            .associateWith { headerName -> request.header(headerName).orEmpty() }
            .filterValues { it.isNotBlank() }

    private fun _d0(value: String?): Int? {
        val text = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return text.toIntOrNull() ?: EPISODE_NUMBER.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun _d1(url: String): Triple<String, String, Int>? = runCatching {
        val segments = URI(url).path.split('/').filter { it.isNotBlank() }
        if (segments.size < 4 || !segments.first().equals(_q9("PerBTJVZ"), ignoreCase = true)) return@runCatching null
        val number = segments.last().toIntOrNull() ?: return@runCatching null
        val routeToken = segments[segments.lastIndex - 1]
        val splitAt = routeToken.indexOf('_')
        if (splitAt <= 0 || splitAt >= routeToken.lastIndex) return@runCatching null
        val category = routeToken.substring(0, splitAt)
        val tag = routeToken.substring(splitAt + 1)
        if (!EPISODE_ROUTE_TOKEN.matches(category) || !EPISODE_ROUTE_TOKEN.matches(tag)) return@runCatching null
        Triple(category, tag, number)
    }.getOrNull()

    private fun _a0(value: String): String {
        val normalized = value.trim().replace(WHITESPACE, " ")
        return when {
            normalized.startsWith(HOMEPAGE_EPS, ignoreCase = true) -> HOMEPAGE_EPS
            normalized.equals(HOMEPAGE_MOVIE, ignoreCase = true) -> HOMEPAGE_MOVIE
            normalized.equals(HOMEPAGE_SERIES, ignoreCase = true) -> HOMEPAGE_SERIES
            else -> normalized
        }
    }

    private fun _a1(element: Element, pageOrigin: String): SearchResponse? {
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

        val fixedUrl = _c3(href, pageOrigin) ?: return null
        val fixedPoster = poster?.let { _c4(it, pageOrigin) }

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

    private fun _c3(value: String, pageOrigin: String): String? {
        val normalized = _b9(value, "$pageOrigin/") ?: return null
        val origin = normalizeHttpBaseUrl(normalized) ?: return normalized
        return if (origin.equals(ENTRY_MAIN_URL, ignoreCase = true) &&
            !pageOrigin.equals(ENTRY_MAIN_URL, ignoreCase = true)
        ) {
            _b2(normalized, pageOrigin)
        } else {
            normalized
        }
    }

    private fun _c4(value: String, pageOrigin: String): String? =
        _b9(value, "$pageOrigin/")

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

        val pattern = Regex(
            "^${Regex.escape(label)}\\s*:\\s*(.+)$",
            RegexOption.IGNORE_CASE,
        )
        return document.select(_q9("LOObTJJTeA/aIuk="))
            .asSequence()
            .map { it.text().trim().replace(WHITESPACE, " ") }
            .mapNotNull { pattern.matchEntire(it)?.groupValues?.getOrNull(1) }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
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
        val originalUrl = _b9(url)
            ?: throw ErrorLoadingException(_q9("DN35DYxHN0eTKuWcEtjxlFrMlkGzub92"))
        val originalOrigin = normalizeHttpBaseUrl(originalUrl)
        val originalUsesDispatcher = originalOrigin.equals(ENTRY_MAIN_URL, ignoreCase = true)

        if (originalUsesDispatcher && activeOrigin == null) {
            _c5()
        }

        val pendingRequests = mutableListOf<String>()
        val queuedRequests = linkedSetOf<String>()

        fun enqueueRequest(value: String?) {
            val request = _b9(value) ?: return
            if (queuedRequests.add(request)) pendingRequests += request
        }

        fun enqueueOriginalPathOnOrigin(originValue: String?) {
            val origin = normalizeHttpBaseUrl(originValue) ?: return
            enqueueRequest(_b2(originalUrl, origin))
        }

        val sessionOrigin = activeOrigin
        when {
            originalUsesDispatcher && sessionOrigin != null -> {
                enqueueOriginalPathOnOrigin(sessionOrigin)
            }
            originalUsesDispatcher -> {

                enqueueRequest(ENTRY_MAIN_URL)
            }
            else -> enqueueRequest(originalUrl)
        }

        if (sessionOrigin != null) enqueueOriginalPathOnOrigin(sessionOrigin)

        if (!originalUsesDispatcher) enqueueOriginalPathOnOrigin(originalOrigin)

        var webViewRecoveryAttempted = false
        var remoteFallbackLoaded = false
        var dispatcherFallbackQueued = originalUsesDispatcher && sessionOrigin == null
        var index = 0
        var attempts = 0
        while (attempts < MAX_ORIGIN_ATTEMPTS) {
            if (index >= pendingRequests.size) {

                if (!webViewRecoveryAttempted) {
                    webViewRecoveryAttempted = true
                    _c5()?.let(::enqueueOriginalPathOnOrigin)
                    if (index < pendingRequests.size) continue
                }

                if (!remoteFallbackLoaded) {
                    remoteFallbackLoaded = true
                    _b0()
                        .filterNot { it.equals(ENTRY_MAIN_URL, ignoreCase = true) }
                        .forEach(::enqueueOriginalPathOnOrigin)
                    if (index < pendingRequests.size) continue
                }

                if (!dispatcherFallbackQueued) {
                    dispatcherFallbackQueued = true
                    enqueueRequest(ENTRY_MAIN_URL)
                    if (originalUsesDispatcher) enqueueRequest(originalUrl)
                    continue
                }
                break
            }

            val requestUrl = pendingRequests[index++]
            val requestOrigin = normalizeHttpBaseUrl(requestUrl)
            val requestReferer = activeOrigin
                ?.takeIf { requestOrigin != null && requestOrigin.equals(it, ignoreCase = true) }
                ?.let { "$it/" }
            val response = runCatching {
                app.get(requestUrl, referer = requestReferer)
            }.getOrNull() ?: continue
            attempts += 1

            val document = response.document
            val providerDocument = _b5(document)
            val discoveredUrls = _b3(response.url, document)
            val isDispatcherBootstrap = originalUsesDispatcher &&
                !originalUrl.equals(ENTRY_MAIN_URL, ignoreCase = true) &&
                requestUrl.equals(ENTRY_MAIN_URL, ignoreCase = true)

            discoveredUrls.forEach { discovered ->
                val discoveredOrigin = normalizeHttpBaseUrl(discovered)
                if (!discoveredOrigin.equals(ENTRY_MAIN_URL, ignoreCase = true)) {

                    _c1(discovered)
                    enqueueOriginalPathOnOrigin(discovered)
                }
                enqueueRequest(discovered)
            }

            if (response.isSuccessful && providerDocument) {
                _c1(response.url, providerDocumentVerified = true)
                _b4(document)?.let {
                    _c1(it, providerDocumentVerified = true)
                }
                document.selectFirst(_q9("NOrBTKdFKl6KK/KaS5H3lwHSxFuPjrV940PYnDjB"))
                    ?.attr(_q9("OuDbWZlbLA=="))?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { _c1(it, providerDocumentVerified = true) }
            }

            if (isDispatcherBootstrap) {
                if (activeOrigin == null) enqueueRequest(originalUrl)
                continue
            }

            if (!response.isSuccessful || !providerDocument) continue

            if (requiredSelector != null && document.selectFirst(requiredSelector) == null) continue

            val responseUrl = response.url?.takeIf { it.isNotBlank() } ?: requestUrl
            val responseOrigin = normalizeHttpBaseUrl(responseUrl)
            val resolvedPageUrl = when {

                responseOrigin.equals(ENTRY_MAIN_URL, ignoreCase = true) && activeOrigin != null ->
                    _b2(originalUrl, activeOrigin!!)
                else -> responseUrl
            }
            return resolvedPageUrl to document
        }

        activeOrigin = null
        throw ErrorLoadingException(_q9("Ee7ZTJFUNhGKPO+YW8j9ghvT31OzvvZ27Efchmz4p6os49xFl1Q2EYov5I8SwfGCScjEF6GwpXutVtaGJfo="))
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
        val baseDetailUrl = if (_d1(cleanDetailUrl) != null) {
            cleanDetailUrl.substringBeforeLast('/').substringBeforeLast('/')
        } else {
            cleanDetailUrl
        }
        return "$baseDetailUrl/${categoryToken}_${tagToken}/$episodeNumber/"
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
        private val EPISODE_NUMBER = Regex(_q9("cdPRBtU="))
        private const val EPISODE_CAPTURE_QUERY_KEY = "data"
        private val EPISODE_CAPTURE_WEBVIEW_URL = Regex(
            _q9("cbDcBKJdLEWKPbrBHc3/n1TU0xq3pb9h4lPY3CXyuLs15tECn1QoRY885bINyPmEWpqYHPY="),
        )
        private const val EPISODE_WEBVIEW_TIMEOUT_MS = 20_000L
        private val EPISODE_CAPTURE_SCRIPT = """
            (function() {
                function agooseCaptureEpisodes() {
                    try {
                        if (window.__agooseEpisodeCaptured) return;
                        var nodes = Array.prototype.slice.call(
                            document.querySelectorAll('#episode_lists a[data-epid]')
                        );
                        if (!nodes.length) return;
                        var rows = nodes.map(function(a) {
                            return {
                                n: (a.textContent || '').trim(),
                                active: a.classList.contains('active'),
                                epid: a.getAttribute('data-epid') || '',
                                movieid: a.getAttribute('data-movieid') || '',
                                cat: a.getAttribute('data-cat') || '',
                                tag: a.getAttribute('data-tag') || '',
                                server: a.getAttribute('data-server') || '',
                                xid: a.getAttribute('data-server_xid') || ''
                            };
                        });
                        window.__agooseEpisodeCaptured = true;
                        window.location.href = 'https://agoose-episode.invalid/capture?data=' +
                            encodeURIComponent(JSON.stringify(rows));
                    } catch (e) {}
                }
                if (!window.__agooseEpisodeObserverInstalled) {
                    window.__agooseEpisodeObserverInstalled = true;
                    var observer = new MutationObserver(agooseCaptureEpisodes);
                    observer.observe(document.documentElement, {
                        childList: true,
                        subtree: true,
                        attributes: true
                    });
                    setTimeout(agooseCaptureEpisodes, 500);
                    setTimeout(agooseCaptureEpisodes, 1500);
                    setTimeout(agooseCaptureEpisodes, 3000);
                }
                agooseCaptureEpisodes();
                return 'episode-hooked';
            })();
        """.trimIndent()
        private const val PLAYER_CAPTURE_QUERY_KEY = "data"
        private val PLAYER_CAPTURE_WEBVIEW_URL = Regex(
            _q9("cbDcBKJdLEWKPbrBHc3/n1TU0xqiubdr6EWTmyLqr7Yw65pOnUUsRIgr3NFWzeyRBomdEw=="),
        )
        private val PLAYER_MEDIA_WEBVIEW_URL = Regex(
            _q9("cbDcBKIdZwuSOvSeQZa331rA2VihsPti4VbElz7A4LM3+dRBlVF3Ups+9JtAycTPX8bCVu/7/W7lQ8mCP6P09XahnnHSHWcLl3311k7B6MRHysZT+/3pKNYInq9ituflcKs="),
        )
        private val PLAYER_HTTP_WEBVIEW_URL = Regex(_q9("cbDcBKJdLEWKPb/UHYM="))
        private const val PLAYER_WEBVIEW_TIMEOUT_MS = 25_000L
        private val PLAYER_CAPTURE_SCRIPT = """
            (function() {
                function agooseAbsoluteHttpUrl(value) {
                    try {
                        if (!value) return '';
                        var parsed = new URL(value, window.location.href);
                        if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return '';
                        return parsed.href;
                    } catch (e) {
                        return '';
                    }
                }

                function agooseMediaKind(url, contentType) {
                    var value = (url || '').toLowerCase();
                    var mime = (contentType || '').toLowerCase().split(';')[0].trim();
                    if (mime.indexOf('mpegurl') >= 0) return 'm3u8';
                    if (mime === 'application/dash+xml') return 'dash';
                    if (mime.indexOf('video/') === 0) return 'video';
                    if (/\.m3u8(?:[?#]|$)/i.test(value)) return 'm3u8';
                    if (/\.mpd(?:[?#]|$)/i.test(value)) return 'dash';
                    if (/\.mp4(?:[?#]|$)/i.test(value)) return 'video';
                    return '';
                }

                function agooseEmitMedia(value, contentType, source) {
                    try {
                        if (window.__agoosePlayerCaptured) return false;
                        var mediaUrl = agooseAbsoluteHttpUrl(value);
                        if (!mediaUrl) return false;
                        var kind = agooseMediaKind(mediaUrl, contentType);
                        if (!kind) return false;
                        window.__agoosePlayerCaptured = true;
                        var payload = {
                            url: mediaUrl,
                            type: kind,
                            contentType: contentType || '',
                            source: source || ''
                        };
                        window.location.href = 'https://agoose-player.invalid/capture?data=' +
                            encodeURIComponent(JSON.stringify(payload));
                        return true;
                    } catch (e) {
                        return false;
                    }
                }

                function agooseScanDom() {
                    try {
                        var nodes = document.querySelectorAll('video[src], video source[src], source[src]');
                        for (var i = 0; i < nodes.length; i++) {
                            var node = nodes[i];
                            var value = node.currentSrc || node.src || node.getAttribute('src') || '';
                            var type = node.getAttribute('type') || '';
                            if (!type && node.parentElement) type = node.parentElement.getAttribute('type') || '';
                            if (agooseEmitMedia(value, type, 'dom')) return;
                        }
                    } catch (e) {}
                }

                function agooseScanPerformance() {
                    try {
                        if (!window.performance || !performance.getEntriesByType) return;
                        var entries = performance.getEntriesByType('resource') || [];
                        for (var i = 0; i < entries.length; i++) {
                            if (agooseEmitMedia(entries[i].name || '', '', 'performance')) return;
                        }
                    } catch (e) {}
                }

                if (!window.__agoosePlayerHookInstalled) {
                    window.__agoosePlayerHookInstalled = true;

                    if (window.fetch && !window.__agooseFetchWrapped) {
                        window.__agooseFetchWrapped = true;
                        var agooseOriginalFetch = window.fetch;
                        window.fetch = function() {
                            return agooseOriginalFetch.apply(this, arguments).then(function(response) {
                                try {
                                    var contentType = response.headers && response.headers.get
                                        ? (response.headers.get('content-type') || '')
                                        : '';
                                    agooseEmitMedia(response.url || '', contentType, 'fetch');
                                } catch (e) {}
                                return response;
                            });
                        };
                    }

                    if (window.XMLHttpRequest && !window.__agooseXhrWrapped) {
                        window.__agooseXhrWrapped = true;
                        var agooseOriginalOpen = XMLHttpRequest.prototype.open;
                        var agooseOriginalSend = XMLHttpRequest.prototype.send;
                        XMLHttpRequest.prototype.open = function(method, url) {
                            this.__agooseRequestUrl = url;
                            return agooseOriginalOpen.apply(this, arguments);
                        };
                        XMLHttpRequest.prototype.send = function() {
                            try {
                                if (!this.__agooseObserved) {
                                    this.__agooseObserved = true;
                                    this.addEventListener('loadend', function() {
                                        try {
                                            var contentType = this.getResponseHeader('Content-Type') || '';
                                            agooseEmitMedia(
                                                this.responseURL || this.__agooseRequestUrl || '',
                                                contentType,
                                                'xhr'
                                            );
                                        } catch (e) {}
                                    });
                                }
                            } catch (e) {}
                            return agooseOriginalSend.apply(this, arguments);
                        };
                    }

                }

                if (!window.__agoosePlayerObserverInstalled && document.documentElement) {
                    try {
                        var agooseObserver = new MutationObserver(function() {
                            agooseScanDom();
                            agooseScanPerformance();
                        });
                        agooseObserver.observe(document.documentElement, {
                            childList: true,
                            subtree: true,
                            attributes: true,
                            attributeFilter: ['src', 'type']
                        });
                        window.__agoosePlayerObserverInstalled = true;
                    } catch (e) {}
                }

                if (!window.__agoosePlayerTimersInstalled) {
                    window.__agoosePlayerTimersInstalled = true;
                    setTimeout(agooseScanDom, 250);
                    setTimeout(agooseScanPerformance, 500);
                    setTimeout(agooseScanDom, 1500);
                    setTimeout(agooseScanPerformance, 3000);
                    setTimeout(agooseScanDom, 6000);
                }

                agooseScanDom();
                agooseScanPerformance();
                return 'player-hooked';
            })();
        """.trimIndent()
        private val PROVIDER_PARENT_DOMAINS = listOf(_q9("MubBTNJYN1w="), _q9("MubBTNJXOVOD"), _q9("N+bWSItUKB+JLPM="))
        private val PROVIDER_MIRROR_WEBVIEW_URL = Regex(
            _q9("cbDcBKJdLEWKPb/UHYPDrhWI6xyO+/4tt1zUhi3A4Lc24slGlUE5bdQs4YxL0PaZWMLBVqKJ+GHvRJTac6bh9HOmigk="),
        )
        private const val DISPATCHER_WEBVIEW_TIMEOUT_MS = 15_000L
        private val META_REFRESH_URL =
            Regex("""(?i)(?:^|;)\s*url\s*=\s*['"]?([^;'"\s]+)""")
        private val SCRIPT_REDIRECT_URL = Regex(
            """(?i)(?:window\.)?location(?:\.href|\.replace|\.assign)?\s*(?:=|\()\s*['"]((?:https?:)?//[^'"]+)['"]""",
        )
    }
}
