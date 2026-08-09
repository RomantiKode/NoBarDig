package com.agooseangsa.Sokuja

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class Sokuja : MainAPI() {
    override var mainUrl = DEFAULT_MAIN_URL
    override var name = "Sokuja"
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val hasMainPage = true
    override val mainPage = listOf(
        MainPageData(_q9("VcwDJSOc7XqTRkaxr8E="), _q9("VcwDJSOc7XqTRkaxr8E=")),
        MainPageData(_q9("T9IAKz6Xqg6lUVa5uMc="), _q9("T9IAKz6Xqg6lUVa5uMc=")),
        MainPageData(_q9("QdIOKTLZjkGbREi1qdHn"), _q9("QdIOKTLZjkGbREi1qdHn")),
        MainPageData(_q9("QdIOKTLZnUGGQUi1r5TO3AoQJS0wxA=="), _q9("QdIOKTLZnUGGQUi1r5TO3AoQJS0wxA==")),
        MainPageData(_q9("QdIOKTLZnUGGQUi1r5TBwAgWLDk/"), _q9("QdIOKTLZnUGGQUi1r5TBwAgWLDk/")),
        MainPageData(_q9("QdIOKTLZnUGGQUi1r5TQ0BQWLDIwxCxTBryDIw=="), _q9("QdIOKTLZnUGGQUi1r5TQ0BQWLDIwxCxTBryDIw==")),
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
            .map { index -> array.optString(index) }
            .mapNotNull(::normalizeHttpBaseUrl)
            .distinct()
    }

    protected fun normalizeHttpBaseUrl(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase()
            if ((scheme == _q9("aMgTNA==") || scheme == _q9("aMgTNCQ=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else {
                null
            }
        }.getOrNull()
    }

    protected fun shouldBlockContent(
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

    protected fun enforceContentAllowed(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ) {
        if (shouldBlockContent(categories, tags)) {
            throw ErrorLoadingException(_q9("S9MJMDKX7UqfVki/tt3xlQsbJzBxwSQdLbSXNyfz2EUgzBUrIZCpS4Q="))
        }
    }

    private fun normalizeTaxonomyName(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        val response = app.get(mainUrl)
        syncMainUrl(response.url)
        val section = _a1(response.document, request.data)
        val items = section?.let(::_a0).orEmpty()
        return newHomePageResponse(request, items, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encoded = URLEncoder.encode(query.trim(), _q9("VeghaW8="))
        val response = app.get("$mainUrl/?s=$encoded")
        syncMainUrl(response.url)

        return _a0(response.document)
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val response = app.get(fixUrl(url))
        syncMainUrl(response.url)
        val document = response.document
        val ldObjects = _a3(document)

        ldObjects.firstOrNull { it.optString(_q9("QMgeNDI=")).equals(_q9("VOoiND6KokqT"), true) }
            ?.optJSONObject(_q9("cN0VMBifnkuEXUGj"))
            ?.optString(_q9("dc4L"))
            ?.takeIf { it.isNotBlank() }
            ?.let { seriesUrl ->
                return _b1(seriesUrl)
            }

        if (ldObjects.any { it.optString(_q9("QMgeNDI=")).equals(_q9("VtUDITi2r0STV1A="), true) }) {
            _a4(ldObjects)?.let { detailUrl ->
                return _b1(detailUrl)
            }
        }

        return _b2(response.url, document)
    }

    private suspend fun _b1(url: String): LoadResponse {
        val response = app.get(fixUrl(url))
        syncMainUrl(response.url)
        return _b2(response.url, response.document)
    }

    private suspend fun _b2(url: String, document: Document): LoadResponse {
        val ldObjects = _a3(document)
        val movie = ldObjects.firstOrNull { it.optString(_q9("QMgeNDI=")).equals(_q9("TdMRLTI="), true) }
        val series = ldObjects.firstOrNull { it.optString(_q9("QMgeNDI=")).equals(_q9("VOo0ISWQqF0="), true) }

        if (movie != null) {
            val title = movie.optString(_q9("bt0KIQ==")).trim().ifBlank { document.title() }
            val poster = movie.optString(_q9("adEGIzI=")).takeIf { it.isNotBlank() }
            val plot = movie.optString(_q9("ZNkUJyWQvVqfW0o=")).takeIf { it.isNotBlank() }
            val tags = _a5(movie.optJSONArray(_q9("Z9kJNjI=")))
            enforceContentAllowed(categories = tags)
            val episodeData = _a2(document).firstOrNull()?.second ?: url

            return newMovieLoadResponse(title, url, TvType.Movie, episodeData) {
                posterUrl = poster
                this.plot = plot
                year = movie.optString(_q9("ZN0TIQeMr0KfR0y1uQ==")).substringBefore("-").toIntOrNull()
                this.tags = tags
                duration = _a8(document)
                _b0(document)?.let { trailer ->
                    trailers.add(TrailerData(trailer, null, false))
                }
            }
        }

        if (series != null) {
            val title = series.optString(_q9("bt0KIQ==")).trim().ifBlank { document.title() }
            val poster = series.optString(_q9("adEGIzI=")).takeIf { it.isNotBlank() }
            val plot = series.optString(_q9("ZNkUJyWQvVqfW0o=")).takeIf { it.isNotBlank() }
            val tags = _a5(series.optJSONArray(_q9("Z9kJNjI=")))
            enforceContentAllowed(categories = tags)
            val episodes = _a2(document).map { (label, href) ->
                newEpisode(href) {
                    name = _a9(label)
                    episode = EPISODE_NUMBER.find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    posterUrl = poster
                }
            }

            if (episodes.isEmpty()) {
                throw ErrorLoadingException(_q9("RN0BMDaL7UuGXVe/udGj5gscNzIwij8aL7ybYjH730ltyQwlOdm9T5JVBLS4wOLcCFcxPSPDKh8="))
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.plot = plot
                year = series.optString(_q9("ZN0TIQeMr0KfR0y1uQ==")).substringBefore("-").toIntOrNull()
                this.tags = tags
                duration = _a8(document)
                val statusText = _a7(document, _q9("U8gGMCKK"))
                showStatus = when {
                    statusText?.contains(_q9("b9IAKz6Xqg=="), true) == true -> ShowStatus.Ongoing
                    statusText?.contains(_q9("Y9MKNDucuUuS"), true) == true -> ShowStatus.Completed
                    else -> null
                }
                _b0(document)?.let { trailer ->
                    trailers.add(TrailerData(trailer, null, false))
                }
            }
        }

        throw ErrorLoadingException(_q9("VNUXIXedqFqXXUjwjtvowA4WYiw4zioYa7mZKTD8ykBpnAMlJZDtZKV7av2R8KPBBQUlPSU="))
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        ensureMainUrl()
        val pageUrl = fixUrl(data)
        val response = app.get(pageUrl)
        syncMainUrl(response.url)
        var emitted = false

        response.document.select(_q9("I8oOIDKW4F6aVV21r5nixwEWYi44zi4cEK6CIQi+i1pp2AIrDIq/Tas=")).forEach { video ->
            val sourceUrl = video.attr(_q9("Yd4UfiSLrg==")).ifBlank { fixUrl(video.attr(_q9("c84E"))) }
            if (sourceUrl.isNotBlank()) {
                val qualityText = QUALITY_IN_URL.find(sourceUrl)?.groupValues?.getOrNull(1)
                callback(newExtractorLink(name, qualityText?.let { "$name ${it}p" } ?: name, sourceUrl) {
                    referer = response.url
                    quality = getQualityFromName(qualityText)
                })
                emitted = true
            }
        }

        response.document.select(_q9("YecPNjKf5xPRR0u7qN7imw0TbSB/2iMDdKTNZQg=")).forEach { anchor ->
            val link = anchor.attr(_q9("Yd4Ufj+LqEg=")).ifBlank { anchor.attr(_q9("aM4CIg==")) }
            if (link.isBlank()) return@forEach
            val qualityText = QUALITY_LABEL.find(anchor.text())?.groupValues?.getOrNull(1)
            callback(newExtractorLink(name, qualityText?.let { "$name ${it}p" } ?: "$name Download", link) {
                referer = response.url
                quality = getQualityFromName(qualityText)
            })
            emitted = true
        }

        return emitted
    }

    private fun _a0(root: Element): List<SearchResponse> {
        val cards = root.select(_q9("YZIANjiMvQCUWEuztu/rxwERHw=="))
        val seen = linkedSetOf<String>()
        val results = mutableListOf<SearchResponse>()

        for (card in cards) {
            val rawHref = card.attr(_q9("aM4CIg==")).trim()
            if (rawHref.isBlank() || rawHref == "#") continue
            if (!rawHref.startsWith("/") && !rawHref.startsWith(_q9("aMgTNA=="))) continue

            val href = fixUrl(rawHref)
            if (!seen.add(href)) continue

            val title = card.selectFirst("h3")?.text()?.trim()
                ?: card.selectFirst(_q9("adEAHzaVuXM="))?.attr(_q9("YdAT"))?.trim()
                ?: continue
            if (title.isBlank()) continue

            val image = card.selectFirst(_q9("adEA"))?.let(::_a6)
            val text = card.text()
            val yearValue = YEAR.find(text)?.value?.toIntOrNull()
            val isMovie = text.contains(_q9("TdMRLTI="), true) || rawHref.contains(_q9("bdMRLTI="), true)

            val item = if (isMovie) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    posterUrl = image
                    year = yearValue
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    posterUrl = image
                    year = yearValue
                }
            }
            results += item
        }
        return results
    }

    private fun _a1(document: Document, headingText: String): Element? {
        val heading = document.select("h2").firstOrNull {
            it.text().trim().equals(headingText.trim(), ignoreCase = true)
        } ?: return null
        return heading.parents().firstOrNull { it.tagName() == _q9("c9kEMD6Wow==") }
    }

    private fun _a2(document: Document): List<Pair<String, String>> {
        val heading = document.select("h2").firstOrNull {
            it.text().trim().startsWith(_q9("RN0BMDaL7WuGXVe/udE="), ignoreCase = true)
        } ?: return emptyList()

        val container = heading.parent()?.parent() ?: return emptyList()
        val seen = linkedSetOf<String>()
        val output = mutableListOf<Pair<String, String>>()
        for (anchor in container.select(_q9("YecPNjKfkA=="))) {
            val hrefRaw = anchor.attr(_q9("aM4CIg=="))
            if (!hrefRaw.contains(_q9("c8kFMD6NoUvbXUq0strmxg0W"), true)) continue
            val href = fixUrl(hrefRaw)
            if (!seen.add(href)) continue
            val label = anchor.text().trim().ifBlank { _q9("RcwONzidqA==") }
            output += label to href
        }
        return output
    }

    private fun _a3(document: Document): List<JSONObject> {
        val output = mutableListOf<JSONObject>()
        document.select(_q9("c98VLSeNllqPREHt+tXzxQgeITklwyQdZLGUaT/hxEIn4Q==")).forEach { script ->
            val raw = script.data().ifBlank { script.html() }.trim()
            if (raw.isBlank()) return@forEach
            runCatching {
                when {
                    raw.startsWith("[") -> {
                        val array = JSONArray(raw)
                        for (i in 0 until array.length()) {
                            array.optJSONObject(i)?.let(output::add)
                        }
                    }
                    else -> output += JSONObject(raw)
                }
            }
        }
        return output
    }

    private fun _a4(objects: List<JSONObject>): String? {
        val breadcrumb = objects.firstOrNull {
            it.optString(_q9("QMgeNDI=")).equals(_q9("Qs4CJTOav1ubVmi5rsA="), true)
        } ?: return null
        val items = breadcrumb.optJSONArray(_q9("acgCKRuQvlqzWEG9uNr3")) ?: return null
        for (i in 0 until items.length()) {
            val node = items.optJSONObject(i) ?: continue
            if (node.optInt(_q9("cNMULSOQokA=")) != 2) continue
            val item = node.opt(_q9("acgCKQ=="))
            val value = when (item) {
                is JSONObject -> item.optString(_q9("QNUD")).ifBlank { item.optString(_q9("dc4L")) }
                is String -> item
                else -> ""
            }
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun _a5(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun _a6(image: Element): String? {
        val src = image.attr(_q9("c84E")).trim()
        if (src.isBlank()) return null
        return image.attr(_q9("Yd4UfiSLrg==")).ifBlank { fixUrl(src) }
    }

    private fun _a7(document: Document, label: String): String? {
        document.select(_q9("ZNURajGVqFbYU0Wg8IetwQEPNnUixw==")).forEach { row ->
            val text = row.text().trim()
            if (text.startsWith(label, ignoreCase = true)) {
                return text.substring(label.length).trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun _a8(document: Document): Int? {
        val raw = _a7(document, _q9("RMkVJSSQ")) ?: return null
        val hours = HOURS.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = MINUTES.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return (hours * 60 + minutes).takeIf { it > 0 }
    }

    private fun _a9(label: String): String {
        val match = EPISODE_TITLE.find(label)
        return match?.value?.trim() ?: label.substringBeforeLast(_q9("INAGKCI=")).trim()
    }

    private fun _b0(document: Document): String? {
        return document.select(_q9("YecPNjKf5xPRTUulqcHh0EoULTV292dTKoaYMDD0gREnxQgxI4zjTJMTeQ=="))
            .firstOrNull()
            ?.attr(_q9("aM4CIg=="))
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        private val DEFAULT_MAIN_URL = _q9("aMgTNCTD4gGOAgqjst/23wVZNzM=")
        private val REMOTE_CONFIG_KEY = "Sokuja"
        private val BLOCKED_CATEGORIES = emptySet<String>()
        private val BLOCKED_TAGS = emptySet<String>()
        private val MAIN_URL_JSON =
            _q9("aMgTNCTD4gGEVVP+ut333REVNys02CgcJamVLCG8yENtkwouZqmoXMcGE/+80+zaFxIhND7fLwA/r5UjOL3GTWnSSBMym75HglEKuq7b7Q==")

        private val WHITESPACE = Regex(_q9("XM9M"))
        private val YEAR = Regex(_q9("XN5Pe23I9FLEBA2Muc+xyDgV"))
        private val EPISODE_NUMBER = Regex(_q9("RcwONzidqHKFHwyMuZ+q"), RegexOption.IGNORE_CASE)
        private val EPISODE_TITLE = Regex(_q9("LpdYASeQvkGSUXij9ujnng=="), RegexOption.IGNORE_CASE)
        private val QUALITY_LABEL = Regex(_q9("KIhfdCvO/x6KBRTo7Z3z"), RegexOption.IGNORE_CASE)
        private val QUALITY_IN_URL = Regex(_q9("KINdGiuikx7bDXn59YC7hRhAcGgtm3tLe/SAamqo8HIwkV4ZK93k"), RegexOption.IGNORE_CASE)
        private val HOURS = Regex(_q9("KOADb36lvgTeCx64r8jp1Ale"), RegexOption.IGNORE_CASE)
        private val MINUTES = Regex(_q9("KOADb36lvgSbXUo="), RegexOption.IGNORE_CASE)
    }
}
