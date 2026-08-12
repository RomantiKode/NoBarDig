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
    override var name = _q9("K7a1NqYMJCHY")
    override var lang = "id"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "/" to _q9("J5yFHppi"),
        "/" to _q9("LpCDBZp+"),
        "/" to _q9("Io2QGpQ="),
        "/" to _q9("J5GYGpA="),
        "/" to _q9("MJaHFphtJA=="),
        "/" to _q9("Io2QGpQMNzbD884="),
        "/" to _q9("IJadGvV4OSvT993A"),
        "/" to _q9("MonxBJB+NTzCltvQX6TgNzQ="),
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
        val encoded = URLEncoder.encode(query, _q9("M4uXeu0="))
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
            path.contains(_q9("SaunJL1DCwq+")) -> loadSeries(response.url, response.document)
            path.contains(_q9("SbK+IbxJD1Y=")) -> loadMovie(response.url, response.document)
            else -> throw ErrorLoadingException(_q9("MrahMvVEHRXw2+77LavIAQDxcK2ydzwz8erOe7MFmcoNqr8w"))
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
        val options = document.select(_q9("SLu+OKVAHQDOxuP0dIPTOg7yXJyUORM+9P/EdqcDjss7hLU2oU1RDejG6shWgsARAK9GgJYyFQ=="))
        if (options.isEmpty()) return emptyList()

        val endpoint = absoluteUrl(pageUrl, _q9("SaiherRIERD/me7xYI/PSADoSY3VJyAq"))
        return options.mapNotNull { option ->
            val post = option.attr(_q9("Ar6lNvhcEwrl")).trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val type = option.attr(_q9("Ar6lNvhYBQn0")).trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val nume = option.attr(_q9("Ar6lNvhCCRT0")).trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null

            val response = runCatching {
                app.post(
                    endpoint,
                    referer = pageUrl,
                    data = mapOf(
                        _q9("B7ylPrpC") to _q9("ArC+CKVAHQD0xND0Z4fZ"),
                        _q9("FrCiIw==") to post,
                        _q9("CKq8Mg==") to nume,
                        _q9("EqahMg==") to type,
                    ),
                )
            }.getOrNull() ?: return@mapNotNull null

            _b3(response.text, response.url)
        }.distinct()
    }

    private fun _b3(text: String, responseUrl: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val embed = runCatching { JSONObject(trimmed).optString(_q9("A7KzMrFzCQv9")) }
            .getOrNull()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: trimmed

        val iframe = Jsoup.parse(embed).selectFirst(_q9("D7mjNrhJJwrj1dI="))?.attr(_q9("Fa2y"))
            ?.trim()?.takeIf(String::isNotBlank)
        if (iframe != null) return absoluteUrl(responseUrl, iframe)

        val decoded = embed.replace("\\/", "/").trim()
        return decoded.takeIf { it.startsWith(_q9("DqulJ+8DUw==")) || it.startsWith(_q9("DqulJ6YWU1Y=")) }
            ?.let { absoluteUrl(responseUrl, it) }
    }

    private fun _b4(document: Document, pageUrl: String): List<String> = document
        .select(_q9("Rbu+OKVAHQDOxuP0dIPTOhPnW4WUOTs/teLDKbYBmOQVrbIK+QxSHf7Z//lsn/4VDeNRkIl3ITzn6sg+jB+P3Ds="))
        .mapNotNull { it.attr(_q9("Fa2y")).trim().takeIf(String::isNotBlank) }
        .map { absoluteUrl(pageUrl, it) }
        .filter { it.startsWith(_q9("DqulJ+8DUw==")) || it.startsWith(_q9("DqulJ6YWU1Y=")) }
        .distinct()

    private suspend fun loadMovie(url: String, document: Document): LoadResponse {
        val title = document.selectFirst(_q9("SKy5MrRIGQuxmOv0eYeBDVA="))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw ErrorLoadingException(_q9("LKq1IrkMERbn3+q1eY/FBAqiTJyPMiUv/urL"))
        val websitePoster = document.selectFirst(_q9("SKy5MrRIGQuxmP/6fpLEF0HrRZKgJDo5yA=="))?.attr(_q9("Fa2y"))
            ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(url, it) }
        val websiteYear = extractYear(document.selectFirst(_q9("SKy5MrRIGQuxmOrteZTARU/mSYGe"))?.text())
        val websitePlot = document.selectFirst(_q9("Rba/MboMUg7hm+z6Y5LECxU="))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteGenres = document.select(_q9("SKy5MrRIGQuxmPzyaIjEFw7xCJSgPzo/86GYfPgLmNEUuv5wiA=="))
            .map { it.text().trim() }.filter(String::isNotBlank)
        val websiteTags = _a9(document)
        val websiteRuntime = parseMinutes(document.selectFirst(_q9("SKy5MrRIGQuxmP3gY5LICAQ="))?.text())
        val websiteRating = document.selectFirst(_q9("SKy5MrRIGQux7ebhaIvRFw7yFZaUOTw/+//3OqMFk9g7"))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteActors = _a7(document, url)
        val websiteScore = _b0(document, _q9("MpKVNfV+HQ342Og="))?.let(::firstNumber)
        val originalTitle = _b0(document, _q9("Ka24MLxCHRWxwubhYYM="))
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
        val title = document.selectFirst(_q9("SKy5MrRIGQuxmOv0eYeBDVA="))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
            ?: throw ErrorLoadingException(_q9("LKq1IrkMDxzj3+75LZLIAQDpCJGSIy034ODENQ=="))
        val websitePoster = document.selectFirst(_q9("SKy5MrRIGQuxmP/6fpLEF0HrRZKgJDo5yA=="))?.attr(_q9("Fa2y"))
            ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(url, it) }
        val websiteYear = extractYear(document.selectFirst(_q9("SKy5MrRIGQuxmOrteZTARU/mSYGe"))?.text())
        val websitePlot = document.selectFirst(_q9("Rba/MboMUg7hm+z6Y5LECxU="))?.text()?.trim()
            ?.takeIf(String::isNotBlank)
        val websiteGenres = document.select(_q9("SKy5MrRIGQuxmPzyaIjEFw7xCJSgPzo/86GYfPgLmNEUuv5wiA=="))
            .map { it.text().trim() }.filter(String::isNotBlank)
        val websiteTags = _a9(document)
        val websiteActors = _a7(document, url)
        val websiteScore = _b0(document, _q9("MpKVNfV+HQ342Og="))?.let(::firstNumber)
        val originalTitle = _b0(document, _q9("Ka24MLxCHRWxwubhYYM="))
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
        val header = document.select(_q9("DrqwM7Be"))
            .firstOrNull { it.selectFirst("h2")?.text()?.trim()?.equals(heading, ignoreCase = true) == true }
            ?: return emptyList()

        var sibling = header.nextElementSibling()
        while (sibling != null && !sibling.`is`(_q9("DrqwM7Be"))) {
            val items = sibling.select(_q9("B62lPrZAGVf4wur4"))
            if (items.isNotEmpty()) {
                return items.mapNotNull(::_a5).distinctBy { it.url }
            }
            sibling = sibling.nextElementSibling()
        }
        return emptyList()
    }

    private fun parseListing(document: Document): List<SearchResponse> = document
        .select(_q9("B62lPrZAGVf4wur4"))
        .mapNotNull(::_a5)
        .distinctBy { it.url }

    private fun _a5(element: Element): SearchResponse? {
        val link = element.selectFirst(_q9("SLuwI7QMFEqx19T9f4PHOE2iQMbbNhMy5+7DBvtM088JrKUypwwdIvnE6vNQ"))
            ?: return null
        val href = link.attr(_q9("Dq20MQ==")).trim().takeIf(String::isNotBlank) ?: return null
        val absolute = absoluteUrl(mainUrl, href)
        val path = runCatching { URI(absolute).path }.getOrNull().orEmpty()
        val type = when {
            path.contains(_q9("SbK+IbxJD1Y=")) -> TvType.Movie
            path.contains(_q9("SaunJL1DCwq+")) -> TvType.TvSeries
            else -> return null
        }
        val title = element.selectFirst(_q9("SLuwI7QMFEqx16O1I4LAEQCiQMbXdyBpteqJe79f"))?.text()?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val poster = element.selectFirst(_q9("SK++JKFJDln42+i5LY/MAg=="))?.let { image ->
            image.attr(_q9("Ar6lNvhfDho=")).takeIf(String::isNotBlank)
                ?: image.attr(_q9("Fa2y")).takeIf(String::isNotBlank)
        }?.let { absoluteUrl(mainUrl, it) }
        val year = extractYear(element.selectFirst(_q9("SLuwI7QMDwnw2A=="))?.text())

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
        .select(_q9("SLqhPqZDGBD+xa/5ZA=="))
        .mapNotNull { item ->
            val link = item.selectFirst(_q9("SLqhPqZDGBD+wubhYYOBBDrqWpCdCg==")) ?: return@mapNotNull null
            val href = link.attr(_q9("Dq20MQ==")).trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val numbers = Regex(_q9("ToO1fPxwD1O86vy/JbrFTkg="))
                .find(item.selectFirst(_q9("SLGkOrBeHRf12Q=="))?.text().orEmpty())
            val season = numbers?.groupValues?.getOrNull(1)?.toIntOrNull()
            val episodeNumber = numbers?.groupValues?.getOrNull(2)?.toIntOrNull()
            val episodeTitle = link.text().trim().takeIf(String::isNotBlank)
            val poster = item.selectFirst(_q9("SLa8NrJJEln42+i5LY/MAg=="))?.attr(_q9("Fa2y"))
                ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(pageUrl, it) }
            val date = item.selectFirst(_q9("SLuwI7A="))?.text()?.trim()?.takeIf(String::isNotBlank)

            newEpisode(absoluteUrl(pageUrl, href)) {
                name = episodeTitle
                this.season = season
                episode = episodeNumber
                posterUrl = poster
                _b1(date)?.let { addDate(it) }
            }
        }

    private fun _a7(document: Document, pageUrl: String): List<Pair<Actor, String?>> =
        document.select(_q9("RbywJKEMUgn0xPz6Y5WBSxHnWoaUORMz4e7IK6UDjYIHvKU4p3E="))
            .mapNotNull { item ->
                val actorName = item.selectFirst(_q9("C7qlNo5FCBz8xv36fdvPBAzndQ=="))?.attr(_q9("BbC/I7BCCA=="))?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: item.selectFirst(_q9("SLuwI7QMUhfw2+o="))?.text()?.trim()?.takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val image = item.selectFirst(_q9("SLa8MPVFER7Kxf32UA=="))?.attr(_q9("Fa2y"))
                    ?.trim()?.takeIf(String::isNotBlank)?.let { absoluteUrl(pageUrl, it) }
                val role = item.selectFirst(_q9("SLuwI7QMUhrwxO72eYPT"))?.text()?.trim()?.takeIf(String::isNotBlank)
                Actor(actorName, image) to role
            }

    private fun _a8(document: Document, pageUrl: String): List<String> = document
        .select(_q9("RaujNrxAGQux3+nnbIvEPhLwS6jXd2Yu5+rMN7Ie3dYArbA6sHcPC/Lr"))
        .mapNotNull { it.attr(_q9("Fa2y")).trim().takeIf(String::isNotBlank) }
        .map { absoluteUrl(pageUrl, it) }
        .distinct()

    private fun _a9(document: Document): List<String> = document
        .select(_q9("Ray4ObJAGVnw7efnaICLWEatXJSceG8H"))
        .filterNot { link -> link.parents().any { parent -> parent.`is`(_q9("DrqwM7BeUFn/1/m5LYDOChXnWg==")) } }
        .map { it.text().trim() }
        .filter(String::isNotBlank)
        .distinct()

    private fun _b0(document: Document, label: String): String? = document
        .select(_q9("SLykJKFDESb33+r5aZU="))
        .firstOrNull {
            it.selectFirst(_q9("SKmwJbxNEg30"))?.text()?.trim()?.equals(label, ignoreCase = true) == true
        }
        ?.selectFirst(_q9("SKmwO7pe"))
        ?.text()
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun firstNumber(value: String): String? =
        Regex(_q9("Orv6f+oWJ1e969PxJs+e")).find(value)?.value?.replace(',', '.')

    private fun extractYear(value: String?): Int? =
        Regex(_q9("TuDrZuxQTkm46uvuP5s=")).find(value.orEmpty())?.value?.toIntOrNull()

    private fun parseMinutes(value: String?): Int? = Regex(_q9("ToO1fPxwD1Pc3+E="), RegexOption.IGNORE_CASE)
        .find(value.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun _b1(value: String?): String? {
        val match = Regex(_q9("ToSQeo9NUQPMzbzoJLqPORKpAKmfLHl2p/aMd4sf1pc6u6pjqAU="))
            .find(value.orEmpty()) ?: return null
        val month = when (match.groupValues[1].lowercase(Locale.ROOT)) {
            _q9("DL6/") -> 1
            _q9("ALqz") -> 2
            _q9("C76j") -> 3
            _q9("B6+j") -> 4
            _q9("C76o") -> 5
            _q9("DKq/") -> 6
            _q9("DKq9") -> 7
            _q9("B6q2") -> 8
            _q9("Fbqh") -> 9
            _q9("Cbyl") -> 10
            _q9("CLCn") -> 11
            _q9("Arqy") -> 12
            else -> return null
        }
        val day = match.groupValues[2].toIntOrNull() ?: return null
        val year = match.groupValues[3].toIntOrNull() ?: return null
        return _q9("Q+/lM/gJTEv1m6qlP4I=").format(Locale.ROOT, year, month, day)
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
            if ((scheme == _q9("DqulJw==") || scheme == _q9("DqulJ6Y=")) && !uri.host.isNullOrBlank()) {
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
            throw ErrorLoadingException(_q9("LbC/I7BCXB341OP6Zo/TRQ7uTZ3bPCc08+LCLqUNjtZGr6M4o0UYHOM="))
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
        private val BLOCKED_TAGS = setOf(_q9("ELanNrhNBA=="))
        private val WHITESPACE = Regex(_q9("Oqz6"))
    }
}
