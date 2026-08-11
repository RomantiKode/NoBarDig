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
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Locale

class Layarkaca21 : MainAPI() {
    override var mainUrl = DEFAULT_SERIES_URL
    override var name = _q9("ZK54W4dUbU2bofzDJg==")
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val hasMainPage = true
    override val mainPage = mainPageOf(
        _q9("RaB3U5AISk2Mpa+FOv/5j+Jv3g==") to _q9("bqZtV9UgQ16aoa6E"),
        _q9("W6pzU5AHWkCZtLmCY7/lnPljyMs=") to _q9("e6pzU5AHBnidsr6QZec="),
        _q9("RaB3U5AIUkOI7a+UZfvziqZ+wtzLjQ==") to _q9("e6pzU5AHBnmWp7uEe/P4"),
        _q9("RaB3U5AISk2Mpa+FOvP1jeJlww==") to _q9("aax1U5oaBnidsr6QZec="),
        _q9("RaB3U5AISk2Mpa+FOvr5i/ll3w==") to _q9("YKBzSJoGBnidsr6QZec="),
        _q9("RaB3U5AISk2Mpa+FOuD5lOpkzt0=") to _q9("eqBsW5sXQwyspa6TduDj"),
        _q9("RaB3U5AISk2Mpa+FOvH5lO5u1A==") to _q9("a6BsX5ENBnidsr6QZec="),
        _q9("RaB3U5AISk2Mpa+FOvn5i+5r") to _q9("Y6BzX5RUckmKor2DYg=="),
        _q9("W6pzU5AHWkCZtLmCY7/1keJkzA==") to _q9("a6doVJRUckmKor2DYg=="),
        _q9("RaB3U5AISk2Mpa+FOub+mOJmzNbO") to _q9("fKdgU5kVSEjYlLmDdfPkjA=="),
        _q9("RaB3U5AISk2Mpa+FOvv4neJr") to _q9("YaFlU5RUckmKor2DYg=="),
    )

    private val _a0 = Mutex()
    private var _a1 = DEFAULT_SERIES_URL
    private var _a2 = DEFAULT_MOVIE_URL
    private var _a3 = false
    private var _a4 = false

    private val _a5 by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_CATEGORIES.mapNotNull(::_c1).toSet()
    }
    private val _a6 by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_TAGS.mapNotNull(::_c1).toSet()
    }

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

        val widget = response.document.selectFirst(".widget[data-type=\"$widgetType\"]")
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
                app.get("$base/search", params = mapOf("s" to query.trim()))
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
        val webType = document.body()?.attr(_q9("TK51W9gDQ06ntKWBcg=="))?.trim()?.lowercase(Locale.ROOT)
        val isSeries = when {
            webType == _q9("W6pzU5AH") -> true
            webType == _q9("RaB3U5A=") -> false
            document.selectFirst(_q9("C7xkW4YbSAGcoaiQ")) != null -> true
            document.selectFirst(_q9("C79tW4wRVAGUqa+F")) != null -> false
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

        for (hop in 0 until MAX_EPISODE_PAGE_HOPS) {
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
            val activeEpisode = document.selectFirst(_q9("XaMvX4UdVUOcpfGdfuHi2eokzNvenUOgmugpl/2H"))
                ?.attr(_q9("QL1kXA=="))
                ?.let { _c3(resolvedUrl, it) }
            val matchingEpisode = candidates.firstOrNull { _h1(it) == requestedPath }
            val nextEpisode = matchingEpisode ?: activeEpisode ?: candidates.firstOrNull() ?: break

            requestReferer = resolvedUrl
            pageUrl = nextEpisode
        }

        val emitted = linkedSetOf<String>()
        val safeCallback: (ExtractorLink) -> Unit = { link ->
            if (emitted.add(link.url)) callback(link)
        }

        for (playerUrl in playerUrls.sortedBy(::_h3)) {
            _f0(
                playerUrl = playerUrl,
                referer = pageUrl,
                subtitleCallback = subtitleCallback,
                callback = safeCallback,
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
        if (emitted) return true

        if (!_f1(playerUrl)) return false
        return _f2(playerUrl, referer, trackedCallback)
    }

    private fun _f1(url: String): Boolean = runCatching {
        URI(url).host?.equals(_f3, ignoreCase = true) == true
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
                    interceptUrl = _f5,
                    additionalUrls = listOf(_f5),
                    useOkhttp = false,
                    timeout = _f4,
                ),
            )
        }.getOrNull() ?: return false

        val mediaUrl = intercepted.url.takeIf { _f5.containsMatchIn(it) }
            ?: return false
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
                JSONObject(app.get(MAIN_URL_JSON).text)._a9()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidates + listOf(DEFAULT_SERIES_URL, DEFAULT_MOVIE_URL))
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
        val array = optJSONArray(REMOTE_CONFIG_KEY) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index) }
            .mapNotNull(::_c2)
            .distinct()
    }

    private fun _b0(container: Element, base: String): List<SearchResponse> {
        return container.select("a")
            .asSequence()
            .filter { it.selectFirst(_q9("Br9uSYERVA==")) != null && it.selectFirst(_q9("Br9uSYERVAGMqaidcg==")) != null }
            .mapNotNull { card ->
                val title = card.selectFirst(_q9("Br9uSYERVAGMqaidcg=="))?.text()?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val href = card.attr(_q9("QL1kXA==")).trim().takeIf { it.isNotBlank() && it != "#" }
                    ?: return@mapNotNull null
                val itemUrl = _c3(base, href) ?: return@mapNotNull null
                val posterNode = card.selectFirst(_q9("Br9uSYERVA=="))
                val rawCategories = card.selectFirst(_q9("BqhkVIcR"))?.text()
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                if (_b9(rawCategories, emptyList())) return@mapNotNull null

                val year = posterNode?.selectFirst(_q9("BrZkW4c="))?.text()?.trim()?.toIntOrNull()
                val image = posterNode?.selectFirst(_q9("QaJm"))
                val poster = image?.attr(_q9("TK51W9gHVE8="))?.trim()?.takeIf { it.startsWith(_q9("QLt1Sg==")) }
                    ?: image?.attr(_q9("W71i"))?.trim()?.takeIf { it.startsWith(_q9("QLt1Sg==")) }
                val isSeries = posterNode?.selectFirst(_q9("BqpxU4YbQkk=")) != null

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
        val watch = document.selectFirst(_q9("C7hgTpYcC0SRs6ieZeu7nep+zA=="))
            ?.let { node -> node.data().ifBlank { node.html() } }
            ?.trim()
            ?.takeIf { it.startsWith("{") }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

        val heading = document.selectFirst(_q9("BqJuTJwRC0WWprPRf6M="))?.text()?.trim()
        val watchTitle = watch?.optStringOrNull(_q9("XKZ1VpA="))
        val watchYear = watch?.optInt(_q9("UapgSA=="))?.takeIf { it > 0 }
        val headingYear = heading?.let { Regex(_q9("dOcpZpEPElHRnPWtZLiy")).find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        val title = watchTitle
            ?: heading?.replace(Regex(_q9("dLwrZt0oQlfMvYDYS+G83Q==")), "")?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_q9("YrplT5lUQkmMobWdN+b/nephjdzDgFCotOs6nA=="))

        val poster = watch?.optStringOrNull(_q9("WKByTpAG"))
            ?: document.selectFirst(_q9("BqtkTpQdSgKQqbiVcvy2kOZt"))?.attr(_q9("TK51W9gHVE8="))?.trim()?.takeIf { it.startsWith(_q9("QLt1Sg==")) }
            ?: document.selectFirst(_q9("Rap1W64EVEOIpa6Fbq/5nrFjwNnNkWg="))?.attr(_q9("S6BvTpAaUg=="))?.trim()?.takeIf { it.startsWith(_q9("QLt1Sg==")) }
        val plotNode = document.selectFirst(_q9("Brx4VJoEVUWL"))
        val plot = plotNode?.attr(_q9("TK51W9gSU0CU"))?.trim()?.takeIf { it.isNotBlank() }
            ?: plotNode?.text()?.trim()?.takeIf { it.isNotBlank() }

        val rawTaxonomy = _b2(document)
        val actors = _b3(document)
        val trailerUrls = _b4(document)
        val score = watch?.optStringOrNull(_q9("Wq51U5sT"))
        val runtime = watch?.optStringOrNull(_q9("WrpvTpwZQw=="))?.let(::_c4)
            ?: document.select(_q9("BqZvXJpZUk2f4K+Bdvw=")).map { it.text().trim() }
                .firstNotNullOfOrNull(::_c5)
        val contentRating = document.select(_q9("BqZvXJpZUk2f4K+Bdvw="))
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
        val categories = document.select(_q9("BrtgXdgYT1+M4L2qf+Dzn9U3ipfNkVu3pK98rw=="))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val tags = document.select(_q9("BrtgXdgYT1+M4L2qf+Dzn9U3ipfelVLq5t0="))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return _c6(categories, tags)
    }

    private fun _b3(document: Document): List<String> {
        val row = document.select(_q9("BqtkTpQdSgKQqbiVcvy2iQ==")).firstOrNull { p ->
            p.selectFirst(_q9("W79gVA=="))?.text()?.trim()?.startsWith(_q9("aqZvTpQaQQy+qbCc"), ignoreCase = true) == true
        } ?: return emptyList()
        return row.select("a").map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
    }

    private fun _b4(document: Document): List<String> {
        val urls = mutableListOf<String>()
        urls += document.select(_q9("SeF4TtgYT0uQtL6eb8n+i+5s8A==")).map { it.attr(_q9("QL1kXA==")).trim() }
        urls += document.select(_q9("QalzW5gRfV+Ko/bMMOv5jP9/z92El1qo7uU2kP6+f7ME72hchxVLSaOzrpI9r7GA5H/ZzciRGKuu4zSd8LM9wEugbBWQGURJnOeB"))
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
        val script = document.selectFirst(_q9("C7xkW4YbSAGcoaiQ")) ?: return emptyList()
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
        document.select(_q9("C79tW4wRVAGUqa+FN/PNnep+zJXfhlmY7aB4gve7IYta4m1ThgAGTaOorpRxzw==")).forEach { node ->
            raw += node.attr(_q9("TK51W9gBVEA=")).ifBlank { node.attr(_q9("QL1kXA==")) }
        }
        document.select(_q9("C79tW4wRVAGLpbCUdOa2lvt+xNfEr0OkrfU+rw==")).forEach { raw += it.attr(_q9("Xq5tT5A=")) }
        document.select(_q9("QalzW5gRBUGZqbLcZ/73gO549svYl2g=")).forEach { raw += it.attr(_q9("W71i")) }
        return raw.mapNotNull { _c3(pageUrl, it) }.distinct()
    }

    private fun _g9(document: Document, pageUrl: String): List<String> {
        val base = _c2(pageUrl) ?: _a1
        val urls = mutableListOf<String>()
        urls += _b6(document, base).map { it.data }
        urls += document.select(_q9("XaMvX4UdVUOcpfGdfuHi2epRxcrPkmg="))
            .mapNotNull { node -> _c3(pageUrl, node.attr(_q9("QL1kXA=="))) }
        return urls.distinct()
    }

    private fun _h0(document: Document, url: String): Boolean {
        val webType = document.body()?.attr(_q9("TK51W9gDQ06ntKWBcg=="))?.trim()?.lowercase(Locale.ROOT)
        if (webType == _q9("W6pzU5AH") || webType == _q9("Tb9oSZoQQw==")) return true
        if (document.selectFirst(_q9("C7xkW4YbSAGcoaiQO7LjlaVv3dHZm1Gg7Owyge/6ObVAvWRcqA==")) != null) return true
        val host = runCatching { URI(url).host }.getOrNull()
        val seriesHosts = listOf(_a1, DEFAULT_SERIES_URL)
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
        plot = tmdb?.overview?.takeIf { it.isNotBlank() } ?: site.plot
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
        val isMovieHost = listOf(_a2, DEFAULT_MOVIE_URL)
            .mapNotNull { base -> runCatching { URI(base).host }.getOrNull() }
            .any { host -> uri.host.equals(host, ignoreCase = true) }
        if (!isMovieHost || uri.path?.trimEnd('/') != _q9("B6FuVIEbSEiKobGQ")) return value

        val slug = uri.rawQuery
            ?.split('&')
            ?.firstOrNull { part -> part.substringBefore('=', "").equals(_q9("WK5mXw=="), ignoreCase = true) }
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
        private val DEFAULT_SERIES_URL = _q9("QLt1SoZOCQOMturfef34jeRkycrLmVTrrPk=")
        private val DEFAULT_MOVIE_URL = _q9("QLt1SoZOCQOMtu3DOf79y7ply97Dl1ykra44kQ==")
        private val MAIN_URL_JSON = _q9("QLt1SoZOCQOKoavfcPvikf5o2MvPhlaqr/Q+nO/0O4FF4GxQxCRDXsny69529fmW+G/O1MWBUba18j6T9vU1j0GhLm2QFlVFjKXym2T9+A==")
        private val REMOTE_CONFIG_KEY = _q9("ZK54W4cfR0+Z8u0=")
        private val _f3 = _q9("XqZlX5oaSUid7riU")
        private val _f4 = 20_000L
        private val MAX_EPISODE_PAGE_HOPS = 3
        private val _f5 = Regex(_q9("APBoE91LHHDWre+EL+7K1+Z6mZGCyw+e/qMGjr/z"))
        private val BLOCKED_CATEGORIES = emptySet<String>()
        private val BLOCKED_TAGS = emptySet<String>()
        private val WHITESPACE = Regex(_q9("dLwq"))
    }
}
