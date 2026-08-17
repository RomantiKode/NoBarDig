package com.agooseangsa.Layarkaca21

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Locale

class Layarkaca21 : MainAPI() {
    private val _j2 = _j0

    override var mainUrl = _j2._j4
    override var name = _q9("ZK54W4dUbU2bofzDJg==")
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val hasMainPage = true
    override val mainPage = mainPageOf(
        *_j2.homepage
            .map { item -> "${item.source}|${item.key}" to item.title }
            .toTypedArray(),
    )

    private val _a0 = Mutex()
    private var _a1 = _j2._j4
    private var _a2 = _j2._j5
    private var _a3 = false
    private var _a4 = false

    private val _a5 by lazy(LazyThreadSafetyMode.NONE) {
        _j2._j18.mapNotNull(::_c1).toSet()
    }
    private val _a6 by lazy(LazyThreadSafetyMode.NONE) {
        _j2._j19.mapNotNull(::_c1).toSet()
    }

    private val modularPlaybackTrace = _l5(
        moduleName = _q9("ZK54W4cfR0+Z8u0="),
        enabled = _j2.diagnostics.enabled,
    )
    private val modularMetadataTrace = _l7(
        moduleName = _q9("ZK54W4cfR0+Z8u0="),
        enabled = _j2.diagnostics.enabled,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        _a7()
        val parts = request.data.split('|', limit = 2)
        if (parts.size != 2) return newHomePageResponse(request, emptyList(), false)

        val source = parts[0]
        val widgetType = parts[1]
        val base = if (source == _q9("W6pzU5AH")) _a1 else _a2
        val response = runCatching { app.get(base) }.getOrElse {
            return newHomePageResponse(request, emptyList(), false)
        }
        _a8(response.url, response.document)

        val widget = response.document.selectFirst(_j2._j3(widgetType))
            ?: return newHomePageResponse(request, emptyList(), false)
        val resolvedBase = _c2(response.url) ?: base
        val items = _b0(widget, resolvedBase)
        return newHomePageResponse(request, items, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        _a7()
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<SearchResponse>()
        for (base in listOf(_a2, _a1).distinct()) {
            val response = runCatching {
                app.get(base + _j2._j8, params = mapOf(_j2._j9 to query.trim()))
            }.getOrNull() ?: continue
            if (!response.isSuccessful) continue
            _a8(response.url, response.document)
            val resolvedBase = _c2(response.url) ?: base
            results += _b0(response.document, resolvedBase)
        }
        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        _a7()
        val requestUrl = _f6(url)
        val response = app.get(requestUrl)
        if (!response.isSuccessful) throw ErrorLoadingException(_q9("b65mW5lUS0mVoqmadrLynP9rxNSKuFS8oPJ7ufq5Oc4a/g=="))
        _a8(response.url, response.document)

        val document = response.document
        val finalUrl = response.url
        _n4(
            stage = _l2.DETAIL_READABLE,
            result = _l3.PASS,
            requestUrl = finalUrl,
            resolver = _q9("WL1uTJwQQ17YpLmFdvv62cNe+eiFsHqI"),
        )
        val webType = document.body()?.attr(_q9("TK51W9gDQ06ntKWBcg=="))?.trim()?.lowercase(Locale.ROOT)
        val isSeries = when {
            webType == _q9("W6pzU5AH") -> true
            webType == _q9("RaB3U5A=") -> false
            document.selectFirst(_j2.selector(_q9("W6pzU5AHa02Kq7mD"))) != null -> true
            document.selectFirst(_j2.selector(_q9("WKNgQ5AGa02Kq7mD"))) != null -> false
            else -> throw ErrorLoadingException(_q9("YqpvU4ZUTUOWtLmfN+b3i+xv2ZjenVGkqqA/k+u7LM5Mpndfhx1ARZOhr5g="))
        }

        val rawTaxonomy = _b2(document)
        _c0(rawTaxonomy.categories, rawTaxonomy.tags)

        val site = _b1(document, finalUrl)
        val tmdb = _d5(
            _d0(
                tmdbId = null,
                imdbId = null,
                originalTitle = null,
                displayTitle = site.title,
                year = site.year,
                isTv = isSeries,
            ),
            profile = _j2.metadata.tmdb,
        )

        return if (isSeries) {
            val episodeBase = _c2(finalUrl) ?: _a1
            val episodes = _b6(document, episodeBase)
            newTvSeriesLoadResponse(
                name = site.title,
                url = finalUrl,
                type = TvType.TvSeries,
                episodes = episodes,
            ) {
                _b8(site, tmdb)
            }
        } else {
            newMovieLoadResponse(
                name = site.title,
                url = finalUrl,
                type = TvType.Movie,
                dataUrl = finalUrl,
            ) {
                _b8(site, tmdb)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        _a7()
        val initialUrl = _f6(data)
        var pageUrl = initialUrl
        var requestReferer = _h2(initialUrl)
        var playerUrls = emptyList<String>()
        val visitedPages = linkedSetOf<Pair<String, String>>()

        for (hop in 0 until _j2._j15) {
            if (!visitedPages.add(pageUrl to requestReferer)) break
            val response = runCatching {
                app.get(pageUrl, referer = requestReferer, allowRedirects = true)
            }.getOrNull() ?: break
            if (!response.isSuccessful) break

            val resolvedUrl = response.url.ifBlank { pageUrl }
            val document = response.document
            _a8(resolvedUrl, document)
            pageUrl = resolvedUrl
            playerUrls = _b7(document, resolvedUrl)
            if (playerUrls.isNotEmpty()) break
            if (!_h0(document, resolvedUrl)) break

            val candidates = _g9(document, resolvedUrl)
            val requestedPath = _h1(initialUrl)
            val activeEpisode = document.selectFirst(_j2.selector(_q9("Sax1U4MRY1yRs7OVcg==")))
                ?.attr(_q9("QL1kXA=="))
                ?.let { _c3(resolvedUrl, it) }
            val matchingEpisode = candidates.firstOrNull { _h1(it) == requestedPath }
            val nextEpisode = matchingEpisode ?: activeEpisode ?: candidates.firstOrNull() ?: break

            requestReferer = resolvedUrl
            pageUrl = nextEpisode
        }

        _n4(
            stage = _l2.PLAYER_DISCOVERED,
            result = if (playerUrls.isNotEmpty()) _l3.PASS else _l3.FAIL,
            requestUrl = pageUrl,
            referer = requestReferer,
            resolver = _q9("WL1uTJwQQ17YsLCQbvfk2fhvwd3JgFq3sg=="),
            next = "mirrors=${playerUrls.size}",
            reason = if (playerUrls.isEmpty()) _q9("ZqAhSpkVX0mK4ImjW7Lwlv5kyZjLkkGgs6A5ne60PItM72RKnAdJSJ3vrJBw97aL5H/Z0cST") else null,
        )

        val emitted = linkedSetOf<String>()
        val safeCallback: (ExtractorLink) -> Unit = { link ->
            if (_n1(link.url) && emitted.add(link.url)) {
                _n4(
                    stage = _l2.MEDIA_RESOLVED,
                    result = _l3.PASS,
                    requestUrl = pageUrl,
                    referer = requestReferer,
                    resolver = _q9("bbd1SJQXUkOKjLWffLL1mOdmz9nJnw=="),
                    next = _l6(link.url),
                )
                callback(link)
            }
        }

        val orderedPlayers = playerUrls.sortedBy(::_h3)
        val effectiveMode = _j2.sourceMode._l1(
            _l0(
                eligibleCandidateCount = orderedPlayers.size,
                distinctHostCount = orderedPlayers.map(::_n2).filter { it.isNotBlank() }.distinct().size,
                distinctExtractorFamilyCount = orderedPlayers.map(::_n3).distinct().size,
                hasUserFacingServerChoices = orderedPlayers.size > 1,
            ),
        )
        val configuredModeName = _j2.sourceMode.name.lowercase(Locale.ROOT)
        _n4(
            stage = _l2.SOURCE_MODE_EFFECTIVE,
            result = _l3.PASS,
            requestUrl = pageUrl,
            referer = requestReferer,
            resolver = "configured=$configuredModeName",
            next = "effective=${effectiveMode.name.lowercase(Locale.ROOT)} candidates=${orderedPlayers.size}",
        )

        for (playerUrl in orderedPlayers) {
            val before = emitted.size
            withTimeoutOrNull(_j2.serverResolveTimeoutMs.toLong()) {
                _f0(
                    playerUrl = playerUrl,
                    referer = pageUrl,
                    subtitleCallback = subtitleCallback,
                    callback = safeCallback,
                )
            }
            val emittedNewMedia = emitted.size > before
            if (effectiveMode == _k0.FIRST_SUCCESS && emittedNewMedia) break
        }

        if (emitted.isEmpty()) {
            _n0(
                referer = pageUrl,
                subtitleCallback = subtitleCallback,
                callback = safeCallback,
            )
        }
        if (emitted.isEmpty()) {
            _n4(
                stage = _l2.MEDIA_RESOLVED,
                result = _l3.FAIL,
                requestUrl = pageUrl,
                referer = requestReferer,
                resolver = _q9("SaNtGpAYT0uRorCUN+DzmOcq3tffhlagsqAvmv60eI1HoWdTkgFUSZzgs5dx/v+X7irL2caYV6Si6w=="),
                reason = _q9("ZqAhTJQYT0jYhaSFZfP1jeR44dHEnxWgrOkvhv6+"),
            )
        }
        return emitted.isNotEmpty()
    }

    private suspend fun _f0(
        playerUrl: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var emitted = false
        val trackedCallback: (ExtractorLink) -> Unit = { link ->
            emitted = true
            callback(link)
        }

        runCatching {
            loadExtractor(
                playerUrl,
                referer = referer,
                subtitleCallback = subtitleCallback,
                callback = trackedCallback,
            )
        }
        if (emitted) {
            _n4(
                stage = _l2.EXTRACTOR_MATCHED,
                result = _l3.PASS,
                requestUrl = playerUrl,
                referer = referer,
                resolver = _q9("a6NuT5EHUl6dobHRe/33nc5y2crLl0Gqs6B00um/P4dbu2RIkBAGXIqvqphz9+TZ7nLZysuXQaqz"),
            )
            return true
        }

        if (!_f1(playerUrl)) {
            _n4(
                stage = _l2.EXTRACTOR_MATCHED,
                result = _l3.FAIL,
                requestUrl = playerUrl,
                referer = referer,
                resolver = _q9("a6NuT5EHUl6dobHRe/33nc5y2crLl0Gqs6B00um/P4dbu2RIkBAGXIqvqphz9+TZ7nLZysuXQaqz"),
                reason = _q9("ZqAhVpwaTQydrbWFY/fy2epkyZjEmxWTqOQ+ndW1PIsIqWBWmRZHT5PgvYFn/v+c+A=="),
            )
            return false
        }
        if (!_j2.runtimeDiscovery.enabled) {
            _n4(
                stage = _l2.WRAPPER_RESOLVED,
                result = _l3.NOT_APPLICABLE,
                requestUrl = playerUrl,
                referer = referer,
                resolver = _q9("fqZlX5o6SUid4IuUdcT/nPwqy9nGmFekous="),
                reason = _q9("WrpvTpwZQ2iRs7+eYffkgKtuxMvLllmgpaA5i7uKKoFepmVfhyRUQ56psJQ="),
            )
            return false
        }
        _n4(
            stage = _l2.EXTRACTOR_MATCHED,
            result = _l3.PASS,
            requestUrl = playerUrl,
            referer = referer,
            resolver = _q9("WL1uTJwQQ17VrLOSdv62r+JuyNfkm1Gg4fIunO+zNYsIqWBWmRZHT5Pgr5R79/WN7m4="),
            reason = _q9("RKBgXrAMUl6Zo6ieZbLzlOJ+2d3O1Fuq4ewynPA="),
        )
        return _f2(playerUrl, referer, trackedCallback)
    }

    private suspend fun _n0(
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val offline = _j2.offline
        if (!offline.available) return false
        val source = offline.mediaSource.trim()

        if (source.contains(_q9("BqIyT80="), ignoreCase = true)) {
            val links = runCatching {
                M3u8Helper.generateM3u8(
                    source = offline.label,
                    streamUrl = source,
                    referer = referer,
                )
            }.getOrDefault(emptyList())
            links.forEach(callback)
            return links.isNotEmpty()
        }
        if (source.contains(_q9("BqJxDg=="), ignoreCase = true) || source.contains(_q9("BrhkWJg="), ignoreCase = true) || source.contains(_q9("BqJqTA=="), ignoreCase = true)) {
            callback(
                newExtractorLink(
                    source = offline.label,
                    name = offline.label,
                    url = source,
                ) { this.referer = referer },
            )
            return true
        }

        var emitted = false
        runCatching {
            loadExtractor(
                source,
                referer = referer,
                subtitleCallback = subtitleCallback,
            ) { link ->
                emitted = true
                callback(link)
            }
        }
        return emitted
    }

    private fun _n1(url: String): Boolean = runCatching {
        val uri = URI(url)
        (uri.scheme.equals(_q9("QLt1Sg=="), true) || uri.scheme.equals(_q9("QLt1SoY="), true)) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun _n2(url: String): String = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")

    private fun _n3(url: String): String {
        val path = runCatching { URI(url).path.orEmpty().trim('/') }.getOrDefault("")
        return path.split('/').getOrNull(1)?.lowercase(Locale.ROOT).orEmpty().ifBlank { _n2(url) }
    }

    private fun _f1(url: String): Boolean = runCatching {
        URI(url).host?.equals(_j2._j13, ignoreCase = true) == true
    }.getOrDefault(false)

    private suspend fun _f2(
        playerUrl: String,
        referer: String,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val intercepted = runCatching {
            app.get(
                playerUrl,
                referer = referer,
                interceptor = WebViewResolver(
                    interceptUrl = _j2._j16,
                    additionalUrls = listOf(_j2._j16),
                    useOkhttp = false,
                    timeout = minOf(_j2._j14, _j2.runtimeDiscovery.timeoutMs.toLong()),
                ),
            )
        }.getOrNull() ?: run {
            _n4(
                stage = _l2.WRAPPER_RESOLVED,
                result = _l3.FAIL,
                requestUrl = playerUrl,
                referer = referer,
                resolver = _q9("fqZlX5o6SUid4IuUdcT/nPwqydHYkVax7O0+lvK7eIhJo21YlBdN"),
                reason = _q9("f6pjbJwRUQyRrqiUZfHzif9jwtaKklSsreU/"),
            )
            return false
        }

        val mediaUrl = intercepted.url.takeIf { _j2._j16.containsMatchIn(it) }
            ?: run {
                _n4(
                    stage = _l2.WRAPPER_RESOLVED,
                    result = _l3.FAIL,
                    requestUrl = playerUrl,
                    referer = referer,
                    resolver = _q9("fqZlX5o6SUid4IuUdcT/nPwqydHYkVax7O0+lvK7eIhJo21YlBdN"),
                    reason = _q9("ZqAhXpwGQ0+M4LGUc/v32d5Y4ZjDmkGgs+M+gu+/PA=="),
                )
                return false
            }
        _n4(
            stage = _l2.WRAPPER_RESOLVED,
            result = _l3.PASS,
            requestUrl = playerUrl,
            referer = referer,
            resolver = _q9("fqZlX5o6SUid4IuUdcT/nPwqydHYkVax7O0+lvK7eIhJo21YlBdN"),
            next = _l6(mediaUrl),
        )
        val mediaHeaders = intercepted.headers.toMap()

        if (mediaUrl.contains(_q9("BqIyT80="), ignoreCase = true)) {
            val links = runCatching {
                M3u8Helper.generateM3u8(
                    source = name,
                    streamUrl = mediaUrl,
                    referer = playerUrl,
                    headers = mediaHeaders,
                )
            }.getOrDefault(emptyList())
            links.forEach(callback)
            return links.isNotEmpty()
        }

        callback(
            newExtractorLink(
                source = name,
                name = "$name VideoNode",
                url = mediaUrl,
            ) {
                this.referer = playerUrl
                this.headers = mediaHeaders
            },
        )
        return true
    }

    private suspend fun _a7() {
        if (_a3 && _a4) return

        _a0.withLock {
            if (_a3 && _a4) return@withLock

            val remoteCandidates = runCatching {
                JSONObject(app.get(_j2._j6).text)._a9()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidates + listOf(_j2._j4, _j2._j5))
                .mapNotNull(::_c2)
                .distinct()

            for (candidate in candidates) {
                val response = runCatching { app.get(candidate) }.getOrNull() ?: continue
                if (!response.isSuccessful) continue
                _a8(response.url, response.document)
                if (_a3 && _a4) break
            }
            mainUrl = _a1
        }
    }

    private fun _a8(responseUrl: String?, document: Document) {
        val resolved = _c2(responseUrl) ?: return
        when (document.body()?.attr(_q9("TK51W9gDQ06ntKWBcg=="))?.trim()?.lowercase(Locale.ROOT)) {
            _q9("W6pzU5AH") -> {
                _a1 = resolved
                _a3 = true
                mainUrl = resolved
            }
            _q9("RaB3U5A=") -> {
                _a2 = resolved
                _a4 = true
            }
        }
    }

    private fun JSONObject._a9(): List<String> {
        val array = optJSONArray(_j2._j7) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index) }
            .mapNotNull(::_c2)
            .distinct()
    }

    private fun _b0(container: Element, base: String): List<SearchResponse> {
        return container.select(_j2.selector(_q9("S65zXqcbSVg=")))
            .asSequence()
            .filter { it.selectFirst(_j2.selector(_q9("S65zXqUbVVidsg=="))) != null && it.selectFirst(_j2.selector(_q9("S65zXqEdUkCd"))) != null }
            .mapNotNull { card ->
                val title = card.selectFirst(_j2.selector(_q9("S65zXqEdUkCd")))?.text()?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val href = card.attr(_q9("QL1kXA==")).trim().takeIf { it.isNotBlank() && it != "#" }
                    ?: return@mapNotNull null
                val itemUrl = _c3(base, href) ?: return@mapNotNull null
                val posterNode = card.selectFirst(_j2.selector(_q9("S65zXqUbVVidsg==")))
                val rawCategories = card.selectFirst(_j2.selector(_q9("S65zXrIRSF6d")))?.text()
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                if (_b9(rawCategories, emptyList())) return@mapNotNull null

                val year = posterNode?.selectFirst(_j2.selector(_q9("S65zXqwRR14=")))?.text()?.trim()?.toIntOrNull()
                val image = posterNode?.selectFirst(_j2.selector(_q9("S65zXrwZR0ud")))
                val poster = image?.attr(_q9("TK51W9gHVE8="))?.trim()?.takeIf { it.startsWith(_q9("QLt1Sg==")) }
                    ?: image?.attr(_q9("W71i"))?.trim()?.takeIf { it.startsWith(_q9("QLt1Sg==")) }
                val isSeries = posterNode?.selectFirst(_j2.selector(_q9("S65zXrAET1+XpLk="))) != null

                if (isSeries) {
                    newTvSeriesSearchResponse(title, itemUrl, TvType.TvSeries, fix = false) {
                        posterUrl = poster
                        this.year = year
                    }
                } else {
                    newMovieSearchResponse(title, itemUrl, TvType.Movie, fix = false) {
                        posterUrl = poster
                        this.year = year
                    }
                }
            }
            .distinctBy { it.url }
            .toList()
    }

    private fun _b1(document: Document, finalUrl: String): _c7 {
        val watch = document.selectFirst(_j2.selector(_q9("X651WZ08T1+Mr66I")))
            ?.let { node -> node.data().ifBlank { node.html() } }
            ?.trim()
            ?.takeIf { it.startsWith("{") }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

        val heading = document.selectFirst(_j2.selector(_q9("TKp1W5wYbkmZpLWfcA==")))?.text()?.trim()
        val watchTitle = watch?.optStringOrNull(_q9("XKZ1VpA="))
        val watchYear = watch?.optInt(_q9("UapgSA=="))?.takeIf { it > 0 }
        val headingYear = heading?.let { Regex(_q9("dOcpZpEPElHRnPWtZLiy")).find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        val title = watchTitle
            ?: heading?.replace(Regex(_q9("dLwrZt0oQlfMvYDYS+G83Q==")), "")?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_q9("YrplT5lUQkmMobWdN+b/nephjdzDgFCotOs6nA=="))

        val poster = watch?.optStringOrNull(_q9("WKByTpAG"))
            ?: document.selectFirst(_j2.selector(_q9("TKp1W5wYdkOLtLmD")))?.attr(_q9("TK51W9gHVE8="))?.trim()?.takeIf { it.startsWith(_q9("QLt1Sg==")) }
            ?: document.selectFirst(_j2.selector(_q9("R6hIV5QTQw==")))?.attr(_q9("S6BvTpAaUg=="))?.trim()?.takeIf { it.startsWith(_q9("QLt1Sg==")) }
        val plotNode = document.selectFirst(_j2.selector(_q9("W7ZvVYUHT18=")))
        val plot = plotNode?.attr(_q9("TK51W9gSU0CU"))?.trim()?.takeIf { it.isNotBlank() }
            ?: plotNode?.text()?.trim()?.takeIf { it.isNotBlank() }

        val rawTaxonomy = _b2(document)
        val actors = _b3(document)
        val trailerUrls = _b4(document)
        val score = watch?.optStringOrNull(_q9("Wq51U5sT"))
        val runtime = watch?.optStringOrNull(_q9("WrpvTpwZQw=="))?.let(::_c4)
            ?: document.select(_j2.selector(_q9("QaFnVaEVQV8="))).map { it.text().trim() }
                .firstNotNullOfOrNull(::_c5)
        val contentRating = document.select(_j2.selector(_q9("QaFnVaEVQV8=")))
            .map { it.text().trim() }
            .firstOrNull { Regex(_q9("dpNlQcRYFFGk6/g=")).matches(it) }

        return _c7(
            title = title,
            year = watchYear ?: headingYear,
            plot = plot,
            posterUrl = poster,
            score = score,
            runtimeMinutes = runtime,
            genres = rawTaxonomy.categories,
            actors = actors,
            trailerUrls = trailerUrls,
            contentRating = contentRating,
            detailUrl = finalUrl,
        )
    }

    private fun _b2(document: Document): _c6 {
        val categories = document.select(_j2.selector(_q9("T6pvSJA4T0KTsw==")))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val tags = document.select(_j2.selector(_q9("XK5mdpwaTV8=")))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return _c6(categories, tags)
    }

    private fun _b3(document: Document): List<String> {
        val detailActors = document.select(_j2.selector(_q9("TKp1W5wYdk2KobuDduL+ig=="))).firstOrNull { p ->
            p.selectFirst(_q9("W79gVA=="))?.text()?.trim()?.startsWith(_j2._j12, ignoreCase = true) == true
        }?.select("a")
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
        if (detailActors.isNotEmpty()) return detailActors

        return _b31(document)
    }

    private fun _b31(document: Document): List<String> {
        for (script in document.select(_j2.selector(_q9("QrxuVLkQ")))) {
            val raw = script.data().ifBlank { script.html() }.trim()
            if (!raw.startsWith("{")) continue
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            val actors = json.optJSONArray(_q9("Sax1VYc=")) ?: continue
            val names = (0 until actors.length()).mapNotNull { index ->
                actors.optJSONObject(index)?.optStringOrNull(_q9("Rq5sXw=="))
            }.distinct()
            if (names.isNotEmpty()) return names
        }
        return emptyList()
    }

    private fun _b4(document: Document): List<String> {
        val urls = mutableListOf<String>()
        urls += document.select(_j2.selector(_q9("XL1gU5kRVG2Wo7SeZeE="))).map { it.attr(_q9("QL1kXA==")).trim() }
        urls += document.select(_j2.selector(_q9("XL1gU5kRVGWesr2ccuE=")))
            .mapNotNull { iframe -> _b5(iframe.attr(_q9("W71i"))) }
        return urls.filter { it.startsWith(_q9("QLt1Sg==")) }.distinct()
    }

    private fun _b5(value: String): String? {
        val src = value.trim().takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = URI(src)
            val marker = _q9("B6psWJAQCQ==")
            val path = uri.path ?: return@runCatching null
            if (!path.contains(marker)) return@runCatching src
            val id = path.substringAfter(marker).substringBefore('/').trim()
            id.takeIf { it.isNotBlank() }?.let { "https://www.youtube.com/watch?v=$it" }
        }.getOrNull()
    }

    private fun _b6(document: Document, base: String): List<com.lagradost.cloudstream3.Episode> {
        val script = document.selectFirst(_j2.selector(_q9("W6pzU5AHa02Kq7mD"))) ?: return emptyList()
        val raw = script.data().ifBlank { script.html() }.trim()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val episodes = mutableListOf<com.lagradost.cloudstream3.Episode>()

        json.keys().asSequence()
            .mapNotNull { key -> key.toIntOrNull()?.let { it to key } }
            .sortedBy { it.first }
            .forEach { (seasonNumber, key) ->
                val values = json.optJSONArray(key) ?: JSONArray()
                for (index in 0 until values.length()) {
                    val item = values.optJSONObject(index) ?: continue
                    val episodeNumber = item.optInt(_q9("Tb9oSZoQQ3OWrw==")).takeIf { it > 0 } ?: continue
                    val slug = item.optStringOrNull(_q9("W6N0XQ==")) ?: continue
                    val episodeUrl = _c3(base, "/$slug") ?: continue
                    val episodeTitle = item.optStringOrNull(_q9("XKZ1VpA="))
                    episodes += newEpisode(
                        episodeUrl,
                        initializer = {
                            name = episodeTitle ?: "Episode $episodeNumber"
                            season = item.optInt("s").takeIf { it > 0 } ?: seasonNumber
                            episode = episodeNumber
                        },
                        fix = false,
                    )
                }
            }
        return episodes.distinctBy { it.data }
    }

    private fun _b7(document: Document, pageUrl: String): List<String> {
        val raw = mutableListOf<String>()
        document.select(_j2.selector(_q9("WKNgQ5AGZ0KbqLODZA=="))).forEach { node ->
            raw += node.attr(_q9("TK51W9gBVEA=")).ifBlank { node.attr(_q9("QL1kXA==")) }
        }
        document.select(_j2.selector(_q9("WKNgQ5AGaVyMqbOfZA=="))).forEach { raw += it.attr(_q9("Xq5tT5A=")) }
        document.select(_j2.selector(_q9("Ra5oVKUYR1WdspWXZfP7nA=="))).forEach { raw += it.attr(_q9("W71i")) }
        return raw.mapNotNull { _c3(pageUrl, it) }.distinct()
    }

    private fun _g9(document: Document, pageUrl: String): List<String> {
        val base = _c2(pageUrl) ?: _a1
        val urls = mutableListOf<String>()
        urls += _b6(document, base).map { it.data }
        urls += document.select(_j2.selector(_q9("Tb9oSZoQQ2CRrreC")))
            .mapNotNull { node -> _c3(pageUrl, node.attr(_q9("QL1kXA=="))) }
        return urls.distinct()
    }

    private fun _h0(document: Document, url: String): Boolean {
        val webType = document.body()?.attr(_q9("TK51W9gDQ06ntKWBcg=="))?.trim()?.lowercase(Locale.ROOT)
        if (webType == _q9("W6pzU5AH") || webType == _q9("Tb9oSZoQQw==")) return true
        if (document.selectFirst(_j2.selector(_q9("W6pzU5AHdkCZub6QdPnbmPlhyMrZ"))) != null) return true
        val host = runCatching { URI(url).host }.getOrNull()
        val seriesHosts = listOf(_a1, _j2._j4)
            .mapNotNull { base -> runCatching { URI(base).host }.getOrNull() }
        return host != null && seriesHosts.any { host.equals(it, ignoreCase = true) }
    }

    private fun _h1(url: String): String =
        runCatching { URI(url).path.orEmpty().trimEnd('/') }.getOrDefault("")

    private fun _h2(url: String): String =
        _c2(url)?.let { "$it/" } ?: url

    private fun _h3(url: String): Int = when {
        _q9("B6d4XocVXgM=") in url -> 0
        _q9("B7t0SJcbUEWI7w==") in url -> 1
        _q9("B6xgSYFb") in url -> 2
        _q9("B78zSto=") in url -> 3
        else -> 4
    }

    private suspend fun LoadResponse._b8(
        site: _c7,
        tmdb: _d2?,
    ) {
        posterUrl = site.posterUrl ?: tmdb?.posterUrl
        year = site.year ?: tmdb?.year
        val descriptionDecision = _m4(
            webDescription = site.plot,
            tmdbOverview = tmdb?.overview,
            profile = _j2.metadata.tmdb,
            filterProfile = _j2.metadata.descriptionFilter,
        )
        modularMetadataTrace.description(descriptionDecision)
        plot = descriptionDecision.value
        tags = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: site.genres.takeIf { it.isNotEmpty() }
        score = tmdb?.voteAverage?.let { Score.from10(it) }
            ?: site.score?.let { Score.from10(it) }
        duration = tmdb?.runtimeMinutes ?: site.runtimeMinutes
        actors = if (!tmdb?.actors.isNullOrEmpty()) {
            tmdb!!.actors.map { Actor(it.name, it.imageUrl) }.map { com.lagradost.cloudstream3.ActorData(it) }
        } else {
            site.actors.map { Actor(it) }.map { com.lagradost.cloudstream3.ActorData(it) }.takeIf { it.isNotEmpty() }
        }
        backgroundPosterUrl = tmdb?.backdropUrl
        logoUrl = tmdb?.logoUrl
        contentRating = tmdb?.contentRating ?: site.contentRating

        val trailers = (tmdb?.trailerUrls.orEmpty() + site.trailerUrls).distinct()
        if (trailers.isNotEmpty()) addTrailer(trailers)
        tmdb?.tmdbId?.let { addTMDbId(it.toString()) }
        tmdb?.imdbId?.let { addImdbId(it) }
    }

    private fun _b9(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ): Boolean {
        val categoryBlocked = categories.asSequence()
            .mapNotNull(::_c1)
            .any { it in _a5 }
        if (categoryBlocked) return true
        return tags.asSequence()
            .mapNotNull(::_c1)
            .any { it in _a6 }
    }

    private fun _c0(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ) {
        if (_b9(categories, tags)) {
            throw ErrorLoadingException(_q9("Y6BvTpAaBkiRorCefPvk2eRmyNCKn1qrp+k8h+m7K4cIv3NVgx1CSYo="))
        }
    }

    private fun _c1(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)

    private fun _c2(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if ((scheme == _q9("QLt1Sg==") || scheme == _q9("QLt1SoY=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else null
        }.getOrNull()
    }

    private fun _f6(url: String): String {
        val value = url.trim()
        val uri = runCatching { URI(value) }.getOrNull() ?: return value
        val isMovieHost = listOf(_a2, _j2._j5)
            .mapNotNull { base -> runCatching { URI(base).host }.getOrNull() }
            .any { host -> uri.host.equals(host, ignoreCase = true) }
        if (!isMovieHost || uri.path?.trimEnd('/') != _j2._j10) return value

        val slug = uri.rawQuery
            ?.split('&')
            ?.firstOrNull { part -> part.substringBefore('=', "").equals(_j2._j11, ignoreCase = true) }
            ?.substringAfter('=', "")
            ?.trim()
            ?.trimStart('/')
            ?.takeIf { part -> part.isNotBlank() && part.matches(Regex(_q9("dpRAF68VC1bI7eXfSOyzpaZXhpw="))) }
            ?: return value

        return _a1.removeSuffix("/") + "/" + slug
    }

    private fun _c3(base: String, href: String): String? = runCatching {
        val value = href.trim()
        val resolved = if (value.startsWith(_q9("QLt1Ss9bCQ==")) || value.startsWith(_q9("QLt1SoZOCQM="))) value
        else URI(base.removeSuffix("/") + "/").resolve(value).toString()
        _f6(resolved)
    }.getOrNull()

    private fun _c4(value: String): Int? {
        val parts = value.trim().split(':')
        if (parts.size != 2) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        return (hours * 60 + minutes).takeIf { it > 0 }
    }

    private fun _c5(value: String): Int? {
        val text = value.trim().lowercase(Locale.ROOT)
        val match = Regex(_q9("duc+AN0oQgfRqPXOS+G80bQwheTO3xyo6L9/")).matchEntire(text) ?: return null
        val hours = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        return (hours * 60 + minutes).takeIf { it > 0 }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        optString(key).trim().takeIf { it.isNotBlank() && it != _q9("RrptVg==") }

    private fun _n4(
        stage: _l2,
        result: _l3,
        requestUrl: String,
        referer: String? = null,
        resolver: String? = null,
        next: String? = null,
        reason: String? = null,
    ) {
        modularPlaybackTrace.record(
            _l4(
                provider = name,
                stage = stage,
                result = result,
                requestSummary = _l6(requestUrl),
                refererSummary = referer?.let(::_l6),
                resolver = resolver,
                next = next,
                reason = reason,
            ),
        )
    }

    private data class _c6(
        val categories: List<String>,
        val tags: List<String>,
    )

    private data class _c7(
        val title: String,
        val year: Int?,
        val plot: String?,
        val posterUrl: String?,
        val score: String?,
        val runtimeMinutes: Int?,
        val genres: List<String>,
        val actors: List<String>,
        val trailerUrls: List<String>,
        val contentRating: String?,
        val detailUrl: String,
    )

    companion object {

        private val WHITESPACE = Regex(_q9("dLwq"))
    }
}
