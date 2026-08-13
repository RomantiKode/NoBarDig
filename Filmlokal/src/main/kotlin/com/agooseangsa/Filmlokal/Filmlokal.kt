package com.agooseangsa.Filmlokal

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
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addQuality
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class Filmlokal : MainAPI() {
    override var mainUrl = DEFAULT_MAIN_URL
    override var name = "Filmlokal"
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val hasMainPage = true
    override val mainPage = listOf(
        MainPageData(_q9("nstFJS6yj38RmVPUvPwH"), _q9("vsdIPHuUj2lO")),
        MainPageData(_q9("nstFJS6yi2UUmAOT4KtA"), _q9("ocdIOiHU2j9U2Q==")),
        MainPageData(_q9("nstFJS6uhX8TmVE="), _q9("sM1bOmGUxQ==")),
        MainPageData(_q9("nstFJS61j38Ik1A="), _q9("vstFJSOVj38Ik1CO")),
    )

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false
    private val _a2 = _a0()

    private val _a3 by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_TAXONOMY_SLUGS.map(::normalizeTaxonomyName).toSet()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        val base = resolveSiteUrl(request.data)
        val pageUrl = if (page <= 1) base else pagedArchiveUrl(base, page)
        val response = app.get(pageUrl)
        syncMainUrl(response.url)

        val items = response.document.select(LISTING_SELECTOR)
            .mapNotNull(::_a4)

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false,
            ),
            hasNext = hasNextPage(response.document, page),
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val searchUrl = "$mainUrl/?s=$encoded&post_type%5B%5D=post&post_type%5B%5D=tv"
        val response = app.get(searchUrl)
        syncMainUrl(response.url)
        return response.document.select(LISTING_SELECTOR).mapNotNull(::_a4)
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val response = app.get(url)
        syncMainUrl(response.url)
        val document = response.document
        val canonicalUrl = document.selectFirst(_q9("tMtHI1WUj2FclULPv/cchvmlbA=="))?._a9(_q9("sNBMLg=="))
            ?.takeIf { it.isNotBlank() }
            ?: response.url
        val title = document.selectFirst(_q9("sJMHLWCSmHRMgkrVvPxZxfD4"))?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_q9("ktdNPWLGrGQNm0/Ou/gZxeygVTLHp+ILRplQh5ryfg=="))

        val categories = detailCategories(document)
        val nativeTags = detailTags(document)
        val taxonomySlugs = _a8(document)
        enforceContentAllowed(categories, nativeTags, taxonomySlugs)

        val isSeries = canonicalUrl.contains(_q9("99ZfZw==")) ||
            document.select(_q9("9sVEOiOKg34VhUbTufwGxfmSWSHJ4axfFdNYgoK8N6A=")).isNotEmpty()
        val websiteYear = detailField(document, _q9("gcdIOg=="))?.toIntOrNull()
            ?: TITLE_YEAR.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val poster = bestImageUrl(document.selectFirst(_q9("9sVEOiOLhXsIkw7Fse0Uxf6gVibe4qYLX5sR0t/wf5Osx0c8I5KCeAyUTcC59VWM9a4=")))
        val websitePlot = cleanDetailPlot(document, title)
        val websiteScore = document.selectFirst(_q9("g8tdLWOWmGIRy1HApPAbgs6oXSbJ2g=="))?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
        val websiteDuration = detailField(document, _q9("nNdbKXqPhWM="))
        val actors = detailFieldLinks(document, _q9("m8NaPA=="))
        val trailer = primaryTrailer(document, isSeries)
        val tmdbLookupTitle = title.replace(TITLE_YEAR_WITH_SPACE, " ").replace(WHITESPACE, " ").trim()
        val tmdb = _a5(
            AgooseTmdbIdentity(
                displayTitle = tmdbLookupTitle,
                year = websiteYear,
                isTv = isSeries,
            ),
        )

        val displayPlot = tmdb?.overview?.takeIf { it.isNotBlank() } ?: websitePlot
        val displayPoster = poster ?: tmdb?.posterPath?.let(::tmdbPosterUrl)
        val backdrop = tmdb?.backdropPath?.let(::tmdbBackdropUrl)
        val displayTags = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: categories
        val displayYear = websiteYear ?: tmdb?.year
        val displayScore = tmdb?.voteAverage?.toString() ?: websiteScore
        val displayDuration = tmdb?.runtimeMinutes ?: durationMinutes(websiteDuration)

        return if (isSeries) {
            val episodes = document.select(_q9("9sVEOiOKg34VhUbTufwGxfmSWSHJ4axfFdNYgoK8N6A="))
                .mapNotNull { anchor ->
                    val episodeUrl = anchor.absUrl(_q9("sNBMLg==")).ifBlank { anchor._a9(_q9("sNBMLg==")) }
                    val label = anchor.text().trim()
                    val match = EPISODE_LABEL.find(label) ?: return@mapNotNull null
                    newEpisode(episodeUrl) {
                        season = match.groupValues[1].toIntOrNull()
                        episode = match.groupValues[2].toIntOrNull()
                        name = label
                    }
                }
                .distinctBy { it.data }

            newTvSeriesLoadResponse(title, canonicalUrl, TvType.TvSeries, episodes) {
                posterUrl = displayPoster
                backgroundPosterUrl = backdrop
                year = displayYear
                plot = displayPlot
                this.tags = displayTags
                duration = displayDuration
                addActors(actors)
                addScore(displayScore)
                addTrailer(trailer, referer = canonicalUrl)
                tmdb?.let {
                    addTMDbId(it.tmdbId.toString())
                    addImdbId(it.imdbId)
                }
            }
        } else {
            newMovieLoadResponse(title, canonicalUrl, TvType.Movie, canonicalUrl) {
                posterUrl = displayPoster
                backgroundPosterUrl = backdrop
                year = displayYear
                plot = displayPlot
                this.tags = displayTags
                duration = displayDuration
                addActors(actors)
                addScore(displayScore)
                addTrailer(trailer, referer = canonicalUrl)
                tmdb?.let {
                    addTMDbId(it.tmdbId.toString())
                    addImdbId(it.imdbId)
                }
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
        val response = app.get(data)
        syncMainUrl(response.url)
        val document = response.document
        val canonicalUrl = document.selectFirst(_q9("tMtHI1WUj2FclULPv/cchvmlbA=="))?._a9(_q9("sNBMLg=="))
            ?.takeIf { it.isNotBlank() }
            ?: response.url

        val categories = detailCategories(document)
        val nativeTags = detailTags(document)
        val taxonomySlugs = _a8(document)
        enforceContentAllowed(categories, nativeTags, taxonomySlugs)

        val candidates = linkedSetOf<String>()
        document.select(_q9("9sVEOiOVj38Xk1GMp+sUlbigVyHN6uM5QY5er92zPpCt1EA4fIm1fQ2XWsSixhaK9r1UPdin7wRAnVCXquBinoU=")).forEach { iframe ->
            iframe.absUrl(_q9("q9BK")).ifBlank { iframe._a9(_q9("q9BK")) }
                .takeIf(::isPlaybackCandidate)
                ?.let(candidates::add)
        }

        document.select(_q9("uflBOmuAtw==")).forEach { anchor ->
            val label = anchor.text().trim()
            if (!label.startsWith(_q9("nM1eJmKJi2lBoErA"), ignoreCase = true)) return@forEach
            anchor.absUrl(_q9("sNBMLg==")).ifBlank { anchor._a9(_q9("sNBMLg==")) }
                .takeIf(::isPlaybackCandidate)
                ?.let(candidates::add)
        }

        var emitted = false
        for (candidate in candidates) {
            val resolved = _a2.resolve(
                url = candidate,
                referer = canonicalUrl,
                subtitleCallback = subtitleCallback,
            ) { link ->
                emitted = true
                callback(link)
            }
            emitted = resolved || emitted
        }
        return emitted
    }

    private fun _a4(item: Element): SearchResponse? {
        val titleAnchor = item.selectFirst(_q9("sJAHLWCSmHRMgkrVvPxVhMOhQzbK2qpCWs8Tl5/nYoT11kA8YoPKbDqeUcS2xA==")) ?: return null
        val title = titleAnchor.text().trim().takeIf { it.isNotBlank() } ?: return null
        val url = titleAnchor.absUrl(_q9("sNBMLg==")).ifBlank { titleAnchor._a9(_q9("sNBMLg==")) }
        if (url.isBlank()) return null

        val categoryAnchors = item.select(_q9("9sVEOiOLhXsIkw7OvrkUvuqsXW6L5OcWV5tSgIizZJy/hXRkLoexfwSaHoaz+AGA/6ZDKozz5wUVoQ=="))
        val categoryLabels = categoryAnchors.map { it.text().trim() }.filter { it.isNotBlank() }
        val taxonomySlugs = categoryAnchors.mapNotNull { taxonomySlug(it._a9(_q9("sNBMLg=="))) }
        if (isBlocked(categoryLabels, emptyList(), taxonomySlugs)) return null

        val poster = bestImageUrl(item.selectFirst(_q9("9sFGJnqDhHlMgkvUvfsbhPGlETrB4KpCW5Fa")))
        val year = TITLE_YEAR.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val quality = item.selectFirst(_q9("9sVEOiOXn2wNn1fY/fABgPXlEX3L6vRPQ4lcnpjnaQ=="))?.text()?.trim()
        val episodeCount = item.selectFirst(_q9("9sVEOiOIn2ADk1PS8OoFhPblEX3L6vRPXIlQkJTjYw=="))?.text()
            ?.filter(Char::isDigit)
            ?.toIntOrNull()
        val isSeries = url.contains(_q9("99ZfZw==")) ||
            item.selectFirst(_q9("9sVEOiOWhX4VglrRtbQckf2k"))?.text()?.contains("TV", ignoreCase = true) == true

        return if (isSeries) {
            newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                posterUrl = poster
                this.year = year
                episodes = episodeCount
                quality?.let { addQuality(it) }
            }
        } else {
            newMovieSearchResponse(title, url, TvType.Movie) {
                posterUrl = poster
                this.year = year
                quality?.let { addQuality(it) }
            }
        }
    }

    private fun detailCategories(document: Document): List<String> =
        detailFieldElement(document, _q9("n8dHOms="))
            ?.select(_q9("uflBOmuAtw=="))
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

    private fun detailTags(document: Document): List<String> =
        document.select(_q9("9tZIL33LhmQPnVCMs/Ybkf2nRXPN3O4QV5oXz9a8ZJy/jQ4V"))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun _a8(document: Document): List<String> {
        val categoryAnchors = detailFieldElement(document, _q9("n8dHOms="))?.select(_q9("uflBOmuAtw==")).orEmpty()
        val tagAnchors = document.select(_q9("9tZIL33LhmQPnVCMs/Ybkf2nRXPN3O4QV5oXz9a8ZJy/jQ4V"))
        return (categoryAnchors + tagAnchors)
            .mapNotNull { taxonomySlug(it._a9(_q9("sNBMLg=="))) }
            .distinct()
    }

    private fun detailField(document: Document, label: String): String? {
        val element = detailFieldElement(document, label) ?: return null
        val clone = element.clone()
        clone.select(_q9("q9ZbJ2CB")).firstOrNull()?.remove()
        return clone.text().trim().takeIf { it.isNotBlank() }
    }

    private fun detailFieldLinks(document: Document, label: String): List<String> {
        val element = detailFieldElement(document, label) ?: return emptyList()
        val links = element.select(_q9("uflBOmuAtw==")).map { it.text().trim() }.filter { it.isNotBlank() }
        if (links.isNotEmpty()) return links.distinct()
        return detailField(document, label)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    private fun detailFieldElement(document: Document, label: String): Element? =
        document.select(_q9("9sFGJnqDhHlMm0zXufwRhOyoEX3L6vRPX5NLm5T3cYm5jglmaYuYIAyZVci1/RSR+Q==")).firstOrNull { element ->
            element.selectFirst(_q9("q9ZbJ2CB"))?.text()?.trim()?.removeSuffix(":")?.equals(label, ignoreCase = true) == true
        }

    private fun cleanDetailPlot(document: Document, title: String): String? {
        val raw = document.selectFirst(_q9("9sdHPHyfx24OmFfEvu1bgPa9QyqB5OkMRplThtzgeZO/zkwTZ5KPYBGETNHt/RCW+7tYI9ju6Qxv3E3e0b11k6zQUGVtiYR5BJhXj7X3AZfh5FI8wvPjDEbRTpuf9HyY+NI="))
            ?.text()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return raw.removePrefix(title).trim().takeIf { it.isNotBlank() } ?: raw
    }

    private fun primaryTrailer(document: Document, isSeries: Boolean): String? {
        if (isSeries) {
            return document.selectFirst(_q9("9sVEOiOVj38Xk1GMp+sUlbigVyHN6uM5QY5e2My0aZKt1lwqa8iJYgzRfo3wtxiQ7qBBIcPY9g5ThViArvB/k6zHRzwuj4x/AJtG+qPrFs+l7kg82fPzAFfSXp2ctE0="))
                ?._a9(_q9("q9BK"))
                ?.takeIf { it.isNotBlank() }
        }
        return document.selectFirst(_q9("9sVEOiOVj38Xk1GMp+sUlbioHzTB9asWQJ1UnpThPY230lw4VY6YaAerD4GxtxKI6uRFIc3u6gdA0U2dgeZgprDQTC5T"))
            ?._a9(_q9("sNBMLg=="))
            ?.takeIf { it.contains(_q9("oc1cPHuEjyMCmU4="), ignoreCase = true) || it.contains(_q9("oc1cPHvIiGg="), ignoreCase = true) }
    }

    private fun bestImageUrl(image: Element?): String? {
        image ?: return null
        val srcset = image._a9(_q9("q9BKO2uS")).trim()
        if (srcset.isNotBlank()) {
            val best = srcset.split(',')
                .mapNotNull { part ->
                    val pieces = part.trim().split(Regex(_q9("hNEC")))
                    val url = pieces.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val width = pieces.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
                    width to url
                }
                .maxByOrNull { it.first }
                ?.second
            if (!best.isNullOrBlank()) return best
        }
        return image.absUrl(_q9("q9BK")).ifBlank { image._a9(_q9("q9BK")) }.takeIf { it.isNotBlank() }
    }

    private fun Element._a9(name: String): String =
        attributes().get(name)

    private fun durationMinutes(value: String?): Int? =
        value?.let { DURATION_MIN.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

    private fun isBlocked(
        categories: Iterable<String>,
        tags: Iterable<String>,
        taxonomySlugs: Iterable<String>,
    ): Boolean {
        val values = sequenceOf(categories, tags, taxonomySlugs)
            .flatMap { it.asSequence() }
            .map(::normalizeTaxonomyName)
        return values.any { it in _a3 }
    }

    private fun enforceContentAllowed(
        categories: Iterable<String>,
        tags: Iterable<String>,
        taxonomySlugs: Iterable<String>,
    ) {
        if (isBlocked(categories, tags, taxonomySlugs)) {
            throw ErrorLoadingException(_q9("k81HPGuIymkIlE/Ou/AHxfelVDuM7OkMVJVah4PyY5T40lsneI+OaBM="))
        }
    }

    private fun normalizeTaxonomyName(value: String): String =
        value.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT)

    private fun taxonomySlug(url: String?): String? {
        val value = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            URI(value).path.trim('/').substringAfterLast('/').takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun hasNextPage(document: Document, page: Int): Boolean {
        val next = document.selectFirst(_q9("tMtHI1WUj2FcmEbZpMRZxfnnXzbU86gSU5tY35/mfZ+90FpkLoexfwSaHs+14QG4"))?._a9(_q9("sNBMLg=="))
        if (!next.isNullOrBlank()) return true
        return document.select(_q9("uYxZKWmDx2MUm0HEououjeqsVw4=")).any { anchor ->
            anchor.text().trim().toIntOrNull()?.let { it > page } == true
        }
    }

    private fun pagedArchiveUrl(base: String, page: Int): String =
        "${base.trimEnd('/')}/page/$page/"

    private fun resolveSiteUrl(pathOrUrl: String): String {
        if (pathOrUrl.startsWith(_q9("sNZdODTJxQ==")) || pathOrUrl.startsWith(_q9("sNZdOH3cxSI="))) return pathOrUrl
        return "${mainUrl.trimEnd('/')}/${pathOrUrl.trimStart('/')}"
    }

    private fun isPlaybackCandidate(value: String): Boolean =
        value.startsWith(_q9("sNZdODTJxQ==")) || value.startsWith(_q9("sNZdOH3cxSI="))

    private suspend fun ensureMainUrl() {
        if (mainUrlResolved) return

        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock

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
                mainUrlResolved = true
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
            val scheme = uri.scheme?.lowercase()
            if ((scheme == _q9("sNZdOA==") || scheme == _q9("sNZdOH0=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else {
                null
            }
        }.getOrNull()
    }

    private fun tmdbPosterUrl(path: String): String = "https://image.tmdb.org/t/p/w500$path"
    private fun tmdbBackdropUrl(path: String): String = "https://image.tmdb.org/t/p/w1280$path"

    companion object {
        private const val DEFAULT_MAIN_URL = "https://tv1.filmlokal.me"
        private const val REMOTE_CONFIG_KEY = "Filmlokal"
        private const val MAIN_URL_JSON =
            "https://raw.githubusercontent.com/mj1Per127/agoosecloudstream/main/Website.json"

        private const val LISTING_SELECTOR =
            "article.item-infinite, article.item.has-post-thumbnail, article.item"

        private val BLOCKED_TAXONOMY_SLUGS = setOf(
            _q9("q9daOGuIjn4E"),
            _q9("6pMEJX6O"),
            _q9("q9dLZWeIjmI="),
            _q9("q9dLZWuIjWEIhUs="),
            _q9("u8dHO2GUj2k="),
            _q9("vstFJSOVj2AI"),
            _q9("rcxKLWCVhX8Ekg=="),
            _q9("tMtfLSOVnn8El07Ivv5YjP+m"),
            _q9("ssdbPWXLiGwTl1c="),
            _q9("udFAKWDLi2AAgkbUog=="),
            _q9("ssNf"),
        )

        private val TITLE_YEAR = Regex(_q9("hIoBFGqd3nBIqgo="))
        private val TITLE_YEAR_WITH_SPACE = Regex(_q9("hNEDFCa6jnZVi3+IjOpf"))
        private val EPISODE_LABEL = Regex(_q9("i4p1LCXPtn5Ls1PSjOpfzcStGno="), RegexOption.IGNORE_CASE)
        private val DURATION_MIN = Regex(_q9("8P5NYye6mSdJyRnMufcJiP2nWCeF"), RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex(_q9("hNEC"))
    }
}
