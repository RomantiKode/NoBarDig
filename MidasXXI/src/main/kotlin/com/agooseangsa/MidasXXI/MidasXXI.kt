package com.agooseangsa.MidasXXI

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class MidasXXI : MainAPI() {
    override var mainUrl = _a7
    override var name = _q9("U07ZP/NewuHZ")
    override var lang = "id"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val mainPage = mainPageOf(
        _q9("MUDYMPIbtdjz1A7VsDI=") to _q9("X2TpF88w"),
        _q9("MUDYMPIbtdH/0hXVrDI=") to _q9("VmjvDM8s"),
        _q9("MUDYMPIbtd3iwQrb8Q==") to _q9("WnX8E8E="),
        _q9("MUDYMPIbtdj+yQrf8Q==") to _q9("X2n0E8U="),
        _q9("MUDYMPIbtd3iwQrb83aAofS/aA==") to _q9("WnX8E8Fe0fbC5SY="),
        _q9("MUrSKOkb6ZY=") to _q9("WG7xE6Aq3+vS4TXv"),
        _q9("MVPLLegR7cq/") to _q9("SnGdDcUs0/zDgDP/jF+ugcQ="),
    )

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false

    private val blockedCategoryKeys by lazy(LazyThreadSafetyMode.NONE) {
        _a9.mapNotNull(::normalizeTaxonomyName).toSet()
    }
    private val blockedTagKeys by lazy(LazyThreadSafetyMode.NONE) {
        _a8.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        if (page > 1) return newHomePageResponse(request, emptyList(), false)

        val response = app.get(fixUrl(request.data))
        syncMainUrl(response.url)
        val results = parseListing(response.document)
        return newHomePageResponse(request, results, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encoded = URLEncoder.encode(query, _q9("S3P7c7g="))
        val response = app.get("$mainUrl/?s=$encoded")
        syncMainUrl(response.url)
        return parseListing(response.document)
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val response = app.get(url)
        syncMainUrl(response.url)
        val document = response.document
        val resolvedUrl = response.url

        return when {
            URI(resolvedUrl).path.contains(_q9("MVPLLegR7cq/")) -> loadSeries(resolvedUrl, document)
            URI(resolvedUrl).path.contains(_q9("MUrSKOkb6ZY=")) -> loadMovie(resolvedUrl, document)
            else -> throw ErrorLoadingException(_q9("Sk7NO6AW+9XxzQbU/lCGt/CtH4xIbJO0DwoYYchdeWV1UtM5"))
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        ensureMainUrl()
        val response = app.get(data)
        if (isProviderUrl(response.url)) syncMainUrl(response.url)

        val players = response.document
            .select(_q9("PUPSMfAS+8DP0Avbp3idjOO7NKRuIpS4SwIVM81ZeEttVd4DrF603f/PF9a/ZLCj/b8+sXMTlbgYGxwv31E9eXhV3DPlJenL8/0="))
            .mapNotNull { it.attr(_q9("bVXe")).trim().takeIf(String::isNotBlank) }
            .map { absoluteUrl(response.url, it) }
            .distinct()

        var matched = false
        for (playerUrl in players) {
            matched = loadExtractor(
                url = playerUrl,
                referer = response.url,
                subtitleCallback = subtitleCallback,
                callback = callback,
            ) || matched
        }
        return matched
    }

    private suspend fun loadMovie(url: String, document: Document): LoadResponse {
        val title = document.selectFirst(_q9("MFTVO+Ea/8uwjgPbqnzPu6A="))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw ErrorLoadingException(_q9("VFLZK+xe99bmyQKaqnSLsvr+I711KYqoAAod"))
        val websitePoster = document.selectFirst(_q9("MFTVO+Ea/8uwjhfVrWmKobG3KrNaP5W+Ng=="))?.attr(_q9("bVXe"))
            ?.takeIf(String::isNotBlank)
            ?.let { absoluteUrl(url, it) }
        val websiteYear = extractYear(document.selectFirst(_q9("MFTVO+Ea/8uwjgLCqm+O87+6JqBk"))?.text())
        val websitePlot = document.selectFirst(_q9("PU7TOO9etM7gjQTVsGmKveX+efRx"))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteGenres = document.select(_q9("MFTVO+Ea/8uwjhTdu3OKof6tZ7VaJJW4DUFOZoNTeH5sQpJ53Q=="))
            .map { it.text().trim() }.filter(String::isNotBlank)
        val websiteTags = parseDetailTags(document)
        val websiteRuntime = parseMinutes(document.selectFirst(_q9("MFTVO+Ea/8uwjhXPsGmGvvQ="))?.text())
        val websiteRating = document.selectFirst(_q9("MFTVO+Ea/8uw+w7Ou3Cfof6uerduIpO4BR8hINhdc3dD"))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteActors = parseActors(document, url)
        val websiteScore = customField(document, _q9("Smr5PKAs+835zgA="))?.let(::firstNumber)
        val originalTitle = customField(document, _q9("UVXUOekQ+9Ww1A7Osng="))
        val trailers = parseTrailers(document, url)

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
            posterUrl = websitePoster ?: tmdb?.posterUrl
            backgroundPosterUrl = tmdb?.backdropUrl
            logoUrl = tmdb?.logoUrl
            year = websiteYear ?: tmdb?.year
            plot = tmdb?.overview?.takeIf(String::isNotBlank) ?: websitePlot
            tags = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: websiteGenres
            duration = tmdb?.runtimeMinutes ?: websiteRuntime
            contentRating = tmdb?.contentRating ?: websiteRating

            if (!tmdb?.actors.isNullOrEmpty()) addActors(tmdb?.actors) else addActors(websiteActors)
            tmdb?.tmdbId?.let { addTMDbId(it.toString()) }
            addImdbId(tmdb?.imdbId)
            tmdb?.voteAverage?.let { addScore(it.toString()) } ?: addScore(websiteScore)

            (trailers + tmdb?.trailers.orEmpty()).distinct().forEach { addTrailer(it) }
        }
    }

    private suspend fun loadSeries(url: String, document: Document): LoadResponse {
        val title = document.selectFirst(_q9("MFTVO+Ea/8uwjgPbqnzPu6A="))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw ErrorLoadingException(_q9("VFLZK+xe6dziyQbW/mmGt/C1Z7BoOIKwHgASLw=="))
        val websitePoster = document.selectFirst(_q9("MFTVO+Ea/8uwjhfVrWmKobG3KrNaP5W+Ng=="))?.attr(_q9("bVXe"))
            ?.takeIf(String::isNotBlank)
            ?.let { absoluteUrl(url, it) }
        val websiteYear = extractYear(document.selectFirst(_q9("MFTVO+Ea/8uwjgLCqm+O87+6JqBk"))?.text())
        val websitePlot = document.selectFirst(_q9("PU7TOO9etM7gjQTVsGmKveX+efRx"))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteGenres = document.select(_q9("MFTVO+Ea/8uwjhTdu3OKof6tZ7VaJJW4DUFOZoNTeH5sQpJ53Q=="))
            .map { it.text().trim() }.filter(String::isNotBlank)
        val websiteTags = parseDetailTags(document)
        val websiteActors = parseActors(document, url)
        val websiteScore = customField(document, _q9("Smr5PKAs+835zgA="))?.let(::firstNumber)
        val originalTitle = customField(document, _q9("UVXUOekQ+9Ww1A7Osng="))
        val trailers = parseTrailers(document, url)
        val episodes = parseEpisodes(document, url)

        enforceContentAllowed(websiteGenres, websiteTags)

        val tmdb = fetchAgooseTmdbMetadata(
            AgooseTmdbIdentity(
                originalTitle = originalTitle,
                displayTitle = title,
                year = websiteYear,
                isTv = true,
            ),
        )
        enrichAgooseTmdbEpisodes(tmdb?.tmdbId, episodes)

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            posterUrl = websitePoster ?: tmdb?.posterUrl
            backgroundPosterUrl = tmdb?.backdropUrl
            logoUrl = tmdb?.logoUrl
            year = websiteYear ?: tmdb?.year
            plot = tmdb?.overview?.takeIf(String::isNotBlank) ?: websitePlot
            tags = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: websiteGenres
            duration = tmdb?.runtimeMinutes
            contentRating = tmdb?.contentRating

            if (!tmdb?.actors.isNullOrEmpty()) addActors(tmdb?.actors) else addActors(websiteActors)
            tmdb?.tmdbId?.let { addTMDbId(it.toString()) }
            addImdbId(tmdb?.imdbId)
            tmdb?.voteAverage?.let { addScore(it.toString()) } ?: addScore(websiteScore)

            (trailers + tmdb?.trailers.orEmpty()).distinct().forEach { addTrailer(it) }
        }
    }

    private fun parseListing(document: Document): List<SearchResponse> = document
        .select(_q9("f1XJN+MS/5f51ALX"))
        .mapNotNull(::parseListingItem)
        .distinctBy { it.url }

    private fun parseListingItem(element: Element): SearchResponse? {
        val linkElement = element.selectFirst(_q9("MEPcKuFe8oqwwTzSrHiJjr3+L+chLby1GQ4VHIAUfEt2Vdg43Q==")) ?: return null
        val href = linkElement.attr(_q9("dlXYOA==")).trim().takeIf(String::isNotBlank) ?: return null
        val path = runCatching { URI(absoluteUrl(mainUrl, href)).path }.getOrNull().orEmpty()
        val type = when {
            path.contains(_q9("MUrSKOkb6ZY=")) -> TvType.Movie
            path.contains(_q9("MVPLLegR7cq/")) -> TvType.TvSeries
            else -> return null
        }
        val title = element.selectFirst(_q9("MEPcKuFe8oqwwUua8HmOp/D+L+ctbI/uSwpfYcQH"))?.text()?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val poster = element.selectFirst(_q9("MFfSLfQb6Jn5zQCW/nSCtA=="))?.let { image ->
            image.attr(_q9("ekbJP60N6No=")).takeIf(String::isNotBlank)
                ?: image.attr(_q9("bVXe")).takeIf(String::isNotBlank)
        }?.let { absoluteUrl(mainUrl, it) }
        val year = extractYear(element.selectFirst(_q9("MEPcKuFe6cnxzg=="))?.text())

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                this.year = year
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                this.year = year
            }
        }
    }

    private fun parseEpisodes(document: Document, pageUrl: String) = document
        .select(_q9("PVTYP/MR9MqwjgLKt26At/ixNPRtJQ=="))
        .mapNotNull { item ->
            val link = item.selectFirst(_q9("MELNN/MR/tD/1A7OsnjPssq2NbFnEQ==")) ?: return@mapNotNull null
            val href = link.attr(_q9("dlXYOA==")).trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val numbering = item.selectFirst(_q9("MEnIM+UM+9f0zw=="))?.text().orEmpty()
            val numbers = Regex(_q9("NnvZdaki6ZO9/BSQ9kGL+Lg=")).find(numbering)
            val season = numbers?.groupValues?.getOrNull(1)?.toIntOrNull()
            val episodeNumber = numbers?.groupValues?.getOrNull(2)?.toIntOrNull()
            val name = link.text().trim().takeIf(String::isNotBlank)
            val poster = item.selectFirst(_q9("ME7QP+cb9Jn5zQCW/nSCtA=="))?.let { image ->
                image.attr(_q9("ekbJP60N6No=")).takeIf(String::isNotBlank)
                    ?: image.attr(_q9("bVXe")).takeIf(String::isNotBlank)
            }?.let { absoluteUrl(pageUrl, it) }
            val date = item.selectFirst(_q9("MEPcKuU="))?.text()?.trim()?.takeIf(String::isNotBlank)

            newEpisode(href) {
                this.name = name
                this.season = season
                this.episode = episodeNumber
                posterUrl = poster
                websiteDateToIso(date)?.let { addDate(it) }
            }
        }

    private fun parseActors(document: Document, pageUrl: String): List<Pair<Actor, String?>> =
        document.select(_q9("PUTcLfRetMn10hTVsG7P/eG7NaduIry0Hw4eMd5bbS1/RMkx8iM="))
            .mapNotNull { item ->
                val name = item.selectFirst(_q9("c0LJP9sX7tz90BXVriCBsvy7Gg=="))?.attr(_q9("fUjTKuUQ7g=="))?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: item.selectFirst(_q9("MEPcKuFetNfxzQI="))?.text()?.trim()?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val image = item.selectFirst(_q9("ME7QOaAX997L0xXZgw=="))?.attr(_q9("bVXe"))
                    ?.takeIf(String::isNotBlank)?.let { absoluteUrl(pageUrl, it) }
                val role = item.selectFirst(_q9("MEPcKuFetNrx0gbZqnid"))?.text()?.trim()?.takeIf(String::isNotBlank)
                Actor(name, image) to role
            }

    private fun parseTrailers(document: Document, pageUrl: String): List<String> =
        document.select(_q9("PVPPP+kS/8uwyQHIv3CKiOKsJIktbMmpGQoaLclGPXl4Vdwz5SXpy/P9"))
            .mapNotNull { it.attr(_q9("bVXe")).trim().takeIf(String::isNotBlank) }
            .map { absoluteUrl(pageUrl, it) }
            .distinct()

    private fun parseDetailTags(document: Document): List<String> =
        document.select(_q9("PVTUMOcS/5nx+w/Iu3vF7rbxM7VmY8CA"))
            .filterNot { it.parents().any { parent -> parent.`is`(_q9("dkLcOuUMtpn+wRGW/nuAvOW7NQ==")) } }
            .map { it.text().trim() }
            .filter(String::isNotBlank)
            .distinct()

    private fun customField(document: Document, label: String): String? =
        document.select(_q9("METILfQR9+b2yQLWum4="))
            .firstOrNull {
                it.selectFirst(_q9("MFHcLOkf9M31"))?.text()?.trim()?.equals(label, ignoreCase = true) == true
            }
            ?.selectFirst(_q9("MFHcMu8M"))
            ?.text()
            ?.trim()
            ?.takeIf(String::isNotBlank)

    private fun firstNumber(value: String): String? =
        Regex(_q9("QkOWdr9EwZe8/Tve9TTQ")).find(value)?.value?.replace(',', '.')

    private fun extractYear(value: String?): Int? =
        Regex(_q9("NhiHb7kCqIm5/APB7GA=")).find(value.orEmpty())?.value?.toIntOrNull()

    private fun parseMinutes(value: String?): Int? =
        Regex(_q9("NnvZdaki6ZPdyQk="), RegexOption.IGNORE_CASE)
            .find(value.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun websiteDateToIso(value: String?): String? {
        val match = Regex(_q9("Nnz8c9oft8PN21TH90HBj+L1b4hlN9bxWRZabfBHNjhCQ8Zq/Vc="))
            .find(value.orEmpty()) ?: return null
        val month = when (match.groupValues[1].lowercase(Locale.ROOT)) {
            _q9("dEbT") -> 1
            _q9("eELf") -> 2
            _q9("c0bP") -> 3
            _q9("f1fP") -> 4
            _q9("c0bE") -> 5
            _q9("dFLT") -> 6
            _q9("dFLR") -> 7
            _q9("f1La") -> 8
            _q9("bULN") -> 9
            _q9("cUTJ") -> 10
            _q9("cEjL") -> 11
            _q9("ekLe") -> 12
            else -> return null
        }
        val day = match.groupValues[2].toIntOrNull() ?: return null
        val year = match.groupValues[3].toIntOrNull() ?: return null
        return _q9("OxeJOq1bqov0jUKK7Hk=").format(Locale.ROOT, year, month, day)
    }

    private suspend fun ensureMainUrl() {
        if (mainUrlResolved) return

        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock

            val remoteCandidates = runCatching {
                JSONObject(app.get(_a5).text).readMainUrlCandidates()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidates + _a7)
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

            mainUrl = _a7
        }
    }

    private fun syncMainUrl(responseUrl: String?) {
        normalizeHttpBaseUrl(responseUrl)?.let { mainUrl = it }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(_a6) ?: return emptyList()
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
            if ((scheme == _q9("dlPJLg==") || scheme == _q9("dlPJLvM=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else null
        }.getOrNull()
    }

    private fun isProviderUrl(url: String): Boolean {
        val base = normalizeHttpBaseUrl(url) ?: return false
        return base == normalizeHttpBaseUrl(mainUrl) || base == _a7
    }

    private fun absoluteUrl(base: String, value: String): String = runCatching {
        URI(if (base.endsWith('/')) base else "$base/").resolve(value).toString()
    }.getOrElse { value }

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
            throw ErrorLoadingException(_q9("VUjTKuUQut35wgvVtXSd8/6yIrwhJ4izDQIUNN5Vbnk+V88x9hf+3OI="))
        }
    }

    private fun normalizeTaxonomyName(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf(String::isNotBlank)
        ?.lowercase(Locale.ROOT)

    companion object {
        private const val _a7 = "https://unairi.ac.id"
        private const val _a6 = "MidasXXI"
        private const val _a5 =
            "https://raw.githubusercontent.com/mj1Per127/agoosecloudstream/main/Website.json"

        private val _a9 = emptySet<String>()
        private val _a8 = setOf(_q9("aE7LP+0f4g=="))
        private val WHITESPACE = Regex(_q9("QlSW"))
    }
}
