package com.agooseangsa.MidasXXI

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class MidasXXI : MainAPI() {
    private val providerProfile = AgooseProviderProfile.current

    override var mainUrl = providerProfile.defaultMainUrl
    override var name = _q9("ReY0rUlfVRgR")
    override var lang = "id"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        *providerProfile.homepage.map { it.source to it.title }.toTypedArray(),
    )

    private val _a0 = Mutex()
    private var _a1 = false

    private val _a2 by lazy(LazyThreadSafetyMode.NONE) {
        providerProfile.blockedCategories().mapNotNull(::normalizeTaxonomyName).toSet()
    }
    private val _a3 by lazy(LazyThreadSafetyMode.NONE) {
        providerProfile.blockedTags().mapNotNull(::normalizeTaxonomyName).toSet()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        if (page > 1) return newHomePageResponse(request, emptyList(), false)

        val response = app.get(fixUrl(request.data))
        syncMainUrl(response.url)
        val items = _a4(response.document, request.name)
        return newHomePageResponse(
            listOf(HomePageList(request.name, items, isHorizontalImages = false)),
            false,
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encoded = URLEncoder.encode(query, _q9("XdsW4QI="))
        val searchBase = absoluteUrl(mainUrl, providerProfile.endpoint(_q9("e+oxvlkXXSEsXg==")))
        val searchParam = URLEncoder.encode(providerProfile.endpoint(_q9("e+oxvlkXXSEqVz8=")), _q9("XdsW4QI="))
        val response = app.get("$searchBase?$searchParam=$encoded")
        syncMainUrl(response.url)
        return parseListing(response.document)
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val response = app.get(url)
        syncMainUrl(response.url)
        val path = runCatching { URI(response.url).path }.getOrNull().orEmpty()

        return when {
            path.contains(providerProfile.endpoint(_q9("e+oipV8MXSEsXh8MIWhrLg=="))) -> loadSeries(response.url, response.document)
            path.contains(providerProfile.endpoint(_q9("ZeAmpV8vbDQwezMfOGZ8"))) -> loadMovie(response.url, response.document)
            else -> throw ErrorLoadingException(_q9("XOYgqRoXbCw5WzMDc05nOD+ZhyCn0TeiHUGPYSixizhj+j6r"))
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        ensureMainUrl()
        val pageResponse = app.get(data)
        if (isProviderUrl(pageResponse.url)) syncMainUrl(pageResponse.url)

        val directPlayers = _b4(pageResponse.document, pageResponse.url)
        if (directPlayers.isNotEmpty()) {
            return _b5(directPlayers, pageResponse.url, subtitleCallback, callback)
        }

        val ajaxPlayers = _b2(pageResponse.document, pageResponse.url)
        return _b5(ajaxPlayers, pageResponse.url, subtitleCallback, callback)
    }

    private suspend fun _b5(
        players: List<String>,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var matched = false
        for (playerUrl in players.distinct()) {
            matched = loadExtractor(
                url = playerUrl,
                referer = referer,
                subtitleCallback = subtitleCallback,
                callback = callback,
            ) || matched
        }
        return matched
    }

    private suspend fun _b2(document: Document, pageUrl: String): List<String> {
        val options = document.select(providerProfile.selector(_q9("eOMxtV8NQjAsXz0D")))
        if (options.isEmpty()) return emptyList()

        val endpoint = absoluteUrl(pageUrl, providerProfile.endpoint(_q9("aes9pVQ+ZyEgZjMZOw==")))
        return options.mapNotNull { option ->
            val post = option.attr(providerProfile.selector(_q9("eOMxtV8NXS8rQhMZJ3E="))).trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val type = option.attr(providerProfile.selector(_q9("eOMxtV8NWTkoUxMZJ3E="))).trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val nume = option.attr(providerProfile.selector(_q9("eOMxtV8NQzU1VDcfEnd6Lg=="))).trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null

            val response = runCatching {
                app.post(
                    endpoint,
                    referer = pageUrl,
                    data = mapOf(
                        _q9("aewkpVUR") to _q9("bOA/k0oTbDk9RA0MOWJ2"),
                        _q9("eOAjuA==") to post,
                        _q9("Zvo9qQ==") to nume,
                        _q9("fPYgqQ==") to type,
                    ),
                )
            }.getOrNull() ?: return@mapNotNull null

            _b3(response.text, response.url)
        }.distinct()
    }

    private fun _b3(text: String, responseUrl: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val embed = runCatching { JSONObject(trimmed).optString(_q9("beIyqV4geDI0")) }
            .getOrNull()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: trimmed

        val iframe = Jsoup.parse(embed).selectFirst(providerProfile.selector(_q9("aeUxtHMZfyE1Uw==")))?.attr(_q9("e/0z"))
            ?.trim()?.takeIf(String::isNotBlank)
        if (iframe != null) return absoluteUrl(responseUrl, iframe)

        val decoded = embed.replace("\\/", "/").trim()
        return decoded.takeIf { it.startsWith(_q9("YPskvABQIg==")) || it.startsWith(_q9("YPskvElFIm8=")) }
            ?.let { absoluteUrl(responseUrl, it) }
    }

    private fun _b4(document: Document, pageUrl: String): List<String> = document
        .select(providerProfile.selector(_q9("eOMxtV8NRCYqVz8I")))
        .mapNotNull { it.attr(_q9("e/0z")).trim().takeIf(String::isNotBlank) }
        .map { absoluteUrl(pageUrl, it) }
        .filter { it.startsWith(_q9("YPskvABQIg==")) || it.startsWith(_q9("YPskvElFIm8=")) }
        .distinct()

    private suspend fun loadMovie(url: String, document: Document): LoadResponse {
        val title = document.selectFirst(providerProfile.selector(_q9("bOokrVMTWSksWjc=")))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw ErrorLoadingException(_q9("Qvo0uVZfYC8uXzdNJ2pqPTXKuxGalC6+EkGK"))
        val websitePoster = document.selectFirst(providerProfile.selector(_q9("bOokrVMTXS8rQjcf")))?.attr(_q9("e/0z"))
            ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(url, it) }
        val websiteYear = extractYear(document.selectFirst(providerProfile.selector(_q9("bOokrVMTSSEsUw==")))?.text())
        val websitePlot = document.selectFirst(providerProfile.selector(_q9("bOokrVMTXSw3Qg==")))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteGenres = document.select(providerProfile.selector(_q9("bOokrVMTSiU2RDce")))
            .map { it.text().trim() }.filter(String::isNotBlank)
        val websiteTags = _a9(document)
        val websiteRuntime = parseMinutes(document.selectFirst(providerProfile.selector(_q9("ZeAmpV8teC4sXz8I")))?.text())
        val websiteRating = document.selectFirst(providerProfile.selector(_q9("ZeAmpV88Yi4sUzwZAWJ6NTCN")))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteActors = _a7(document, url)
        val websiteScore = _b0(document, _q9("XMIUrhotbDQxWDU="))?.let(::firstNumber)
        val originalTitle = _b0(document, _q9("R/05q1MRbCx4QjsZP2Y="))
        val websiteTrailers = _a8(document, url)

        enforceContentAllowed(websiteGenres, websiteTags)

        val tmdb = fetchAgooseTmdbMetadata(
            AgooseTmdbIdentity(
                originalTitle = originalTitle,
                displayTitle = title,
                year = websiteYear,
                isTv = false,
            ),
        )

        return newMovieLoadResponse(title, url, TvType.Movie, dataUrl = url) {
            posterUrl = tmdb?.posterUrl ?: websitePoster
            backgroundPosterUrl = tmdb?.backdropUrl
            year = tmdb?.year ?: websiteYear
            plot = tmdb?.overview?.takeIf(String::isNotBlank) ?: websitePlot
            tags = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: websiteGenres
            duration = tmdb?.runtimeMinutes ?: websiteRuntime
            contentRating = websiteRating

            addActors(websiteActors)
            tmdb?.tmdbId?.let { addTMDbId(it.toString()) }
            tmdb?.imdbId?.let { addImdbId(it) }
            tmdb?.voteAverage?.let { addScore(it.toString()) }
                ?: websiteScore?.let { addScore(it) }
            (websiteTrailers + tmdb?.trailers.orEmpty()).distinct().forEach { addTrailer(it) }
        }
    }

    private suspend fun loadSeries(url: String, document: Document): LoadResponse {
        val title = document.selectFirst(providerProfile.selector(_q9("bOokrVMTWSksWjc=")))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw ErrorLoadingException(_q9("Qvo0uVZffiUqXzMBc3dnOD+B/xyHhSamDEuFLw=="))
        val websitePoster = document.selectFirst(providerProfile.selector(_q9("bOokrVMTXS8rQjcf")))?.attr(_q9("e/0z"))
            ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(url, it) }
        val websiteYear = extractYear(document.selectFirst(providerProfile.selector(_q9("bOokrVMTSSEsUw==")))?.text())
        val websitePlot = document.selectFirst(providerProfile.selector(_q9("bOokrVMTXSw3Qg==")))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteGenres = document.select(providerProfile.selector(_q9("bOokrVMTSiU2RDce")))
            .map { it.text().trim() }.filter(String::isNotBlank)
        val websiteTags = _a9(document)
        val websiteActors = _a7(document, url)
        val websiteScore = _b0(document, _q9("XMIUrhotbDQxWDU="))?.let(::firstNumber)
        val originalTitle = _b0(document, _q9("R/05q1MRbCx4QjsZP2Y="))
        val websiteTrailers = _a8(document, url)
        val episodes = _a6(document, url)

        enforceContentAllowed(websiteGenres, websiteTags)

        val tmdb = fetchAgooseTmdbMetadata(
            AgooseTmdbIdentity(
                originalTitle = originalTitle,
                displayTitle = title,
                year = websiteYear,
                isTv = true,
            ),
        )

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = tmdb?.posterUrl ?: websitePoster
            backgroundPosterUrl = tmdb?.backdropUrl
            year = tmdb?.year ?: websiteYear
            plot = tmdb?.overview?.takeIf(String::isNotBlank) ?: websitePlot
            tags = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: websiteGenres
            duration = tmdb?.runtimeMinutes

            addActors(websiteActors)
            tmdb?.tmdbId?.let { addTMDbId(it.toString()) }
            tmdb?.imdbId?.let { addImdbId(it) }
            tmdb?.voteAverage?.let { addScore(it.toString()) }
                ?: websiteScore?.let { addScore(it) }
            (websiteTrailers + tmdb?.trailers.orEmpty()).distinct().forEach { addTrailer(it) }
        }
    }

    private fun _a4(document: Document, heading: String): List<SearchResponse> {
        val header = document.select(providerProfile.selector(_q9("YOA9qUoeaiUQUzMJNnE=")))
            .firstOrNull { it.selectFirst(providerProfile.selector(_q9("YOA9qUoeaiUQUzMJOm1p")))?.text()?.trim()?.equals(heading, ignoreCase = true) == true }
            ?: return emptyList()

        var sibling = header.nextElementSibling()
        while (sibling != null && !sibling.`is`(providerProfile.selector(_q9("YOA9qUoeaiUQUzMJNnE=")))) {
            val items = sibling.select(providerProfile.selector(_q9("ZOYjuFMRagksUz8=")))
            if (items.isNotEmpty()) {
                return items.mapNotNull(::_a5).distinctBy { it.url }
            }
            sibling = sibling.nextElementSibling()
        }
        return emptyList()
    }

    private fun parseListing(document: Document): List<SearchResponse> = document
        .select(providerProfile.selector(_q9("ZOYjuFMRagksUz8=")))
        .mapNotNull(::_a5)
        .distinctBy { it.url }

    private fun _a5(element: Element): SearchResponse? {
        val link = element.selectFirst(providerProfile.selector(_q9("ZOYjuFMRagwxWDk=")))
            ?: return null
        val href = link.attr(_q9("YP01qg==")).trim().takeIf(String::isNotBlank) ?: return null
        val absolute = absoluteUrl(mainUrl, href)
        val path = runCatching { URI(absolute).path }.getOrNull().orEmpty()
        val type = when {
            path.contains(providerProfile.endpoint(_q9("ZeAmpV8vbDQwezMfOGZ8"))) -> TvType.Movie
            path.contains(providerProfile.endpoint(_q9("e+oipV8MXSEsXh8MIWhrLg=="))) -> TvType.TvSeries
            else -> return null
        }
        val title = element.selectFirst(providerProfile.selector(_q9("ZOYjuFMRahQxQj4I")))?.text()?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val poster = element.selectFirst(providerProfile.selector(_q9("ZOYjuFMRahA3RSYIIQ==")))?.let { image ->
            image.attr(_q9("bO4krRcMfyM=")).takeIf(String::isNotBlank)
                ?: image.attr(_q9("e/0z")).takeIf(String::isNotBlank)
        }?.let { absoluteUrl(mainUrl, it) }
        val year = extractYear(element.selectFirst(providerProfile.selector(_q9("ZOYjuFMRagQ5Qjc=")))?.text())

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, absolute, TvType.TvSeries) {
                posterUrl = poster
                this.year = year
            }
        } else {
            newMovieSearchResponse(title, absolute, TvType.Movie) {
                posterUrl = poster
                this.year = year
            }
        }
    }

    private fun _a6(document: Document, pageUrl: String) = document
        .select(providerProfile.selector(_q9("bf85v1UbaAksUz8=")))
        .mapNotNull { item ->
            val link = item.selectFirst(providerProfile.selector(_q9("bf85v1UbaAwxWDk="))) ?: return@mapNotNull null
            val href = link.attr(_q9("YP01qg==")).trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val numbers = Regex(_q9("INM05xMjfmp1aiFHe19qd3c="))
                .find(item.selectFirst(providerProfile.selector(_q9("bf85v1UbaA4tWzAIIQ==")))?.text().orEmpty())
            val season = numbers?.groupValues?.getOrNull(1)?.toIntOrNull()
            val episodeNumber = numbers?.groupValues?.getOrNull(2)?.toIntOrNull()
            val episodeTitle = link.text().trim().takeIf(String::isNotBlank)
            val poster = item.selectFirst(providerProfile.selector(_q9("bf85v1UbaBA3RSYIIQ==")))?.attr(_q9("e/0z"))
                ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(pageUrl, it) }
            val date = item.selectFirst(providerProfile.selector(_q9("bf85v1UbaAQ5Qjc=")))?.text()?.trim()?.takeIf(String::isNotBlank)

            newEpisode(absoluteUrl(pageUrl, href)) {
                name = episodeTitle
                this.season = season
                episode = episodeNumber
                posterUrl = poster
                _b1(date)?.let { addDate(it) }
            }
        }

    private fun _a7(document: Document, pageUrl: String): List<Pair<Actor, String?>> =
        document.select(providerProfile.selector(_q9("aewko0g2eSU1")))
            .mapNotNull { item ->
                val actorName = item.selectFirst(providerProfile.selector(_q9("aewko0gxbC09ezcZMg==")))?.attr(_q9("a+A+uF8ReQ=="))?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: item.selectFirst(providerProfile.selector(_q9("aewko0gxbC09YjcVJw==")))?.text()?.trim()?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val image = item.selectFirst(providerProfile.selector(_q9("aewko0g2YCE/Uw==")))?.attr(_q9("e/0z"))
                    ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(pageUrl, it) }
                val role = item.selectFirst(providerProfile.selector(_q9("aewko0gtYiw9")))?.text()?.trim()?.takeIf(String::isNotBlank)
                Actor(actorName, image) to role
            }

    private fun _a8(document: Document, pageUrl: String): List<String> = document
        .select(providerProfile.selector(_q9("fP0xpVYafwk+RDMANg==")))
        .mapNotNull { it.attr(_q9("e/0z")).trim().takeIf(String::isNotBlank) }
        .map { absoluteUrl(pageUrl, it) }
        .distinct()

    private fun _a9(document: Document): List<String> = document
        .select(providerProfile.selector(_q9("bOokrVMTWSE/")))
        .filterNot { link -> link.parents().any { parent -> parent.`is`(providerProfile.selector(_q9("fO4oo1QQYDkdTjEBJmdrOB+EvB2dhSy5Cg=="))) } }
        .map { it.text().trim() }
        .filter(String::isNotBlank)
        .distinct()

    private fun _b0(document: Document, label: String): String? = document
        .select(providerProfile.selector(_q9("a/ojuFUSSyk9WjY=")))
        .firstOrNull {
            it.selectFirst(providerProfile.selector(_q9("a/ojuFUSSyk9WjYhMmFrMA==")))?.text()?.trim()?.equals(label, ignoreCase = true) == true
        }
        ?.selectFirst(providerProfile.selector(_q9("a/ojuFUSSyk9WjY7Mm97OQ==")))
        ?.text()
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun firstNumber(value: String): String? =
        Regex(_q9("VOt75AVFVm50aw4JeCox")).find(value)?.value?.replace(',', '.')

    private fun extractYear(value: String?): Int? =
        Regex(_q9("ILBq/QMDP3BxajYWYX4=")).find(value.orEmpty())?.value?.toIntOrNull()

    private fun parseMinutes(value: String?): Int? = Regex(_q9("INM05xMjfmoVXzw="), RegexOption.IGNORE_CASE)
        .find(value.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun _b1(value: String?): String? {
        val match = Regex(_q9("INQR4WAeIDoFTWEQel8gAC3B9ySKinLnS13NbRCrxGVU6yv4R1Y="))
            .find(value.orEmpty()) ?: return null
        val month = when (match.groupValues[1].lowercase(Locale.ROOT)) {
            _q9("Yu4+") -> 1
            _q9("buoy") -> 2
            _q9("Ze4i") -> 3
            _q9("af8i") -> 4
            _q9("Ze4p") -> 5
            _q9("Yvo+") -> 6
            _q9("Yvo8") -> 7
            _q9("afo3") -> 8
            _q9("e+og") -> 9
            _q9("Z+wk") -> 10
            _q9("ZuAm") -> 11
            _q9("bOoz") -> 12
            else -> return null
        }
        val day = match.groupValues[2].toIntOrNull() ?: return null
        val year = match.groupValues[3].toIntOrNull() ?: return null
        return _q9("Lb9kqBdaPXI8G3ddYWc=").format(Locale.ROOT, year, month, day)
    }

    private suspend fun ensureMainUrl() {
        if (_a1) return
        _a0.withLock {
            if (_a1) return@withLock

            val remoteCandidates = runCatching {
                JSONObject(app.get(providerProfile.websiteJsonUrl).text).readMainUrlCandidates()
            }.getOrDefault(emptyList())
            val candidates = (remoteCandidates + providerProfile.defaultMainUrl)
                .mapNotNull(::normalizeHttpBaseUrl)
                .distinct()

            for (candidate in candidates) {
                val response = runCatching { app.get(candidate) }.getOrNull() ?: continue
                if (!response.isSuccessful) continue
                val resolved = normalizeHttpBaseUrl(response.url) ?: continue
                mainUrl = resolved
                _a1 = true
                return@withLock
            }
            mainUrl = providerProfile.defaultMainUrl
        }
    }

    private fun syncMainUrl(responseUrl: String?) {
        normalizeHttpBaseUrl(responseUrl)?.let { mainUrl = it }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(providerProfile.websiteKey) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index) }
            .mapNotNull(::normalizeHttpBaseUrl)
            .distinct()
    }

    private fun normalizeHttpBaseUrl(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf(String::isNotBlank) ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            if ((scheme == _q9("YPskvA==") || scheme == _q9("YPskvEk=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else null
        }.getOrNull()
    }

    private fun isProviderUrl(url: String): Boolean {
        val base = normalizeHttpBaseUrl(url) ?: return false
        return base == normalizeHttpBaseUrl(mainUrl) || base == providerProfile.defaultMainUrl
    }

    private fun absoluteUrl(base: String, value: String): String = runCatching {
        URI(if (base.endsWith('/')) base else "$base/").resolve(value).toString()
    }.getOrElse { value }

    private fun shouldBlockContent(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ): Boolean {
        if (categories.asSequence().mapNotNull(::normalizeTaxonomyName).any { it in _a2 }) {
            return true
        }
        return tags.asSequence().mapNotNull(::normalizeTaxonomyName).any { it in _a3 }
    }

    private fun enforceContentAllowed(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ) {
        if (shouldBlockContent(categories, tags)) {
            throw ErrorLoadingException(_q9("Q+A+uF8RLSQxVD4COGp8fDGGuhDOmiylH0mDND65nCQo/yKjTBZpJSo="))
        }
    }

    private fun normalizeTaxonomyName(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf(String::isNotBlank)
        ?.lowercase(Locale.ROOT)

    companion object {
        private val WHITESPACE = Regex(_q9("VPx7"))
    }
}
