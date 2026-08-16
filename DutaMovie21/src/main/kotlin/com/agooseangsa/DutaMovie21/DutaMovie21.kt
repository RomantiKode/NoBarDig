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
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
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
    private val providerProfile = AgooseProviderProfile.current

    override var mainUrl = providerProfile.defaultMainUrl
    override var name = _q9("YRNASZKb5OehJ3S68Q==")
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true
    override val loadLinksTimeoutMs: Long = 90_000L

    override val mainPage: List<MainPageData> = providerProfile.homepage.map {
        MainPageData(name = it.title, data = it.source, horizontalImages = false)
    }

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false

    private val blockedCategoryKeys by lazy(LazyThreadSafetyMode.NONE) {
        providerProfile.blockedCategories().mapNotNull(::normalizeTaxonomyName).toSet()
    }
    private val blockedTagKeys by lazy(LazyThreadSafetyMode.NONE) {
        providerProfile.blockedTags().mapNotNull(::normalizeTaxonomyName).toSet()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        val target = buildArchiveUrl(request.data, page)
        val response = app.get(target)
        syncMainUrl(response.url)
        val cards = parseCards(response.document)
        return newHomePageResponse(request, cards, hasNext = cards.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val searchPath = providerProfile.endpoint(_q9("VgNVWtG+2/C8Kg=="), "/").ifBlank { "/" }
        val searchParam = providerProfile.endpoint(_q9("VgNVWtG+2/C6Izk="), "s").ifBlank { "s" }
        val base = if (searchPath.startsWith(_q9("TRJAWA=="))) searchPath else "$mainUrl${normalizePath(searchPath)}"
        val separator = if (base.contains('?')) "&" else "?"
        val target = "$base$separator$searchParam=$encoded&post_type%5B%5D=post&post_type%5B%5D=tv"
        val response = app.get(target)
        syncMainUrl(response.url)
        return parseCards(response.document)
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val response = app.get(url, referer = mainUrl)
        syncMainUrl(response.url)
        val document = response.document

        val title = findDetailTitle(document)
            ?: throw ErrorLoadingException(_q9("bQdYSd+35bGsJyDpqeBWvUpXQsbCv+HeRIvZGbkfBWpBE1gIxr/v8KNiMOG06Ru8SFJN"))
        val poster = findDetailPoster(document)
        val description = findDetailDescription(document)
        val genres = parseGenres(document)
        val tags = emptyList<String>()
        enforceContentAllowed(genres, tags)

        val year = YEAR_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: parseReleaseYear(document)
        val episodeLinks = findEpisodeLinks(document)
        val isSeries = response.url.contains(_q9("ChJCBw=="), ignoreCase = true) || episodeLinks.isNotEmpty()

        return if (isSeries) {
            if (response.url.contains(_q9("ChJCBw=="), ignoreCase = true) && episodeLinks.isEmpty()) {
                throw ErrorLoadingException(_q9("bQdYSd+35bGsJyDpqeBWukZBSsyO6/DJVIjbG+ITT2tAElVY2/bv8K42Nfrg6QagUFxHyMK/7chHgZoc6ksKclANVUY="))
            }

            val season = SEASON_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val episodes = episodeLinks.mapNotNull { node ->
                val episodeUrl = resolveHttpUrl(node.attr(_q9("TRRRTg==")), response.url) ?: return@mapNotNull null
                val episodeNumber = EPISODE_URL_REGEX.find(episodeUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: EPISODE_TEXT_REGEX.find(node.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: EPISODE_TEXT_REGEX.find(node.attr(_q9("UQ9ARNc=")))?.groupValues?.getOrNull(1)?.toIntOrNull()
                val displayName = episodeNumber?.let { "Episode $it" }
                    ?: node.attr(_q9("UQ9ARNc=")).removePrefix(_q9("dQNGRdO64v+jYj/t4A==")).trim().takeIf { it.isNotBlank() }
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
                plot = description
            }
        } else {
            newMovieLoadResponse(title, response.url, TvType.Movie, response.url) {
                posterUrl = poster
                this.year = year
                this.tags = genres.takeIf { it.isNotEmpty() }
                plot = description
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
            app.get(data, referer = mainUrl)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            return false
        }

        if (findDetailTitle(pageResponse.document).isNullOrBlank()) return false

        val playerUrls = discoverDetailPlayerUrls(
            document = pageResponse.document,
            pageUrl = pageResponse.url,
        )
        if (playerUrls.isEmpty()) return false

        var sawEligibleCandidate = false
        for (playerUrl in playerUrls) {
            val isPlaysobatWrapper = runCatching {
                URI(playerUrl).host?.contains(_q9("VQpVUcG56fC8bCzxug=="), ignoreCase = true) == true
            }.getOrDefault(false)

            val candidates: List<PlayerCandidate>
            val referer: String
            if (isPlaysobatWrapper) {
                val wrapperResponse = try {
                    app.get(playerUrl, referer = pageResponse.url)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Throwable) {
                    continue
                }
                candidates = discoverWrapperCandidates(wrapperResponse.document)
                referer = playerUrl
            } else {
                candidates = listOf(
                    PlayerCandidate(
                        label = URI(playerUrl).host?.uppercase(Locale.ROOT) ?: _q9("diNmfveE"),
                        url = playerUrl,
                    ),
                )
                referer = pageResponse.url
            }

            if (candidates.isEmpty()) continue
            sawEligibleCandidate = true

            val failoverProfile = providerProfile.failover
            val result = AgoosePlaybackFailover.resolve(
                candidates = candidates,
                labelOf = { it.label },
                policy = AgooseFailoverPolicy(
                    enabled = failoverProfile.enabled && candidates.size > 1,
                    mode = failoverProfile.mode,
                    serverResolveTimeoutMs = failoverProfile.serverResolveTimeoutMs.toLong(),
                ),
            ) { candidate ->
                resolveCandidate(
                    candidate = candidate,
                    wrapperUrl = referer,
                    subtitleCallback = subtitleCallback,
                    callback = callback,
                )
            }

            if (result.success) return true
        }

        return if (sawEligibleCandidate) emitOfflineIndicator(callback) else false
    }

    private suspend fun discoverDetailPlayerUrls(
        document: Document,
        pageUrl: String,
    ): List<String> {
        val found = linkedSetOf<String>()

        fun addIframe(node: Element?) {
            val raw = node?.iframeUrl().orEmpty()
            resolveHttpUrl(raw, pageUrl)?.takeIf { !it.equals(_q9("TwdCScG1+fi4Nm7uoeAFrA=="), true) }?.let(found::add)
        }

        val profileSelector = providerProfile.selector(
            _q9("SAddRuK66uitMB3usu0brA=="),
            _q9("CwFZWp+l7uO+Jyalt/4XuQMdRMCQ5uHBRI/eVfFaHG9KCEdBxLOr+K4wNeWl1wW7QG4="),
        )
        document.select(profileSelector).forEach(::addIframe)
        document.select(
            _q9("CwFZWp+l7uO+Jyalt/4XuQNaRd+DpuH3VZjZJa8fQXhIFBlb16T99LpvI/qh/FagRUFCwIeQ4M1Si5cU6ksKbFUDUUyfpfnylW50") +
                _q9("CwFZWp+z5vOtJnn6pf8Gpk1AStuH6+3KVIvXHdhMHXx4ShQG1bv5vK0vNu2koQSsUENMw5Gi8skGg9wK4lIKREEHQEmfuuLlrTEk7aXoW7pRUH6Bwg==") +
                _q9("TABGSd+z0OK6IX61sOAXsFBcQcyW5fzVXLeWWOpZHX5IA29M06LqvKQrIO2z/BOsRx5Q34HhudxKi8ML7F0OawseTVLv"),
        ).forEach(::addIframe)

        document.select(providerProfile.selector(_q9("VQpVUdek3/CqMQ=="), _q9("UAoaRceg4uG6LXn4rO0PrFEeV8yAuKTAT8rbI+tNCnl4"))).forEach { tab ->
            val tabUrl = resolveHttpUrl(tab.attr(_q9("TRRRTg==")), pageUrl) ?: return@forEach
            if (tabUrl == pageUrl && found.isNotEmpty()) return@forEach
            val tabResponse = runCatching { app.get(tabUrl, referer = pageUrl) }.getOrNull() ?: return@forEach
            tabResponse.document.select(
                _q9("CwFZWp+z5vOtJnn6pf8Gpk1AStuH6+3KVIvXHdhMHXx4ShQG1bv5vK0vNu2koQSsUENMw5Gi8skGg9wK4lIKREEHQEmfuuLlrTEk7aXoW7pRUH4="),
            ).forEach { addIframe(it) }
        }

        val postId = document.selectFirst(providerProfile.selector(_q9("VQpVUdekyvupOh3s"), _q9("QQ9CC9+j/fi4MDvXsOAXsEZBfM6NpfDJSJ7lEedkC35RBxlB1os=")))
            ?.attr(_q9("QQdASZ+/7w=="))?.trim()?.takeIf { it.isNotBlank() }
        if (postId != null) {
            val origin = normalizeHttpBaseUrl(pageUrl)
            if (origin != null) {
                document.select(providerProfile.selector(_q9("VQpVUdekyvupOgDpov8="), _q9("QQ9CBsa36byrLTr8peIC5EJZQtW5ouDx"))).forEach { tab ->
                    val tabId = tab.attr("id").trim().takeIf { it.isNotBlank() } ?: return@forEach
                    val ajax = runCatching {
                        app.post(
                            "$origin/wp-admin/admin-ajax.php",
                            referer = pageUrl,
                            data = mapOf(
                                _q9("RAVAQd24") to _q9("SBNCQcKk5M64LjXxpf4pqkxdV8iMvw=="),
                                _q9("UQdW") to tabId,
                                _q9("VQlHXO2/7w==") to postId,
                            ),
                        )
                    }.getOrNull() ?: return@forEach
                    ajax.document.select(_q9("TABGSd+z0OK6IQmk4OUQu0JeRvaGqvDNC4bTDOZMH3pAAhlbwLXW")).forEach { iframe ->
                        val raw = iframe.iframeUrl()
                        resolveHttpUrl(raw, pageUrl)?.let(found::add)
                    }
                }
            }
        }

        return found.filter(::isHttpUrl)
    }

    private suspend fun resolveCandidate(
        candidate: PlayerCandidate,
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

        if (candidate.url.contains(_q9("TQFYQdy9peWn"), ignoreCase = true)) {
            try {
                DutaMovieHglink().getUrl(
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
        }

        val webViewUrl = normalizeCandidateForWebView(candidate)
        val mediaRegex = Regex(providerProfile.playbackString(_q9("SANQQdOE7uC9Jyf8kukRrFs="), _q9("DVldAZrpsc3mL2f9+PAq505DF4TK9L73GcnnBKcW")))
        val timeout = (providerProfile.failover.serverResolveTimeoutMs - 1_500).coerceAtLeast(2_000).toLong()
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
        return if (mediaUrl.substringBefore('?').substringBefore('#').endsWith(_q9("CwsHXYo="), ignoreCase = true)) {
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

    private suspend fun emitOfflineIndicator(callback: (ExtractorLink) -> Unit): Boolean {
        val offline = providerProfile.offlineIndicator
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

    private fun discoverWrapperCandidates(document: Document): List<PlayerCandidate> {
        val serverSelector = providerProfile.selector(_q9("UhRVWMKz+cKtMCLtssAfp0hA"), _q9("BhVRWsSz+eLoIw/nru8aoEBYfg=="))
        val serverCandidates = document.select(serverSelector).mapNotNull { node ->
            val onclick = node.attr(_q9("SghXRNu14A=="))
            val url = PLAY_SELECTED_REGEX.find(onclick)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf(::isHttpUrl)
                ?: return@mapNotNull null
            val label = node.text().trim().ifBlank { URI(url).host ?: _q9("diNmfveE") }
            PlayerCandidate(label = label, url = url)
        }.distinctBy { candidateIdentity(it.url) }

        val currentUrl = document
            .selectFirst(providerProfile.selector(_q9("UhRVWMKz+dK9MCbtrvg/r1FSTsg="), _q9("BhBdTNe52/2pOzH64OUQu0JeRvaRuefx")))
            ?.iframeUrl()?.trim()?.takeIf(::isHttpUrl)

        val ordered = mutableListOf<PlayerCandidate>()
        if (currentUrl != null) {
            val currentIdentity = candidateIdentity(currentUrl)
            val matched = serverCandidates.firstOrNull { candidateIdentity(it.url) == currentIdentity }
            ordered += PlayerCandidate(
                label = matched?.label ?: inferCandidateLabel(currentUrl),
                url = currentUrl,
            )
        }
        ordered += serverCandidates.filter { candidate ->
            ordered.none { candidateIdentity(it.url) == candidateIdentity(candidate.url) }
        }
        return ordered.distinctBy { candidateIdentity(it.url) }
    }

    private fun inferCandidateLabel(url: String): String {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return when {
            host.contains(_q9("RARNW8Gm5/CxJyY=")) -> _q9("bT9wevOO")
            host.contains(_q9("TQFYQdy9")) -> _q9("djJmbfOb3NibCg==")
            host.contains(_q9("QQlbTA==")) -> _q9("YSl7bOGC2dSJDw==")
            host.contains(_q9("SA9MTMC5+w==")) || host.contains(_q9("SAJSUA==")) -> _q9("aC9sbOCZ2w==")
            host.contains(_q9("VVREW8ak7vCl")) -> _q9("djJmbfOb26OY")
            else -> host.ifBlank { _q9("YSNyaeea3w==") }.uppercase(Locale.ROOT)
        }
    }

    private fun candidateIdentity(url: String): String = runCatching {
        val uri = URI(url)
        val host = uri.host.orEmpty().lowercase(Locale.ROOT).removePrefix(_q9("UhFDBg=="))
        val videoId = uri.path.orEmpty().trimEnd('/').substringAfterLast('/')
        when {
            host.endsWith(_q9("RARNW8Gm5/CxJyamo+Mb")) -> "abyssplayer|$videoId"
            else -> "$host|${uri.path.orEmpty().trimEnd('/')}|${uri.fragment.orEmpty()}"
        }
    }.getOrDefault(url)

    private fun normalizeCandidateForWebView(candidate: PlayerCandidate): String {
        if (!candidate.label.equals(_q9("bT9wevOO"), ignoreCase = true)) return candidate.url
        return runCatching {
            val uri = URI(candidate.url)
            if (uri.host.equals(_q9("RARNW8Gm5/CxJyamo+Mb"), ignoreCase = true)) {
                URI(_q9("TRJAWME="), _q9("XxZYScv46vOxMSf4rO0PrFEdQMKP"), uri.path, uri.query, uri.fragment).toString()
            } else {
                candidate.url
            }
        }.getOrDefault(candidate.url)
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val cardSelector = providerProfile.selector(_q9("RgdGTA=="), _q9("RBRAQdG67r+hNjHl7eUYr0pdStmH"))
        return document.select(cardSelector).mapNotNull(::toSearchResponse)
    }

    private fun toSearchResponse(card: Element): SearchResponse? {
        val titleNode = card.selectFirst(providerProfile.selector(_q9("RgdGTOa///2t"), _q9("TVQaTdyi+ejlNj38rOlWqHhbUciElg==")))
            ?: return null
        val title = titleNode.text().trim().takeIf { it.isNotBlank() } ?: return null
        val url = titleNode.attr(_q9("TRRRTg==")).trim().takeIf(::isHttpUrl) ?: return null
        val categories = card.select(providerProfile.selector(_q9("RgdGTPG3//SvLSbhpf8="), _q9("CwFZWp+75OehJ3nnrqwX")))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        if (shouldBlockContent(categories = categories)) return null

        val poster = card.selectFirst(providerProfile.selector(_q9("RgdGTOK5+OWtMA=="), _q9("CwVbRsaz5eXlNjz9re4YqEpfA8SPrA==")))?.imageUrl()
        val year = YEAR_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val isSeries = url.contains(_q9("ChJCBw=="), ignoreCase = true) ||
            card.selectFirst(providerProfile.selector(_q9("RgdGTOav+/SFIybjpf4="), _q9("CwFZWp+m5OK8Ni34paEfvUZe")))
                ?.text()?.contains(_q9("cTAUe9q5/A=="), ignoreCase = true) == true

        return if (isSeries) {
            val episodeCount = card.selectFirst(providerProfile.selector(_q9("RgdGTPem4uKnJjE="), _q9("CwFZWp+4/vyqJyT74P8GqE0=")))
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

    private fun findDetailTitle(document: Document): String? {
        val selectors = listOf(
            providerProfile.selector(_q9("QQNASdu6w/SpJj3mpw=="), _q9("TVcaTdyi+ejlNj38rOktoFdWTt2QpPSRBITbFeYdMg==")),
            _q9("TVcaTdyi+ejlNj38rOktoFdWTt2QpPSRSIvXHd4="),
            _q9("TVcaTdyi+ejlNj38rOk="),
            _q9("SAddRpK+ug=="),
            _q9("RBRAQdG67rGgcw=="),
        )
        return selectors.asSequence()
            .mapNotNull { selector -> document.selectFirst(selector)?.text()?.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun findDetailPoster(document: Document): String? {
        val selectors = listOf(
            providerProfile.selector(_q9("QQNASdu62/67NjH6"), _q9("CwFZWp+75OehJ3nsofgX6UVaRNiQrqTFS40=")),
            _q9("CwFZWp+75OehJ3nsofgX6UVaRNiQrqrcU4bWVe9aCWsFD1lP"),
            _q9("Qw9TXcCzpeG9LjilrOkQvQMNA8SPrA=="),
            _q9("RBRAQdG67rGuKzP9sulWoE5U"),
        )
        selectors.asSequence().mapNotNull { selector ->
            document.selectFirst(selector)?.imageUrl()
        }.firstOrNull()?.let { return it }
        return document.selectFirst(_q9("SANASemm+f64Jyb8ubEZrhlaTsyFrtk="))
            ?.attr(_q9("RglaXNe4/w=="))?.trim()?.takeIf(::isHttpUrl)
    }

    private fun findDetailDescription(document: Document): String? {
        val selectors = listOf(
            providerProfile.selector(_q9("QQNASdu6z/S7ISbhsPgfpk0="), _q9("fg9ATd+m+f64fzDts+8EoFNHSsKMlqSSBpo=")),
            _q9("fg9ATd+m+f64fzDts+8EoFNHSsKMlqSSBpo="),
            _q9("CwNaXMCvpvKnLCDtrvgtoFdWTt2QpPSRQo/JG/FWH2tMCVp1kuir4Q=="),
            _q9("RBRAQdG67rGTKyDtrfwEplMOR8iRqPbFVp7TF+1iT28="),
        )
        return selectors.asSequence()
            .mapNotNull { selector -> document.selectFirst(selector)?.text()?.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun findEpisodeLinks(document: Document): List<Element> {
        val selectors = listOf(
            providerProfile.selector(_q9("QBZdW92y7t2hLD/7"), _q9("CwFZWp+64uK8MTH6qekF6UJoS9+Hra6RBMXfCPAQTUI=")),
            _q9("QQ9CBtW7+bykKyf8s+kEoEZAA8y5o/bJQMCHWqxaH2wKRGk="),
            _q9("QQ9CBsS/77ytMj37r+gTugNSeMWQruKGG8iVHfNMQD14"),
            _q9("REhWXcai5P+TKibtpqZL6wxWU97N6dk="),
        )
        val byUrl = linkedMapOf<String, Element>()
        selectors.forEach { selector ->
            document.select(selector).forEach { node ->
                val href = node.attr(_q9("TRRRTg==")).trim()
                if (href.contains(_q9("CgNEW50="), ignoreCase = true)) byUrl.putIfAbsent(href, node)
            }
        }
        return byUrl.values.toList()
    }

    private fun Element.iframeUrl(): String = sequenceOf(
        attr(_q9("QQdASZ+64uWtMSTtpehbulFQ")),
        attr(_q9("QQdASZ+l+fI=")),
        attr(_q9("VhRX")),
    ).map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun resolveHttpUrl(raw: String?, baseUrl: String): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() && !it.equals(_q9("TwdCScG1+fi4Nm7uoeAFrA=="), true) } ?: return null
        return runCatching {
            val resolved = if (isHttpUrl(value)) URI(value) else URI(baseUrl).resolve(value)
            resolved.toString().takeIf(::isHttpUrl)
        }.getOrNull()
    }

    private fun parseGenres(document: Document): List<String> {
        val metadataSelector = providerProfile.selector(_q9("QQNASdu6xvS8IzDptO0="), _q9("CwVbRsaz5eXlLzv+qekSqFdSA4OFpvaBS4XMEeZbDmtE"))
        val row = document.select(metadataSelector).firstOrNull {
            it.text().trim().startsWith(_q9("YgNaWtfs"), ignoreCase = true)
        } ?: return emptyList()
        val linked = row.select("a").map { it.text().trim() }.filter { it.isNotBlank() }
        if (linked.isNotEmpty()) return linked.distinct()
        return row.text().substringAfter(':', "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun parseReleaseYear(document: Document): Int? {
        val metadataSelector = providerProfile.selector(_q9("QQNASdu6xvS8IzDptO0="), _q9("CwVbRsaz5eXlLzv+qekSqFdSA4OFpvaBS4XMEeZbDmtE"))
        val releaseText = document.select(metadataSelector).firstOrNull {
            it.text().trim().startsWith(_q9("dw9YQcHs"), ignoreCase = true)
        }?.text() ?: return null
        return RELEASE_YEAR_REGEX.find(releaseText)?.value?.toIntOrNull()
    }

    private fun Element.imageUrl(): String? = sequenceOf(
        attr(_q9("QQdASZ+l+fI=")),
        attr(_q9("QQdASZ+66uuxbyf6ow==")),
        attr(_q9("VhRX")),
    ).map { it.trim() }.firstOrNull(::isHttpUrl)

    private fun buildArchiveUrl(source: String, page: Int): String {
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

    private suspend fun ensureMainUrl() {
        if (mainUrlResolved) return
        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock
            val remoteCandidates = runCatching {
                JSONObject(app.get(providerProfile.websiteJsonUrl).text).readMainUrlCandidates()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidates + providerProfile.defaultMainUrl)
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
            mainUrl = providerProfile.defaultMainUrl
            mainUrlResolved = true
        }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(providerProfile.websiteKey) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index) }
            .mapNotNull(::normalizeHttpBaseUrl)
            .distinct()
    }

    private fun syncMainUrl(responseUrl: String?) {
        normalizeHttpBaseUrl(responseUrl)?.let { mainUrl = it }
    }

    private fun normalizeHttpBaseUrl(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if ((scheme == _q9("TRJAWA==") || scheme == _q9("TRJAWME=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else null
        }.getOrNull()
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
            throw ErrorLoadingException(_q9("bglaXNe4q/WhIDjnq+UE6UxfRsXCoOvCQIPdDfFeHHYFFkZHxL/v9Lo="))
        }
    }

    private fun normalizeTaxonomyName(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)

    private fun isHttpUrl(value: String?): Boolean = value?.let {
        it.startsWith(_q9("TRJAWMHspL4="), ignoreCase = true) || it.startsWith(_q9("TRJAWIj5pA=="), ignoreCase = true)
    } == true

    private data class PlayerCandidate(
        val label: String,
        val url: String,
    )

    companion object {
        private val YEAR_REGEX = Regex(_q9("eU4cAI3suqi0cGShnOgN+14af4Q="))
        private val RELEASE_YEAR_REGEX = Regex(_q9("DVkOGYuquaHhHjDz8vE="))
        private val SEASON_REGEX = Regex(_q9("dgNVW9241+Ljagjs66U="), RegexOption.IGNORE_CASE)
        private val EPISODE_URL_REGEX = Regex(_q9("QBZdW92y7rzgHjCj6Q=="), RegexOption.IGNORE_CASE)
        private val EPISODE_TEXT_REGEX = Regex(_q9("DVkOTcKltO2tMj37r+gT4H9ACYW+r6+F"), RegexOption.IGNORE_CASE)
        private val PLAY_SELECTED_REGEX = Regex("""playSelectedVideo\(\s*['\"]([^'\"]+)['\"]\s*\)""", RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex(_q9("eRUf"))
    }
}

private class DutaMovieHglink : StreamWishExtractor() {
    override val name = _q9("bQFYQdy9")
    override val mainUrl = _q9("TRJAWMHspL6gJTjhrudYvUw=")
}
