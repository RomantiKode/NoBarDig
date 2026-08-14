package com.agooseangsa.AnimeXin

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.base64Decode
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

class AnimeXin : MainAPI() {
    private val profile = AgooseProviderProfile.current

    override var mainUrl = profile.defaultMainUrl
    override var name = _q9("UsRqz26TKnIs")
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.Anime,
    )

    override val hasMainPage = true
    override val mainPage = profile.homepage.map { item ->
        MainPageData(item.title, item.key)
    }

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false

    private val blockedCategoryKeys by lazy(LazyThreadSafetyMode.NONE) {
        profile.blockedCategories().mapNotNull(::_b2).toSet()
    }
    private val blockedTagKeys by lazy(LazyThreadSafetyMode.NONE) {
        profile.blockedTags().mapNotNull(::_b2).toSet()
    }

    protected suspend fun ensureMainUrl() {
        if (mainUrlResolved) return
        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock

            val remoteCandidates = runCatching {
                JSONObject(app.get(profile.websiteJsonUrl).text).readMainUrlCandidates()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidates + profile.defaultMainUrl)
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
            mainUrl = profile.defaultMainUrl
        }
    }

    protected fun syncMainUrl(responseUrl: String?) {
        normalizeHttpBaseUrl(responseUrl)?.let { mainUrl = it }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(profile.websiteKey) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index) }
            .mapNotNull(::normalizeHttpBaseUrl)
            .distinct()
    }

    protected fun normalizeHttpBaseUrl(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            if ((scheme == _q9("e9530g==") || scheme == _q9("e9530ng=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else null
        }.getOrNull()
    }

    protected fun shouldBlockContent(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ): Boolean {
        if (categories.asSequence().mapNotNull(::_b2).any { it in blockedCategoryKeys }) {
            return true
        }
        return tags.asSequence().mapNotNull(::_b2).any { it in blockedTagKeys }
    }

    protected fun enforceContentAllowed(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ) {
        if (shouldBlockContent(categories, tags)) {
            throw ErrorLoadingException(_q9("WMVt1m7dUn8rZjqjhm3nnVbJ2VlurEE3Y+i2vkpzdTUz2nHNfdoWfjA="))
        }
    }

    private fun _b2(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList(), false)
        ensureMainUrl()
        val response = app.get(mainUrl)
        syncMainUrl(response.url)
        val document = response.document

        val home = profile.homepage.firstOrNull { it.key == request.data }
        val items = when (home?.source) {
            _q9("f8t3x3jH"), _q9("Yc9gzWbeF3UmZSKlgmo=") -> _a2(document, home.heading)
                ?.select(home.itemSelector)
                ?.mapNotNull(::_a0)
                ?: emptyList()
            _q9("fsV1y27A") -> _a2(document, home.heading)
                ?.select(home.itemSelector)
                ?.mapNotNull { _a1(it) }
                ?: emptyList()
            else -> emptyList()
        }

        return newHomePageResponse(request.name, items, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()
        ensureMainUrl()

        val encoded = URLEncoder.encode(cleanQuery, _q9("Rv5FjzM="))
        val searchPath = profile.endpoint(_q9("YM9i0GjbIno2bA=="))
        val searchParam = profile.endpoint(_q9("YM9i0GjbInowZTs="))
        val response = app.get("$mainUrl$searchPath?$searchParam=$encoded")
        syncMainUrl(response.url)

        return response.document.select(profile.selector(_q9("YM9i0GjbMXowYCU=")))
            .mapNotNull(::_a0)
            .filter { it.name.contains(cleanQuery, ignoreCase = true) }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val initialResponse = app.get(_b1(url))
        syncMainUrl(initialResponse.url)
        var document = initialResponse.document
        var detailUrl = initialResponse.url

        if (document.selectFirst(profile.selector(_q9("YMNtxWfWO3Ukaw=="))) != null) {
            val currentType = _a3(document)[_q9("R9Nzxw==")].orEmpty()
            if (!currentType.equals(_q9("XsV1y24="), ignoreCase = true)) {
                val allEpisodes = document.selectFirst(profile.selector(_q9("csZv53vaAXQmYSWAhGr+")))
                    ?.attr(_q9("e9hmxA=="))
                    ?.takeIf { it.isNotBlank() }
                if (allEpisodes != null) {
                    val detailResponse = app.get(_b1(allEpisodes), referer = initialResponse.url)
                    syncMainUrl(detailResponse.url)
                    document = detailResponse.document
                    detailUrl = detailResponse.url
                }
            }
        }

        return parseDetail(document, detailUrl)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        ensureMainUrl()
        val episodeUrl = _b1(data)
        val response = app.get(episodeUrl)
        syncMainUrl(response.url)

        var mediaResolved = false
        response.document.select(profile.selector(_q9("fsNx0GTBPWs2bTming=="))).forEach { option ->
            val label = option.text().trim()
            if (!_a9(label)) return@forEach
            val iframeUrl = _b0(option.attr(_q9("Zctv124="))) ?: return@forEach
            runCatching {
                loadExtractor(
                    url = iframeUrl,
                    referer = response.url,
                    subtitleCallback = subtitleCallback,
                    callback = { link ->
                        mediaResolved = true
                        callback(link)
                    },
                )
            }
        }
        return mediaResolved
    }

    private suspend fun parseDetail(document: Document, detailUrl: String): LoadResponse {
        val info = _a3(document)
        val websiteTitle = document.selectFirst(profile.selector(_q9("d893w2LfJnI2aDM=")))
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(profile.selector(_q9("dsR30HLnG28uYQ==")))?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_q9("Wd9n12eTFn42ZT+gzUX71FTA5Fgg51owYeC661x7cjl+32jDZQ=="))
        val originalTitle = document.selectFirst(profile.selector(_q9("fNhqxWLdE3cWbSKgiA==")))?.text()?.trim()?.takeIf { it.isNotBlank() }
        val poster = document.selectFirst(profile.selector(_q9("Y8Vw1m7B")))
            ?.attr(_q9("YNhg"))?.takeIf { it.isNotBlank() }
        val websitePlot = _a4(document)
        val websiteGenres = document.select(profile.selector(_q9("dM9t0G7A"))).map { it.text().trim() }.filter { it.isNotBlank() }
        enforceContentAllowed(categories = websiteGenres)

        val isMovie = info[_q9("R9Nzxw==")].equals(_q9("XsV1y24="), ignoreCase = true)
        val year = YEAR_REGEX.find(info[_q9("Qc9vx2rAF38=")].orEmpty())?.value?.toIntOrNull()
        val tmdb = fetchAgooseTmdbMetadata(
            AgooseTmdbIdentity(
                originalTitle = originalTitle,
                displayTitle = websiteTitle,
                year = year,
                isTv = !isMovie,
            ),
        )
        val title = tmdb?.localizedTitle?.takeIf { it.isNotBlank() } ?: websiteTitle
        val plot = tmdb?.overview?.takeIf { it.isNotBlank() } ?: websitePlot
        val posterUrl = tmdb?.posterUrl ?: poster
        val rating = tmdb?.voteAverage ?: _a7(document)
        val duration = tmdb?.runtimeMinutes ?: _a8(info[_q9("V99xw3/aHXU=")])

        if (isMovie) {
            val directEpisodeUrl = if (document.selectFirst(profile.selector(_q9("fsNx0GTBIHQtcA=="))) != null) {
                detailUrl
            } else {
                document.selectFirst(profile.selector(_q9("fsV1y272AnIxazKpoW371g==")))
                    ?.attr(_q9("e9hmxA=="))
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::_b1)
                    ?: throw ErrorLoadingException(_q9("Vtpq0WTXFzsvayCliCTU01DI2WknqQ4tbOWwoBh2byh2x3bJat0="))
            }

            return newMovieLoadResponse(title, detailUrl, TvType.Movie, directEpisodeUrl) {
                this.posterUrl = posterUrl
                backgroundPosterUrl = tmdb?.backdropUrl
                this.year = tmdb?.year ?: year
                this.plot = plot
                tags = websiteGenres
                this.duration = duration
                tmdb?.actors?.takeIf { it.isNotEmpty() }?.let { actors = it.map { actor -> ActorData(Actor(actor.name, actor.profileUrl), roleString = actor.character) } }
                rating?.takeIf { it > 0.0 }?.let { addScore(it.toString(), 10) }
                tmdb?.trailers?.takeIf { it.isNotEmpty() }?.let { addTrailer(it) }
                addTMDbId(tmdb?.tmdbId?.toString())
                addImdbId(tmdb?.imdbId)
            }
        }

        val episodes = _a5(document)
        if (episodes.isEmpty()) {
            throw ErrorLoadingException(_q9("V8tl1mrBUlouaHaJnW3m0l3AnHAgrkM8Xei/60x7Yj14imfLf9YfbillOA=="))
        }

        return newTvSeriesLoadResponse(title, detailUrl, TvType.Anime, episodes) {
            this.posterUrl = posterUrl
            backgroundPosterUrl = tmdb?.backdropUrl
            this.year = tmdb?.year ?: year
            this.plot = plot
            tags = websiteGenres
            this.duration = duration
            showStatus = when (info[_q9("QN5i1n7A")]?.lowercase(Locale.ROOT)) {
                _q9("fMRkzWLdFQ==") -> ShowStatus.Ongoing
                _q9("cMVu0mfWBn4m") -> ShowStatus.Completed
                else -> null
            }
            tmdb?.actors?.takeIf { it.isNotEmpty() }?.let { actors = it.map { actor -> ActorData(Actor(actor.name, actor.profileUrl), roleString = actor.character) } }
            rating?.takeIf { it > 0.0 }?.let { addScore(it.toString(), 10) }
            tmdb?.trailers?.takeIf { it.isNotEmpty() }?.let { addTrailer(it) }
            addTMDbId(tmdb?.tmdbId?.toString())
            addImdbId(tmdb?.imdbId)
        }
    }

    private fun _a0(element: Element): SearchResponse? {
        val anchor = element.selectFirst(profile.selector(_q9("cMtxxkrdEXMtdg=="))) ?: return null
        val href = anchor.attr(_q9("e9hmxA==")).takeIf { it.isNotBlank() } ?: return null
        val title = element.selectFirst(profile.selector(_q9("cMtxxl/aBncn")))?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: anchor.attr(_q9("Z8N3zm4=")).trim().takeIf { it.isNotBlank() }
            ?: return null
        val poster = element.selectFirst(profile.selector(_q9("cMtxxkLeE3wn")))?.attr(_q9("YNhg"))?.takeIf { it.isNotBlank() }
        val typeBadge = element.selectFirst(profile.selector(_q9("cMtxxl/KAn4AZTKriA==")))?.text()?.trim().orEmpty()
        val type = if (typeBadge.equals(_q9("XsV1y24="), ignoreCase = true)) TvType.Movie else TvType.Anime

        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, _b1(href), TvType.Movie) { posterUrl = poster }
        } else {
            newTvSeriesSearchResponse(title, _b1(href), TvType.Anime) { posterUrl = poster }
        }
    }

    private fun _a1(element: Element): SearchResponse? {
        val anchor = element.selectFirst(profile.selector(_q9("fsV1y27/G2g2RTivhWvn"))) ?: return null
        val href = anchor.attr(_q9("e9hmxA==")).takeIf { it.isNotBlank() } ?: return null
        val title = element.selectFirst(profile.selector(_q9("fsV1y27/G2g2UD+4gWE=")))?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: element.selectFirst(_q9("esdk+X/aBncnWQ=="))?.attr(_q9("Z8N3zm4="))?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = element.selectFirst(profile.selector(_q9("fsV1y27/G2g2TTutimE=")))?.attr(_q9("YNhg"))?.takeIf { it.isNotBlank() }
        return newMovieSearchResponse(title, _b1(href), TvType.Movie) { posterUrl = poster }
    }

    private fun _a2(document: Document, heading: String): Element? = document
        .select(profile.selector(_q9("e8Vux3vSFX4QYTqpjHfw71bKyA==")))
        .firstOrNull { it.selectFirst(profile.selector(_q9("e8Vux3vSFX4KYTeohGry")))?.text()?.trim() == heading }
        ?.nextElementSibling()

    private fun _a3(document: Document): Map<String, String> {
        val root = document.selectFirst(profile.selector(_q9("esRlzVncHW8="))) ?: return emptyMap()
        return root.select(_q9("YNpizA==")).mapNotNull { span ->
            val keyElement = span.selectFirst("b") ?: return@mapNotNull null
            val key = keyElement.text().removeSuffix(":").trim()
            val clone = span.clone()
            clone.select("b").remove()
            val value = clone.text().trim()
            if (key.isBlank() || value.isBlank()) null else key to value
        }.toMap()
    }

    private fun _a4(document: Document): String? {
        val root = document.selectFirst(profile.selector(_q9("Y8Zs1lncHW8="))) ?: return null
        val paragraphs = root.select("p")
        val index = paragraphs.indexOfFirst { it.text().trim().equals(_q9("WsRnzWXWAXIj"), ignoreCase = true) }
        if (index >= 0 && index + 1 < paragraphs.size) {
            return paragraphs[index + 1].text().trim().takeIf { it.isNotBlank() }
        }
        return paragraphs.firstOrNull { it.text().trim().isNotBlank() }?.text()?.trim()
    }

    private fun _a5(document: Document) = document.select(profile.selector(_q9("dtpq0WTXF1I2YTu/"))).mapNotNull { item ->
        val anchor = item.selectFirst(profile.selector(_q9("dtpq0WTXF1craj0="))) ?: return@mapNotNull null
        val href = anchor.attr(_q9("e9hmxA==")).takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val number = item.selectFirst(profile.selector(_q9("dtpq0WTXF1U3aTSpnw==")))?.text()?.trim()?.toIntOrNull()
        val episodeTitle = item.selectFirst(profile.selector(_q9("dtpq0WTXF08rcDqp")))?.text()?.trim()?.takeIf { it.isNotBlank() }
        newEpisode(_b1(href)) {
            name = episodeTitle
            episode = number
        }
    }.sortedBy { it.episode ?: Int.MAX_VALUE }

    private fun _a7(document: Document): Double? = document
        .selectFirst(profile.selector(_q9("Yct3y2XU")))
        ?.text()
        ?.let { RATING_REGEX.find(it)?.groupValues?.getOrNull(1)?.toDoubleOrNull() }

    private fun _a8(value: String?): Int? {
        val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val numbers = NUMBER_REGEX.findAll(clean).mapNotNull { it.value.toIntOrNull() }.toList()
        if (numbers.isEmpty()) return null
        return if (clean.contains(":" ) && numbers.size >= 2) numbers[0] * 60 + numbers[1] else numbers[0]
    }

    private fun _a9(label: String): Boolean =
        label.contains(_q9("WsRnzWXWAXIj"), ignoreCase = true) || label.contains(_q9("UsZvgljGEA=="), ignoreCase = true)

    private fun _b0(value: String): String? {
        val decoded = runCatching { base64Decode(value) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val src = Jsoup.parse(decoded).selectFirst(profile.selector(_q9("fsNx0GTBO30wZTup")))?.attr(_q9("YNhg"))?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        return when {
            src.startsWith("//") -> "https:$src"
            src.startsWith(_q9("e9530jGcXQ==")) || src.startsWith(_q9("e9530niJXTQ=")) -> src
            else -> _b1(src)
        }
    }

    private fun _b1(url: String): String {
        val clean = url.trim()
        return when {
            clean.startsWith(_q9("e9530niJXTQ=")) || clean.startsWith(_q9("e9530jGcXQ==")) -> clean
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> mainUrl + clean
            else -> "$mainUrl/$clean"
        }
    }

    companion object {
        private val WHITESPACE = Regex(_q9("T9ko"))
        private val YEAR_REGEX = Regex(_q9("T8grkzLPQCtrWDK333nJ3w=="))
        private val RATING_REGEX = Regex(_q9("O/EzjzLuWTN9PgritjS4hGSOlQ5n"))
        private val NUMBER_REGEX = Regex(_q9("T84o"))
    }
}
