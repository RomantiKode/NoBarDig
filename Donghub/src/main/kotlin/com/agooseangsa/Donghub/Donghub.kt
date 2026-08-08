package com.agooseangsa.Donghub

import android.util.Base64
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TrailerData
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
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
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class Donghub : MainAPI() {
    override var mainUrl = _e0
    override var name = _q9("TVoaqUYs9Q==")
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        _e3 to _q9("WVoEu0I45QV7dlN01A=="),
        _e4 to _q9("RVQAq10tt3dKdVJ03mk="),
        _e5 to _q9("W1AXoUM08ktLeEN8wmI="),
    )

    private val _a0 = mapOf(
        _q9("XEYRvAMY8EBBbQ==") to USER_AGENT,
        _q9("SFYXq14t") to _q9("fVAMugEx40hDNVZl3WCPfLGUpIfgOoB5WuIYyAO32lhoRQSiRzr2UUZ2WTrVYYokod39xrc50j4EtAXeS/SO"),
    )

    private val _a1 = Mutex()
    private var _a2 = false

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
        _b0()

        val document = _b1("$mainUrl/")
        val selector = when (request.data) {
            _e3 -> _q9("J0cRoks45EBcN1962WSJcrXA5sigeZFiWvoEh1Wq2QR8WRW8XTX+QUprF3TfeI98vIXjiv0=")
            _e4 -> _q9("J0cRoks45EBcN1t02WmVa7iPoI2uPtg/QuYHlw6q0lpnWgajTzW3RF1tXnbBach9ow==")
            _e5 -> _q9("J0YRvEc85AhIfFk1g2CPbKSVvYyudIplR+wYhlW4xQ==")
            else -> _q9("J0cRoks45EBcN1t02WmVa7iPoI2uPtg/QuYHlw6q0lpnWgajTzW3RF1tXnbBach9ow==")
        }
        return newHomePageResponse(request.name, _b2(document, selector))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        _b0()
        val encoded = URLEncoder.encode(query.trim(), _q9("XGEy4xY="))
        return _b2(_b1("$mainUrl/?s=$encoded"))
    }

    override suspend fun load(url: String): LoadResponse? {
        _b0()

        val initialDocument = _b1(url)
        val parentUrl = _b3(initialDocument, url)
        val pageUrl = parentUrl ?: url
        val document = if (parentUrl != null) _b1(parentUrl, url) else initialDocument

        val title = document.selectFirst(_q9("YQRaq0At5VwCbV5hwWnKP/6CpI/tepZlS+EAwxPrmlQnUBq6XCC6UUZtW3A="))
            ?.text()
            ?._d1()
            ?.takeIf(String::isNotBlank)
            ?: return null

        val poster = document.selectFirst(
            _q9("J1cdqU02+VFKd0M1g3iOar2C7YHjctQxAPsclha4lh1kUljuACryV0Z8RGHFeYt98Imgj6I1kXxJoQOTVqrZB30YHaNPPvIJDzdHet54g23wiaCP")
        )?._c6(pageUrl)

        val plot = _b6(document, title)
        val genres = document.select(_q9("J1IRoFY88wVOQl9nyGq7M/CBlprrecVlT+gpuBOo0xIjCFupSzflQFw2ag=="))
            .map { it.text().trim() }
            .filter(String::isNotBlank)
            .distinct()

        val informationText = document.selectFirst(_q9("J0YEqw=="))?.text().orEmpty()
        val year = _b7(informationText)
        val duration = _b8(informationText)
        val status = _b9(informationText)
        val trailer = _c0(document, pageUrl)

        return if (_b5(document)) {
            val movieData = _b4(document, pageUrl).firstOrNull()?.data ?: pageUrl
            newMovieLoadResponse(title, pageUrl, TvType.Movie, movieData) {
                posterUrl = poster
                this.plot = plot
                tags = genres
                this.year = year
                this.duration = duration
                trailer?.let {
                    trailers = mutableListOf(TrailerData(it, pageUrl, false))
                }
            }
        } else {
            val episodes = _b4(document, pageUrl)
            newTvSeriesLoadResponse(title, pageUrl, TvType.Anime, episodes) {
                posterUrl = poster
                this.plot = plot
                tags = genres
                this.year = year
                this.duration = duration
                showStatus = status
                trailer?.let {
                    trailers = mutableListOf(TrailerData(it, pageUrl, false))
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
        _b0()
        val document = _b1(data)
        val embedUrls = linkedSetOf<String>()
        val directUrls = linkedSetOf<String>()

        document.select(
            _q9("KlAZrEs9yE1AdVNw3yyPeaKBoI2iNdthS+IWhh/63xJ7VBmrAnn+Q114WnCOeo97tY/gmOJ0gXRco1Q=") +
                _q9("J0UYr1c85QhKdFVwySyPeaKBoI2iNdZnR+sRjFa52Rp9UBq6DjDxV050Ug==")
        ).forEach { iframe ->
            iframe._c5()?.let {
                _c2(it, data, embedUrls, directUrls)
            }
        }

        document.select(
            _q9("KlAZrEs9yE1AdVNw3yyVcKWSro3VZopyc6NUwAu/2xZsUVS9QSzlRkpCRGfOUco//pChifdwijxL4haGH/rFG3xHF6t1KuVGcjUX") +
                _q9("J0Mdqks2ukZAd0Nww3jGbL+Vv4vrTotjTdJYw1Ws3xBsWlmtQTfjQEFtF2PEaINwi5O/i9M=")
        ).forEach { media ->
            media._c5()?.let {
                _c2(it, data, embedUrls, directUrls)
            }
        }

        document.select(_q9("elAYq00tuUhGa0V63yyJb6SJoobVY5l9W+opz1v02x17Rxu8DjbnUUZ2WU7bbYpqtb3hyKB4l3NH+gfDFKrCHWZbL7hPNeJAcg=="))
            .forEach { option ->
                _c1(option.attr(_q9("f1QYu0s=")), data)
                    .forEach { _c2(it, data, embedUrls, directUrls) }
            }

        var emittedCount = 0
        val trackedCallback: (ExtractorLink) -> Unit = { link ->
            emittedCount += 1
            callback(link)
        }

        directUrls.forEach { streamUrl ->
            val isHls = streamUrl.contains(_q9("J1hHuxY="), ignoreCase = true) ||
                streamUrl.contains(_q9("Jl0YvQE="), ignoreCase = true)
            trackedCallback(
                newExtractorLink(
                    source = name,
                    name = "$name Direct",
                    url = streamUrl,
                    type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                ) {
                    referer = data
                    quality = Qualities.Unknown.value
                }
            )
        }

        embedUrls.forEach { embedUrl ->
            val before = emittedCount
            try {
                loadExtractor(
                    embedUrl,
                    data,
                    subtitleCallback,
                    trackedCallback,
                )
            } catch (_: Throwable) {

            }

            if (emittedCount == before && _c4(embedUrl)) {
                _c3(embedUrl, data)?.let(trackedCallback)
            }
        }

        return emittedCount > 0
    }

    private suspend fun _b0() {
        if (_a2) return

        _a1.withLock {
            if (_a2) return@withLock

            val remoteCandidates = runCatching {
                JSONObject(app.get(_e2).text).readMainUrlCandidates()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidates + _e0)
                .mapNotNull(::normalizeHttpBaseUrl)
                .distinct()

            for (candidate in candidates) {
                val response = runCatching { app.get(candidate) }.getOrNull() ?: continue
                if (!response.isSuccessful) continue

                val resolved = normalizeHttpBaseUrl(response.url) ?: continue
                mainUrl = resolved
                _a2 = true
                return@withLock
            }

            mainUrl = _e0
        }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(_e1) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index) }
            .mapNotNull(::normalizeHttpBaseUrl)
            .distinct()
    }

    private fun normalizeHttpBaseUrl(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf(String::isNotBlank) ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if ((scheme == _q9("YUEAvg==") || scheme == _q9("YUEAvl0=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun _b1(url: String, referer: String = mainUrl): Document {
        _b0()
        val response = app.get(url, headers = _a0 + (_q9("W1ASq1w85Q==") to referer))
        normalizeHttpBaseUrl(response.url)?.let { mainUrl = it }
        return response.document
    }

    private fun _b2(document: Document, selector: String? = null): List<SearchResponse> {
        val cards = if (selector != null) {
            document.select(selector)
        } else {
            val preferred = document.select(_q9("J0UbvVo7+EFWORl5xH+SaqCE7Yn8YZFyQupagQj2llplXAe6WynzC0F2RXjMYMZ+opSki+Jw1nNd"))
            if (preferred.isNotEmpty()) preferred else document.select(_q9("J1kdvVos50EPeEVhxG+Kev6Cvg=="))
        }

        return cards.mapNotNull { card ->
            val anchor = card.selectFirst(_q9("J1cHtg5nt0R0cUVwy1HKP7HOuYH+TpBjS+kpz1u77Rx7UBKT"))
                ?: return@mapNotNull null
            val href = _c7(anchor.attr(_q9("YUcRqA==")), mainUrl)
                ?.takeUnless(::_c8)
                ?: return@mapNotNull null

            val canonicalTitle = card.selectFirst(_q9("J1ATqVow40lK"))?.text()?.trim()
            val visibleTitle = card.selectFirst(_q9("J0EA7kZruwVHK2x82WmLb6KPvdXmcJl1QuYahib2lhw7"))?.text()?.trim()
                ?: anchor.attr(_q9("fVwAoks=")).trim()
            val title = _c9(visibleTitle, canonicalTitle)
                ?._d0(canonicalTitle)
                ?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null

            val poster = card.selectFirst(_q9("YFgT"))?._c6(href)
            val typeText = card.selectFirst(_q9("J1ATqVog50ADORlh1HyDZQ=="))?.text().orEmpty()

            if (typeText.contains(_q9("ZFoCp0s="), ignoreCase = true)) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.Anime) {
                    posterUrl = poster
                }
            }
        }.distinctBy { it.url }
    }

    private fun _b3(document: Document, sourceUrl: String): String? {
        val allEpisodes = document.selectFirst(_q9("J1sVuEsp5AUBd0FmziyHRLiSqI7TTpljR+5Zjxq40xgjCDG+Ryr4QUpEGzWDYodptZC+yKB7jmJNrxW4E6jTElQ="))
        val breadcrumbParent = document.select(_q9("J0EH40wr8kRLekVgwG7GfouIv43oSA==")).getOrNull(1)
        val candidates = listOfNotNull(allEpisodes, breadcrumbParent)

        return candidates.asSequence()
            .mapNotNull { _c7(it.attr(_q9("YUcRqA==")), sourceUrl) }
            .filterNot(::_c8)
            .firstOrNull { _d3(it) != _d3(sourceUrl) }
    }

    private fun _b4(document: Document, pageUrl: String): List<Episode> {
        return document.select(_q9("J1AEokcq40BdOUJ5jWCPP7G7pZrrc6U9DqERkxezxQBsR1SiR3n2fkdrUnPw"))
            .mapNotNull { anchor ->
                val episodeUrl = _c7(anchor.attr(_q9("YUcRqA==")), pageUrl)
                    ?.takeUnless(::_c8)
                    ?: return@mapNotNull null

                val episodeTitle = anchor.selectFirst(_q9("J1AEogMt/lFDfA=="))
                    ?.text()?.trim()?.takeIf(String::isNotBlank)
                    ?: anchor.attr(_q9("fVwAoks=")).trim().takeIf(String::isNotBlank)
                    ?: anchor.text().trim()

                val episodeNumber = anchor.selectFirst(_q9("J1AEogM34kg="))
                    ?.text()?.trim()?.toIntOrNull()
                    ?: episodeTitle._d2()

                newEpisode(episodeUrl) {
                    name = episodeTitle
                    episode = episodeNumber
                }
            }
            .distinctBy { it.data }
            .sortedWith(compareBy<Episode> { it.episode ?: Int.MAX_VALUE }.thenBy { it.name })
    }

    private fun _b5(document: Document): Boolean {
        val informationText = document.selectFirst(_q9("J0YEqw=="))?.text().orEmpty()
        return Regex(_q9("IQod53I7w1xffGtmhza6bPqtop7ncKRz")).containsMatchIn(informationText)
    }

    private fun _b6(document: Document, title: String): String? {
        val candidates = listOf(
            document.selectFirst(_q9("J1cdtkw27wtcYFlljSKDcaSStMXtepZlS+EA"))?.text(),
            document.selectFirst(_q9("J1AaulwgukZAd0Nww3i9dqSFoJj8eogsSuoHgAmzxgBgWhqT"))?.text(),
            document.selectFirst(_q9("J1ERvU13+kxBfVJmgSzIe7WTrsSuO5V4QOsRkA=="))?.text(),
            document.selectFirst(_q9("ZFAAr3U39khKJFNw3m+UdqCUpIfgSA=="))?.attr(_q9("aloauks34w==")),
            document.selectFirst(_q9("ZFAAr3Up5UpffEVh1DGJeOqEqJvtZ5FhWuYbjSY="))?.attr(_q9("aloauks34w==")),
        )

        return candidates.asSequence()
            .mapNotNull { it?.trim()?.replace(_e8, " ")?.takeIf(String::isNotBlank) }
            .firstOrNull { !it.equals(title, ignoreCase = true) }
    }

    private fun _b7(informationText: String): Int? {
        val released = Regex(_q9("IQod53w8+0BOalJx8X/MJYyT57PQJdUoc6Vc0UuG0g87SAj/FwXzXh1kHg=="))
            .find(informationText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return released ?: Regex(_q9("VVdc/xclpRUGRVNun3G6fQ=="))
            .find(informationText)?.value?.toIntOrNull()
    }

    private fun _b8(informationText: String): Int? {
        Regex(_q9("IQod52os5URbcFh78X/MJYyT58DScdM4cvxe2SepnFxVUV/n"))
            .find(informationText)?.let { match ->
                val hours = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
                val minutes = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                return hours * 60 + minutes
            }

        return Regex(_q9("IQod52os5URbcFh78X/MJYyT58DScdM4cvxey0Tg2x1nSRmrQDDjDA=="))
            .find(informationText)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun _b9(informationText: String): ShowStatus? = when {
        informationText.contains(_q9("aloZvkI840BL"), ignoreCase = true) -> ShowStatus.Completed
        informationText.contains(_q9("ZlsToUc38A=="), ignoreCase = true) -> ShowStatus.Ongoing
        else -> null
    }

    private fun _c0(document: Document, pageUrl: String): String? {
        return document.select(_q9("aG4cvEs/ygkPcFFnzGGDRKOSrrU=")).asSequence().mapNotNull { element ->
            val marker = buildString {
                append(element.id()).append(' ')
                append(element.className()).append(' ')
                append(element.text())
            }
            if (!marker.contains(_q9("fUcVp0I85Q=="), ignoreCase = true)) return@mapNotNull null
            val raw = element.attr(_q9("YUcRqA==")).ifBlank { element.attr(_q9("ekcX")) }
            _c7(raw, pageUrl)
        }.firstOrNull()
    }

    private fun _c1(value: String, baseUrl: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith(_q9("YUEAvhR2uA==")) || trimmed.startsWith(_q9("YUEAvl1juAo=")) || trimmed.startsWith("//")) {
            return listOfNotNull(_c7(trimmed, baseUrl))
        }

        val decoded = runCatching {
            String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()?.trim().orEmpty()
        if (decoded.isEmpty()) return emptyList()

        val parsed = Jsoup.parse(decoded, baseUrl)
        val urls = parsed.select(_q9("YFMGr0M8zFZdemo5jWWAbbGNqLPqdIxwA/wGgCb2lgdmQAatSwLkV0xEGzXbZYJ6v7u+mu1I"))
            .mapNotNull { it._c5() }
            .mapNotNull { _c7(it, baseUrl) }
            .toMutableList()

        if (urls.isEmpty() && (
                decoded.startsWith(_q9("YUEAvhR2uA==")) ||
                    decoded.startsWith(_q9("YUEAvl1juAo=")) ||
                    decoded.startsWith("//")
            )
        ) {
            _c7(decoded, baseUrl)?.let(urls::add)
        }
        return urls
    }

    private fun _c2(
        rawUrl: String,
        baseUrl: String,
        embeds: MutableSet<String>,
        direct: MutableSet<String>,
    ) {
        val url = _c7(rawUrl, baseUrl) ?: return
        if (_c8(url)) return
        if (
            url.contains(_q9("J1hHuxY="), ignoreCase = true) ||
            url.contains(_q9("J1gE+g=="), ignoreCase = true) ||
            url.contains(_q9("Jl0YvQE="), ignoreCase = true)
        ) {
            direct += url
        } else {
            embeds += url
        }
    }

    private suspend fun _c3(embedUrl: String, referer: String): ExtractorLink? {
        val response = try {
            app.get(
                embedUrl,
                referer = referer,
                interceptor = WebViewResolver(_e9),
            )
        } catch (_: Throwable) {
            return null
        }

        val mediaUrl = response.url
        if (!_e9.containsMatchIn(mediaUrl)) return null
        val isHls = mediaUrl.contains(_q9("J1hHuxY="), ignoreCase = true)
        return newExtractorLink(
            source = name,
            name = "$name WebView",
            url = mediaUrl,
            type = if (isHls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
        ) {
            this.referer = embedUrl
            quality = Qualities.Unknown.value
        }
    }

    private fun _c4(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return host == _q9("ZlENvUs8uUZAdA==") || host.endsWith(_q9("J1oQt1088gtMdlo=")) || host == _q9("eVkVtwA9uVFae1I=")
    }

    private fun Element._c5(): String? {
        return listOf(_q9("ekcX"), _q9("bVQArwMq5UY="), _q9("bVQArwM1/lFKakdwyGjLbKKD"), _q9("bVQArwM19l9WNERnzg=="))
            .asSequence()
            .map { attr(it).trim() }
            .firstOrNull(String::isNotBlank)
    }

    private fun Element._c6(baseUrl: String): String? {
        val raw = listOf(_q9("bVQArwMq5UY="), _q9("bVQArwM19l9WNERnzg=="), _q9("bVQArwM25UxIcFl0wQ=="), _q9("ekcX"))
            .asSequence()
            .map { attr(it).trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith(_q9("bVQArxQw+kRIfA==")) }
            ?: attr(_q9("ekcXvUst")).substringBefore(',').trim().substringBefore(' ')
                .takeIf(String::isNotBlank)
        return raw?.let { _c7(it, baseUrl) }
    }

    private fun _c7(rawUrl: String, baseUrl: String): String? {
        val cleaned = rawUrl.trim().replace("\\/", "/")
        if (
            cleaned.isEmpty() ||
            cleaned.startsWith(_q9("Y1QCr1065UxfbQ0="), ignoreCase = true) ||
            cleaned.startsWith('#')
        ) {
            return null
        }
        return runCatching {
            when {
                cleaned.startsWith("//") -> "https:$cleaned"
                cleaned.startsWith(_q9("YUEAvhR2uA==")) || cleaned.startsWith(_q9("YUEAvl1juAo=")) -> cleaned
                else -> URI(baseUrl).resolve(cleaned).toString()
            }
        }.getOrNull()
    }

    private fun _c8(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return _e6.any { host.contains(it) }
    }

    private fun _c9(visibleTitle: String?, vararg canonicalTitles: String?): String? {
        val visible = visibleTitle?.trim()?.takeIf(String::isNotBlank) ?: return null
        val stripped = visible.replaceFirst(_e7, "").trim()
        if (stripped == visible) return visible

        val confirmed = canonicalTitles
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .firstOrNull { it.equals(stripped, ignoreCase = true) }
        return confirmed ?: visible
    }

    private fun String._d0(canonicalTitle: String?): String {
        val canonical = canonicalTitle?.trim()?.takeIf(String::isNotBlank)
        return (canonical ?: this)
            .removeSuffix(_q9("KRhUikE38E1aew=="))
            .replace(_e8, " ")
            .trim()
    }

    private fun String._d1(): String = trim()
        .removeSuffix(_q9("KRhUikE38E1aew=="))
        .replace(_e8, " ")

    private fun String._d2(): Int? {
        return Regex(_q9("IQod53I7vxoVfEd83mOCeqyFvcHSZtIhBKcoh1Dz"))
            .find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun _d3(url: String): String = url.substringBefore('#').trimEnd('/')

    companion object {
        private val _e0 = _q9("YUEAvl1juApLdllyxXmEMaaJvQ==")
        private val _e1 = _q9("TVoaqUYs9Q==")
        private val _e2 =
            _q9("YUEAvl1juApdeEA7ymWSd6WCuJvrZ5t+QPsRjQ/01RtkGhmkHwnyVx4rADrMa4lwo4WuhOFgnGJa/RGCFvXbFWBbW5lLO+RMW3wZf95jiA==")

        private val _e3 = _q9("YVoZqxQp+FVadVZn")
        private val _e4 = _q9("YVoZqxQ19lFKakM=")
        private val _e5 = _q9("YVoZqxQr8kZAdFpww2iHa7mPow==")

        private val _e6 = setOf(
            _q9("bVoBrEI89ElGelw7w2mS"),
            _q9("blobqUI85FxBfV52zHiPcL7Orofj"),
            _q9("blobqUI840RIdFZ7zGuDbf6DooU="),
            _q9("blobqUI8ukRBeFts2WWFbP6DooU="),
            _q9("bUEHrUEs4wtMdlo="),
            _q9("YVwHuk8t5AtMdlo="),
            _q9("ZlsRvUc++URDN1R6wA=="),
            _q9("f1QSvEEs5FdKfUB032nIfKmPuA=="),
        )

        private val _e7 = Regex(_q9("V3sboFo2+XlcMg=="), RegexOption.IGNORE_CASE)
        private val _e8 = Regex(_q9("VUZf"))
        private val _e9 = Regex(
            _q9("IQod5wZmrXkBdARglSTZJYvf7rWgP9EuCvMozRaqglw2Dy/xDQS5DwYmEzw=")
        )
    }
}
