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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class DrakorKita : MainAPI() {
    override var mainUrl = DEFAULT_MAIN_URL
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

    private val _a7 = Mutex()
    private var _a8 = false

    private val blockedCategoryKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_CATEGORIES.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    private val blockedTagKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_TAGS.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    private suspend fun ensureMainUrl() {
        if (_a8) return

        _a7.withLock {
            if (_a8) return@withLock

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
                _a8 = true
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

        ensureMainUrl()
        val response = app.get(mainUrl)
        syncMainUrl(response.url)

        val heading = response.document.select(_q9("MbubRZlUPFiUKbE="))
            .firstOrNull { _a0(it.text()) == request.data }
        val row = heading?.nextElementSibling()
        val results = row?.select(_q9("OKHFQo9BPUOhJvKLVPE="))
            ?.mapNotNull(::_a1)
            ?.distinctBy { it.url }
            .orEmpty()

        return newHomePageResponse(request, results, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encoded = URLEncoder.encode(query, _q9("DNvzAMQ="))
        val response = app.get("$mainUrl/all?q=$encoded")
        syncMainUrl(response.url)
        return response.document.select(_q9("OKHFQo9BPUOhJvKLVPE="))
            .mapNotNull(::_a1)
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val response = app.get(_a5(url))
        syncMainUrl(response.url)
        val document = response.document
        val detailUrl = response.url

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
                val data = _a4(
                    pageUrl = detailUrl,
                    iframe = exactActiveIframe,
                    mediaType = "tv",
                    episodeId = episodeElement.attr(_q9("Pe7BTNFQKFie")).takeIf { it.isNotBlank() },
                    movieId = episodeElement.attr(_q9("Pe7BTNFYN0eTK+mK")).takeIf { it.isNotBlank() },
                    category = episodeElement.attr(_q9("Pe7BTNFWOUU=")).takeIf { it.isNotBlank() },
                    tag = episodeElement.attr(_q9("Pe7BTNFBOVY=")).takeIf { it.isNotBlank() },
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
        val iframe = payload.optString(_q9("MOnHTJFQ")).trim().takeIf { it.isNotBlank() } ?: return false
        val referer = payload.optString(_q9("Ke7SSKlHNA==")).trim().takeIf { it.isNotBlank() }
        return loadExtractor(iframe, referer, subtitleCallback, callback)
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

        val fixedUrl = if (href.startsWith(_q9("MfvBXcYadw==")) || href.startsWith(_q9("MfvBXY8Pdx4="))) href else "$mainUrl/${href.trimStart('/')}"
        val fixedPoster = poster?.let {
            if (it.startsWith(_q9("MfvBXcYadw==")) || it.startsWith(_q9("MfvBXY8Pdx4="))) it else "$mainUrl/${it.trimStart('/')}"
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

    private fun _a5(url: String): String {
        return runCatching {
            val original = URI(url)
            val current = URI(mainUrl)
            URI(
                current.scheme,
                original.userInfo,
                current.host,
                current.port,
                original.path,
                original.query,
                original.fragment,
            ).toString()
        }.getOrDefault(url)
    }

    private fun _a4(
        pageUrl: String,
        iframe: String?,
        mediaType: String,
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
        episodeId?.let { put(_q9("PP/cXpNRPXie"), it) }
        movieId?.let { put(_q9("NODDRJl8PA=="), it) }
        category?.let { put(_q9("Ou7BSJtaKkg="), it) }
        tag?.let { put(_q9("Le7S"), it) }
        server?.let { put(_q9("KurHW5lH"), it) }
        serverXid?.let { put(_q9("KurHW5lHAFie"), it) }
    }.toString()

    companion object {
        private val DEFAULT_MAIN_URL = _q9("MfvBXY8Pdx6ePOGFXd62m1LT1xm/urR7")
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
    }
}
