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
    override var mainUrl = DEFAULT_MAIN_URL
    override var name = _q9("Mt5xFw0i12/q")
    override var lang = "id"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "/" to _q9("PvRBPzFM"),
        "/" to _q9("N/hHJDFQ"),
        "/" to _q9("O+VUOz8="),
        "/" to _q9("PvlcOzs="),
        "/" to _q9("Kf5DNzND1w=="),
        "/" to _q9("O+VUOz8ixHjxUFw="),
        "/" to _q9("Of5ZO15WymXhVE95"),
        "/" to _q9("K+E1JTtQxnLwNUlp6+cRnEE="),
    )

    private val _a0 = Mutex()
    private var _a1 = false

    private val _a2 by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_CATEGORIES.mapNotNull(::normalizeTaxonomyName).toSet()
    }
    private val _a3 by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_TAGS.mapNotNull(::normalizeTaxonomyName).toSet()
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
        val encoded = URLEncoder.encode(query, _q9("KuNTW0Y="))
        val response = app.get("$mainUrl/?s=$encoded")
        syncMainUrl(response.url)
        return parseListing(response.document)
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val response = app.get(url)
        syncMainUrl(response.url)
        val path = runCatching { URI(response.url).path }.getOrNull().orEmpty()

        return when {
            path.contains(_q9("UMNjBRZt+ESM")) -> loadSeries(response.url, response.document)
            path.contains(_q9("UNp6ABdn/Bg=")) -> loadMovie(response.url, response.document)
            else -> throw ErrorLoadingException(_q9("K95lE15q7lvCeHxCmeg5qnXzk5h0lWKAPsyCOTeUdtoUwnsR"))
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
        val options = document.select(_q9("UdN6GQ5u7k78ZXFNwMAikXvwv6lS202NO9mINCOSYdsi7HEXCmOiQ9pleHHiwTG6da2ltVDQSw=="))
        if (options.isEmpty()) return emptyList()

        val endpoint = absoluteUrl(pageUrl, _q9("UMBlWx9m4l7NOnxI1Mw+43XqqrgTxX6Z"))
        return options.mapNotNull { option ->
            val post = option.attr(_q9("G9ZhF1Ny4ETX")).trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val type = option.attr(_q9("G9ZhF1N29kfG")).trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val nume = option.attr(_q9("G9ZhF1Ns+lrG")).trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null

            val response = runCatching {
                app.post(
                    endpoint,
                    referer = pageUrl,
                    data = mapOf(
                        _q9("HtRhHxFs") to _q9("G9h6KQ5u7k7GZ0JN08Qo"),
                        _q9("D9hmAg==") to post,
                        _q9("EcJ4Ew==") to nume,
                        _q9("C85lEw==") to type,
                    ),
                )
            }.getOrNull() ?: return@mapNotNull null

            _b3(response.text, response.url)
        }.distinct()
    }

    private fun _b3(text: String, responseUrl: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val embed = runCatching { JSONObject(trimmed).optString(_q9("Gtp3Expd+kXP")) }
            .getOrNull()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: trimmed

        val iframe = Jsoup.parse(embed).selectFirst(_q9("FtFnFxNn1ETRdkA="))?.attr(_q9("DMV2"))
            ?.trim()?.takeIf(String::isNotBlank)
        if (iframe != null) return absoluteUrl(responseUrl, iframe)

        val decoded = embed.replace("\\/", "/").trim()
        return decoded.takeIf { it.startsWith(_q9("F8NhBkQtoA==")) || it.startsWith(_q9("F8NhBg04oBg=")) }
            ?.let { absoluteUrl(responseUrl, it) }
    }

    private fun _b4(document: Document, pageUrl: String): List<String> = document
        .select(_q9("XNN6GQ5u7k78ZXFNwMAikWbluLBS22WMesSPazKQd/QMxXYrUiKhU8x6bUDY3A++eOGypU+Vf48ozIR8CI5gzCI="))
        .mapNotNull { it.attr(_q9("DMV2")).trim().takeIf(String::isNotBlank) }
        .map { absoluteUrl(pageUrl, it) }
        .filter { it.startsWith(_q9("F8NhBkQtoA==")) || it.startsWith(_q9("F8NhBg04oBg=")) }
        .distinct()

    private suspend fun loadMovie(url: String, document: Document): LoadResponse {
        val title = document.selectFirst(_q9("UcR9Ex9m6kWDO3lNzcRwpiU="))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw ErrorLoadingException(_q9("NcJxAxIi4ljVfHgMzcw0r3+gr6lJ0HucMcyH"))
        val websitePoster = document.selectFirst(_q9("UcR9Ex9m6kWDO21DytE1vDTppqdmxmSKBw=="))?.attr(_q9("DMV2"))
            ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(url, it) }
        val websiteYear = extractYear(document.selectFirst(_q9("UcR9Ex9m6kWDO3hUzdcx7jrkqrRY"))?.text())
        val websitePlot = document.selectFirst(_q9("XN57EBEioUDTOH5D19E1oGA="))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteGenres = document.select(_q9("UcR9Ex9m6kWDO25L3Ms1vHvz66Fm3WSMPIfUPnyad8EN0jpRIw=="))
            .map { it.text().trim() }.filter(String::isNotBlank)
        val websiteTags = _a9(document)
        val websiteRuntime = parseMinutes(document.selectFirst(_q9("UcR9Ex9m6kWDO29Z19E5o3E="))?.text())
        val websiteRating = document.selectFirst(_q9("UcR9Ex9m6kWDTnRY3MggvHvw9qNS22KMNNm7eCeUfMgi"))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteActors = _a7(document, url)
        val websiteScore = _b0(document, _q9("K/pRFF5Q7kPKe3o="))?.let(::firstNumber)
        val originalTitle = _b0(document, _q9("MMV8ERds7luDYXRY1cA="))
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
        val title = document.selectFirst(_q9("UcR9Ex9m6kWDO3lNzcRwpiU="))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw ErrorLoadingException(_q9("NcJxAxIi/FLRfHxAmdE5qnXr66RUwXOEL8aIdw=="))
        val websitePoster = document.selectFirst(_q9("UcR9Ex9m6kWDO21DytE1vDTppqdmxmSKBw=="))?.attr(_q9("DMV2"))
            ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(url, it) }
        val websiteYear = extractYear(document.selectFirst(_q9("UcR9Ex9m6kWDO3hUzdcx7jrkqrRY"))?.text())
        val websitePlot = document.selectFirst(_q9("XN57EBEioUDTOH5D19E1oGA="))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteGenres = document.select(_q9("UcR9Ex9m6kWDO25L3Ms1vHvz66Fm3WSMPIfUPnyad8EN0jpRIw=="))
            .map { it.text().trim() }.filter(String::isNotBlank)
        val websiteTags = _a9(document)
        val websiteActors = _a7(document, url)
        val websiteScore = _b0(document, _q9("K/pRFF5Q7kPKe3o="))?.let(::firstNumber)
        val originalTitle = _b0(document, _q9("MMV8ERds7luDYXRY1cA="))
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
        val header = document.select(_q9("F9J0Ehtw"))
            .firstOrNull { it.selectFirst("h2")?.text()?.trim()?.equals(heading, ignoreCase = true) == true }
            ?: return emptyList()

        var sibling = header.nextElementSibling()
        while (sibling != null && !sibling.`is`(_q9("F9J0Ehtw"))) {
            val items = sibling.select(_q9("HsVhHx1u6hnKYXhB"))
            if (items.isNotEmpty()) {
                return items.mapNotNull(::_a5).distinctBy { it.url }
            }
            sibling = sibling.nextElementSibling()
        }
        return emptyList()
    }

    private fun parseListing(document: Document): List<SearchResponse> = document
        .select(_q9("HsVhHx1u6hnKYXhB"))
        .mapNotNull(::_a5)
        .distinctBy { it.url }

    private fun _a5(element: Element): SearchResponse? {
        val link = element.selectFirst(_q9("UdN0Ah8i5wSDdEZEy8A2kzigo/Md1E2BKMiPRH/dPN8QxGETDCLubMtneErk"))
            ?: return null
        val href = link.attr(_q9("F8VwEA==")).trim().takeIf(String::isNotBlank) ?: return null
        val absolute = absoluteUrl(mainUrl, href)
        val path = runCatching { URI(absolute).path }.getOrNull().orEmpty()
        val type = when {
            path.contains(_q9("UNp6ABdn/Bg=")) -> TvType.Movie
            path.contains(_q9("UMNjBRZt+ESM")) -> TvType.TvSeries
            else -> return null
        }
        val title = element.selectFirst(_q9("UdN0Ah8i5wSDdDEMl8ExunWgo/MRlX7aeszFOTvO"))?.text()?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val poster = element.selectFirst(_q9("Ucd6BQpn/RfKeHoAmcw9qQ=="))?.let { image ->
            image.attr(_q9("G9ZhF1Nx/VQ=")).takeIf(String::isNotBlank)
                ?: image.attr(_q9("DMV2")).takeIf(String::isNotBlank)
        }?.let { absoluteUrl(mainUrl, it) }
        val year = extractYear(element.selectFirst(_q9("UdN0Ah8i/EfCew=="))?.text())

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
        .select(_q9("UdJlHw1t617MZj1A0A=="))
        .mapNotNull { item ->
            val link = item.selectFirst(_q9("UdJlHw1t617MYXRY1cBwr0/ouaVb6A==")) ?: return@mapNotNull null
            val href = link.attr(_q9("F8VwEA==")).trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val numbers = Regex(_q9("V+txXVde/B2OSW4Gkfk05T0="))
                .find(item.selectFirst(_q9("UdlgGxtw7lnHeg=="))?.text().orEmpty())
            val season = numbers?.groupValues?.getOrNull(1)?.toIntOrNull()
            val episodeNumber = numbers?.groupValues?.getOrNull(2)?.toIntOrNull()
            val episodeTitle = link.text().trim().takeIf(String::isNotBlank)
            val poster = item.selectFirst(_q9("Ud54Fxln4RfKeHoAmcw9qQ=="))?.attr(_q9("DMV2"))
                ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(pageUrl, it) }
            val date = item.selectFirst(_q9("UdN0Ahs="))?.text()?.trim()?.takeIf(String::isNotBlank)

            newEpisode(absoluteUrl(pageUrl, href)) {
                name = episodeTitle
                this.season = season
                episode = episodeNumber
                posterUrl = poster
                _b1(date)?.let { addDate(it) }
            }
        }

    private fun _a7(document: Document, pageUrl: String): List<Pair<Actor, String?>> =
        document.select(_q9("XNR0BQoioUfGZ25D19Zw4GTlubNS202ALsiEaSGSYpIe1GEZDF8="))
            .mapNotNull { item ->
                val actorName = item.selectFirst(_q9("EtJhFyVr+1LOZW9DyZg+r3nllg=="))?.attr(_q9("HNh7Ahts+w=="))?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: item.selectFirst(_q9("UdN0Ah8ioVnCeHg="))?.text()?.trim()?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val image = item.selectFirst(_q9("Ud54EV5r4lD4Zm9P5A=="))?.attr(_q9("DMV2"))
                    ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(pageUrl, it) }
                val role = item.selectFirst(_q9("UdN0Ah8ioVTCZ3xPzcAi"))?.text()?.trim()?.takeIf(String::isNotBlank)
                Actor(actorName, image) to role
            }

    private fun _a8(document: Document, pageUrl: String): List<String> = document
        .select(_q9("XMNnFxdu6kWDfHte2Mg1lWfyqJ0RlTidKMyAdTaPMsYZxXQbG1n8RcBI"))
        .mapNotNull { it.attr(_q9("DMV2")).trim().takeIf(String::isNotBlank) }
        .map { absoluteUrl(pageUrl, it) }
        .distinct()

    private fun _a9(document: Document): List<String> = document
        .select(_q9("XMR8GBlu6hfCTnVe3MN68zOvv6FamjG0"))
        .filterNot { link -> link.parents().any { parent -> parent.`is`(_q9("F9J0EhtwoxfNdGsAmcM/oWDluQ==")) } }
        .map { it.text().trim() }
        .filter(String::isNotBlank)
        .distinct()

    private fun _b0(document: Document, label: String): String? = document
        .select(_q9("UdRgBQpt4mjFfHhA3dY="))
        .firstOrNull {
            it.selectFirst(_q9("UcF0BBdj4UPG"))?.text()?.trim()?.equals(label, ignoreCase = true) == true
        }
        ?.selectFirst(_q9("UcF0GhFw"))
        ?.text()
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun firstNumber(value: String): String? =
        Regex(_q9("I9M+XkE41BmPSEFIkoxv")).find(value)?.value?.replace(',', '.')

    private fun extractYear(value: String?): Int? =
        Regex(_q9("V4gvR0d+vQeKSXlXi9g=")).find(value.orEmpty())?.value?.toIntOrNull()

    private fun parseMinutes(value: String?): Int? = Regex(_q9("V+txXVde/B3ufHM="), RegexOption.IGNORE_CASE)
        .find(value.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun _b1(value: String?): String? {
        val match = Regex(_q9("V+xUWyRjok3+bi5RkPl+kmer45xZzifFaNDANQ+OOYcj025CAys="))
            .find(value.orEmpty()) ?: return null
        val month = when (match.groupValues[1].lowercase(Locale.ROOT)) {
            _q9("FdZ7") -> 1
            _q9("GdJ3") -> 2
            _q9("EtZn") -> 3
            _q9("Hsdn") -> 4
            _q9("EtZs") -> 5
            _q9("FcJ7") -> 6
            _q9("FcJ5") -> 7
            _q9("HsJy") -> 8
            _q9("DNJl") -> 9
            _q9("ENRh") -> 10
            _q9("Edhj") -> 11
            _q9("G9J2") -> 12
            else -> return null
        }
        val day = match.groupValues[2].toIntOrNull() ?: return null
        val year = match.groupValues[3].toIntOrNull() ?: return null
        return _q9("WochElMnvwXHODgci8E=").format(Locale.ROOT, year, month, day)
    }

    private suspend fun ensureMainUrl() {
        if (_a1) return
        _a0.withLock {
            if (_a1) return@withLock

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
                _a1 = true
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
        val value = url?.trim()?.removeSuffix("/")?.takeIf(String::isNotBlank) ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            if ((scheme == _q9("F8NhBg==") || scheme == _q9("F8NhBg0=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else null
        }.getOrNull()
    }

    private fun isProviderUrl(url: String): Boolean {
        val base = normalizeHttpBaseUrl(url) ?: return false
        return base == normalizeHttpBaseUrl(mainUrl) || base == DEFAULT_MAIN_URL
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
            throw ErrorLoadingException(_q9("NNh7Ahtsr1PKd3FD0swi7nvsrqgd3nmHPMSObCGcYcZfx2cZCGvrUtE="))
        }
    }

    private fun normalizeTaxonomyName(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf(String::isNotBlank)
        ?.lowercase(Locale.ROOT)

    companion object {
        private const val DEFAULT_MAIN_URL = "https://unairi.ac.id"
        private const val REMOTE_CONFIG_KEY = "MidasXXI"
        private const val MAIN_URL_JSON =
            "https://raw.githubusercontent.com/mj1Per127/agoosecloudstream/main/Website.json"

        private val BLOCKED_CATEGORIES = emptySet<String>()
        private val BLOCKED_TAGS = setOf(_q9("Cd5jFxNj9w=="))
        private val WHITESPACE = Regex(_q9("I8Q+"))
    }
}
