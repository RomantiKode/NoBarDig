package com.agooseangsa.DutaMovie21

import com.agooseangsa.DutaMovie21.shared.AgooseFailoverPolicy
import com.agooseangsa.DutaMovie21.shared.AgoosePlaybackFailover
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class DutaMovie21 : MainAPI() {
    private val _a0 = AgooseProviderProfile.current

    override var mainUrl = _a0.defaultMainUrl
    override var name = _q9("6StmuK4SQJRK8SOs7A==")
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val loadLinksTimeoutMs: Long = 90_000L

    override val mainPage: List<MainPageData> = _a0.homepage.map {
        MainPageData(name = it.title, data = it.source, horizontalImages = false)
    }

    private val _a1 = Mutex()
    private var _a2 = false

    private val _a3 by lazy(LazyThreadSafetyMode.NONE) {
        _a0.blockedCategories().mapNotNull(::_c0).toSet()
    }
    private val _a4 by lazy(LazyThreadSafetyMode.NONE) {
        _a0.blockedTags().mapNotNull(::_c0).toSet()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        _b5()
        val target = _b4(request.data, page)
        val response = app.get(target)
        _b6(response.url)
        val cards = _a6(response.document)
        return newHomePageResponse(request, cards, hasNext = cards.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        _b5()
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val searchPath = _a0.endpoint(_q9("3jtzq+03f4NX/A=="), "/").ifBlank { "/" }
        val searchParam = _a0.endpoint(_q9("3jtzq+03f4NR9W4="), "s").ifBlank { "s" }
        val base = if (searchPath.startsWith(_q9("xSpmqQ=="))) searchPath else "$mainUrl${normalizePath(searchPath)}"
        val separator = if (base.contains('?')) "&" else "?"
        val target = "$base$separator$searchParam=$encoded&post_type%5B%5D=post&post_type%5B%5D=tv"
        val response = app.get(target)
        _b6(response.url)
        return _a6(response.document)
    }

    override suspend fun load(url: String): LoadResponse {
        _b5()
        val response = app.get(url)
        _b6(response.url)
        val document = response.document

        val title = document.selectFirst(_a0.selector(_q9("yTtmuOczZ4dC8Grwug=="), _q9("xW88vOArXZsO4GrqsXw=")))
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_q9("5yt2rOJ/S4dX9Wry/W2W2pLVlA6pjhsHHvZPQA=="))
        val poster = document.selectFirst(_a0.selector(_q9("yTtmuOczf41Q4Gbs"), _q9("gzl/q6MyQJRK8S76vG2enpXX0x+yn14DBvo=")))
            ?.imageUrl()
        val genres = _a8(document)
        val tags = emptyList<String>()
        _b9(genres, tags)

        val year = YEAR_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: _a9(document)
        val episodeLinks = document.select(_a0.selector(_q9("yC57quE7Sq5K+mjt"), _q9("gzl/q6MzRpFX52bstHyMnpLl3BilnFRXSbJLXjPqX7I=")))
        val isSeries = response.url.contains(_q9("gipk9g=="), ignoreCase = true) || episodeLinks.isNotEmpty()

        return if (isSeries) {
            val season = SEASON_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val episodes = episodeLinks.mapNotNull { node ->
                val episodeUrl = node.attr(_q9("xSx3vw==")).trim().takeIf { it.startsWith(_q9("xSpmqQ==")) } ?: return@mapNotNull null
                val episodeNumber = EPISODE_URL_REGEX.find(episodeUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: EPISODE_TEXT_REGEX.find(node.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
                val displayName = episodeNumber?.let { "Episode $it" }
                    ?: node.attr(_q9("2Tdmtes=")).removePrefix(_q9("/TtgtO8zRoxItGj7/Q==")).trim().takeIf { it.isNotBlank() }
                    ?: node.text().trim().takeIf { it.isNotBlank() }
                newEpisode(episodeUrl) {
                    name = displayName
                    this.season = season
                    episode = episodeNumber
                    posterUrl = poster
                }
            }.distinctBy { it.data }

            newTvSeriesLoadResponse(title, response.url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.year = year
                this.tags = genres.takeIf { it.isNotEmpty() }
            }
        } else {
            newMovieLoadResponse(title, response.url, TvType.Movie, response.url) {
                posterUrl = poster
                this.year = year
                this.tags = genres.takeIf { it.isNotEmpty() }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val pageResponse = try {
            app.get(data)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            return false
        }

        val playerUrl = pageResponse.document
            .selectFirst(_a0.selector(_q9("wD97t94zTptG5kr4r3iS2w=="), _q9("gzl/q6MsSpBV8XGzqmueztPX0hihlxsxGO9Ncw==")))
            ?.attr(_q9("3ixx"))
            ?.trim()
            ?.takeIf(::isHttpUrl)
            ?: return false

        val wrapperResponse = try {
            app.get(playerUrl, referer = data)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            return false
        }

        val candidates = _b0(wrapperResponse.document)
        if (candidates.isEmpty()) return false

        val failoverProfile = _a0.failover
        val result = AgoosePlaybackFailover.resolve(
            candidates = candidates,
            labelOf = { it.label },
            policy = AgooseFailoverPolicy(
                enabled = failoverProfile.enabled && candidates.size > 1,
                mode = failoverProfile.mode,
                serverResolveTimeoutMs = failoverProfile.serverResolveTimeoutMs.toLong(),
            ),
        ) { candidate ->
            _b1(
                candidate = candidate,
                wrapperUrl = playerUrl,
                subtitleCallback = subtitleCallback,
                callback = callback,
            )
        }

        if (result.success) return true

        return _b3(callback)
    }

    private suspend fun _b1(
        candidate: _a5,
        wrapperUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var emitted = false
        try {
            loadExtractor(
                url = candidate.url,
                referer = wrapperUrl,
                subtitleCallback = subtitleCallback,
            ) { link ->
                emitted = true
                callback(link)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {

        }
        if (emitted) return true

        val webViewUrl = _b2(candidate)
        val mediaRegex = Regex(_a0.playbackString(_q9("wDt2sO8NSpNW8XDqj3yY24s="), _q9("hWF78KZgFb4N+TDr5WWjkJ7OgEPoxUQxVL5zUmTs")))
        val timeout = (_a0.failover.serverResolveTimeoutMs - 1_500).coerceAtLeast(2_000).toLong()
        val mediaResponse = try {
            app.get(
                webViewUrl,
                referer = wrapperUrl,
                interceptor = WebViewResolver(mediaRegex, timeout = timeout),
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            return false
        }

        val mediaUrl = mediaResponse.url.takeIf { mediaRegex.containsMatchIn(it) } ?: return false
        val headers = mediaResponse.headers.toMap()
        return if (mediaUrl.substringBefore('?').substringBefore('#').endsWith(_q9("gzMhrLY="), ignoreCase = true)) {
            val links = M3u8Helper.generateM3u8(
                candidate.label,
                mediaUrl,
                webViewUrl,
                headers = headers,
            )
            links.forEach(callback)
            links.isNotEmpty()
        } else {
            val link = newExtractorLink(
                source = candidate.label,
                name = candidate.label,
                url = mediaUrl,
            ) {
                referer = webViewUrl
                quality = Qualities.Unknown.value
                this.headers = headers
            }
            callback(link)
            true
        }
    }

    private suspend fun _b3(callback: (ExtractorLink) -> Unit): Boolean {
        val offline = _a0.offlineIndicator
        if (!offline.enabled || offline.mediaSource.isBlank()) return false

        val collected = mutableListOf<ExtractorLink>()
        loadExtractor(
            url = offline.mediaSource,
            referer = mainUrl,
            subtitleCallback = {},
        ) { link -> collected += link }

        for (link in collected) {
            callback(
                newExtractorLink(
                    source = offline.label,
                    name = offline.label,
                    url = link.url,
                    type = link.type,
                ) {
                    referer = link.referer
                    quality = link.quality
                    headers = link.headers
                },
            )
        }
        return collected.isNotEmpty()
    }

    private fun _b0(document: Document): List<_a5> {
        val serverSelector = _a0.selector(_q9("2ixzqf46XbFG5nX7r1WW0JjN"), _q9("ji13q/g6XZED9Vjxs3qT15DV6Q=="))
        val parsed = document.select(serverSelector).mapNotNull { node ->
            val onclick = node.attr(_q9("wjBxtec8RA=="))
            val url = PLAY_SELECTED_REGEX.find(onclick)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf(::isHttpUrl)
                ?: return@mapNotNull null
            val label = node.text().trim().ifBlank { URI(url).host ?: _q9("/htAj8sN") }
            _a5(label = label, url = url)
        }.distinctBy { it.url }

        if (parsed.isNotEmpty()) return parsed

        val fallback = document.selectFirst(_a0.selector(_q9("2ixzqf46XaFW5nH7s2222IHf2Q8="), _q9("jih7veswf45C7Wbs/XCZzJLT0TGziB03")))
            ?.attr(_q9("3ixx"))?.trim()?.takeIf(::isHttpUrl)
            ?: return emptyList()
        return listOf(_a5(label = _q9("6RtUmNsTew=="), url = fallback))
    }

    private fun _b2(candidate: _a5): String {
        if (!candidate.label.equals(_q9("5QdWi88H"), ignoreCase = true)) return candidate.url
        return runCatching {
            val uri = URI(candidate.url)
            if (uri.host.equals(_q9("zDxrqv0vQ4Na8XGwvnaS"), ignoreCase = true)) {
                URI(_q9("xSpmqf0="), _q9("1y5+uPdxToBa53DusXiG24GQ1wWt"), uri.path, uri.query, uri.fragment).toString()
            } else {
                candidate.url
            }
        }.getOrDefault(candidate.url)
    }

    private fun _a6(document: Document): List<SearchResponse> {
        val cardSelector = _a0.selector(_q9("zj9gvQ=="), _q9("zCxmsO0zSsxK4Gbz8HCR2JrQ3R6l"))
        return document.select(cardSelector).mapNotNull(::_a7)
    }

    private fun _a7(card: Element): SearchResponse? {
        val titleNode = card.selectFirst(_a0.selector(_q9("zj9gvdo2W45G"), _q9("xWw8vOArXZsO4GrqsXzf36jWxg+mpw==")))
            ?: return null
        val title = titleNode.text().trim().takeIf { it.isNotBlank() } ?: return null
        val url = titleNode.attr(_q9("xSx3vw==")).trim().takeIf(::isHttpUrl) ?: return null
        val categories = card.select(_a0.selector(_q9("zj9gvc0+W4dE+3H3uGo="), _q9("gzl/q6MyQJRK8S7xszme")))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        if (_b8(categories = categories)) return null

        val poster = card.selectFirst(_a0.selector(_q9("zj9gvd4wXJZG5g=="), _q9("gz19t/o6QZYO4GvrsHuR35rSlAOtnQ==")))?.imageUrl()
        val year = YEAR_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val isSeries = url.contains(_q9("gipk9g=="), ignoreCase = true) ||
            card.selectFirst(_a0.selector(_q9("zj9gvdomX4du9XH1uGs="), _q9("gzl/q6MvQJFX4HruuDSWypbT")))
                ?.text()?.contains(_q9("+QgyiuYwWA=="), ignoreCase = true) == true

        return if (isSeries) {
            val episodeCount = card.selectFirst(_a0.selector(_q9("zj9gvcsvRpFM8GY="), _q9("gzl/q6MxWo9B8XPt/WqP350=")))
                ?.text()?.trim()?.toIntOrNull()
            newTvSeriesSearchResponse(title, url, TvType.TvSeries, fix = false) {
                posterUrl = poster
                this.year = year
                episodes = episodeCount
            }
        } else {
            newMovieSearchResponse(title, url, TvType.Movie, fix = false) {
                posterUrl = poster
                this.year = year
            }
        }
    }

    private fun _a8(document: Document): List<String> {
        val metadataSelector = _a0.selector(_q9("yTtmuOczYodX9Wf/qXg="), _q9("gz19t/o6QZYO+WzotHyb34fflESnlwxHBvJYRyWhHJvM"))
        val row = document.select(metadataSelector).firstOrNull {
            it.text().trim().startsWith(_q9("6jt8q+tl"), ignoreCase = true)
        } ?: return emptyList()
        val linked = row.select("a").map { it.text().trim() }.filter { it.isNotBlank() }
        if (linked.isNotEmpty()) return linked.distinct()
        return row.text().substringAfter(':', "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun _a9(document: Document): Int? {
        val metadataSelector = _a0.selector(_q9("yTtmuOczYodX9Wf/qXg="), _q9("gz19t/o6QZYO+WzotHyb34fflESnlwxHBvJYRyWhHJvM"))
        val releaseText = document.select(metadataSelector).firstOrNull {
            it.text().trim().startsWith(_q9("/zd+sP1l"), ignoreCase = true)
        }?.text() ?: return null
        return RELEASE_YEAR_REGEX.find(releaseText)?.value?.toIntOrNull()
    }

    private fun Element.imageUrl(): String? = sequenceOf(
        attr(_q9("yT9muKMsXYE=")),
        attr(_q9("yT9muKMzTphauXDsvg==")),
        attr(_q9("3ixx")),
    ).map { it.trim() }.firstOrNull(::isHttpUrl)

    private fun _b4(source: String, page: Int): String {
        val normalized = normalizePath(source)
        if (page <= 1) return if (normalized == "/") "$mainUrl/" else "$mainUrl$normalized"
        return if (normalized == "/") {
            "$mainUrl/page/$page/"
        } else {
            "$mainUrl${normalized.trimEnd('/')}/page/$page/"
        }
    }

    private fun normalizePath(value: String): String {
        val clean = value.trim().ifBlank { "/" }
        val leading = if (clean.startsWith('/')) clean else "/$clean"
        return if (leading == "/" || leading.endsWith('/')) leading else "$leading/"
    }

    private suspend fun _b5() {
        if (_a2) return
        _a1.withLock {
            if (_a2) return@withLock
            val remoteCandidates = runCatching {
                JSONObject(app.get(_a0.websiteJsonUrl).text).readMainUrlCandidates()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidates + _a0.defaultMainUrl)
                .mapNotNull(::_b7)
                .distinct()

            for (candidate in candidates) {
                val response = runCatching { app.get(candidate) }.getOrNull() ?: continue
                if (!response.isSuccessful) continue
                val resolved = _b7(response.url) ?: continue
                mainUrl = resolved
                _a2 = true
                return@withLock
            }
            mainUrl = _a0.defaultMainUrl
            _a2 = true
        }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(_a0.websiteKey) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index) }
            .mapNotNull(::_b7)
            .distinct()
    }

    private fun _b6(responseUrl: String?) {
        _b7(responseUrl)?.let { mainUrl = it }
    }

    private fun _b7(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if ((scheme == _q9("xSpmqQ==") || scheme == _q9("xSpmqf0=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else null
        }.getOrNull()
    }

    private fun _b8(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ): Boolean {
        if (categories.asSequence().mapNotNull(::_c0).any { it in _a3 }) return true
        return tags.asSequence().mapNotNull(::_c0).any { it in _a4 }
    }

    private fun _b9(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ) {
        if (_b8(categories, tags)) {
            throw ErrorLoadingException(_q9("5jF8resxD4ZK9m/xtnCNnpzS0QLgkREEDfRJWzKkDoaNLmC2+DZLh1E="))
        }
    }

    private fun _c0(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)

    private fun isHttpUrl(value: String?): Boolean = value?.let {
        it.startsWith(_q9("xSpmqf1lAM0="), ignoreCase = true) || it.startsWith(_q9("xSpmqbRwAA=="), ignoreCase = true)
    } == true

    private data class _a5(
        val label: String,
        val url: String,
    )

    companion object {
        private val YEAR_REGEX = Regex(_q9("8XY68bFlHttfpjO3gX2EjI6X6EM="))
        private val RELEASE_YEAR_REGEX = Regex(_q9("hWEo6LcjHdIKyGfl72Q="))
        private val SEASON_REGEX = Regex(_q9("/jtzquExc5EIvF/69jA="), RegexOption.IGNORE_CASE)
        private val EPISODE_URL_REGEX = Regex(_q9("yC57quE7Ss8LyGe19A=="), RegexOption.IGNORE_CASE)
        private val EPISODE_TEXT_REGEX = Regex(_q9("hWEovP4sEJ5G5Grtsn2al6/NnkKcnlVD"), RegexOption.IGNORE_CASE)
        private val PLAY_SELECTED_REGEX = Regex("""playSelectedVideo\(\s*['\"]([^'\"]+)['\"]\s*\)""", RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex(_q9("8S05"))
    }
}
