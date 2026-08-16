package com.agooseangsa.DutaMovie21

import com.agooseangsa.DutaMovie21.shared.AgooseFailoverPolicy
import com.agooseangsa.DutaMovie21.shared.AgoosePlaybackFailover
import com.lagradost.api.Log
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
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.Playmogo
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
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
    override var name = _q9("E422KGvP1H6/UXOKWA==")
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
        val searchPath = providerProfile.endpoint(_q9("JJ2jOyjq62miXA=="), "/").ifBlank { "/" }
        val searchParam = providerProfile.endpoint(_q9("JJ2jOyjq62mkVT4="), "s").ifBlank { "s" }
        val base = if (searchPath.startsWith(_q9("P4y2OQ=="))) searchPath else "$mainUrl${normalizePath(searchPath)}"
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
            ?: throw ErrorLoadingException(_q9("H5muKCbj1SiyUSfZAB6Hkp4sFsx1fuXu9p5WjWzYPu0zja5pP+vfab0UN9EdF8qTnCkZ"))
        val poster = findDetailPoster(document)
        val description = findDetailDescription(document)
        val genres = parseGenres(document)
        val tags = emptyList<String>()
        enforceContentAllowed(genres, tags)

        val year = YEAR_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: parseReleaseYear(document)
        val episodeLinks = findEpisodeLinks(document)
        val isSeries = response.url.contains(_q9("eIy0Zg=="), ignoreCase = true) || episodeLinks.isNotEmpty()

        return if (isSeries) {
            if (response.url.contains(_q9("eIy0Zg=="), ignoreCase = true) && episodeLinks.isEmpty()) {
                throw ErrorLoadingException(_q9("H5muKCbj1SiyUSfZAB6HlZI6HsY5KvT55p1UjzfUdOwyjKM5IqLfabBAMspJF9ePhCcTwnV+6fj1lBWIP4wx9SKToyc="))
            }

            val season = SEASON_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val episodes = episodeLinks.mapNotNull { node ->
                val episodeUrl = resolveHttpUrl(node.attr(_q9("P4qnLw==")), response.url) ?: return@mapNotNull null
                val episodeNumber = EPISODE_URL_REGEX.find(episodeUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: EPISODE_TEXT_REGEX.find(node.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: EPISODE_TEXT_REGEX.find(node.attr(_q9("I5G2JS4=")))?.groupValues?.getOrNull(1)?.toIntOrNull()
                val displayName = episodeNumber?.let { "Episode $it" }
                    ?: node.attr(_q9("I5G2JS4=")).removePrefix(_q9("B52wJCru0ma9FDjdSQ==")).trim().takeIf { it.isNotBlank() }
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

        val providerDownloadUrls = discoverProviderDownloadUrls(
            document = pageResponse.document,
            pageUrl = pageResponse.url,
        )
        val playerUrls = discoverDetailPlayerUrls(
            document = pageResponse.document,
            pageUrl = pageResponse.url,
        )
        if (playerUrls.isEmpty() && providerDownloadUrls.isEmpty()) {
            return false
        }

        var sawEligibleCandidate = providerDownloadUrls.isNotEmpty()
        for (playerUrl in playerUrls) {
            val isPlaysobatWrapper = runCatching {
                URI(playerUrl).host?.contains(_q9("J5SjMDjt2WmiGivBEw=="), ignoreCase = true) == true
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
                candidates = discoverPlaysobatCandidates(
                    wrapperUrl = playerUrl,
                    pageReferer = pageResponse.url,
                    wrapperDocument = wrapperResponse.document,
                    wrapperText = wrapperResponse.text,
                )
                referer = playerUrl
            } else {
                candidates = listOf(
                    PlayerCandidate(
                        label = URI(playerUrl).host?.uppercase(Locale.ROOT) ?: _q9("BL2QHw7Q"),
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

        for (downloadUrl in providerDownloadUrls) {
            if (resolveProviderDownloadFallback(
                    downloadUrl = downloadUrl,
                    pageUrl = pageResponse.url,
                    callback = callback,
                )
            ) return true
        }

        return if (sawEligibleCandidate) emitOfflineIndicator(callback) else false
    }

    private fun discoverProviderDownloadUrls(document: Document, pageUrl: String): List<String> {
        val selectors = listOf(
            providerProfile.selector(
                _q9("M5e1Jyft2myaXT3TGg=="),
                _q9("eZ+vO2bm1H+4WDzZDV/Lj4Q8V8YOYvL58tUIzjKUIqp5iK4oMvHUardAfcAQCIiCmD8Zyzpr5LLkl0XOCw=="),
            ),
            _q9("eZ+vO2bm1H+4WDzZDV/Lj4Q8V8YOYvL58qI="),
            _q9("dJytPiXu1GmyFDLjAQDCgKo="),
        )
        val found = linkedSetOf<String>()
        selectors.forEach { selector ->
            document.select(selector).forEach { node ->
                val url = resolveHttpUrl(node.attr(_q9("P4qnLw==")), pageUrl) ?: return@forEach
                val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
                if (host == _q9("M5S0e2Xy12mvRzzaCAaJno4y") || url.contains(_q9("eJytPiXu1GmyGiPQGQ=="), ignoreCase = true)) {
                    found += url
                }
            }
        }
        return found.toList()
    }

    private suspend fun resolveProviderDownloadFallback(
        downloadUrl: String,
        pageUrl: String,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        Log.d(PLAYBACK_TAG, "provider download fallback start url=$downloadUrl")

        val head = try {
            withTimeoutOrNull(5_000L) { app.head(downloadUrl, referer = pageUrl) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            null
        }
        if (head != null) {
            val headUrl = head.url.takeIf(::isHttpUrl) ?: downloadUrl
            val contentType = head.headers[_q9("FJesPS7szyWCTSPd")].orEmpty().lowercase(Locale.ROOT)
            val disposition = head.headers[_q9("FJesPS7szyWSXSDIBgHOkp4nGQ==")].orEmpty().lowercase(Locale.ROOT)
            if (isDirectMediaUrl(headUrl) || contentType.startsWith(_q9("IZGmLCSt")) ||
                contentType.contains(_q9("OoinLj7w1w==")) || disposition.contains(_q9("eZWyfQ=="))
            ) {
                if (emitDirectMediaLink(
                        label = _q9("E62WCAbN7UGTFBf3PjzrqbYM"),
                        mediaUrl = headUrl,
                        referer = pageUrl,
                        headers = emptyMap(),
                        callback = callback,
                        forceVideo = contentType.startsWith(_q9("IZGmLCSt")) || disposition.contains(_q9("eZWyfQ==")),
                    )
                ) {
                    Log.d(PLAYBACK_TAG, "provider download fallback HEAD media=$headUrl")
                    return true
                }
            }
        }

        val htmlResponse = try {
            withTimeoutOrNull(6_000L) { app.get(downloadUrl, referer = pageUrl) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            null
        }
        if (htmlResponse != null) {
            val responseUrl = htmlResponse.url
            val contentType = htmlResponse.headers[_q9("FJesPS7szyWCTSPd")].orEmpty().lowercase(Locale.ROOT)
            if (isDirectMediaUrl(responseUrl) || contentType.startsWith(_q9("IZGmLCSt")) || contentType.contains(_q9("OoinLj7w1w=="))) {
                if (emitDirectMediaLink(
                        label = _q9("E62WCAbN7UGTFBf3PjzrqbYM"),
                        mediaUrl = responseUrl,
                        referer = pageUrl,
                        headers = emptyMap(),
                        callback = callback,
                        forceVideo = contentType.startsWith(_q9("IZGmLCSt")),
                    )
                ) return true
            }

            val direct = linkedSetOf<String>()
            htmlResponse.document.select(_q9("NqOqOy7k5iT2RzzNGxHCvYQ6FPp5Kvb18JpatyWKN8U=")).forEach { node ->
                val raw = node.attr(_q9("P4qnLw==")).ifBlank { node.attr(_q9("JIqh")) }
                resolveHttpUrl(raw, responseUrl)?.takeIf(::isDirectMediaUrl)?.let(direct::add)
            }
            DIRECT_MEDIA_URL_REGEX.findAll(htmlResponse.text.replace("\\/", "/")).forEach { match ->
                match.value.replace(_q9("cZmvOXA="), "&").takeIf(::isDirectMediaUrl)?.let(direct::add)
            }
            for (mediaUrl in direct) {
                if (emitDirectMediaLink(_q9("E62WCAbN7UGTFBf3PjzrqbYM"), mediaUrl, responseUrl, emptyMap(), callback)) return true
            }
        }

        val resolver = WebViewResolver(
            interceptUrl = DIRECT_MEDIA_REQUEST_REGEX,
            useOkhttp = false,
            script = DOWNLOAD_PAGE_SCAN_SCRIPT,
            timeout = 7_000L,
        )
        val request = try {
            resolver.resolveUsingWebView(
                url = htmlResponse?.url ?: downloadUrl,
                referer = pageUrl,
            ).first
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            Log.d(PLAYBACK_TAG, "provider download WebView failed ${error::class.simpleName}")
            null
        } ?: return false

        val mediaUrl = request.url.toString().takeIf(::isDirectMediaUrl) ?: return false
        val headers = request.headers.toMap()
        val success = emitDirectMediaLink(
            label = _q9("E62WCAbN7UGTFBf3PjzrqbYM"),
            mediaUrl = mediaUrl,
            referer = htmlResponse?.url ?: downloadUrl,
            headers = headers,
            callback = callback,
        )
        if (success) Log.d(PLAYBACK_TAG, "provider download WebView media=$mediaUrl")
        return success
    }

    private suspend fun emitDirectMediaLink(
        label: String,
        mediaUrl: String,
        referer: String,
        headers: Map<String, String>,
        callback: (ExtractorLink) -> Unit,
        forceVideo: Boolean = false,
    ): Boolean {
        if (!forceVideo && !isDirectMediaUrl(mediaUrl)) return false
        val isM3u8 = mediaUrl.substringBefore('?').substringBefore('#').endsWith(_q9("eZXxPHM="), ignoreCase = true)
        val link = if (isM3u8) {
            M3u8Helper.generateM3u8(label, mediaUrl, referer, headers = headers).firstOrNull()
        } else {
            newExtractorLink(source = label, name = label, url = mediaUrl) {
                this.referer = referer
                quality = Qualities.Unknown.value
                this.headers = headers
            }
        } ?: return false
        callback(link)
        return true
    }

    private fun isDirectMediaUrl(url: String): Boolean = DIRECT_MEDIA_REQUEST_REGEX.containsMatchIn(url)

    private suspend fun discoverDetailPlayerUrls(
        document: Document,
        pageUrl: String,
    ): List<String> {
        val found = linkedSetOf<String>()

        fun addIframe(node: Element?) {
            val raw = node?.iframeUrl().orEmpty()
            resolveHttpUrl(raw, pageUrl)?.takeIf { !it.equals(_q9("PZm0KDjhyWGmQGneCB7Ugw=="), true) }?.let(found::add)
        }

        val profileSelector = providerProfile.selector(
            _q9("OpmrJxvu2nGzRhreGxPKgw=="),
            _q9("eZ+vO2bx3nqgUSGVHgDGltdmEMonJ+Xx9ppRwSSdJ+g4lrEgPeebYbBGMtUMKdSUlBU="),
        )
        document.select(profileSelector).forEach(::addIframe)
        document.select(
            _q9("eZ+vO2bx3nqgUSGVHgDGltchEdU0Z+XH541WsXrYev86iu86LvDNbaQZJMoIAoePkToWyjBR5P3gnhiAP4wx6yedpy1m8clrixhz") +
                _q9("eZ+vO2bn1mqzUH7KDAHXiZk7HtEwKun65p5YiQ2LJvsK1OJnLO/JJbNZMd0NX9WDhDgYySZj9vm0llOeN5UxwzOZtihm7tJ8s0cj3QwWipWFKyqLdQ==") +
                _q9("Pp6wKCbn4HukV3mFGR7Gn4QnFcYhJPjl7qIZzD+eJvk6nZktKvbaJbpdJ90aAsKDk2UE1TYgvez4nkyfOZo17HmAuzMW"),
        ).forEach(::addIframe)

        document.select(providerProfile.selector(_q9("J5SjMC7w72m0Rw=="), _q9("IpTsJD700nikW37IBRPeg4VlA8Y3eaDw/d9Utz6KMf4K"))).forEach { tab ->
            val tabUrl = resolveHttpUrl(tab.attr(_q9("P4qnLw==")), pageUrl) ?: return@forEach
            if (tabUrl == pageUrl && found.isNotEmpty()) return@forEach
            val tabResponse = runCatching { app.get(tabUrl, referer = pageUrl) }.getOrNull() ?: return@forEach
            tabResponse.document.select(
                _q9("eZ+vO2bn1mqzUH7KDAHXiZk7HtEwKun65p5YiQ2LJvsK1OJnLO/JJbNZMd0NX9WDhDgYySZj9vm0llOeN5UxwzOZtihm7tJ8s0cj3QwWipWFKyo="),
            ).forEach { addIframe(it) }
        }

        val postId = document.selectFirst(providerProfile.selector(_q9("J5SjMC7w+mK3TBrc"), _q9("M5G0aib3zWGmRjznGR7Gn5I6KMQ6ZPT5+otqhTKjMPkjme8gL98=")))
            ?.attr(_q9("M5m2KGbr3w=="))?.trim()?.takeIf { it.isNotBlank() }
        if (postId != null) {
            val origin = normalizeHttpBaseUrl(pageUrl)
            if (origin != null) {
                document.select(providerProfile.selector(_q9("J5SjMC7w+mK3TAfZCwE="), _q9("M5G0Zz/j2SW1Wz3MDBzTy5YiFt8OY+TB"))).forEach { tab ->
                    val tabId = tab.attr("id").trim().takeIf { it.isNotBlank() } ?: return@forEach
                    val ajax = runCatching {
                        app.post(
                            "$origin/wp-admin/admin-ajax.php",
                            referer = pageUrl,
                            data = mapOf(
                                _q9("Npu2ICTs") to _q9("Oo20IDvw1FemWDLBDAD4hZgmA8I7fg=="),
                                _q9("I5mg") to tabId,
                                _q9("J5exPRTr3w==") to postId,
                            ),
                        )
                    }.getOrNull() ?: return@forEach
                    ajax.document.select(_q9("Pp6wKCbn4HukVw6USRvBlJYlEvwxa/T9uZNcmDOLJP0ynO86OeHm")).forEach { iframe ->
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
        Log.d(PLAYBACK_TAG, "resolve start label=${candidate.label} url=${candidate.url}")

        val pendingLinks = mutableListOf<ExtractorLink>()
        val pendingSubtitles = mutableListOf<SubtitleFile>()
        val host = runCatching { URI(candidate.url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")

        suspend fun runExtractor(extractor: ExtractorApi): Boolean {
            pendingLinks.clear()
            pendingSubtitles.clear()
            return try {
                extractor.getUrl(
                    url = candidate.url,
                    referer = wrapperUrl,
                    subtitleCallback = { pendingSubtitles += it },
                ) { pendingLinks += it }
                flushValidatedLinks(candidate, pendingLinks, pendingSubtitles, subtitleCallback, callback)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                Log.d(PLAYBACK_TAG, "extractor ${extractor.name} failed ${error::class.simpleName}")
                false
            }
        }

        when {
            host == _q9("M5etLWXu2g==") || host == _q9("J5SjMCbt3Gf4VzzV") -> {
                if (runExtractor(Playmogo())) return true
            }
            host == _q9("M5GsPS74zn6/W33bBh8=") -> {
                if (runExtractor(DutaMovieVidHide())) return true
            }
            host == _q9("OpykMXLm2DC4Gj3dHQ==") -> {
                if (runExtractor(DutaMovieMixDrop())) return true
            }
        }

        pendingLinks.clear()
        pendingSubtitles.clear()
        try {
            loadExtractor(
                url = candidate.url,
                referer = wrapperUrl,
                subtitleCallback = { pendingSubtitles += it },
            ) { pendingLinks += it }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            Log.d(PLAYBACK_TAG, "built-in extractor failed ${error::class.simpleName}")
        }
        if (flushValidatedLinks(candidate, pendingLinks, pendingSubtitles, subtitleCallback, callback)) return true

        if (host.endsWith(_q9("Npq7Ojjy12mvUSGWCh3K"))) {
            Log.d(PLAYBACK_TAG, _q9("H6GGGwralEm0TSDLSRHGiJMhE8Yhb6Du8Y5AhSSdJ7gknaUkLuzPbbIUJ8oIHNSWmDoDnHVk77zwlkeJNYx09TKcqyhr59ZhokA23A=="))
            return false
        }

        val webViewUrl = normalizeCandidateForWebView(candidate)
        val mediaRegex = Regex(providerProfile.playbackString(_q9("Op2mICrQ3nmjUSDMOxfAg48="), _q9("f8erYGO9gVT4WWDNUQ77yJo4Q459NbrHq9xokHLR")))
        val timeout = (providerProfile.failover.serverResolveTimeoutMs - 4_000).coerceIn(2_000, 6_000).toLong()
        val mediaResponse = try {
            app.get(
                webViewUrl,
                referer = wrapperUrl,
                interceptor = WebViewResolver(mediaRegex, timeout = timeout),
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            Log.d(PLAYBACK_TAG, "webview fallback failed ${error::class.simpleName}")
            return false
        }

        val mediaUrl = mediaResponse.url.takeIf { mediaRegex.containsMatchIn(it) } ?: return false
        val headers = mediaResponse.headers.toMap()
        val link = if (mediaUrl.substringBefore('?').substringBefore('#').endsWith(_q9("eZXxPHM="), ignoreCase = true)) {
            M3u8Helper.generateM3u8(candidate.label, mediaUrl, webViewUrl, headers = headers).firstOrNull()
        } else {
            newExtractorLink(source = candidate.label, name = candidate.label, url = mediaUrl) {
                referer = webViewUrl
                quality = Qualities.Unknown.value
                this.headers = headers
            }
        } ?: return false

        if (!validateResolvedLink(link)) return false
        callback(link)
        Log.d(PLAYBACK_TAG, "resolve success label=${candidate.label} media=${link.url}")
        return true
    }

    private suspend fun flushValidatedLinks(
        candidate: PlayerCandidate,
        links: List<ExtractorLink>,
        subtitles: List<SubtitleFile>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        if (links.isEmpty()) return false
        val valid = mutableListOf<ExtractorLink>()
        for (link in links.distinctBy { it.url }) {
            if (validateResolvedLink(link)) valid += link
        }
        if (valid.isEmpty()) {
            Log.d(PLAYBACK_TAG, "extractor emitted ${links.size} link(s), validation rejected all for ${candidate.label}")
            return false
        }
        subtitles.distinctBy { it.url }.forEach(subtitleCallback)
        valid.forEach(callback)
        Log.d(PLAYBACK_TAG, "resolve success label=${candidate.label} validated=${valid.size}")
        return true
    }

    private suspend fun validateResolvedLink(link: ExtractorLink): Boolean {
        if (!isHttpUrl(link.url)) return false
        return try {
            when (link.type) {
                ExtractorLinkType.M3U8 -> {
                    val response = withTimeoutOrNull(3_000L) {
                        app.get(
                            link.url,
                            headers = link.headers,
                            referer = link.referer,
                        )
                    } ?: return false
                    response.isSuccessful && response.text.contains(_q9("dL2aHQax7g=="), ignoreCase = true)
                }
                ExtractorLinkType.VIDEO -> {

                    val response = withTimeoutOrNull(2_500L) {
                        runCatching {
                            app.head(
                                link.url,
                                headers = link.headers,
                                referer = link.referer,
                            )
                        }.getOrNull()
                    } ?: return true
                    val contentType = response.headers[_q9("FJesPS7szyWCTSPd")].orEmpty().lowercase(Locale.ROOT)
                    !contentType.contains(_q9("I526PWTqz2W6"))
                }
                else -> true
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun emitOfflineIndicator(callback: (ExtractorLink) -> Unit): Boolean {
        val offline = providerProfile.offlineIndicator
        if (!offline.enabled || offline.mediaSource.isBlank()) return false
        if (offline.mediaSource.contains(_q9("FLeMHQTK5F6fcBb3Nj3hoLsBOeI="), ignoreCase = true)) {
            Log.d(PLAYBACK_TAG, _q9("OJ6kJSLs3ii7UTfRCCHIk4UrEoc8eaDv4JZZgHaZdOg7maEsI+3XbLNGaJgHHdOOniYQhyJj7PC0nVDMM5U97COdpg=="))
            return false
        }

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

    private suspend fun discoverPlaysobatCandidates(
        wrapperUrl: String,
        pageReferer: String,
        wrapperDocument: Document,
        wrapperText: String,
    ): List<PlayerCandidate> {
        val staticCandidates = discoverWrapperCandidates(wrapperDocument)
        val shouldRender = staticCandidates.isEmpty() ||
            wrapperText.contains(_q9("IJGsLST1lXi3TT/XCBY="), ignoreCase = false) ||
            wrapperText.contains(_q9("NouxLD/xlHi6VSrdG1zNlQ=="), ignoreCase = true)

        if (!shouldRender) return staticCandidates

        val runtimeCandidates = runCatching {
            val resolver = WebViewResolver(
                interceptUrl = NEVER_MATCH_REGEX,
                additionalUrls = listOf(PLAYSOBAT_SERVER_REQUEST_REGEX),
                useOkhttp = false,
                script = PLAYSOBAT_SERVER_SCAN_SCRIPT,
                timeout = PLAYSOBAT_DISCOVERY_TIMEOUT_MS,
            )
            val (_, requests) = resolver.resolveUsingWebView(
                url = wrapperUrl,
                referer = pageReferer,
            )
            requests.mapNotNull { request ->
                runtimePlayerCandidate(request.url.toString())
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.d(PLAYBACK_TAG, "playsobat WebView discovery failed ${error::class.simpleName}")
        }.getOrDefault(emptyList())

        val merged = (runtimeCandidates + staticCandidates)
            .distinctBy { candidateIdentity(it.url) }
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<PlayerCandidate>> { candidatePriority(it.value) }
                    .thenBy { it.index },
            )
            .map { it.value }

        Log.d(
            PLAYBACK_TAG,
            "playsobat discovery static=${staticCandidates.size} runtime=${runtimeCandidates.size} merged=${merged.size}",
        )
        return merged
    }

    private fun runtimePlayerCandidate(url: String): PlayerCandidate? {
        if (!isHttpUrl(url)) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase(Locale.ROOT).removePrefix(_q9("II+1Zw=="))
        val path = uri.path.orEmpty()
        val isServerRequest = when {
            host == _q9("Npq7Ojjy12mvUSGWCh3K") || host == _q9("LYiuKDKs2mqvRyDIBRPeg4VmFMg4") -> path.trim('/').isNotBlank()
            host == _q9("M5GsPS74zn6/W33bBh8=") -> path.startsWith(_q9("eJ2vKy7mlA=="))
            host == _q9("P5+uICXplXy5") -> path.startsWith(_q9("eJ3t"))
            host == _q9("OpykMXLm2DC4Gj3dHQ==") -> path.startsWith(_q9("eJ3t"))
            host == _q9("M5etLWXu2g==") || host == _q9("J5SjMCbt3Gf4VzzV") -> path.startsWith(_q9("eJ3t"))
            host == _q9("NJStPC/y12mvGiOKGQHTlJIpGokjY/A=") -> true
            else -> false
        }
        if (!isServerRequest) return null
        return PlayerCandidate(label = inferCandidateLabel(url), url = url)
    }

    private fun discoverWrapperCandidates(document: Document): List<PlayerCandidate> {
        val serverSelector = providerProfile.selector(_q9("IIqjOTvnyVuzRiXdGz7OiJw7"), _q9("dIunOz3nyXv2VQjXBxHLj5QjKg=="))
        val serverCandidates = document.select(serverSelector).mapNotNull { node ->
            val onclick = node.attr(_q9("OJahJSLh0A=="))
            val url = PLAY_SELECTED_REGEX.find(onclick)?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf(::isHttpUrl)
                ?: return@mapNotNull null
            val label = node.text().trim().ifBlank { URI(url).host ?: _q9("BL2QHw7Q") }
            PlayerCandidate(label = label, url = url)
        }.distinctBy { candidateIdentity(it.url) }

        val currentUrl = document
            .selectFirst(providerProfile.selector(_q9("IIqjOTvnyUujRiHdBwbugIUpGsI="), _q9("dI6rLS7t62S3TTbKSRvBlJYlEvwmeOPB")))
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

        return ordered
            .distinctBy { candidateIdentity(it.url) }
            .withIndex()
            .sortedWith(compareBy<IndexedValue<PlayerCandidate>> { candidatePriority(it.value) }.thenBy { it.index })
            .map { it.value }
    }

    private fun candidatePriority(candidate: PlayerCandidate): Int {
        val host = runCatching { URI(candidate.url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return when {
            host == _q9("M5etLWXu2g==") || host == _q9("J5SjMCbt3Gf4VzzV") -> 0
            host == _q9("P5+uICXplXy5") -> 1
            host == _q9("M5GsPS74zn6/W33bBh8=") -> 2
            host == _q9("OpykMXLm2DC4Gj3dHQ==") -> 3
            host.contains(_q9("J8qyOj/w3mm7")) -> 4
            host.endsWith(_q9("Npq7Ojjy12mvUSGWCh3K")) -> 9
            else -> 5
        }
    }

    private fun inferCandidateLabel(url: String): String {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return when {
            host.contains(_q9("Npq7Ojjy12mvUSE=")) -> _q9("H6GGGwra")
            host.contains(_q9("P5+uICXp")) -> _q9("BKyQDArP7EGFfA==")
            host.contains(_q9("M5etLQ==")) -> _q9("E7eNDRjW6U2XeQ==")
            host.contains(_q9("OpG6LTntyw==")) || host.contains(_q9("OpykMQ==")) -> _q9("GrGaDRnN6w==")
            host.contains(_q9("J8qyOj/w3mm7")) -> _q9("BKyQDArP6zqG")
            else -> host.ifBlank { _q9("E72ECB7O7w==") }.uppercase(Locale.ROOT)
        }
    }

    private fun candidateIdentity(url: String): String = runCatching {
        val uri = URI(url)
        val host = uri.host.orEmpty().lowercase(Locale.ROOT).removePrefix(_q9("II+1Zw=="))
        val videoId = uri.path.orEmpty().trimEnd('/').substringAfterLast('/')
        when {
            host.endsWith(_q9("Npq7Ojjy12mvUSGWCh3K")) -> "abyssplayer|$videoId"
            else -> "$host|${uri.path.orEmpty().trimEnd('/')}|${uri.fragment.orEmpty()}"
        }
    }.getOrDefault(url)

    private fun normalizeCandidateForWebView(candidate: PlayerCandidate): String {
        if (!candidate.label.equals(_q9("H6GGGwra"), ignoreCase = true)) return candidate.url
        return runCatching {
            val uri = URI(candidate.url)
            if (uri.host.equals(_q9("Npq7Ojjy12mvUSGWCh3K"), ignoreCase = true)) {
                URI(_q9("P4y2OTg="), _q9("LYiuKDKs2mqvRyDIBRPeg4VmFMg4"), uri.path, uri.query, uri.fragment).toString()
            } else {
                candidate.url
            }
        }.getOrDefault(candidate.url)
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val cardSelector = providerProfile.selector(_q9("NJmwLQ=="), _q9("Noq2ICju3ia/QDbVRBvJgJ4mHtMw"))
        return document.select(cardSelector).mapNotNull(::toSearchResponse)
    }

    private fun toSearchResponse(card: Element): SearchResponse? {
        val titleNode = card.selectFirst(providerProfile.selector(_q9("NJmwLR/rz2Sz"), _q9("P8rsLCX2yXH7QDrMBReHh6wgBcIzVw==")))
            ?: return null
        val title = titleNode.text().trim().takeIf { it.isNotBlank() } ?: return null
        val url = titleNode.attr(_q9("P4qnLw==")).trim().takeIf(::isHttpUrl) ?: return null
        val categories = card.select(providerProfile.selector(_q9("NJmwLQjjz22xWyHRDAE="), _q9("eZ+vO2bv1H6/UX7XB1LG")))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        if (shouldBlockContent(categories = categories)) return null

        val poster = card.selectFirst(providerProfile.selector(_q9("NJmwLRvtyHyzRg=="), _q9("eZutJz/n1Xz7QDvNBBDJh54kV844bQ==")))?.imageUrl()
        val year = YEAR_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val isSeries = url.contains(_q9("eIy0Zg=="), ignoreCase = true) ||
            card.selectFirst(providerProfile.selector(_q9("NJmwLR/7y22bVSHTDAA="), _q9("eZ+vO2by1HuiQCrIDF/OkpIl")))
                ?.text()?.contains(_q9("A67iGiPtzA=="), ignoreCase = true) == true

        return if (isSeries) {
            val episodeCount = card.selectFirst(providerProfile.selector(_q9("NJmwLQ7y0nu5UDY="), _q9("eZ+vO2bszmW0USPLSQHXh5k=")))
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
            providerProfile.selector(_q9("M522KCLu8223UDrWDg=="), _q9("P8nsLCX2yXH7QDrMBRf8j4MtGtcnZfChtpFUgTPaCQ==")),
            _q9("P8nsLCX2yXH7QDrMBRf8j4MtGtcnZfCh+p5YiQs="),
            _q9("P8nsLCX2yXH7QDrMBRc="),
            _q9("OpmrJ2vqig=="),
            _q9("Noq2ICju3ii+BQ=="),
        )
        return selectors.asSequence()
            .mapNotNull { selector -> document.selectFirst(selector)?.text()?.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun findDetailPoster(document: Document): String? {
        val selectors = listOf(
            providerProfile.selector(_q9("M522KCLu62elQDbK"), _q9("eZ+vO2bv1H6/UX7cCAbGxpEhENInb6D1+Zg=")),
            _q9("eZ+vO2bv1H6/UX7cCAbGxpEhENInb67s4ZNZwTqdMux3ka8u"),
            _q9("MZGlPDnnlXijWD+VBRfBktd2V844bQ=="),
            _q9("Noq2ICju3iiwXTTNGxeHj5ov"),
        )
        selectors.asSequence().mapNotNull { selector ->
            document.selectFirst(selector)?.imageUrl()
        }.firstOrNull()?.let { return it }
        return document.selectFirst(_q9("Op22KBDyyWemUSHMEE/Igc0hGsYyb90="))
            ?.attr(_q9("NJesPS7szw=="))?.trim()?.takeIf(::isHttpUrl)
    }

    private fun findDetailDescription(document: Document): String? {
        val selectors = listOf(
            providerProfile.selector(_q9("M522KCLu/22lVyHRGQbOiZk="), _q9("DJG2LCbyyWemCTfdGhHVj4c8Hsg7V6CitI8=")),
            _q9("DJG2LCbyyWemCTfdGhHVj4c8Hsg7V6CitI8="),
            _q9("eZ2sPTn7lmu5WifdBwb8j4MtGtcnZfCh8JpGjySRJOw+l6wUa7ybeA=="),
            _q9("Noq2ICju3iiNXSfdBALViYd1E8ImafL15ItcgzildOg="),
        )
        return selectors.asSequence()
            .mapNotNull { selector -> document.selectFirst(selector)?.text()?.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun findEpisodeLinks(document: Document): List<Element> {
        val selectors = listOf(
            providerProfile.selector(_q9("MoirOiTm3kS/WjjL"), _q9("eZ+vO2bu0nuiRzbKABfUxpYTH9UwbKqhttBQnCXXdsU=")),
            _q9("M5G0ZyzvySW6XSDMGhfVj5I7V8YOYvL58tUIznmdJOt42p8="),
            _q9("M5G0Zz3r3yWzRDrLBhbCldcpLM8nb+a2qd0aiSaLe7oK"),
            _q9("NtagPD/21GaNXCHdD1iaxNgtB9R6KN0="),
        )
        val byUrl = linkedMapOf<String, Element>()
        selectors.forEach { selector ->
            document.select(selector).forEach { node ->
                val href = node.attr(_q9("P4qnLw==")).trim()
                if (href.contains(_q9("eJ2yOmQ="), ignoreCase = true)) byUrl.putIfAbsent(href, node)
            }
        }
        return byUrl.values.toList()
    }

    private fun Element.iframeUrl(): String = sequenceOf(
        attr(_q9("M5m2KGbu0nyzRyPdDBaKlYUr")),
        attr(_q9("M5m2KGbxyWs=")),
        attr(_q9("JIqh")),
    ).map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun resolveHttpUrl(raw: String?, baseUrl: String): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() && !it.equals(_q9("PZm0KDjhyWGmQGneCB7Ugw=="), true) } ?: return null
        return runCatching {
            val resolved = if (isHttpUrl(value)) URI(value) else URI(baseUrl).resolve(value)
            resolved.toString().takeIf(::isHttpUrl)
        }.getOrNull()
    }

    private fun parseGenres(document: Document): List<String> {
        val metadataSelector = providerProfile.selector(_q9("M522KCLu9m2iVTfZHRM="), _q9("eZutJz/n1Xz7WTzOABfDh4MpV4kyZ/Kx+ZBDhTOcNew2"))
        val row = document.select(metadataSelector).firstOrNull {
            it.text().trim().startsWith(_q9("EJ2sOy64"), ignoreCase = true)
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
        val metadataSelector = providerProfile.selector(_q9("M522KCLu9m2iVTfZHRM="), _q9("eZutJz/n1Xz7WTzOABfDh4MpV4kyZ/Kx+ZBDhTOcNew2"))
        val releaseText = document.select(metadataSelector).firstOrNull {
            it.text().trim().startsWith(_q9("BZGuIDi4"), ignoreCase = true)
        }?.text() ?: return null
        return RELEASE_YEAR_REGEX.find(releaseText)?.value?.toIntOrNull()
    }

    private fun Element.imageUrl(): String? = sequenceOf(
        attr(_q9("M5m2KGbxyWs=")),
        attr(_q9("M5m2KGbu2nKvGSDKCg==")),
        attr(_q9("JIqh")),
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
            if ((scheme == _q9("P4y2OQ==") || scheme == _q9("P4y2OTg=")) && !uri.host.isNullOrBlank()) {
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
            throw ErrorLoadingException(_q9("HJesPS7sm2y/Vj/XAhvVxpgkEs91Ye/y8pZSmSSZJ/F3iLAmPevfbaQ="))
        }
    }

    private fun normalizeTaxonomyName(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)

    private fun isHttpUrl(value: String?): Boolean = value?.let {
        it.startsWith(_q9("P4y2OTi4lCc="), ignoreCase = true) || it.startsWith(_q9("P4y2OXGtlA=="), ignoreCase = true)
    } == true

    private data class PlayerCandidate(
        val label: String,
        val url: String,
    )

    companion object {
        private const val PLAYBACK_TAG = "DutaMovie21Playback"
        private val YEAR_REGEX = Regex(_q9("C9DqYXS4ijGqBmORNRbc1IphK44="))
        private val RELEASE_YEAR_REGEX = Regex(_q9("f8f4eHL+iTj/aDfDWw8="))
        private val SEASON_REGEX = Regex(_q9("BJ2jOiTs53v9HA/cQls="), RegexOption.IGNORE_CASE)
        private val EPISODE_URL_REGEX = Regex(_q9("MoirOiTm3iX+aDeTQA=="), RegexOption.IGNORE_CASE)
        private val EPISODE_TEXT_REGEX = Regex(_q9("f8f4LDvxhHSzRDrLBhbCz6s7XY8Jbqu1"), RegexOption.IGNORE_CASE)
        private val PLAY_SELECTED_REGEX = Regex("""playSelectedVideo\(\s*['\"]([^'\"]+)['\"]\s*\)""", RegexOption.IGNORE_CASE)
        private val NEVER_MATCH_REGEX = Regex("""a\Ab""")
        private val PLAYSOBAT_SERVER_REQUEST_REGEX = Regex(
            """(?i)^https?://(?:www\.)?(?:abyssplayer\.com|zplay\.abyssplayer\.com|dintezuvio\.com|hglink\.to|mdfx9dc8n\.net|dood\.la|playmogo\.com|cloudplay\.p2pstream\.vip)(?:/|$)""",
        )
        private const val PLAYSOBAT_DISCOVERY_TIMEOUT_MS = 7_500L
        private val PLAYSOBAT_SERVER_SCAN_SCRIPT = """
            (function() {
                if (window.__agooseServerScanStarted) return;
                window.__agooseServerScanStarted = true;
                var attempts = 0;
                var timer = setInterval(function() {
                    attempts += 1;
                    var links = Array.prototype.slice.call(document.querySelectorAll('#servers a[onclick]'));
                    if (links.length > 0) {
                        clearInterval(timer);
                        links.forEach(function(link, index) {
                            setTimeout(function() {
                                try { link.click(); } catch (e) {}
                            }, 350 + (index * 700));
                        });
                    } else if (attempts >= 36) {
                        clearInterval(timer);
                    }
                }, 150);
            })();
        """.trimIndent()
        private val DIRECT_MEDIA_REQUEST_REGEX = Regex("""(?i)\.(?:m3u8|mp4)(?:[?#]|$)""")
        private val DIRECT_MEDIA_URL_REGEX = Regex("""(?i)https?://[^\s'\"<>]+?\.(?:m3u8|mp4)(?:\?[^\s'\"<>]*)?""")
        private val DOWNLOAD_PAGE_SCAN_SCRIPT = """
            (function() {
                if (window.__agooseDownloadScanStarted) return;
                window.__agooseDownloadScanStarted = true;
                var tries = 0;
                var timer = setInterval(function() {
                    tries += 1;
                    var nodes = Array.prototype.slice.call(document.querySelectorAll('a[href], button, input[type=button], input[type=submit]'));
                    var target = nodes.find(function(node) {
                        var text = ((node.innerText || node.value || '') + ' ' + (node.getAttribute('title') || '')).toLowerCase();
                        var href = (node.getAttribute('href') || '').toLowerCase();
                        return text.indexOf('download') >= 0 || text.indexOf('direct') >= 0 || href.indexOf('.mp4') >= 0 || href.indexOf('.m3u8') >= 0;
                    });
                    if (target) {
                        clearInterval(timer);
                        try { target.click(); } catch (e) {}
                    } else if (tries >= 30) {
                        clearInterval(timer);
                    }
                }, 150);
            })();
        """.trimIndent()
        private val WHITESPACE = Regex(_q9("C4vp"))
    }
}

private class DutaMovieVidHide : VidHidePro() {
    override val name = _q9("AZGmASLm3g==")
    override val mainUrl = _q9("P4y2OTi4lCeyXT3MDAjSkJ4nWcQ6Zw==")
}

private class DutaMovieMixDrop : MixDrop() {
    override var mainUrl = _q9("P4y2OTi4lCe7UDXAUBbE3plmGcIh")
}
