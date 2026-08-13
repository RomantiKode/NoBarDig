package com.agooseangsa.Terbit21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Locale

class Terbit21 : MainAPI() {
    override var mainUrl = CURRENT_PUBLIC_MAIN_URL
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

            val candidates = (remoteCandidates + CURRENT_PUBLIC_CANDIDATES)
                .mapNotNull(::normalizeHttpBaseUrl)
                .filterNot(::isHistoricalRawIp)
                .distinct()

            var reachableButUnusable: String? = null

            for (candidate in candidates) {
                val response = runCatching { app.get(candidate) }.getOrNull() ?: continue
                if (!response.isSuccessful) continue
                val resolved = normalizeHttpBaseUrl(response.url) ?: continue
                val document = response.document

                if (!document._b5() && !document._b6()) {
                    if (reachableButUnusable == null) reachableButUnusable = resolved
                    continue
                }

                mainUrl = resolved
                mainUrlResolved = true
                return@withLock
            }

            val incompatibleHint = reachableButUnusable?.let {
                " Domain yang dapat dijangkau ($it) tidak menampilkan katalog Terbit21 yang dapat dipetakan."
            }.orEmpty()
            throw ErrorLoadingException(
                _q9("I1XAFzMHfOKlTvu+652d2zXzRoHmSIXOS3yKBC1zFqYTGskfKgwo16td9/m/") +
                    _q9("N1/fFDsbKd/gV/yuv/vJiTbxRtqxSIPCB16CRjp7EqJJUN4ZNEk+36xdubPwws2SOrhQjfIYjsVDaI8KaQ==") +
                    "Fallback snapshot $LEGACY_SNAPSHOT_MAIN_URL tetap dinonaktifkan karena bukti runtime TLS Android." +
                    incompatibleHint
            )
        }
    }

    private fun Document._b5(): Boolean {
        val hasMuviAssets = select(
            _q9("C1PDHQEBLtOmFqTwsNjc1jf3XJzlBpOEU2GCSSxhSaoSTMQGKAZzkZ0QuQ==") +
                _q9("C1PDHQEBLtOmFqTwsNjc1jf3XJzlBpOEV2WSQyB8FegOXsADLABx1a9O/Pi48g==")
        ).isNotEmpty()
        val hasTargetLayout = select(
            _q9("RF3ABHcEPd+uEfW4/suA23r/X5qtBYbCSWSCSjw+RukAV99bKQwuwKVOtKDtztzXdLZVhfJFi8JUfZRBO3sDtA==")
        ).isNotEmpty()
        return hasMuviAssets && hasTargetLayout
    }

    private fun Document._b6(): Boolean {
        val pageText = text().replace(WHITESPACE, " ").trim()
        val brandEvidence = title().contains("Terbit21", ignoreCase = true) ||
            pageText.contains("Terbit21", ignoreCase = true)
        if (!brandEvidence) return false

        val searchEvidence = select(
            _q9("DlTdAy4yLNqhX/y/8MPIniayD8/DCZXCB0OSQDx+QZpLGsQYKhwo7bBQ+LT6x8OXMP1Awr1PruZja8B5ZTIPqRdP2S00CDHT/U/E")
        ).isNotEmpty()
        val catalogEvidence = pageText.contains(_q9("IX/sIg87GfLgcdaB1uo="), ignoreCase = true) ||
            pageText.contains(_q9("IXPhO3o8DPqPfd33y+r+uRXKZw=="), ignoreCase = true) ||
            pageText.contains(_q9("IXPhO3orGeSEfcqWzeTttXTMc6DVJg=="), ignoreCase = true)
        val watchEvidence = pageText.contains(_q9("M1XDAjUH"), ignoreCase = true)
        val oldCards = select(_q9("RF3ABHcEPd+uEfW4/suMmibsW4vsDcnCU2yKCGlzFLMOWcETdAAo060=")).size >= 2

        return oldCards || catalogEvidence || (searchEvidence && watchEvidence)
    }

    protected fun syncMainUrl(responseUrl: String?) {
        normalizeHttpBaseUrl(responseUrl)
            ?.takeUnless(::isHistoricalRawIp)
            ?.let { mainUrl = it }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(REMOTE_CONFIG_KEY) ?: return emptyList()
        return (0 until array.length())
            .map { array.optString(it) }
            .mapNotNull(::normalizeHttpBaseUrl)
            .filterNot(::isHistoricalRawIp)
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

    private fun isHistoricalRawIp(url: String): Boolean = runCatching {
        URI(url).host == LEGACY_SNAPSHOT_HOST
    }.getOrDefault(false)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        val sourceUrl = fixUrl(request.data)
        val pageUrl = _b4(sourceUrl, page)
        val response = app.get(pageUrl, referer = mainUrl)
        syncMainUrl(response.url)
        val document = response.document
        val items = _a0(document)

        val hasNext = document.select(
            _q9("BhTDEyIdcsahW/z68drBmTHqQcSgRpfKQGCJRT17CalHW4MYPxEo7ahO/LHCg4yaD+pXhL0GgtNTVLxMO3cAmg==")
        ).isNotEmpty()
        return newHomePageResponse(request.name, items, hasNext = hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        if (query.isBlank()) return emptyList()

        val advancedResponse = runCatching {
            app.get(
                mainUrl,
                params = mapOf(
                    "s" to query,
                    _q9("FF/MBDkB") to _q9("Bl7bFzQKOdI="),
                ),
                referer = mainUrl,
            )
        }.getOrNull()

        if (advancedResponse != null) {
            syncMainUrl(advancedResponse.url)
            val advancedItems = _a0(advancedResponse.document)
            if (advancedItems.isNotEmpty()) return advancedItems
        }

        val standardResponse = app.get(
            mainUrl,
            params = mapOf("s" to query),
            referer = mainUrl,
        )
        syncMainUrl(standardResponse.url)
        return _a0(standardResponse.document)
    }

    private fun _a0(document: Document): List<SearchResponse> {
        val oldCards = document.select(_q9("RF3ABHcEPd+uEfW4/suMmibsW4vsDcnCU2yKCGlzFLMOWcETdAAo060="))
            .mapNotNull(::_a1)
            .distinctBy { it.url }
        if (oldCards.isNotEmpty()) return oldCards

        val responses = linkedMapOf<String, SearchResponse>()
        val anchors = document.select(
            _q9("DwiNFwEBLtOmYbX395yMmg/wQI3mNcuLTz3HRRJ6FKIBZ4FW") +
                _q9("PFnBFykadou0Ve27+vKMmg/wQI3mNcuLfGqLRTphTPoJW8ATB0k97ahO/LHC")
        )

        for (anchor in anchors) {
            val response = _a2(anchor) ?: continue
            responses.putIfAbsent(response.url, response)
        }
        return responses.values.toList()
    }

    private fun _a1(card: Element): SearchResponse? {
        val anchor = card.selectFirst(_q9("DwiDEzQdLs/tSPCj88qMmg/wQI3mNQ==")) ?: return null
        return _a3(anchor, card, targetCard = true)
    }

    private fun _a2(anchor: Element): SearchResponse? {
        val container = _a4(anchor)
        return _a3(anchor, container, targetCard = false)
    }

    private fun _a3(
        anchor: Element,
        container: Element,
        targetCard: Boolean,
    ): SearchResponse? {
        val rawHref = anchor.attr(_q9("D0jIEA==")).trim().takeIf { it.isNotBlank() } ?: return null
        val url = _b3(mainUrl, rawHref)
        if (!isLikelyDetailUrl(url)) return null

        val title = anchor.text().trim().replace(WHITESPACE, " ").takeIf { it.isNotBlank() }
            ?: return null
        if (title.length < 3 || title in NON_TITLE_LABELS) return null

        val cardText = container.text().replace(WHITESPACE, " ")
        if (!targetCard &&
            YEAR_REGEX.find(title) == null &&
            !cardText.contains(_q9("M1XDAjUH"), ignoreCase = true) &&
            !cardText.contains("HD", ignoreCase = true) &&
            !cardText.contains(_q9("MH/v"), ignoreCase = true)
        ) return null

        val categories = container.select(
            _q9("SV3ABHcEM8CpWbS48Y/N13T5aYDyDYGBGi7IQyx8FKJIHfBaeggH3rJZ//2iiIOYNexXj+8anoQAVA==")
        ).map { it.text().trim() }.filter { it.isNotBlank() }
        if (shouldBlockContent(categories = categories)) return null

        val poster = container.selectFirst(
            _q9("DlfKLT4IKNftT+u0woOMkjn/aYzhHIaGS2idXWRhFKQ6Fo0fNw4H0qFI+Prw3cWcPfZThN1Ex8JKbrxXO3E7")
        )?.let(::extractImageUrl)

        val isTv = runCatching { URI(url).path.orEmpty() }.getOrDefault(url)
            .contains(_q9("SE7bWQ=="), ignoreCase = true) ||
            container.attr(_q9("Dk7IGy4QLNM=")).contains("TV", ignoreCase = true)

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

    private fun _a4(anchor: Element): Element {
        var current: Element? = anchor.parent()
        var fallback: Element = current ?: anchor
        repeat(5) {
            val node = current ?: return fallback
            fallback = node
            val text = node.text()
            if (node.select(_q9("DlfK")).isNotEmpty() ||
                text.contains(_q9("M1XDAjUH"), ignoreCase = true) ||
                node.hasClass(_q9("Dk7IGw==")) ||
                node.tagName().equals(_q9("BkjZHzkFOQ=="), ignoreCase = true)
            ) return node
            current = node.parent()
        }
        return fallback
    }

    private fun extractImageUrl(element: Element): String? {
        val raw = listOf(_q9("A1vZF3caLtU="), _q9("A1vZF3cFPcy5Eeql/A=="), _q9("A1vZF3cGLt+nVfe28w=="), _q9("FEjO"))
            .firstNotNullOfOrNull { key -> element.attr(key).trim().takeIf { it.isNotBlank() } }
            ?: return null
        if (raw.startsWith(_q9("A1vZF2A="), ignoreCase = true)) return null
        return _b3(mainUrl, raw)
    }

    private fun isLikelyDetailUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != _q9("D07ZBg==") && scheme != _q9("D07ZBik=")) return false
        val path = uri.path.orEmpty().trimEnd('/')
        if (path.isBlank()) return false

        val lowerPath = path.lowercase(Locale.ROOT)
        if (lowerPath == _q9("SE7b") || lowerPath == _q9("SFfCADMM") || lowerPath == _q9("SFzEGjc=")) return false
        if (DETAIL_EXCLUDED_PATHS.any { lowerPath == it || lowerPath.startsWith("$it/") }) return false
        if (lowerPath.contains(_q9("SErMET9G")) || lowerPath.contains(_q9("SFzIEz4=")) || lowerPath.contains(_q9("SE3dWw=="))) return false
        if (lowerPath.endsWith(_q9("SVDdEQ==")) || lowerPath.endsWith(_q9("SVDdEz0=")) ||
            lowerPath.endsWith(_q9("SUrDEQ==")) || lowerPath.endsWith(_q9("SU3IFCo="))
        ) return false
        return true
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val requestUrl = _b7(url)
        val response = app.get(requestUrl, referer = mainUrl)
        syncMainUrl(response.url)
        val document = response.document
        val responseUrl = response.url

        if (document.body()?.hasClass(_q9("FFPDETYMcdOwVeq4+8o=")) == true ||
            runCatching { URI(responseUrl).path.orEmpty().contains(_q9("SF/dBXU=")) }.getOrDefault(false)
        ) {
            val parent = document.selectFirst(
                _q9("SV3ABHcFNcW0T/yl9srf2zW2UJ30HIjFfGGVQS84W+BITttZfTRwlqFn8aX6yYbGc7dGnq9Pug==")
            )?.attr(_q9("D0jIEA=="))?.takeIf { it.isNotBlank() }
            if (parent != null) return load(_b3(responseUrl, parent))
        }

        val canonicalUrl = _b7(
            document.selectFirst(_q9("C1PDHQEbOdr9X/i58MHFmDX0b7PoGoLNeg=="))?.attr(_q9("D0jIEA=="))
                ?.takeIf { it.isNotBlank() }
                ?.let { _b3(responseUrl, it) }
                ?: responseUrl
        )
        val title = extractTitle(document)
            ?: throw ErrorLoadingException(_q9("LU/JAzZJKN+kXfL3+8bYnjntWYnu"))

        val episodeElements = collectEpisodeElements(document)
        val isTv = document.body()?.hasClass(_q9("FFPDETYMccK2")) == true ||
            runCatching { URI(canonicalUrl).path.orEmpty().contains(_q9("SE7bWQ==")) }.getOrDefault(false) ||
            episodeElements.isNotEmpty()

        val rawGenres = detailLinks(document, _q9("IF/DBD8=")).ifEmpty { genericTaxonomyLinks(document, _q9("AF/DBD8=")) }
        enforceContentAllowed(categories = rawGenres)

        val websiteYear = firstDetailText(document, _q9("Pl/MBA=="), _q9("M1vFAzQ="), _q9("NV/BEzsaOdI="), _q9("NVPBHyk="))
            ?.let(YEAR_REGEX::find)?.value?.toIntOrNull()
            ?: TITLE_YEAR_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val websiteDuration = parseDuration(
            firstDetailText(document, _q9("I0/fFy4AM9g="), _q9("I0/fFykA"), _q9("NU/DAjMEOQ=="))
                ?: document.selectFirst(_q9("PFPZEzcZLtmwAf2i7c7Ykjv2bw=="))?.text()
        )
        val websitePlot = _a9(document)
        val websitePoster = document.selectFirst(_q9("Cl/ZFwEZLtmwWeuj5pLDnG7xX4nnDbrwRGaJUCx8Epo="))
            ?.attr(_q9("BFXDAj8HKA=="))?.takeIf { it.isNotBlank() }?.let { _b3(responseUrl, it) }
            ?: document.selectFirst(_q9("PFPZEzcZLtmwAfC6/sjJpnTxX4+sSMnbSHqTQTsyD6oAFo1YLgEp26JS+L7zj8WWMw=="))?.let(::extractImageUrl)
        val websiteRating = extractWebsiteRating(document)
        val trailer = _b0(document, responseUrl)

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
            val episodes = episodeElements.mapNotNull { element ->
                val episodeUrl = element.attr(_q9("D0jIEA==")).takeIf { it.isNotBlank() }
                    ?.let { _b3(responseUrl, it) }
                    ?.let(::_b7)
                    ?: return@mapNotNull null
                val label = element.text().trim().replace(WHITESPACE, " ")
                val seasonEpisode = parseSeasonEpisode(label)
                newEpisode(episodeUrl) {
                    name = label.takeIf { it.isNotBlank() }
                    season = seasonEpisode.first
                    episode = seasonEpisode.second
                }
            }.distinctBy { it.data }

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

        val requestData = _b7(data)
        val firstResponse = app.get(requestData, referer = mainUrl)
        syncMainUrl(firstResponse.url)

        val pageRequests = linkedSetOf(firstResponse.url)
        _a5(firstResponse.document, firstResponse.url).forEach(pageRequests::add)

        val visited = linkedSetOf<String>()
        var resolvedAny = false

        for ((index, pageUrl) in pageRequests.withIndex()) {
            val pageResponse = if (index == 0) {
                firstResponse
            } else {
                runCatching { app.get(_b7(pageUrl), referer = requestData) }
                    .getOrNull() ?: continue
            }
            val referer = pageResponse.url
            val candidates = _a6(pageResponse.document, referer)

            for (candidate in candidates) {
                val resolved = _a7(
                    url = candidate,
                    referer = referer,
                    depth = 0,
                    visited = visited,
                    subtitleCallback = subtitleCallback,
                    callback = callback,
                )
                resolvedAny = resolved || resolvedAny
            }
        }

        return resolvedAny
    }

    private suspend fun _a7(
        url: String,
        referer: String,
        depth: Int,
        visited: MutableSet<String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val normalized = _a8(url, referer) ?: return false
        val requestIdentity = "$normalized\nReferer:$referer"
        if (!visited.add(requestIdentity)) return false

        if (isDirectMediaUrl(normalized)) {
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = normalized,
                ) {
                    this.referer = referer
                    this.quality = Qualities.Unknown.value
                }
            )
            return true
        }

        var builtInMediaCount = 0
        val builtInMatched = runCatching {
            loadExtractor(normalized, referer, subtitleCallback) { link ->
                builtInMediaCount += 1
                callback(link)
            }
        }.getOrDefault(false)
        if (builtInMediaCount > 0) return true

        if (builtInMatched && depth >= MAX_WRAPPER_DEPTH) return false
        if (depth >= MAX_WRAPPER_DEPTH) return false

        val wrapperResponse = runCatching {
            app.get(_b7(normalized), referer = _b7(referer))
        }.getOrNull() ?: return false
        if (!wrapperResponse.isSuccessful) return false

        val wrapperReferer = wrapperResponse.url
        val nested = _a6(wrapperResponse.document, wrapperReferer)
        var nestedResolved = false
        for (next in nested) {
            val result = _a7(
                url = next,
                referer = wrapperReferer,
                depth = depth + 1,
                visited = visited,
                subtitleCallback = subtitleCallback,
                callback = callback,
            )
            nestedResolved = result || nestedResolved
        }
        return nestedResolved
    }

    private fun _a5(document: Document, pageUrl: String): List<String> {
        val urls = linkedSetOf<String>()
        document.select(_q9("SVfYADMZLtntTPW25sre1iD5UJugCbzDVWyBeQ==")).forEach { element ->
            element.attr(_q9("D0jIEA==")).trim().takeIf { it.isNotBlank() }
                ?.let { urls += _b3(pageUrl, it) }
        }

        document.select(_q9("BmHFBD8PAZrgZ/22686Bjib0b8SgM4PKU2jKTDt3AJo=")).forEach { element ->
            val marker = listOf(
                element.text(),
                element.className(),
                element.id(),
            ).joinToString(" ").lowercase(Locale.ROOT)
            if (PLAYER_CONTROL_KEYWORDS.none(marker::contains)) return@forEach

            val value = listOf(_q9("D0jIEA=="), _q9("A1vZF3ccLto="), _q9("A1vZF3cBLtOm"))
                .firstNotNullOfOrNull { attr -> element.attr(attr).trim().takeIf { it.isNotBlank() } }
                ?: return@forEach
            _a8(value, pageUrl)?.let(urls::add)
        }
        return urls.take(MAX_SERVER_PAGES)
    }

    private fun _a6(document: Document, pageUrl: String): List<String> {
        val candidates = linkedSetOf<String>()

        document.select(_q9("DlzfFzcMB8WyX8T7v8nemjn9aZvyC7o=")).forEach { frame ->
            frame.attr(_q9("FEjO")).trim().takeIf { it.isNotBlank() }
                ?.let { _a8(it, pageUrl) }
                ?.takeUnless(::isExcludedPlaybackUrl)
                ?.let(candidates::add)
        }

        document.select(_q9("EVPJEzUyL8SjYbX36cbInju4QYf1GoTOfHqVRxQ+RqYSXsQZARou1Z0Qubbqy8WUdOtdnfILgvBUe4R5")).forEach { media ->
            media.attr(_q9("FEjO")).trim().takeIf { it.isNotBlank() }
                ?.let { _a8(it, pageUrl) }
                ?.let(candidates::add)
        }

        document.select(_q9("PF7MAjtEL8SjYbX3xMvNjzW1R5rsNcuLfG2GUCg/A6oFX8krdkkH0qFI+Prvw82CMepvxKAzg8pTaMpIIHwNmg==")).forEach { element ->
            for (attr in PLAYBACK_DATA_ATTRIBUTES) {
                val value = element.attr(attr).trim().takeIf { it.isNotBlank() } ?: continue
                val normalized = _a8(value, pageUrl) ?: continue
                if (!isPotentialPlaybackUrl(normalized)) continue
                if (!isExcludedPlaybackUrl(normalized)) candidates += normalized
            }
        }

        document.select(_q9("Cl/ZFwEBKMKwEfym6sbahWnqV47yDZTDelKESydmA6kTZw==")).forEach { meta ->
            META_REFRESH_URL.find(meta.attr(_q9("BFXDAj8HKA==")))?.groupValues?.getOrNull(1)
                ?.trim(' ', '\'', '"')
                ?.let { _a8(it, pageUrl) }
                ?.takeUnless(::isExcludedPlaybackUrl)
                ?.let(candidates::add)
        }

        document.select(_q9("FFnfHyod")).forEach { script ->
            val scriptText = script.data().ifBlank { script.html() }
                .replace("\\/", "/")
                .replace(_q9("O0+dRmgv"), "/", ignoreCase = true)
            SCRIPT_MEDIA_URL.findAll(scriptText).forEach { match ->
                _a8(match.value.trim(' ', '\'', '"'), pageUrl)
                    ?.takeIf(::isPotentialPlaybackUrl)
                    ?.takeUnless(::isExcludedPlaybackUrl)
                    ?.let(candidates::add)
            }
            JS_LOCATION_URL.findAll(scriptText).forEach { match ->
                match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { _a8(it, pageUrl) }
                    ?.takeUnless(::isExcludedPlaybackUrl)
                    ?.let(candidates::add)
            }
        }

        return candidates.take(MAX_PLAYER_CANDIDATES)
    }

    private fun _a8(value: String, baseUrl: String): String? {
        val trimmed = value.trim()
            .replace(_q9("QVvABmE="), "&")
            .takeIf { it.isNotBlank() } ?: return null
        if (trimmed.startsWith(_q9("DVvbFykKLt+wSKM="), ignoreCase = true) ||
            trimmed.startsWith(_q9("A1vZF2A="), ignoreCase = true) ||
            trimmed.startsWith(_q9("BVbCFGA="), ignoreCase = true) ||
            trimmed == "#"
        ) return null
        return _b3(baseUrl, trimmed)
            .takeIf { it.startsWith(_q9("D07ZBmBGcw==")) || it.startsWith(_q9("D07ZBilTc5k=")) }
    }

    private fun isDirectMediaUrl(url: String): Boolean {
        val clean = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return DIRECT_MEDIA_EXTENSIONS.any(clean::endsWith)
    }

    private fun isPotentialPlaybackUrl(url: String): Boolean {
        if (isDirectMediaUrl(url)) return true
        val lower = url.lowercase(Locale.ROOT)
        return PLAYER_URL_KEYWORDS.any(lower::contains)
    }

    private fun isExcludedPlaybackUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return EXCLUDED_PLAYER_HOST_HINTS.any(lower::contains)
    }

    private fun _b3(baseUrl: String, value: String): String {
        val trimmed = value.trim()
        val resolved = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith(_q9("D07ZBmBGcw==")) || trimmed.startsWith(_q9("D07ZBilTc5k=")) -> trimmed
            else -> runCatching { URI(baseUrl).resolve(trimmed).toString() }.getOrElse { fixUrl(trimmed) }
        }
        return _b7(resolved)
    }

    private fun _b7(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        if (uri.host != LEGACY_SNAPSHOT_HOST) return url

        val activeOrigin = normalizeHttpBaseUrl(mainUrl) ?: return url
        val path = uri.rawPath.orEmpty().ifBlank { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return activeOrigin.trimEnd('/') + path + query + fragment
    }

    private fun _b4(sourceUrl: String, page: Int): String {
        val base = sourceUrl.substringBefore('#').substringBefore('?').trimEnd('/')
        return if (page <= 1) "$base/" else "$base/page/$page/"
    }

    private fun extractTitle(document: Document): String? {
        val heading = document.selectFirst(
            _q9("DwuDEzQdLs/tSPCj88r3kiD9X5jyB5eWSWiKQRQ+Rq9WFMgYLhslm7RV7bv6g4yTZcNbnOUFl9lIedpKKH8DmksaxUc=")
        )?.text()?.trim()?.replace(WHITESPACE, " ")
        if (!heading.isNullOrBlank()) return heading

        return document.selectFirst(_q9("Cl/ZFwEZLtmwWeuj5pLDnG7sW5zsDbrwRGaJUCx8Epo="))?.attr(_q9("BFXDAj8HKA=="))
            ?.trim()?.replace(WHITESPACE, " ")
            ?.substringBefore(_q9("RxeNIj8bPt+0Dqg="))
            ?.takeIf { it.isNotBlank() }
    }

    private fun collectEpisodeElements(document: Document): List<Element> =
        document.select(
            _q9("SV3ABHcFNcW0T/yl9srf2zW2UJ30HIjFCWuSUD19COoUUswSNR4H3rJZ//2iiIOeJOsdz91Exw==") +
                _q9("SV3ABHcFNcW0T/yl9srf2zXDWprlDs2WACaCVDo9QZpLGswtMhs50OoBvvj639/Uc8U=")
        ).distinctBy { it.attr(_q9("D0jIEA==")) }

    private fun parseSeasonEpisode(label: String): Pair<Int?, Int?> {
        EPISODE_LABEL_REGEX.find(label)?.let { match ->
            return match.groupValues.getOrNull(1)?.toIntOrNull() to
                match.groupValues.getOrNull(2)?.toIntOrNull()
        }
        EPISODE_ONLY_REGEX.find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { episode ->
            return null to episode
        }
        return null to null
    }

    private fun _b0(document: Document, pageUrl: String): String? {
        document.selectFirst(_q9("BhTKGyhEKMShVfWy7YLclCTtQrPoGoLNeg=="))?.attr(_q9("D0jIEA=="))
            ?.trim()?.takeIf { it.isNotBlank() }?.let { return _b3(pageUrl, it) }

        document.selectFirst(
            _q9("BmHFBD8PdovnRfai69rOnnr7XYWvH4bfRGHAeWUyB5wPSMgQcFR7z69J7aKxzcnUc8UeyA==") +
                _q9("DlzfFzcMB8WyX7PquNbDjiDtUI2uC4jGCGyKRix2SeA6")
        )?.let { element ->
            val raw = element.attr(if (element.hasAttr(_q9("D0jIEA=="))) _q9("D0jIEA==") else _q9("FEjO"))
                .trim().takeIf { it.isNotBlank() } ?: return@let
            val resolved = _b3(pageUrl, raw)
            if (resolved.contains(_q9("HlXYAi8LOZijU/T4+sLOnjC3"))) {
                val videoId = resolved.substringAfter(_q9("SF/AFD8Ncw=="), "")
                    .substringBefore('?').substringBefore('/')
                if (videoId.isNotBlank()) return "https://www.youtube.com/watch?v=$videoId"
            }
            return resolved
        }
        return null
    }

    private fun _a9(document: Document): String? {
        val targetParagraphs = document.select(_q9("SV/DAigQcdWvUu2y8duBiD32VYTlSNmLVw=="))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        if (targetParagraphs.isNotEmpty()) {
            val narrative = targetParagraphs
                .dropWhile { it.startsWith(_q9("NFPDGSoaNcXg"), ignoreCase = true) && it.endsWith(":") }
                .takeWhile { text ->
                    !text.startsWith(_q9("I1/ZFzMFfPKyXfS2pQ=="), ignoreCase = true) &&
                        !text.startsWith(_q9("N1/AEygIMpY="), ignoreCase = true) &&
                        !text.startsWith(_q9("IkrEBTUNOZY="), ignoreCase = true)
                }
                .take(3)
                .joinToString("\n\n")
            if (narrative.isNotBlank()) return narrative
        }

        val generic = document.select(
            _q9("PFPZEzcZLtmwAf2y7MzekiTsW4fuNcuLCXqOSiZiFa4UFo1YKRAy2bBP8KSzj4KfMetRmukYk8JIZ8sE") +
                _q9("SV/DAigQcdWvUu2y8duMi3i4HJjvG5OGRGaJUCx8EucXFo0XKB011axZuac=")
        ).map { it.text().trim().replace(WHITESPACE, " ") }
            .filter { text ->
                text.length >= MIN_PLOT_LENGTH &&
                    !text.contains(_q9("M1/fFDMdbofgEbmE+sTNiTX2VQ=="), ignoreCase = true) &&
                    !text.startsWith(_q9("MF/PBTMdOZaBUO2y7cHNjz3+"), ignoreCase = true)
            }
            .take(3)
            .joinToString("\n\n")
        if (generic.isNotBlank()) return generic

        return document.selectFirst(_q9("Cl/ZFwEHPdulAf2y7MzekiTsW4fuNbzISGeTQSdmOw=="))?.attr(_q9("BFXDAj8HKA=="))
            ?.trim()?.takeIf { it.length >= MIN_PLOT_LENGTH }
    }

    private fun extractWebsiteRating(document: Document): Double? {
        document.selectFirst(_q9("PFPZEzcZLtmwAeu268bCnAL5Xp3lNQ=="))?.let { element ->
            val value = element.attr(_q9("BFXDAj8HKA==")).ifBlank { element.text() }
                .trim().toDoubleOrNull()?.takeIf { it in 0.1..10.0 }
            if (value != null) return value
        }

        val shortMetaText = document.select(
            _q9("SUjMAjMHO5rgZ/q7/tzf0WnqU5zpBoD2Cym8RyVzFbRNB8QbPgsBmuBQ8Pu/gcGeIPkeyNsLi8pUes0ZJHcSpjo=")
        ).asSequence()
            .map { it.text().trim().replace(WHITESPACE, " ") }
            .filter { it.length <= 160 }
        for (text in shortMetaText) {
            val value = IMDB_RATING_REGEX.find(text)?.groupValues?.getOrNull(1)
                ?.toDoubleOrNull()?.takeIf { it in 0.1..10.0 }
            if (value != null) return value
        }
        return null
    }

    private fun parseDuration(value: String?): Int? {
        val text = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        MINUTES_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        val hour = HOURS_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val minute = HOUR_MINUTES_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (hour != null) return hour * 60 + (minute ?: 0)
        return null
    }

    private fun firstDetailText(document: Document, vararg labels: String): String? {
        for (label in labels) {
            _b2(document, label)?.let { return it }
            genericLabelText(document, label)?.let { return it }
        }
        return null
    }

    private fun _b1(document: Document, label: String): Element? =
        document.select(_q9("SV/DAigQcdWvUu2y8duBiD32VYTlSMnMSnvKSSZkD6IDW9kX")).firstOrNull { block ->
            block.selectFirst(_q9("FE7fGTQO"))?.text()?.trim()?.removeSuffix(":")
                ?.equals(label, ignoreCase = true) == true
        }

    private fun _b2(document: Document, label: String): String? =
        _b1(document, label)?.text()?.substringAfter(':')?.trim()?.takeIf { it.isNotBlank() }

    private fun detailLinks(document: Document, label: String): List<String> =
        _b1(document, label)?.select("a")
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    private fun genericLabelText(document: Document, label: String): String? {
        val prefix = label.lowercase(Locale.ROOT)
        return document.select(
            _q9("C1OBVipFfO2jUPik7IWRljHsU7WsSLzIS2iUV2MvAqITW8QaB0V87aNQ+KTshZGSOv5dtQ==")
        ).asSequence()
            .map { it.text().trim().replace(WHITESPACE, " ") }
            .filter { it.length in 3..300 }
            .firstOrNull { text ->
                val lower = text.lowercase(Locale.ROOT)
                lower.startsWith("$prefix:") || lower.startsWith("$prefix ")
            }
            ?.substringAfter(':', missingDelimiterValue = "")
            ?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun genericTaxonomyLinks(document: Document, pathKey: String): List<String> =
        document.select("a[href*='/$pathKey/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

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

        private const val CURRENT_PUBLIC_MAIN_URL = "https://terbit21.net"
        private val CURRENT_PUBLIC_CANDIDATES = listOf(
            CURRENT_PUBLIC_MAIN_URL,
            _q9("D07ZBilTc5m0Weu19tueynr1V4zpCQ=="),
            _q9("D07ZBilTc5m0Weu19tueynr3QI8="),
        )
        private const val LEGACY_SNAPSHOT_MAIN_URL = "https://162.244.95.227"
        private const val LEGACY_SNAPSHOT_HOST = "162.244.95.227"
        private const val REMOTE_CONFIG_KEY = "Terbit21"
        private const val MAIN_URL_JSON =
            "https://raw.githubusercontent.com/mj1Per127/agoosecloudstream/main/Website.json"

        private val BLOCKED_CATEGORIES = emptySet<String>()
        private val BLOCKED_TAGS = emptySet<String>()

        private val WHITESPACE = Regex(_q9("O0mG"))
        private val YEAR_REGEX = Regex(_q9("O1iFR2MVbobpYP2srdLwmQ=="))
        private val MINUTES_REGEX = Regex(_q9("T2bJXXM1L5zoA6Oa9sHQtjH2W5yp"), RegexOption.IGNORE_CASE)
        private val HOURS_REGEX = Regex(_q9("T2bJXXM1L5zoA6Od/sLQszvtQJTIB5LZVHWvVjVaFLRO"), RegexOption.IGNORE_CASE)
        private val HOUR_MINUTES_REGEX = Regex(
            _q9("TwWXPDsEIP6vSeur18DZiSfkepr8IJXYDlWUDmFOAuxOZt5cclZm+6VS8KPj4sWVKNVbhvUcgtgONg=="),
            RegexOption.IGNORE_CASE,
        )
        private val TITLE_YEAR_REGEX = Regex(_q9("OxKFKj4SaMvpYLA="))
        private val TITLE_YEAR_SUFFIX_REGEX = Regex(_q9("O0mHKnI1OM30QcX+w9yG3w=="))
        private val EPISODE_LABEL_REGEX = Regex(
            _q9("NBKSTD8IL9muFaaL7IWEpzCzG7TzQs+UHUyXV3ZuI7cOScISP0AAxeoUxbO0hg=="),
            RegexOption.IGNORE_CASE,
        )
        private val EPISODE_ONLY_REGEX = Regex(_q9("TwWXMyoaY8qFTPCk8MvJ0gjrGMDcDMyC"), RegexOption.IGNORE_CASE)
        private val IMDB_RATING_REGEX = Regex(
            _q9("TwWXPxctPsqSXe2+8ciFpyeyadLcRbqUe3rNDBV2Te9YAPFYBg13n/8V"),
            RegexOption.IGNORE_CASE,
        )
        private val META_REFRESH_URL = Regex(_q9("TwXEXy8bMOqzFqSL7IWEoAqjb8OpTA=="))
        private val SCRIPT_MEDIA_URL = Regex(
            _q9("D07ZBilWZpnvZ8eL7IiOx2rFGZSvR7z1e3rABnUsO+w=")
        )
        private val JS_LOCATION_URL = Regex(
            _q9("TwXEX3JWZsGpUv246POC0mv0XYvhHI7ESSHYHhU8DrUCXIRJBhp2i5xPs4y4jfHTD8YVyt1DzvAAK7o=")
        )

        private val DETAIL_EXCLUDED_PATHS = setOf(
            _q9("SF3IGCgM"), _q9("SFnCAzQdLs8="), _q9("SFvOAjUb"), _q9("SFvOAjUbLw=="), _q9("SF7EBD8KKNmy"), _q9("SE7MEQ=="), _q9("SFnMAj8OM8S5"),
            _q9("SEPIFyg="), _q9("SEjIGj8IL9PtRfy27Q=="), _q9("SEnIFygKNA=="), _q9("SFvYAjIGLg=="), _q9("SFnCGzcMMsKz"), _q9("SF/dBQ=="),
        )
        private val NON_TITLE_LABELS = setOf(
            _q9("M1XDAjUH"), _q9("IVPBG3olPd+uUuC2"), _q9("L1XAEw=="), _q9("JV/fFzQNPQ=="), _q9("M0jMHzYMLg=="), _q9("I1XaGDYGPdI="), _q9("MlTJAzIIMg==")
        )
        private val PLAYER_CONTROL_KEYWORDS = listOf(
            _q9("FF/fAD8b"), _q9("F0jCADMNOcQ="), _q9("FFXYBDkM"), _q9("F1bMDz8b"), _q9("AU/BGikKLtOlUg=="), _q9("FE7fEzsE"), _q9("E1XDAjUH")
        )
        private val PLAYBACK_DATA_ATTRIBUTES = listOf(
            _q9("A1vZF3caLtU="), _q9("A1vZF3ccLto="), _q9("A1vZF3cMMdSlWA=="), _q9("A1vZF3cZMNe5Wes="), _q9("A1vZF3cFNdir")
        )
        private val DIRECT_MEDIA_EXTENSIONS = listOf(
            _q9("SVeeA2I="), _q9("SVfdQg=="), _q9("SVfdEg=="), _q9("SU3IFDc="), _q9("SVfGAA==")
        )
        private val PLAYER_URL_KEYWORDS = listOf(
            _q9("SF/AFD8N"), _q9("SErBFyMMLg=="), _q9("SEnZBD8IMQ=="), _q9("F1bMDz8bcsaoTA=="), _q9("AlfPEz5HLN6w"), _q9("EVPJEzVHLN6w"), _q9("FFXYBDkMcsaoTA==")
        )
        private val EXCLUDED_PLAYER_HOST_HINTS = listOf(
            _q9("AVvOEzgGM93uX/a6sN/AjjPxXJuvC4jGSmyJUDo="), _q9("HlXYAi8LOZijU/T4+sLOnjA="), _q9("HlXYAi9HPtPv"), _q9("A1XYFDYMP9qpX/L58crY"),
            _q9("AFXCETYML8+uWPC0/tvFlDq2UYft"), _q9("AFXCETYMcdeuXfWu68bPiHr7XYU="), _q9("AFXCETYMKNenUfi5/sjJiXr7XYU=")
        )

        private const val MAX_SERVER_PAGES = 8
        private const val MAX_PLAYER_CANDIDATES = 24
        private const val MAX_WRAPPER_DEPTH = 2
        private const val MIN_PLOT_LENGTH = 60
    }
}
