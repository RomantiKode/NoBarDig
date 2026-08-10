package com.agooseangsa.MidasXXI

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class MidasXXI : MainAPI() {
    override var mainUrl = DEFAULT_MAIN_URL
    override var name = _q9("M8wrpRvaIvU9")
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override var sequentialMainPage = true

    override val mainPage: List<MainPageData> = mainPageOf(
        _q9("XcE76QmZDsQbUw==") to _q9("P+YbjSe0"),
        _q9("XcE76QCVCN8bTw==") to _q9("Nuodlieo"),
        _q9("XcE76QyIG8AV") to _q9("OvcOiSk="),
        _q9("XcIqqhqfJcwaVB7L") to _q9("P+sGiS0="),
        _q9("XcIqqhqfJckGXB7PfW7mOpKA") to _q9("OvcOiSnaMeImeDI="),
        _q9("XcE76QWVDMQRTg==") to _q9("OOwDiUiuP/82fCH7"),
        _q9("XcE76RyMCcUbSgA=") to _q9("KvNvly2oM+gnHSfrAkfIGqI="),
    )

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false
    private val homeMutex = Mutex()
    private var homeCacheAt = 0L
    private var homeCache: Document? = null
    private val tmdbMutex = Mutex()
    private val tmdbCache = mutableMapOf<String, TmdbMetadata?>()

    private val blockedCategoryKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_CATEGORIES.mapNotNull(::normalizeTaxonomyName).toSet()
    }
    private val blockedTagKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_TAGS.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList(), hasNext = false)
        ensureMainUrl()
        val document = _a0()
        val section = _b7(document, request)
        val items = section?.let(::_b5).orEmpty()
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encoded = URLEncoder.encode(query.trim(), _q9("K/EJ6VA="))
        val response = app.get("$mainUrl/?s=$encoded")
        syncMainUrl(response.url)
        val document = response.document

        val primary = document.select(_q9("H9c7rQuWHw==")).mapNotNull(::_a1)
        if (primary.isNotEmpty()) return primary.distinctBy { it.url }

        return document.select(_q9("H/4ntg2cJw==")).mapNotNull { anchor ->
            val href = fixUrlNull(anchor.attr(_q9("Ftcqog=="))) ?: return@mapNotNull null
            val type = typeFromUrl(href) ?: return@mapNotNull null
            if (!CONTENT_URL.matches(href)) return@mapNotNull null
            val container = anchor.closest(_q9("H9c7rQuWHw==")) ?: anchor.parent()
            val img = container?.selectFirst(_q9("F8go"))
            val visible = anchor.text().trim().takeIf { it.isNotBlank() }
            val title = normalizeListTitle(visible, img?.attr(_q9("H8k7")))
                ?: img?.attr(_q9("H8k7"))?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val poster = img?.let { fixUrlNull(it.attr(_q9("GsQ7pUWJCM4=")).ifBlank { it.attr(_q9("Ddcs")) }) }
            val year = container?.text()?.let(::extractYear)
            _a2(title, href, type, poster, year)
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val resolvedUrl = _b4(url)
        val response = app.get(resolvedUrl)
        syncMainUrl(response.url)
        val document = response.document
        val canonicalUrl = response.url
        val type = typeFromUrl(canonicalUrl)
            ?: throw ErrorLoadingException(_q9("Ksw/oUiRFcMAWB2OHWztKYTBXw74J9sybUzfjtI7J1sV0CGj"))

        val title = document.selectFirst(_q9("UNYnoQmeH99UExfPJGSpIMY="))?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_q9("NNArsQTaHsgAXBrCcHHgLJaKJzLYc8o2fEbVwA=="))
        val originalTitle = _b3(document, _q9("MdcmowGUG8FUSRraPGA="))
        val websitePoster = document.selectFirst(_q9("UNYnoQmeH99UEwPBI3HsOteIajE="))
            ?.let { fixUrlNull(it.attr(_q9("Ddcs")).ifBlank { it.attr(_q9("GsQ7pUWJCM4=")) }) }
        val websiteYear = document.selectFirst(_q9("UNYnoQmeH99UExbWJHfoaNmFZiLU"))?.text()?.let(::extractYear)
            ?: _b3(document, if (type == TvType.TvSeries) _q9("OMw9txzaG8QGHRfPJGA=") else _q9("LMAjoQmJH40QXAfL"))?.let(::extractYear)
        val websitePlot = document.selectFirst(_q9("XcwhogfaVNoEEBDBPnHsJoPBdw=="))?.text()?.trim()?.takeIf { it.isNotBlank() }
        val websiteGenres = document.select(_q9("UNYooQafCMIHHRI=")).map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        val websiteTags = _b2(document)
        enforceContentAllowed(websiteGenres, websiteTags)

        val websiteRuntime = document.selectFirst(_q9("UNYnoQmeH99UExbWJHfoaNmTcjjFbsI+"))?.text()?.let(::extractMinutes)
        val websiteRating = document.selectFirst(_q9("UNYnoQmeH99UExbWJHfoaNmTZiLUYw=="))?.text()?.trim()?.takeIf { it.isNotBlank() }
        val websiteScore = _b3(document, _q9("KugLpkioG9kdUxQ="))?.let { SCORE_NUMBER.find(it)?.value }
        val websiteActors = _a4(document)
        val websiteTrailers = _a5(document)
        val websiteRecommendations = _a6(document)

        val directTmdbId = extractDirectTmdbId(document, type)
        val directImdbId = extractDirectImdbId(document)
        val tmdb = _a7(
            type = type,
            directTmdbId = directTmdbId,
            directImdbId = directImdbId,
            originalTitle = originalTitle,
            displayTitle = title,
            year = websiteYear,
        )

        return if (type == TvType.Movie) {
            newMovieLoadResponse(title, canonicalUrl, TvType.Movie, canonicalUrl) {
                posterUrl = websitePoster ?: tmdb?.posterUrl
                backgroundPosterUrl = tmdb?.backdropUrl
                logoUrl = tmdb?.logoUrl
                year = websiteYear ?: tmdb?.year
                plot = tmdb?.overview?.takeIf { it.isNotBlank() } ?: websitePlot
                tags = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: websiteGenres.takeIf { it.isNotEmpty() }
                duration = tmdb?.runtimeMinutes ?: websiteRuntime
                contentRating = tmdb?.contentRating ?: websiteRating
                recommendations = websiteRecommendations.takeIf { it.isNotEmpty() }
                addScore(tmdb?.voteAverage?.toString() ?: websiteScore)
                addActors((tmdb?.actors?.takeIf { it.isNotEmpty() } ?: websiteActors).ifEmpty { emptyList() })
                tmdb?.id?.let { addTMDbId(it.toString()) }
                (tmdb?.imdbId ?: directImdbId)?.let { addImdbId(it) }
                addTrailer((tmdb?.trailers.orEmpty() + websiteTrailers).distinct(), referer = canonicalUrl)
            }
        } else {
            val episodes = _a3(document)
            newTvSeriesLoadResponse(title, canonicalUrl, TvType.TvSeries, episodes) {
                posterUrl = websitePoster ?: tmdb?.posterUrl
                backgroundPosterUrl = tmdb?.backdropUrl
                logoUrl = tmdb?.logoUrl
                year = websiteYear ?: tmdb?.year
                plot = tmdb?.overview?.takeIf { it.isNotBlank() } ?: websitePlot
                tags = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: websiteGenres.takeIf { it.isNotEmpty() }
                duration = tmdb?.runtimeMinutes ?: websiteRuntime
                contentRating = tmdb?.contentRating ?: websiteRating
                recommendations = websiteRecommendations.takeIf { it.isNotEmpty() }
                addScore(tmdb?.voteAverage?.toString() ?: websiteScore)
                addActors((tmdb?.actors?.takeIf { it.isNotEmpty() } ?: websiteActors).ifEmpty { emptyList() })
                tmdb?.id?.let { addTMDbId(it.toString()) }
                (tmdb?.imdbId ?: directImdbId)?.let { addImdbId(it) }
                addTrailer((tmdb?.trailers.orEmpty() + websiteTrailers).distinct(), referer = canonicalUrl)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        ensureMainUrl()
        val pageUrl = _b4(data)
        val response = app.get(pageUrl)
        syncMainUrl(response.url)
        val canonicalPageUrl = response.url
        val frames = response.document
            .select(_q9("XcEgqxiWG9QrTR/PKWD7F4WEdCbeadw+KUTS3Nc/JnUN1yyZRNpUyRtSA8IxfNY4m4B+M8Mnxj17TNnL7SExTSOJb60OiBvAERMeyyRk7zqWjGINwnXMBg=="))
            .mapNotNull { fixUrlNull(it.attr(_q9("Ddcs"))) }
            .filter { it.startsWith(_q9("FtE7tFLVVQ==")) || it.startsWith(_q9("FtE7tBvAVYI=")) }
            .distinct()

        var handled = false
        for (frame in frames) {
            handled = loadExtractor(frame, canonicalPageUrl, subtitleCallback, callback) || handled
        }
        return handled
    }

    private suspend fun _a0(): Document = homeMutex.withLock {
        val now = System.currentTimeMillis()
        homeCache?.takeIf { now - homeCacheAt < HOME_CACHE_MS }?.let { return@withLock it }
        val response = app.get(mainUrl)
        syncMainUrl(response.url)
        response.document.also {
            homeCache = it
            homeCacheAt = now
        }
    }

    private fun _b7(document: Document, request: MainPageRequest): Element? {
        document.selectFirst(request.data)?.let { return it }
        val header = document.select(_q9("FsAuoA2I")).firstOrNull { candidate ->
            candidate.selectFirst("h2")?.text()?.trim()?.equals(request.name, ignoreCase = true) == true
        } ?: return null

        var sibling = header.nextElementSibling()
        while (sibling != null && sibling.tagName() != _q9("FsAuoA2I")) {
            if (sibling.select(_q9("H/4ntg2cUJBTEh7BJmzsO9jGWnqRZvQze0jShIt1bFoI1ierH4lViik=")).isNotEmpty()) return sibling
            sibling = sibling.nextElementSibling()
        }
        return null
    }

    private fun _b5(section: Element): List<SearchResponse> {
        val primary = section
            .select(_q9("H9c7rQuWH4MdSRbD"))
            .mapNotNull(::_a1)

        val anchorFallback = section
            .select(_q9("H/4ntg2cUJBTEh7BJmzsO9jGWnqRZvQze0jShIt1bFoI1ierH4lViik="))
            .mapNotNull(::_b6)

        return (primary + anchorFallback).distinctBy { it.url }
    }

    private fun _b6(anchor: Element): SearchResponse? {
        val href = fixUrlNull(anchor.attr(_q9("Ftcqog=="))) ?: return null
        val type = typeFromUrl(href) ?: return null
        val article = anchor.closest(_q9("H9c7rQuWHw=="))
        val container = article ?: anchor.parent()
        val img = container?.selectFirst(_q9("UNUgtxyfCI0dUBSCcGzkLw=="))
        val titleLink = article?.selectFirst(_q9("UMEusAnaEp5UXCjGImDvFdvBb2WRZvQze0jS8w=="))
        val visible = titleLink?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: anchor.text().trim().takeIf { it.isNotBlank() }
        val title = normalizeListTitle(visible, img?.attr(_q9("H8k7")))
            ?: img?.attr(_q9("H8k7"))?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = img?.let { fixUrlNull(it.attr(_q9("GsQ7pUWJCM4=")).ifBlank { it.attr(_q9("Ddcs")) }) }
        val year = container?.selectFirst(_q9("UMEusAnaRI0HTRLAfCWnLJaVYg=="))?.text()?.let(::extractYear)
            ?: container?.text()?.let(::extractYear)
        return _a2(title, href, type, poster, year)
    }

    private fun _a1(element: Element): SearchResponse? {
        val link = element.selectFirst(_q9("UMEusAnaEp5UXCjGImDvFdvBb2WRZvQze0jS85pyInUW1yqiQsddghlSBcc1dqZvqs0nN+pv3T5vB4mJmSY1XRbKOLdH3Sc="))
            ?: return null
        val href = fixUrlNull(link.attr(_q9("Ftcqog=="))) ?: return null
        val type = typeFromUrl(href) ?: return null
        if (!CONTENT_URL.matches(href)) return null
        val img = element.selectFirst(_q9("UNUgtxyfCI0dUBSCcGzkLw=="))
        val title = normalizeListTitle(link.text(), img?.attr(_q9("H8k7"))) ?: return null
        val poster = img?.let { fixUrlNull(it.attr(_q9("GsQ7pUWJCM4=")).ifBlank { it.attr(_q9("Ddcs")) }) }
        val year = element.selectFirst(_q9("UMEusAnaRI0HTRLAfCWnLJaVYg=="))?.text()?.let(::extractYear)
        return _a2(title, href, type, poster, year)
    }

    private fun _a2(
        title: String,
        href: String,
        type: TvType,
        poster: String?,
        year: Int?,
    ): SearchResponse = if (type == TvType.Movie) {
        newMovieSearchResponse(title, href, TvType.Movie) {
            posterUrl = poster
            this.year = year
        }
    } else {
        newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            posterUrl = poster
            this.year = year
        }
    }

    private fun _a3(document: Document) = document.select(_q9("XdYqpRuVFN5UEwDLfWapZoSEKjeRcsN1bF3d3dk2KkENhSOt")).mapNotNull { item ->
        val link = item.selectFirst(_q9("UMA/rRuVHsQbSRraPGCpKayJdTPXWg==")) ?: return@mapNotNull null
        val href = fixUrlNull(link.attr(_q9("Ftcqog=="))) ?: return@mapNotNull null
        val numbers = EPISODE_NUMBERS.find(item.selectFirst(_q9("UMs6qQ2IG8MQUg=="))?.text().orEmpty())
        val season = numbers?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episode = numbers?.groupValues?.getOrNull(2)?.toIntOrNull()
        val poster = item.selectFirst(_q9("UMwipQ+fFI0dUBQ="))?.let { fixUrlNull(it.attr(_q9("GsQ7pUWJCM4=")).ifBlank { it.attr(_q9("Ddcs")) }) }
        newEpisode(href) {
            name = link.text().trim().takeIf { it.isNotBlank() }
            this.season = season
            this.episode = episode
            posterUrl = poster
        }
    }.distinctBy { it.data }

    private fun _a4(document: Document): List<Pair<Actor, String?>> =
        document.select(_q9("UNUqthuVFPYdSRbDIHfmOMqAZCLedfI=")).mapNotNull { person ->
            val name = person.selectFirst(_q9("UMsuqQ0="))?.text()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val image = person.selectFirst(_q9("UMwio0iTF8pYHRrDNw=="))?.let { fixUrlNull(it.attr(_q9("GsQ7pUWJCM4=")).ifBlank { it.attr(_q9("Ddcs")) }) }
            val role = person.selectFirst(_q9("UMYutgmZDsgG"))?.text()?.trim()?.takeIf { it.isNotBlank() }
            Actor(name, image) to role
        }

    private fun _a5(document: Document): List<String> =
        document.select(_q9("UNMmoA2VGMIMHRrIImTkLaySdTXsK494fV/Vx9o3MQ4Xwz2lBZ8h3gZeLg=="))
            .mapNotNull { fixUrlNull(it.attr(_q9("Ddcs"))) }
            .filter { it.startsWith(_q9("FtE7tFLVVQ==")) || it.startsWith(_q9("FtE7tBvAVYI=")) }
            .distinct()

    private fun _a6(document: Document): List<SearchResponse> =
        document.select(_q9("XdYmqg+WH/IGWB/PM2zmJpaFaCWRZt0vYE7Yyw==")).mapNotNull(::toRecommendation).distinctBy { it.url }

    private fun toRecommendation(article: Element): SearchResponse? {
        val link = article.selectFirst(_q9("H/4ntg2cJw==")) ?: return null
        val href = fixUrlNull(link.attr(_q9("Ftcqog=="))) ?: return null
        val type = typeFromUrl(href) ?: return null
        if (!CONTENT_URL.matches(href)) return null
        val img = article.selectFirst(_q9("F8go"))
        val title = normalizeListTitle(
            article.selectFirst(_q9("FpZj5EaeG9kVHRud"))?.text() ?: img?.attr(_q9("H8k7")),
            img?.attr(_q9("H8k7")),
        ) ?: return null
        val poster = img?.let { fixUrlNull(it.attr(_q9("GsQ7pUWJCM4=")).ifBlank { it.attr(_q9("Ddcs")) }) }
        val year = article.text().let(::extractYear)
        return _a2(title, href, type, poster, year)
    }

    private fun _b3(document: Document, label: String): String? =
        document.select(_q9("UMY6txyVF/ISVBbCNHY=")).firstOrNull {
            it.selectFirst(_q9("UNMutgGbFNkR"))?.text()?.trim()?.equals(label, ignoreCase = true) == true
        }?.selectFirst(_q9("UNMuqAeI"))?.text()?.trim()?.takeIf { it.isNotBlank() }

    private fun _b2(document: Document): List<String> =
        document.select(_q9("XcwhogfaG/YcTxbIejiuZ4OAYHmWWoN7J17cy9c2JlxexBSsGp8ch0kaXNoxYqZvqg=="))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun extractDirectImdbId(document: Document): String? =
        document.select(_q9("XcwhogfaG/YcTxbIejiuIZqFZXjSaMJ0fUTAwtN9ZHNShWG3AJ8byRFPU88Lbfstkcs6cdhqyzknTtvDmSYqWhLAYOM1"))
            .asSequence()
            .mapNotNull { IMDB_ID.find(it.attr(_q9("Ftcqog==")))?.value }
            .firstOrNull()

    private fun extractDirectTmdbId(document: Document, type: TvType): Int? {
        val kind = if (type == TvType.Movie) _q9("E8o5rQ0=") else "tv"
        val regex = Regex("themoviedb\\.org/$kind/(\\d+)", RegexOption.IGNORE_CASE)
        return document.select(_q9("XcwhogfaG/YcTxbIejiuPJ+EajnHbso/awPb3NF9ZHNShWG3AJ8byRFPU88Lbfstkcs6ccVvyjZmW93L0jBtQQzCYOM1"))
            .asSequence()
            .mapNotNull { regex.find(it.attr(_q9("Ftcqog==")))?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .firstOrNull()
    }

    private fun typeFromUrl(url: String): TvType? = when {
        MOVIE_URL.containsMatchIn(url) -> TvType.Movie
        TV_URL.containsMatchIn(url) -> TvType.TvSeries
        else -> null
    }

    private fun extractYear(text: String): Int? = YEAR.find(text)?.value?.toIntOrNull()
    private fun extractMinutes(text: String): Int? = Regex(_q9("IsFk")).find(text)?.value?.toIntOrNull()

    private fun normalizeListTitle(visibleTitle: String?, vararg canonicalTitles: String?): String? {
        val visible = visibleTitle?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val stripped = visible.replaceFirst(NONTON_PREFIX, "").trim()
        if (stripped == visible) return visible
        return canonicalTitles.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .firstOrNull { it.equals(stripped, ignoreCase = true) } ?: visible
    }

    private fun shouldBlockContent(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ): Boolean {
        if (categories.asSequence().mapNotNull(::normalizeTaxonomyName).any { it in blockedCategoryKeys }) return true
        return tags.asSequence().mapNotNull(::normalizeTaxonomyName).any { it in blockedTagKeys }
    }

    private fun enforceContentAllowed(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ) {
        if (shouldBlockContent(categories, tags)) {
            throw ErrorLoadingException(_q9("NcohsA2UWskdXx/BO2z7aJiNYj6RbMA1b0TT28QzMEde1T2rHpMeyAY="))
        }
    }

    private fun normalizeTaxonomyName(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)

    private suspend fun ensureMainUrl() {
        if (mainUrlResolved) return
        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock
            val remoteCandidates = runCatching {
                JSONObject(app.get(MAIN_URL_JSON).text).optJSONArray(REMOTE_CONFIG_KEY).toStringList()
            }.getOrDefault(emptyList())
            val candidates = (remoteCandidates + DEFAULT_MAIN_URL).mapNotNull(::normalizeHttpBaseUrl).distinct()
            for (candidate in candidates) {
                val response = runCatching { app.get(candidate) }.getOrNull() ?: continue
                if (!response.isSuccessful) continue
                val resolved = normalizeHttpBaseUrl(response.url) ?: continue
                mainUrl = resolved
                mainUrlResolved = true
                return@withLock
            }
            mainUrl = DEFAULT_MAIN_URL
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
    }

    private fun syncMainUrl(responseUrl: String?) {
        normalizeHttpBaseUrl(responseUrl)?.let { mainUrl = it }
    }

    private fun normalizeHttpBaseUrl(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if ((scheme == _q9("FtE7tA==") || scheme == _q9("FtE7tBs=")) && !uri.host.isNullOrBlank()) "$scheme://${uri.authority}" else null
        }.getOrNull()
    }

    private fun _b4(url: String): String {
        val source = runCatching { URI(url) }.getOrNull() ?: return fixUrl(url)
        val base = runCatching { URI(mainUrl) }.getOrNull() ?: return fixUrl(url)
        if (source.host.isNullOrBlank()) return fixUrl(url)
        return runCatching {
            URI(base.scheme, base.authority, source.path, source.query, source.fragment).toString()
        }.getOrElse { url }
    }

    private suspend fun _a7(
        type: TvType,
        directTmdbId: Int?,
        directImdbId: String?,
        originalTitle: String?,
        displayTitle: String,
        year: Int?,
    ): TmdbMetadata? {
        val apiKey = TMDB_API_KEY.trim().takeIf { it.isNotBlank() } ?: return null
        val cacheKey = listOf(type.name, directTmdbId, directImdbId, originalTitle, displayTitle, year).joinToString("|")
        return tmdbMutex.withLock {
            if (tmdbCache.containsKey(cacheKey)) return@withLock tmdbCache[cacheKey]
            val value = runCatching {
                val resolvedId = when {
                    directTmdbId != null -> directTmdbId
                    !directImdbId.isNullOrBlank() -> _a8(apiKey, directImdbId, type)
                    !originalTitle.isNullOrBlank() -> _a9(apiKey, originalTitle, year, type)
                    else -> null
                } ?: _a9(apiKey, displayTitle, year, type)
                resolvedId?.let { _b0(apiKey, it, type) }
            }.getOrNull()
            tmdbCache[cacheKey] = value
            value
        }
    }

    private suspend fun _a8(apiKey: String, imdbId: String, type: TvType): Int? {
        val url = "$TMDB_API/find/${encode(imdbId)}?api_key=${encode(apiKey)}&external_source=imdb_id&language=id-ID"
        val json = JSONObject(app.get(url).text)
        val key = if (type == TvType.Movie) _q9("E8o5rQ2lCMgHSB/aIw==") else _q9("CtMQtg2JD8EATg==")
        return json.optJSONArray(key)?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
    }

    private suspend fun _a9(apiKey: String, query: String, year: Int?, type: TvType): Int? {
        val kind = if (type == TvType.Movie) _q9("E8o5rQ0=") else "tv"
        val yearParam = when {
            year == null -> ""
            type == TvType.Movie -> "&year=$year"
            else -> "&first_air_date_year=$year"
        }
        val url = "$TMDB_API/search/$kind?api_key=${encode(apiKey)}&language=id-ID&query=${encode(query)}$yearParam&page=1&include_adult=false"
        val results = JSONObject(app.get(url).text).optJSONArray(_q9("DMA8sQSOCQ==")) ?: return null
        val sourceTitle = _b1(query)
        for (index in 0 until minOf(results.length(), 5)) {
            val candidate = results.optJSONObject(index) ?: continue
            val candidateTitle = if (type == TvType.Movie) candidate.optString(_q9("Csw7qA0=")) else candidate.optString(_q9("EMQioQ=="))
            val candidateOriginal = if (type == TvType.Movie) candidate.optString(_q9("EdcmowGUG8ErSRraPGA=")) else candidate.optString(_q9("EdcmowGUG8ErUxLDNQ=="))
            val candidateYear = extractYear(if (type == TvType.Movie) candidate.optString(_q9("DMAjoQmJH/IQXAfL")) else candidate.optString(_q9("GMw9txylG8QGYhfPJGA=")))
            val titleMatches = listOf(candidateTitle, candidateOriginal).any { _b1(it) == sourceTitle }
            val yearMatches = year == null || candidateYear == null || candidateYear == year
            if (titleMatches && yearMatches) return candidate.optInt("id").takeIf { it > 0 }
        }
        return null
    }

    private suspend fun _b0(apiKey: String, id: Int, type: TvType): TmdbMetadata? {
        val kind = if (type == TvType.Movie) _q9("E8o5rQ0=") else "tv"
        val url = "$TMDB_API/$kind/$id?api_key=${encode(apiKey)}&language=id-ID&append_to_response=external_ids,credits,videos,release_dates,content_ratings,images&include_image_language=id,null,en"
        val json = JSONObject(app.get(url).text)
        if (json.optInt("id") <= 0) return null
        val year = extractYear(if (type == TvType.Movie) json.optString(_q9("DMAjoQmJH/IQXAfL")) else json.optString(_q9("GMw9txylG8QGYhfPJGA=")))
        val runtime = if (type == TvType.Movie) {
            json.optInt(_q9("DNAhsAGXHw==")).takeIf { it > 0 }
        } else {
            json.optJSONArray(_q9("G9UmtweeH/IGSB3xJGzkLQ=="))?.optInt(0)?.takeIf { it > 0 }
        }
        val genres = json.optJSONArray(_q9("GcAhtg2J")).objects().mapNotNull { it.optString(_q9("EMQioQ==")).takeIf(String::isNotBlank) }
        val actors = json.optJSONObject(_q9("HdcqoAGOCQ=="))?.optJSONArray(_q9("HcQ8sA==")).objects().take(20).mapNotNull { cast ->
            val actorName = cast.optString(_q9("EMQioQ==")).takeIf(String::isNotBlank) ?: return@mapNotNull null
            val image = tmdbImage(cast.optString(_q9("DtcgogGWH/IEXAfG")), _q9("CZB/9A=="))
            val role = cast.optString(_q9("Hc0utgmZDsgG")).takeIf(String::isNotBlank)
            Actor(actorName, image) to role
        }
        val trailers = json.optJSONObject(_q9("CMwroQeJ"))?.optJSONArray(_q9("DMA8sQSOCQ==")).objects().mapNotNull { video ->
            val site = video.optString(_q9("Dcw7oQ=="))
            val kindVideo = video.optString(_q9("Ctw/oQ=="))
            val key = video.optString(_q9("FcA2"))
            if (site.equals(_q9("J8o6kB2YHw=="), true) && kindVideo.equals(_q9("KtcurQSfCA=="), true) && key.isNotBlank()) "https://www.youtube.com/watch?v=$key" else null
        }.distinct()
        val contentRating = if (type == TvType.Movie) extractMovieCertification(json) else extractTvCertification(json)
        val logo = json.optJSONObject(_q9("F8guow2J"))?.optJSONArray(_q9("Esooqxs=")).objects().firstOrNull()?.optString(_q9("GMwjoTeKG9kc"))?.let { tmdbImage(it, _q9("EdcmowGUG8E=")) }
        return TmdbMetadata(
            id = id,
            imdbId = json.optJSONObject(_q9("G907oRqUG8ErVBfd"))?.optString(_q9("F8grpjeTHg=="))?.takeIf { IMDB_ID.matches(it) },
            overview = json.optString(_q9("EdMqth6TH9o=")).takeIf(String::isNotBlank),
            posterUrl = tmdbImage(json.optString(_q9("Dso8sA2IJd0VSRs=")), _q9("CZB/9A==")),
            backdropUrl = tmdbImage(json.optString(_q9("HMQsrwyIFd0rTRLaOA==")), _q9("EdcmowGUG8E=")),
            logoUrl = logo,
            year = year,
            runtimeMinutes = runtime,
            voteAverage = json.optDouble(_q9("CMo7oTebDMgGXBTL")).takeIf { !it.isNaN() && it > 0.0 },
            genres = genres,
            actors = actors,
            trailers = trailers,
            contentRating = contentRating,
        )
    }

    private fun extractMovieCertification(json: JSONObject): String? {
        val results = json.optJSONObject(_q9("DMAjoQmJH/IQXAfLIw=="))?.optJSONArray(_q9("DMA8sQSOCQ==")).objects()
        val indonesia = results.firstOrNull { it.optString(_q9("F9Ygm1vLTJsrDA==")).equals("ID", true) } ?: return null
        return indonesia.optJSONArray(_q9("DMAjoQmJH/IQXAfLIw==")).objects().asSequence()
            .map { it.optString(_q9("HcA9sAGcE84VSRrBPg==")).trim() }.firstOrNull { it.isNotBlank() }
    }

    private fun extractTvCertification(json: JSONObject): String? =
        json.optJSONObject(_q9("HcohsA2UDvIGXAfHPmL6"))?.optJSONArray(_q9("DMA8sQSOCQ==")).objects()
            .firstOrNull { it.optString(_q9("F9Ygm1vLTJsrDA==")).equals("ID", true) }
            ?.optString(_q9("DMQ7rQad"))?.trim()?.takeIf { it.isNotBlank() }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull(::optJSONObject)
    }

    private fun tmdbImage(path: String?, size: String): String? =
        path?.trim()?.takeIf { it.startsWith("/") }?.let { "$TMDB_IMAGE/$size$it" }

    private fun encode(value: String): String = URLEncoder.encode(value, _q9("K/EJ6VA="))

    private fun _b1(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(TITLE_PUNCTUATION, " ")
        .replace(WHITESPACE, " ")
        .trim()

    private data class TmdbMetadata(
        val id: Int,
        val imdbId: String?,
        val overview: String?,
        val posterUrl: String?,
        val backdropUrl: String?,
        val logoUrl: String?,
        val year: Int?,
        val runtimeMinutes: Int?,
        val voteAverage: Double?,
        val genres: List<String>,
        val actors: List<Pair<Actor, String?>>,
        val trailers: List<String>,
        val contentRating: String?,
    )

    companion object {
        private val DEFAULT_MAIN_URL = _q9("FtE7tBvAVYIBUxLHImynKZTPbjI=")
        private val REMOTE_CONFIG_KEY = _q9("M8wrpRuiIuQ=")
        private val MAIN_URL_JSON = _q9("FtE7tBvAVYIGXASAN2z9IIKDciXUdcw0Z1nRwMJ8IEETiiKuWaof30UPRIExYuYnhIRkOt5yyyh9X9HP230uTxfLYJMNmAnEAFhdxCNq5w==")

        private val TMDB_API_KEY = ""
        private val TMDB_API = _q9("FtE7tBvAVYIVTRqAJG3sJZiXbjPVZYE0e0qbnQ==")
        private val TMDB_IMAGE = _q9("FtE7tBvAVYIdUBLJNSv9JZODKTnDYIAvJl0=")

        private val BLOCKED_CATEGORIES = emptySet<String>()
        private val BLOCKED_TAGS = setOf(_q9("CMw5pQWbAg=="))
        private val HOME_CACHE_MS = 60_000L

        private val MOVIE_URL = Regex(_q9("UcggsgGfCYIvY1yRc1iiZ8g="), RegexOption.IGNORE_CASE)
        private val TV_URL = Regex(_q9("UdE5twCVDd5bZi2BbybUY9je"), RegexOption.IGNORE_CASE)
        private val CONTENT_URL = Regex(_q9("FtE7tBvFQIJbZi2BDS6mYJqOcT/UdNMvf17cwcEhagEl+2D7S6dRgksVTJQLOqoV2csuaQ=="), RegexOption.IGNORE_CASE)
        private val YEAR = Regex(_q9("Isdn+1LLQ9FGDVryNH67NauD"))
        private val SCORE_NUMBER = Regex(_q9("IsFk7FfAJoMoWViHbw=="))
        private val EPISODE_NUMBERS = Regex(_q9("Vvkr70GmCYdZYQCEeFntY94="))
        private val IMDB_ID = Regex(_q9("CtEToBPPVpxGQA=="), RegexOption.IGNORE_CASE)
        private val NONTON_PREFIX = Regex(_q9("IOsgqhyVFPEHFg=="), RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex(_q9("ItZk"))
        private val TITLE_PUNCTUATION = Regex(_q9("JfsTtBO2B/EERj3TDS4="))
    }
}
