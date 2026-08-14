package com.agooseangsa.Donghub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class Donghub : MainAPI() {
    private val providerProfile = _b3.current

    override var mainUrl = providerProfile.defaultMainUrl
    override var name = _qD9("MS0yn1BFTA==")
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val hasMainPage = true
    override val mainPage = mainPageOf(
        *providerProfile.homepage.map { it.key to it.title }.toTypedArray(),
    )

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
        val pagePath = providerProfile.endpoint(_qD9("BSM7nWhRWpM=")).replace(_qD9("DjI9n11N"), page.toString())
        val targetUrl = if (page <= 1) mainUrl else mainUrl + pagePath
        val response = app.get(targetUrl)
        syncMainUrl(response.url)
        val document = response.document

        val latestSection = document.selectFirst(providerProfile.selector(_qD9("HS0xnUhRSZ5pmBm6EWJ2")))?.parent()
            ?: throw ErrorLoadingException(_qD9("NyM7kVleDrdbiR+9DC1K7iKubErPM4HBqV8xdwE+VUwRIzfYXFlanleIEa8W"))
        val items = latestSection.select(providerProfile.selector(_qD9("GSsvjFFeSbhbjx69")))
            .mapNotNull(::_a0)

        val hasNext = document.select(providerProfile.selector(_qD9("BSM7kVZRWpJVkzanFmZr"))).any { anchor ->
            val href = anchor.attr(_qD9("HTA5ng=="))
            href.contains("/page/${page + 1}/") || anchor.text().trim().equals(_qD9("OyckjA=="), ignoreCase = true)
        }

        return newHomePageResponse(request, items, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encoded = URLEncoder.encode(query.trim(), _qD9("IBYa1QA="))
        val searchPath = providerProfile.endpoint(_qD9("Bic9iltYfppOlQ=="))
        val searchParam = providerProfile.endpoint(_qD9("Bic9iltYfppInBc="))
        val response = app.get(mainUrl + searchPath + "?" + searchParam + "=" + encoded)
        syncMainUrl(response.url)
        val document = response.document

        val scoped = document.select(providerProfile.selector(_qD9("Bic9iltYfZhVjR+qO2xq7z0=")))
        val cards = if (scoped.isNotEmpty()) scoped else document.select(providerProfile.selector(_qD9("GSsvjFFeSbhbjx69")))
        return cards.mapNotNull(::_a0).distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val normalizedUrl = updateUrl(url)
        val response = app.get(normalizedUrl)
        syncMainUrl(response.url)
        var document = response.document
        var canonicalUrl = canonicalUrl(document) ?: response.url

        if (isEpisodePage(document)) {
            val detailUrl = document.selectFirst(providerProfile.selector(_qD9("FC4wvUhZXZRemAmCEWNz")))
                ?.attr(_qD9("HTA5ng=="))
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(::normalizeProviderUrl)
                ?: throw ErrorLoadingException(_qD9("IBAQ2HlcQtt/jRO9F2l9+G6PYlfNe7DM50wwZgJ1AUEcNjmVTVtPlQ=="))
            val detailResponse = app.get(detailUrl)
            syncMainUrl(detailResponse.url)
            document = detailResponse.document
            canonicalUrl = canonicalUrl(document) ?: detailResponse.url
        }

        val website = _a1(document, canonicalUrl)
        _b7(website.categories, website.rawTags)

        val tmdb = _a8(
            AgooseTmdbIdentity(
                displayTitle = website.title,
                originalTitle = website.originalTitle,
                year = website.year,
                isTv = !website.isMovie,
            ),
        )

        val plot = tmdb?.overview?.takeIf { it.isNotBlank() } ?: website.plot
        val displayTags = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: website.categories
        val duration = tmdb?.runtimeMinutes ?: website.durationMinutes
        val trailers = mergeTrailerData(tmdb?.trailerUrls.orEmpty(), website.trailerUrls, canonicalUrl)

        val recommendations = _a2(document)

        return if (website.isMovie) {
            val dataUrl = website.episodes.firstOrNull()?.url
                ?: throw ErrorLoadingException(_qD9("MDI1i1dUS9RKkRu3WFhKx26mYk/DduXqqFY+ahZ8AVEcJj2TGFRHj1+QD6UZYw=="))

            newMovieLoadResponse(website.title, canonicalUrl, TvType.Movie, dataUrl) {
                posterUrl = website.posterUrl ?: tmdb?.posterUrl
                year = website.year ?: tmdb?.year
                this.plot = plot
                tags = displayTags.takeIf { it.isNotEmpty() }
                this.duration = duration
                backgroundPosterUrl = tmdb?.backdropUrl
                this.trailers = trailers.toMutableList()
                this.recommendations = recommendations.takeIf { it.isNotEmpty() }
            }
        } else {
            val episodes = website.episodes
                .sortedBy { it.number ?: Int.MAX_VALUE }
                .map { item ->
                    newEpisode(item.url, initializer = {
                        name = item.title
                        episode = item.number
                    })
                }

            if (episodes.isEmpty()) {
                throw ErrorLoadingException(_qD9("MSM6jFlCDp5KlAmhHGg4zyGlalHfceXarlw4aUN6SFEQLymTWV4="))
            }

            newTvSeriesLoadResponse(website.title, canonicalUrl, TvType.TvSeries, episodes) {
                posterUrl = website.posterUrl ?: tmdb?.posterUrl
                year = website.year ?: tmdb?.year
                this.plot = plot
                showStatus = website.showStatus
                tags = displayTags.takeIf { it.isNotEmpty() }
                this.duration = duration
                backgroundPosterUrl = tmdb?.backdropUrl
                this.trailers = trailers.toMutableList()
                this.recommendations = recommendations.takeIf { it.isNotEmpty() }
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
        val pageUrl = updateUrl(data)
        val response = app.get(pageUrl)
        syncMainUrl(response.url)
        val document = response.document
        val referer = canonicalUrl(document) ?: response.url

        val embeds = linkedSetOf<String>()
        document.select(providerProfile.selector(_qD9("BTA1lVlCV75Xnx+qCw=="))).forEach { iframe ->
            _a4(iframe.attr(_qD9("BjA/")))?.let(embeds::add)
        }

        document.select(providerProfile.selector(_qD9("GCsuildCYYtOlBWgCw=="))).forEach { option ->
            _a3(option.attr(_qD9("AyMwjV0=")))?.let(embeds::add)
        }

        var extractorMatched = false
        for (embedUrl in embeds) {
            val matched = runCatching {
                loadExtractor(embedUrl, referer, subtitleCallback, callback)
            }.getOrDefault(false)
            extractorMatched = extractorMatched || matched
        }
        return extractorMatched
    }

    private fun _a0(card: Element): SearchResponse? {
        val anchor = card.selectFirst(providerProfile.selector(_qD9("FiMunGhCS51fjwirHEFx5SU="))) ?: card.selectFirst(providerProfile.selector(_qD9("FiMunH5RQpdYnBmlNGR24A=="))) ?: return null
        val href = anchor.attr(_qD9("HTA5ng==")).trim().takeIf { it.isNotBlank() } ?: return null
        val url = normalizeProviderUrl(href)
        val title = card.selectFirst(providerProfile.selector(_qD9("FiMunGxZWpdf")))?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: anchor.attr(_qD9("ASsolF0=")).trim().takeIf { it.isNotBlank() }
            ?: card.selectFirst(providerProfile.selector(_qD9("FiMunGxZWpdfuxuiFG956CU=")))?.ownText()?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val typeLabel = card.selectFirst(providerProfile.selector(_qD9("FiMunGxJXp4=")))?.text()?.trim().orEmpty()
        val poster = card.selectFirst(providerProfile.selector(_qD9("FiMunHFdT5xf")))?.let(::imageUrl)
        val isMovie = typeLabel.equals(_qD9("OC0qkV0="), ignoreCase = true)

        return if (isMovie) {
            newMovieSearchResponse(title, url, TvType.Movie) {
                posterUrl = poster
            }
        } else {
            newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                posterUrl = poster
            }
        }
    }

    private fun _a1(document: Document, canonicalUrl: String): _a7 {
        val title = document.selectFirst(providerProfile.selector(_qD9("EScomVFcepJOkR8=")))?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(providerProfile.selector(_qD9("EScomVFcepJOkR+IGWF06S+oZg==")))?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_qD9("Pzc4jVQQSp5OnBOiWEl35SmjeFuKZ6zKplN5ZgpqREgAKT2W"))
        val originalTitle = document.selectFirst(providerProfile.selector(_qD9("EScomVFcYYlTmhOgGWFM4jqnaA==")))?.text()?.trim()?.takeIf { it.isNotBlank() }
        val type = _a5(document, _qD9("ITssnQ=="))
        val isMovie = type?.equals(_qD9("OC0qkV0="), ignoreCase = true) == true
        val status = when (_a5(document, _qD9("JjY9jE1D"))?.lowercase(Locale.ROOT)) {
            _qD9("Fi0xiFRVWp5e") -> ShowStatus.Completed
            _qD9("Giw7l1FeSQ==") -> ShowStatus.Ongoing
            else -> null
        }
        val released = _a5(document, _qD9("JycwnVlDS58="))
        val year = released?.let { YEAR_REGEX.find(it)?.value?.toIntOrNull() }
            ?: document.selectFirst(providerProfile.selector(_qD9("BTc+lFFDRp5eqROjHQ==")))?.attr(_qD9("ESMonUxZQ54="))
                ?.let { YEAR_REGEX.find(it)?.value?.toIntOrNull() }
        val durationMinutes = parseDurationMinutes(_a5(document, _qD9("MTcumUxZQZU=")))
        val poster = document.selectFirst(providerProfile.selector(_qD9("EScomVFcfpRJiR+8")))?.let(::imageUrl)
        val plot = document.selectFirst(providerProfile.selector(_qD9("EScomVFcfpdViQ==")))
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
        val categories = document.select(providerProfile.selector(_qD9("FiMonV9fXJJfjg==")))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val rawTags = document.select(providerProfile.selector(_qD9("ByMrrFlXXQ==")))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val episodes = document.select(providerProfile.selector(_qD9("EDI1i1dUS7dTkxG9")))
            .mapNotNull { anchor ->
                val href = anchor.attr(_qD9("HTA5ng==")).trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val number = anchor.selectFirst(providerProfile.selector(_qD9("EDI1i1dUS7VPkBirCg==")))?.text()?.trim()?.toIntOrNull()
                val episodeTitle = anchor.selectFirst(providerProfile.selector(_qD9("EDI1i1dUS69TiRar")))?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Episode ${number ?: ""}".trim()
                _a6(normalizeProviderUrl(href), episodeTitle, number)
            }
        val trailerUrls = parseWebsiteTrailerUrls(document)

        return _a7(
            title = title,
            originalTitle = originalTitle,
            canonicalUrl = canonicalUrl,
            isMovie = isMovie,
            posterUrl = poster,
            plot = plot,
            year = year,
            durationMinutes = durationMinutes,
            showStatus = status,
            categories = categories,
            rawTags = rawTags,
            episodes = episodes,
            trailerUrls = trailerUrls,
        )
    }

    private fun _a2(document: Document): List<SearchResponse> {
        val section = document.select(providerProfile.selector(_qD9("Fi0yjF1eWrlVhQ=="))).firstOrNull { box ->
            box.select(providerProfile.selector(_qD9("Fy0ksF1RSpJUmgk="))).any { heading ->
                heading.text().contains(_qD9("Jyc/l1VdS5VemB7uK2hq4iu4"), ignoreCase = true) ||
                    heading.text().contains(_qD9("Jyc/l1VdS5VenA6nF2M="), ignoreCase = true)
            }
        } ?: return emptyList()

        return section.select(providerProfile.selector(_qD9("Byc/l1VdS5VenA6nF2Nb6jyvfg==")))
            .mapNotNull(::_a0)
            .distinctBy { it.url }
    }

    private fun parseWebsiteTrailerUrls(document: Document): List<String> {
        val trailerSection = document.select(providerProfile.selector(_qD9("Fi0yjF1eWrlVhQ=="))).firstOrNull { box ->
            box.select(providerProfile.selector(_qD9("Fy0ksF1RSpJUmgk="))).any { heading ->
                heading.text().contains(_qD9("ITA9kVRVXA=="), ignoreCase = true)
            }
        } ?: return emptyList()

        return trailerSection.select(providerProfile.selector(_qD9("ATA9kVRVXLdTkxG9")))
            .mapNotNull { element ->
                val raw = element.attr(_qD9("BjA/")).ifBlank { element.attr(_qD9("HTA5ng==")) }
                _a4(raw)?.takeIf { url ->
                    url.contains(_qD9("DC0pjE1SS9VZkhc="), ignoreCase = true) ||
                        url.contains(_qD9("DC0pjE0eTJ4="), ignoreCase = true)
                }
            }
            .distinct()
    }

    private fun mergeTrailerData(
        tmdbUrls: List<String>,
        websiteUrls: List<String>,
        referer: String,
    ): List<TrailerData> = (tmdbUrls + websiteUrls)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .map { TrailerData(it, referer, false) }

    private fun _a5(document: Document, label: String): String? {
        for (span in document.select(providerProfile.selector(_qD9("BjI5m2tAT5VJ")))) {
            val key = span.selectFirst(providerProfile.selector(_qD9("BjI5m3RRTJ5W")))?.text()?.trim()?.removeSuffix(":") ?: continue
            if (!key.equals(label, ignoreCase = true)) continue
            return span.text().substringAfter(":", "").trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun parseDurationMinutes(value: String?): Int? {
        val text = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        DURATION_HM_REGEX.find(text)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            return (hours * 60 + minutes).takeIf { it > 0 }
        }
        return DURATION_MIN_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun _a3(encoded: String): String? {
        if (encoded.isBlank()) return null
        val decoded = runCatching { base64Decode(encoded) }.getOrNull() ?: return null
        val iframeSrc = Jsoup.parse(decoded).selectFirst(providerProfile.selector(_qD9("GCsuildCZ51InBer")))?.attr(_qD9("BjA/")) ?: return null
        return _a4(iframeSrc)
    }

    private fun _a4(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith(_qD9("HTYoiAIfAQ==")) || value.startsWith(_qD9("HTYoiEsKAdQ=")) -> value
            value.startsWith("/") -> "$mainUrl$value"
            else -> null
        }
    }

    private fun imageUrl(element: Element): String? {
        return listOf(_qD9("BjA/"), _qD9("ESMomRVDXJg="), _qD9("ESMomRVcT4FD0Am8Gw=="))
            .asSequence()
            .map { element.attr(it).trim() }
            .firstOrNull { it.startsWith(_qD9("HTYoiAIfAQ==")) || it.startsWith(_qD9("HTYoiEsKAdQ=")) }
    }

    private fun canonicalUrl(document: Document): String? =
        document.selectFirst(providerProfile.selector(_qD9("FiMyl1ZZTZpWsROgEw==")))?.attr(_qD9("HTA5ng=="))?.trim()?.takeIf { it.isNotBlank() }
            ?.let(::normalizeProviderUrl)

    private fun isEpisodePage(document: Document): Boolean =
        document.selectFirst(providerProfile.selector(_qD9("EDI1i1dUS6tIlBevCnRV6jygaEs="))) != null ||
            document.selectFirst(providerProfile.selector(_qD9("EDI1i1dUS7pIiROtFGhV6jygaEs="))) != null

    private fun normalizeProviderUrl(raw: String): String {
        val value = raw.trim()
        return when {
            value.startsWith(_qD9("HTYoiAIfAQ==")) || value.startsWith(_qD9("HTYoiEsKAdQ=")) -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "$mainUrl$value"
            else -> "$mainUrl/$value"
        }
    }

    private suspend fun ensureMainUrl() {
        if (mainUrlResolved) return

        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock

            val remoteCandidates = runCatching {
                JSONObject(app.get(providerProfile.websiteJsonUrl).text)._b4()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidates + providerProfile.defaultMainUrl)
                .mapNotNull(::_b5)
                .distinct()

            for (candidate in candidates) {
                val response = runCatching { app.get(candidate) }.getOrNull() ?: continue
                if (!response.isSuccessful) continue

                val resolved = _b5(response.url) ?: continue
                mainUrl = resolved
                mainUrlResolved = true
                return@withLock
            }

            mainUrl = providerProfile.defaultMainUrl
        }
    }

    private fun syncMainUrl(responseUrl: String?) {
        _b5(responseUrl)?.let { mainUrl = it }
    }

    private fun JSONObject._b4(): List<String> {
        val array = optJSONArray(providerProfile.websiteKey) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index) }
            .mapNotNull(::_b5)
            .distinct()
    }

    private fun _b5(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if ((scheme == _qD9("HTYoiA==") || scheme == _qD9("HTYoiEs=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else {
                null
            }
        }.getOrNull()
    }

    private fun _b6(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ): Boolean {
        val categoryBlocked = categories
            .asSequence()
            .mapNotNull(::normalizeTaxonomyName)
            .any { it in blockedCategoryKeys }
        if (categoryBlocked) return true

        return tags
            .asSequence()
            .mapNotNull(::normalizeTaxonomyName)
            .any { it in blockedTagKeys }
    }

    private fun _b7(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ) {
        if (_b6(categories, tags)) {
            throw ErrorLoadingException(_qD9("Pi0yjF1eDp9TnxahE2RqqyGnaFGKeKrAoVE+dxF/UkxVMi6XTllKnkg="))
        }
    }

    private fun normalizeTaxonomyName(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)

    private data class _a6(
        val url: String,
        val title: String,
        val number: Int?,
    )

    private data class _a7(
        val title: String,
        val originalTitle: String?,
        val canonicalUrl: String,
        val isMovie: Boolean,
        val posterUrl: String?,
        val plot: String?,
        val year: Int?,
        val durationMinutes: Int?,
        val showStatus: ShowStatus?,
        val categories: List<String>,
        val rawTags: List<String>,
        val episodes: List<_a6>,
        val trailerUrls: List<String>,
    )

    companion object {
        private val YEAR_REGEX = Regex(_qD9("XX1myQFMHMsToR61SnA="))
        private val DURATION_HM_REGEX = Regex(_qD9("XR440xFsXdEAoQnkUFF8oGc="))
        private val DURATION_MIN_REGEX = Regex(_qD9("XR440xFsXdESwkCjEWNk5iulZE2D"), RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex(_qD9("KTF3"))
    }
}
