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
    override var mainUrl = DEFAULT_MAIN_URL
    override var name = "Donghub"
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val hasMainPage = true
    override val mainPage = mainPageOf(
        LATEST_RELEASE_SOURCE to _qD9("OSMonUtEDqlfkR+vC2g="),
    )

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        val targetUrl = if (page <= 1) mainUrl else "$mainUrl/page/$page/"
        val response = app.get(targetUrl)
        syncMainUrl(response.url)
        val document = response.document

        val latestSection = document.selectFirst(_qD9("WzA5lF1RXZ5J0xavDGhr/yakYFw="))?.parent()
            ?: throw ErrorLoadingException(_qD9("NyM7kVleDrdbiR+9DC1K7iKubErPM4HBqV8xdwE+VUwRIzfYXFlanleIEa8W"))
        val items = latestSection.select(_qD9("Wy41i0xFXp8a0xi9AA=="))
            .mapNotNull(::_a0)

        val hasNext = document.select(_qD9("FGwuo1BCS51n0VqvVmN98zrlfVjNdujAslU7ZxFtek0HJzql")).any { anchor ->
            val href = anchor.attr(_qD9("HTA5ng=="))
            href.contains("/page/${page + 1}/") || anchor.text().trim().equals(_qD9("OyckjA=="), ignoreCase = true)
        }

        return newHomePageResponse(request, items, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encoded = URLEncoder.encode(query.trim(), _qD9("IBYa1QA="))
        val response = app.get("$mainUrl/?s=$encoded")
        syncMainUrl(response.url)
        val document = response.document

        val scoped = document.select(_qD9("WzIzi0xSQZ9D3VSiEX5s/j6vLRfIYL0="))
        val cards = if (scoped.isNotEmpty()) scoped else document.select(_qD9("Wy41i0xFXp8a0xi9AA=="))
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
            val detailUrl = document.selectFirst(_qD9("FBk9ilFRA5dbnx+iRUx0526OfVDZfKHLtGUCahF7R3g="))
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
        enforceContentAllowed(website.categories, website.rawTags)

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
        document.select(_qD9("Vicxml1UcZNVkR6rCi1x7TyqYFzxYLfNmg==")).forEach { iframe ->
            _a4(iframe.attr(_qD9("BjA/")))?.let(embeds::add)
        }

        document.select(_qD9("BicwnVtEAJZTjwihCi13+zqiYlfxZaTCsl0E")).forEach { option ->
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
        val anchor = card.selectFirst(_qD9("FGwokUhrRolfmyc=")) ?: card.selectFirst(_qD9("FBk0il1Wcw==")) ?: return null
        val href = anchor.attr(_qD9("HTA5ng==")).trim().takeIf { it.isNotBlank() } ?: return null
        val url = normalizeProviderUrl(href)
        val title = card.selectFirst(_qD9("WzYo2FAC"))?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: anchor.attr(_qD9("ASsolF0=")).trim().takeIf { it.isNotBlank() }
            ?: card.selectFirst(_qD9("WzYo"))?.ownText()?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val typeLabel = card.selectFirst(_qD9("WzYliF1K"))?.text()?.trim().orEmpty()
        val poster = card.selectFirst(_qD9("HC87"))?.let(::imageUrl)
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
        val title = document.selectFirst(_qD9("WyA1n1tfQI9fkw7uEDw27iC/f0CHZ6zaq10="))?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(_qD9("HXNynVZEXIIXiRO6FGg="))?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_qD9("Pzc4jVQQSp5OnBOiWEl35SmjeFuKZ6zKplN5ZgpqREgAKT2W"))
        val originalTitle = document.selectFirst(_qD9("WyA1n1tfQI9fkw7uVmx0/yu5"))?.text()?.trim()?.takeIf { it.isNotBlank() }
        val type = _a5(document, _qD9("ITssnQ=="))
        val isMovie = type?.equals(_qD9("OC0qkV0="), ignoreCase = true) == true
        val status = when (_a5(document, _qD9("JjY9jE1D"))?.lowercase(Locale.ROOT)) {
            _qD9("Fi0xiFRVWp5e") -> ShowStatus.Completed
            _qD9("Giw7l1FeSQ==") -> ShowStatus.Ongoing
            else -> null
        }
        val released = _a5(document, _qD9("JycwnVlDS58="))
        val year = released?.let { YEAR_REGEX.find(it)?.value?.toIntOrNull() }
            ?: document.selectFirst(_qD9("ASsxnWNZWp5XjQihCDB86jquXUzIf6zdr109Xw=="))?.attr(_qD9("ESMonUxZQ54="))
                ?.let { YEAR_REGEX.find(it)?.value?.toIntOrNull() }
        val durationMinutes = parseDurationMinutes(_a5(document, _qD9("MTcumUxZQZU=")))
        val poster = document.selectFirst(_qD9("WyA1n1tfQI9fkw7uVnlw/iOpLVDHdA=="))?.let(::imageUrl)
        val plot = document.selectFirst(_qD9("WyA1gFpfVtVJhBS+WCN95Tq5dBTJfKvaolYtWQpqREgFMDOIBVRLiFmPE74MZHflEw=="))
            ?.text()?.trim()?.takeIf { it.isNotBlank() }
        val categories = document.select(_qD9("WyU5lkBVSttbpgirFDBs6imW"))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val rawTags = document.select(_qD9("WyAzjExfQ9VOnB29WGxD+SunME3LdJiC5xY7bRdqTkhbNj2fSxBPoFKPH6gl"))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val episodes = document.select(_qD9("WycslFFDWp5I3Q+iWGFxqy+QZUvPdZg="))
            .mapNotNull { anchor ->
                val href = anchor.attr(_qD9("HTA5ng==")).trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val number = anchor.selectFirst(_qD9("WycslBVeW5Y="))?.text()?.trim()?.toIntOrNull()
                val episodeTitle = anchor.selectFirst(_qD9("WycslBVER49WmA=="))?.text()?.trim()
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
        val section = document.select(_qD9("WyA1gFpfVg==")).firstOrNull { box ->
            box.select(_qD9("WzA5lF1RXZ5J3RL8VC02+SunaFjZdraOrws=")).any { heading ->
                heading.text().contains(_qD9("Jyc/l1VdS5VemB7uK2hq4iu4"), ignoreCase = true) ||
                    heading.text().contains(_qD9("Jyc/l1VdS5VenA6nF2M="), ignoreCase = true)
            }
        } ?: return emptyList()

        return section.select(_qD9("Wy41i0xFXp8a0xi9AA=="))
            .mapNotNull(::_a0)
            .distinctBy { it.url }
    }

    private fun parseWebsiteTrailerUrls(document: Document): List<String> {
        val trailerSection = document.select(_qD9("WyA1gFpfVg==")).firstOrNull { box ->
            box.select(_qD9("WzA5lF1RXZ5J3RL8VC02+SunaFjZdraOrws=")).any { heading ->
                heading.text().contains(_qD9("ITA9kVRVXA=="), ignoreCase = true)
            }
        } ?: return emptyList()

        return trailerSection.select(_qD9("HCQumVVVdYhInifiWGxD4zyua2Q="))
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
        for (span in document.select(_qD9("WzEsnRhDXppU"))) {
            val key = span.selectFirst("b")?.text()?.trim()?.removeSuffix(":") ?: continue
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
        val iframeSrc = Jsoup.parse(decoded).selectFirst(_qD9("HCQumVVVdYhInic="))?.attr(_qD9("BjA/")) ?: return null
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
        document.selectFirst(_qD9("GSsyk2NCS5cHnhugF2Nx6C+nUGLCYaDImg=="))?.attr(_qD9("HTA5ng=="))?.trim()?.takeIf { it.isNotBlank() }
            ?.let(::normalizeProviderUrl)

    private fun isEpisodePage(document: Document): Boolean =
        document.selectFirst(_qD9("Vicxml1UcZNVkR6rCg==")) != null ||
            document.selectFirst(_qD9("FDAokVtcS6BTiR+jDHRo7mT2SEnDYKrKomU=")) != null

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
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if ((scheme == _qD9("HTYoiA==") || scheme == _qD9("HTYoiEs=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else {
                null
            }
        }.getOrNull()
    }

    private fun shouldBlockContent(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ): Boolean {
        val categoryBlocked = categories
            .asSequence()
            .mapNotNull(::normalizeTaxonomyName)
            .any { it in BLOCKED_CATEGORY_KEYS }
        if (categoryBlocked) return true

        return tags
            .asSequence()
            .mapNotNull(::normalizeTaxonomyName)
            .any { it in BLOCKED_TAG_KEYS }
    }

    private fun enforceContentAllowed(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ) {
        if (shouldBlockContent(categories, tags)) {
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
        private const val DEFAULT_MAIN_URL = "https://donghub.vip"
        private const val REMOTE_CONFIG_KEY = "Donghub"
        private const val MAIN_URL_JSON =
            "https://raw.githubusercontent.com/mj1Per127/agoosecloudstream/main/Website.json"
        private const val LATEST_RELEASE_SOURCE = "latest-release"

        private val BLOCKED_CATEGORIES = emptySet<String>()
        private val BLOCKED_TAGS = emptySet<String>()
        private val BLOCKED_CATEGORY_KEYS = BLOCKED_CATEGORIES.map { it.lowercase(Locale.ROOT) }.toSet()
        private val BLOCKED_TAG_KEYS = BLOCKED_TAGS.map { it.lowercase(Locale.ROOT) }.toSet()

        private val YEAR_REGEX = Regex(_qD9("XX1myQFMHMsToR61SnA="))
        private val DURATION_HM_REGEX = Regex(_qD9("XR440xFsXdEAoQnkUFF8oGc="))
        private val DURATION_MIN_REGEX = Regex(_qD9("XR440xFsXdESwkCjEWNk5iulZE2D"), RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex(_qD9("KTF3"))
    }
}
