package com.agooseangsa.Terbit21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Locale

class Terbit21 : MainAPI() {
    override var mainUrl = DEFAULT_MAIN_URL
    override var name = "Terbit21"
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "/" to _q9("M1/fFDsbKQ=="),
        _q9("SE7bWQ==") to _q9("I0jMGzs="),
        _q9("SFjIBS5ELte0VfewsA==") to _q9("JV/eAno7PcKpUv4="),
        _q9("SFnCAzQdLs/vX/G+8c6D") to _q9("I0jMGztJH96pUvg="),
    )

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false

    private val blockedCategoryKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_CATEGORIES.mapNotNull(::normalizeTaxonomyName).toSet()
    }
    private val blockedTagKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_TAGS.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    protected suspend fun ensureMainUrl() {
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

    protected fun syncMainUrl(responseUrl: String?) {
        normalizeHttpBaseUrl(responseUrl)?.let { mainUrl = it }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(REMOTE_CONFIG_KEY) ?: return emptyList()
        return (0 until array.length())
            .map { array.optString(it) }
            .mapNotNull(::normalizeHttpBaseUrl)
            .distinct()
    }

    protected fun normalizeHttpBaseUrl(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            if ((scheme == _q9("D07ZBg==") || scheme == _q9("D07ZBik=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else null
        }.getOrNull()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        val sourceUrl = fixUrl(request.data)
        val pageUrl = _a8(sourceUrl, page)
        val response = app.get(pageUrl, referer = mainUrl)
        syncMainUrl(response.url)
        val document = response.document
        val items = document.select(_q9("RF3ABHcEPd+uEfW4/suMmibsW4vsDcnCU2yKCGlzFLMOWcETdAAo060="))
            .mapNotNull(::_a0)
            .distinctBy { it.url }

        val hasNext = document.select(_q9("BhTDEyIdcsahW/z68drBmTHqQcSgRpfKQGCJRT17CalHW4MYPxEo7ahO/LHC")).isNotEmpty()
        return newHomePageResponse(request.name, items, hasNext = hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        if (query.isBlank()) return emptyList()

        val response = app.get(
            mainUrl,
            params = mapOf(
                "s" to query,
                _q9("FF/MBDkB") to _q9("Bl7bFzQKOdI="),
            ),
            referer = mainUrl,
        )
        syncMainUrl(response.url)
        return response.document.select(_q9("RF3ABHcEPd+uEfW4/suMmibsW4vsDcnCU2yKCGlzFLMOWcETdAAo060="))
            .mapNotNull(::_a0)
            .distinctBy { it.url }
    }

    private fun _a0(card: Element): SearchResponse? {
        val anchor = card.selectFirst(_q9("DwiDEzQdLs/tSPCj88qMmg/wQI3mNQ==")) ?: return null
        val title = anchor.text().trim().takeIf { it.isNotBlank() } ?: return null
        val url = fixUrl(anchor.attr(_q9("D0jIEA==")))
        val categories = card.select(_q9("SV3ABHcEM8CpWbS48Y/N")).map { it.text().trim() }.filter { it.isNotBlank() }
        if (shouldBlockContent(categories = categories)) return null

        val poster = card.selectFirst(_q9("SVnCGC4MMsLtSPGi8s3Cmj30EoHtD7zYVWq6"))?.attr(_q9("FEjO"))
            ?.takeIf { it.isNotBlank() }
            ?.let(::fixUrl)
        val isTv = card.attr(_q9("Dk7IGy4QLNM=")).contains("TV", ignoreCase = true) || url.contains(_q9("SE7bWQ=="))

        return if (isTv) {
            newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, url, TvType.Movie) {
                posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val response = app.get(url, referer = mainUrl)
        syncMainUrl(response.url)
        val document = response.document

        if (document.body()?.hasClass(_q9("FFPDETYMcdOwVeq4+8o=")) == true) {
            val parent = document.selectFirst(_q9("SV3ABHcFNcW0T/yl9srf2zW2UJ30HIjFfGGVQS84W+BITttZfTQ="))?.attr(_q9("D0jIEA=="))
                ?.takeIf { it.isNotBlank() }
                ?: throw ErrorLoadingException(_q9("L1vBFzcIMpalTPCk8MvJ2yDxVonrSIrOSmCLTSJ7RrMGT9kXNEkv07JV+Lu/xsKfIfM="))
            return load(fixUrl(parent))
        }

        val canonicalUrl = document.selectFirst(_q9("C1PDHQEbOdr9X/i58MHFmDX0b7PoGoLNeg=="))?.attr(_q9("D0jIEA=="))
            ?.takeIf { it.isNotBlank() }
            ?.let(::fixUrl)
            ?: response.url
        val title = document.selectFirst(_q9("DwuDEzQdLs/tSPCj88r3kiD9X5jyB5eWSWiKQRQ+Rq9WFMgYLhslm7RV7bv6"))
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_q9("LU/JAzZJKN+kXfL3+8bYnjntWYnu"))

        val isTv = document.body()?.hasClass(_q9("FFPDETYMccK2")) == true || canonicalUrl.contains(_q9("SE7bWQ=="))
        val rawGenres = _a6(document, _q9("IF/DBD8="))
        enforceContentAllowed(categories = rawGenres)

        val websiteYear = _a5(document, _q9("Pl/MBA=="))?.let(YEAR_REGEX::find)?.value?.toIntOrNull()
            ?: TITLE_YEAR_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val websiteDuration = _a5(document, _q9("I0/fFy4AM9g="))?.let(MINUTES_REGEX::find)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        val websitePlot = _a2(document)
        val websitePoster = document.selectFirst(_q9("Cl/ZFwEZLtmwWeuj5pLDnG7xX4nnDbrwRGaJUCx8Epo="))
            ?.attr(_q9("BFXDAj8HKA=="))?.takeIf { it.isNotBlank() }?.let(::fixUrl)
        val websiteRating = document.selectFirst(_q9("PFPZEzcZLtmwAeu268bCnAL5Xp3lNQ=="))?.text()?.trim()?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
        val trailer = _a3(document)

        val cleanTitleForMatch = title.replace(TITLE_YEAR_SUFFIX_REGEX, "").trim()
        val tmdb = fetchAgooseTmdbMetadata(
            AgooseTmdbIdentity(
                displayTitle = cleanTitleForMatch,
                year = websiteYear,
                isTv = isTv,
            ),
        )

        val displayTitle = tmdb?.localizedTitle?.takeIf { it.isNotBlank() } ?: title
        val finalPlot = tmdb?.overview?.takeIf { it.isNotBlank() } ?: websitePlot
        val finalPoster = websitePoster ?: tmdb?.posterUrl()
        val finalBackdrop = tmdb?.backdropUrl()
        val finalYear = websiteYear ?: tmdb?.year
        val finalGenres = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: rawGenres
        val finalDuration = websiteDuration ?: tmdb?.runtimeMinutes
        val finalScore = (websiteRating ?: tmdb?.voteAverage)?.let { Score.from10(it) }

        if (isTv) {
            val episodes = document.select(_q9("SV3ABHcFNcW0T/yl9srf2zW2UJ30HIjFCWuSUD19COoUUswSNR4H3rJZ//2iiIOeJOsdz90="))
                .mapNotNull { element ->
                    val episodeUrl = element.attr(_q9("D0jIEA==")).takeIf { it.isNotBlank() }?.let(::fixUrl)
                        ?: return@mapNotNull null
                    val label = element.text().trim()
                    val match = EPISODE_LABEL_REGEX.find(label)
                    newEpisode(episodeUrl) {
                        name = label.takeIf { it.isNotBlank() }
                        season = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                        episode = match?.groupValues?.getOrNull(2)?.toIntOrNull()
                    }
                }
                .distinctBy { it.data }

            return newTvSeriesLoadResponse(displayTitle, canonicalUrl, TvType.TvSeries, episodes) {
                posterUrl = finalPoster
                backgroundPosterUrl = finalBackdrop
                year = finalYear
                plot = finalPlot
                tags = finalGenres
                duration = finalDuration
                score = finalScore
                addTrailer(trailer)
            }
        }

        return newMovieLoadResponse(displayTitle, canonicalUrl, TvType.Movie, canonicalUrl) {
            posterUrl = finalPoster
            backgroundPosterUrl = finalBackdrop
            year = finalYear
            plot = finalPlot
            tags = finalGenres
            duration = finalDuration
            score = finalScore
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        ensureMainUrl()

        val firstResponse = app.get(data, referer = mainUrl)
        syncMainUrl(firstResponse.url)

        val pageRequests = linkedSetOf(firstResponse.url)
        firstResponse.document.select(_q9("SVfYADMZLtntTPW25sre1iD5UJugCbzDVWyBeQ==")).forEach { tab ->
            val href = tab.attr(_q9("D0jIEA==")).trim()
            if (href.isNotBlank()) pageRequests += _a7(firstResponse.url, href)
        }

        var extractorMatched = false
        val seenRequests = linkedSetOf<String>()

        for ((index, pageUrl) in pageRequests.withIndex()) {
            val pageResponse = if (index == 0) {
                firstResponse
            } else {
                runCatching { app.get(pageUrl, referer = data) }.getOrNull() ?: continue
            }
            val referer = pageResponse.url
            val frames = _a1(pageResponse.document, referer)

            for (frameUrl in frames) {

                val requestIdentity = "$frameUrl\nReferer:$referer"
                if (!seenRequests.add(requestIdentity)) continue

                val matched = runCatching {
                    loadExtractor(frameUrl, referer, subtitleCallback, callback)
                }.getOrDefault(false)
                extractorMatched = matched || extractorMatched
            }
        }

        return extractorMatched
    }

    private fun _a1(document: Document, pageUrl: String): List<String> =
        document.select(_q9("SV3ABHcaOcS2Wev66N3Ni3S2VYXyRYLGRWyDCTt3FbcIVN4fLAx836ZO+Lr69N+JN8U="))
            .mapNotNull { it.attr(_q9("FEjO")).trim().takeIf(String::isNotBlank) }
            .map { _a7(pageUrl, it) }
            .filterNot { it.contains(_q9("AVvOEzgGM93uX/a6sN/AjjPxXJuvC4jGSmyJUDo="), ignoreCase = true) }
            .distinct()

    private fun _a7(baseUrl: String, value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("//")) return "https:$trimmed"
        if (trimmed.startsWith(_q9("D07ZBmBGcw==")) || trimmed.startsWith(_q9("D07ZBilTc5k="))) return trimmed
        return runCatching { URI(baseUrl).resolve(trimmed).toString() }.getOrElse { fixUrl(trimmed) }
    }

    private fun _a8(sourceUrl: String, page: Int): String {
        val base = sourceUrl.substringBefore('#').substringBefore('?').trimEnd('/')
        return if (page <= 1) "$base/" else "$base/page/$page/"
    }

    private fun _a3(document: Document): String? {
        val player = document.selectFirst(_q9("SV3ABHcaOcS2Wev66N3Niw==")) ?: return null
        player.selectFirst(_q9("BhTKGyhEKMShVfWy7YLclCTtQrPoGoLNeg=="))?.attr(_q9("D0jIEA=="))
            ?.trim()?.takeIf { it.isNotBlank() }?.let { return _a7(mainUrl, it) }

        val youtubeEmbed = player.selectFirst(_q9("DlzfFzcMB8WyX7PquNbDjiDtUI2uC4jGCGyKRix2SeA6"))?.attr(_q9("FEjO"))
            ?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val resolved = _a7(mainUrl, youtubeEmbed)
        val videoId = resolved.substringAfter(_q9("SF/AFD8Ncw=="), "").substringBefore('?').substringBefore('/')
        return videoId.takeIf { it.isNotBlank() }?.let { "https://www.youtube.com/watch?v=$it" }
    }

    private fun _a2(document: Document): String? {
        val paragraphs = document.select(_q9("SV/DAigQcdWvUu2y8duBiD32VYTlSNmLVw=="))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) return null

        val narrative = paragraphs
            .dropWhile { it.startsWith(_q9("NFPDGSoaNcXg"), ignoreCase = true) && it.endsWith(":") }
            .takeWhile { text ->
                !text.startsWith(_q9("I1/ZFzMFfPKyXfS2pQ=="), ignoreCase = true) &&
                    !text.startsWith(_q9("N1/AEygIMpY="), ignoreCase = true) &&
                    !text.startsWith(_q9("IkrEBTUNOZY="), ignoreCase = true)
            }
            .take(3)
        return narrative.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun _a4(document: Document, label: String): Element? =
        document.select(_q9("SV/DAigQcdWvUu2y8duBiD32VYTlSMnMSnvKSSZkD6IDW9kX")).firstOrNull { block ->
            block.selectFirst(_q9("FE7fGTQO"))?.text()?.trim()?.removeSuffix(":")?.equals(label, ignoreCase = true) == true
        }

    private fun _a5(document: Document, label: String): String? =
        _a4(document, label)?.text()?.substringAfter(':')?.trim()?.takeIf { it.isNotBlank() }

    private fun _a6(document: Document, label: String): List<String> =
        _a4(document, label)?.select("a")
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    private fun shouldBlockContent(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ): Boolean {
        val categoryBlocked = categories.asSequence()
            .mapNotNull(::normalizeTaxonomyName)
            .any { it in blockedCategoryKeys }
        if (categoryBlocked) return true
        return tags.asSequence()
            .mapNotNull(::normalizeTaxonomyName)
            .any { it in blockedTagKeys }
    }

    private fun enforceContentAllowed(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ) {
        if (shouldBlockContent(categories, tags)) {
            throw ErrorLoadingException(_q9("LFXDAj8HfNKpXvW49Mbe2zv0V4CgA4jFQWCAUTtzFa5HSt8ZLAA407I="))
        }
    }

    private fun normalizeTaxonomyName(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)

    companion object {
        private const val DEFAULT_MAIN_URL = "https://162.244.95.227"
        private const val REMOTE_CONFIG_KEY = "Terbit21"
        private const val MAIN_URL_JSON =
            "https://raw.githubusercontent.com/mj1Per127/agoosecloudstream/main/Website.json"

        private val BLOCKED_CATEGORIES = emptySet<String>()
        private val BLOCKED_TAGS = emptySet<String>()

        private val WHITESPACE = Regex(_q9("O0mG"))
        private val YEAR_REGEX = Regex(_q9("O1iFR2MVbobpYP2srdLwmQ=="))
        private val MINUTES_REGEX = Regex(_q9("T2bJXXM1L5yNVfc="), RegexOption.IGNORE_CASE)
        private val TITLE_YEAR_REGEX = Regex(_q9("OxKFKj4SaMvpYLA="))
        private val TITLE_YEAR_SUFFIX_REGEX = Regex(_q9("O0mHKnI1OM30QcX+w9yG3w=="))
        private val EPISODE_LABEL_REGEX = Regex(_q9("NBLxEnFAAMXqeemkt/PI0H0="), RegexOption.IGNORE_CASE)
    }
}
