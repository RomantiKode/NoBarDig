package com.agooseangsa.Indomax21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.net.URLEncoder

class Indomax21 : MainAPI() {
    override var mainUrl = DEFAULT_MAIN_URL
    override var name = "Indomax21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama,
    )

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false

    override val mainPage = mainPageOf(
        "category/box-office/page/%d/" to "Box Office",
        "category/serial-tv/page/%d/" to "TV Series",
        "category/action/page/%d/" to "Action",
        "category/adventure/page/%d/" to "Adventure",
        "category/animation/page/%d/" to "Animation",
        "category/anime/page/%d/" to "Anime",
        "category/comedy/page/%d/" to "Comedy",
        "category/donghua/page/%d/" to "Donghua",
        "category/thriller/page/%d/" to "Thriller",
        "country/china/page/%d/" to "China",
        "country/indonesia/page/%d/" to "Indonesia",
        "country/korea/page/%d/" to "Korea",
        "country/philippines/page/%d/" to "Philippines",
        "country/thailand/page/%d/" to "Thailand",
    )

    private suspend fun loadMainUrlIfNeeded() {
        if (mainUrlResolved) return

        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock

            val remoteCandidates = runCatching {
                val json = JSONObject(app.get(MAIN_URL_JSON).text)
                json.readMainUrlCandidates()
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

            // Keep the built-in fallback. Resolution remains retryable on the next request
            // in case GitHub or the website was only temporarily unavailable.
            mainUrl = DEFAULT_MAIN_URL
        }
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
            if ((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) {
                "${uri.scheme}://${uri.authority}"
            } else {
                null
            }
        }.getOrNull()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        loadMainUrlIfNeeded()
        val response = app.get("$mainUrl/${request.data.format(page)}")
        syncMainUrl(response.url)
        val items = response.document
            .select(ITEM_SELECTOR)
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = selectFirst("h2.entry-title a") ?: return null
        val title = extractListTitle(link) ?: return null
        val href = fixUrl(link.attr("href"))
        val poster = fixUrlNull(selectFirst("img.wp-post-image")?.getImageAttr())?.fixImageQuality()
        val quality = selectFirst(".gmr-quality-item a")?.text()?.trim().orEmpty()
        val rating = selectFirst(".gmr-rating-item")?.ownText()?.trim()?.toDoubleOrNull()
        val episodes = selectFirst(".gmr-numbeps span")?.text()?.firstIntOrNull()
        val isSeries = selectFirst(".gmr-numbeps") != null

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                this.episodes = episodes
                if (quality.isNotEmpty()) addQuality(quality)
                if (rating != null) score = Score.from10(rating)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                if (quality.isNotEmpty()) addQuality(quality)
                if (rating != null) score = Score.from10(rating)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        loadMainUrlIfNeeded()
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val response = app.get("$mainUrl/?s=$encodedQuery&post_type[]=post&post_type[]=tv")
        syncMainUrl(response.url)
        return response.document.select(ITEM_SELECTOR).mapNotNull { it.toSearchResult() }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val link = selectFirst("h2.entry-title > a") ?: return null
        val title = extractListTitle(link) ?: return null
        val href = fixUrl(link.attr("href"))
        val poster = fixUrlNull(selectFirst("div.content-thumbnail img")?.getImageAttr())?.fixImageQuality()
        val quality = select("div.gmr-qual, div.gmr-quality-item > a")
            .text().trim().replace("-", "")
        val rating = selectFirst("div.gmr-rating-item")?.ownText()?.trim()?.toDoubleOrNull()
        val episodeCount = selectFirst("div.gmr-numbeps")?.text()?.firstIntOrNull()
        val isSeries = selectFirst("div.gmr-numbeps") != null || title.contains("TV Show", ignoreCase = true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                episodes = episodeCount
                if (quality.isNotEmpty()) addQuality(quality)
                if (rating != null) score = Score.from10(rating)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                if (quality.isNotEmpty()) addQuality(quality)
                if (rating != null) score = Score.from10(rating)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        loadMainUrlIfNeeded()
        val response = app.get(url)
        val doc = response.document
        val pageUrl = response.url
        syncMainUrl(pageUrl)

        val tvType = if (pageUrl.contains("/tv/")) TvType.TvSeries else TvType.Movie
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            .orIfBlank { doc.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" - ") }
            .orEmpty()
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = doc.extractDescription()
        val tags = doc.movieData("Genre")?.select("a")?.map { it.text().trim() }?.filter { it.isNotBlank() }.orEmpty()
        val year = doc.movieData("Tahun", "Year")?.text()?.firstYearOrNull()
            ?: title.firstYearOrNull()
        val actors = doc.select("span[itemprop=actor] a, span[itemprop=actors] a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val rating = doc.selectFirst("div.gmr-rating-bar span")
            ?.attr("style")
            ?.substringAfter("width:", "")
            ?.substringBefore("%", "")
            ?.trim()
            ?.toDoubleOrNull()
            ?.div(10)
        val duration = doc.movieData("Durasi", "Duration")?.text()?.firstIntOrNull()
        val recommendations = doc.select(RECOMMENDATION_SELECTOR).mapNotNull { it.toRecommendResult() }
        val currentTrailer = doc.selectFirst("div.gmr-box-content.gmr-single a.gmr-trailer-popup")
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
        val embeddedTrailer = doc.selectFirst(PLAYER_IFRAME_SELECTOR)
            ?.getIframeUrl(pageUrl)
            ?.takeIf { it.contains("youtube.com", ignoreCase = true) || it.contains("youtu.be", ignoreCase = true) }

        return if (tvType == TvType.TvSeries) {
            val episodes = doc.select("div.vid-episodes a, div.gmr-listseries a")
                .mapNotNull { it.toEpisode(poster) }
                .distinctBy { it.data }
                .sortedWith(compareBy<Episode> { it.season ?: 1 }.thenBy { it.episode ?: Int.MAX_VALUE })

            newTvSeriesLoadResponse(title, pageUrl, TvType.TvSeries, episodes) {
                posterUrl = poster
                plot = description
                this.tags = tags
                this.year = year
                if (rating != null) score = Score.from10(rating)
                addActors(actors)
                this.recommendations = recommendations
                this.duration = duration ?: 0
                addTrailer(embeddedTrailer ?: currentTrailer)
            }
        } else {
            newMovieLoadResponse(title, pageUrl, TvType.Movie, pageUrl) {
                posterUrl = poster
                plot = description
                this.tags = tags
                this.year = year
                if (rating != null) score = Score.from10(rating)
                addActors(actors)
                this.recommendations = recommendations
                this.duration = duration ?: 0
                addTrailer(currentTrailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        loadMainUrlIfNeeded()
        val response = app.get(data)
        val pageUrl = response.url
        val doc = response.document
        syncMainUrl(pageUrl)

        // We only read known player containers. Scripts, onclick handlers, banners and
        // external ad links are never executed or followed by this provider.
        val directFrames = doc.select(PLAYER_IFRAME_SELECTOR)
            .mapNotNull { iframe -> iframe.getIframeUrl(pageUrl)?.let { it to pageUrl } }

        val serverUrls = doc.select(SERVER_TAB_SELECTOR)
            .mapNotNull { tab ->
                if (tab.hasClass("active")) return@mapNotNull null
                resolveHttpUrl(pageUrl, tab.attr("href"))
            }
            .filter { isSameHost(pageUrl, it) }
            .distinct()

        val alternateFrames = serverUrls.amap { serverUrl ->
            runCatching {
                val serverResponse = app.get(serverUrl)
                serverResponse.document.select(PLAYER_IFRAME_SELECTOR)
                    .mapNotNull { iframe ->
                        iframe.getIframeUrl(serverResponse.url)?.let { it to serverResponse.url }
                    }
            }.getOrDefault(emptyList())
        }.flatten()

        val frames = (directFrames + alternateFrames).distinctBy { it.first }
        frames.amap { (iframeUrl, referer) ->
            runCatching {
                loadExtractor(iframeUrl, referer, subtitleCallback, callback)
            }
        }

        return frames.isNotEmpty()
    }

    /**
     * Prefer title metadata that is already canonical instead of blindly deleting "Nonton".
     * The promotional prefix is removed only when another title value from the same card
     * (permalink metadata or itemprop=name) confirms the title without it.
     * This preserves legitimate works whose real title actually begins with "Nonton".
     */
    private fun Element.extractListTitle(link: Element): String? {
        val visibleTitle = link.text().normalizeTitleText().takeIf { it.isNotBlank() } ?: return null
        val withoutPromoPrefix = visibleTitle.removeLeadingNontonCandidate() ?: return visibleTitle

        val permalinkTitle = link.attr("title")
            .normalizeTitleText()
            .replaceFirst(Regex("""(?i)^Permalink\s+ke\s*:\s*"""), "")
            .takeIf { it.isNotBlank() }
        val structuredTitle = selectFirst("[itemprop=name]")
            ?.text()
            ?.normalizeTitleText()
            ?.takeIf { it.isNotBlank() }

        return listOfNotNull(permalinkTitle, structuredTitle)
            .firstOrNull { it.equals(withoutPromoPrefix, ignoreCase = true) }
            ?: visibleTitle
    }

    private fun String.removeLeadingNontonCandidate(): String? {
        val match = Regex("(?i)^Nonton\\s+(.+)$").matchEntire(trim()) ?: return null
        return match.groupValues[1].normalizeTitleText().takeIf { it.isNotBlank() }
    }

    private fun String.normalizeTitleText(): String =
        replace(Regex("\\s+"), " ").trim()

    private fun Element.toEpisode(poster: String?): Episode? {
        val href = resolveHttpUrl(mainUrl, attr("href")) ?: return null
        val anchorText = text().trim()
        val rawTitle = attr("title").takeIf { it.isNotBlank() } ?: anchorText
        val cleanTitle = rawTitle.replaceFirst(Regex("(?i)^Permalink ke\\s*"), "").trim()

        val seasonEpisode = Regex("(?i)S(\\d+)\\s*Eps?(\\d+)").find(anchorText)
        val season = seasonEpisode?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episode = seasonEpisode?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: Regex("(?i)Episode\\s*(\\d+)").find(cleanTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("(?i)Eps?\\s*(\\d+)").find(anchorText)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return null

        return newEpisode(href) {
            name = "Episode $episode"
            this.season = season
            this.episode = episode
            posterUrl = poster
        }
    }

    private fun Document.extractDescription(): String? {
        val content = selectFirst("div[itemprop=description], div.entry-content")?.clone()
        if (content != null) {
            content.select(
                ".idmuvi-banner-beforecontent, .idmuvi-banner-aftercontent, " +
                    ".content-moviedata, .tags-links-content, script, iframe, noscript"
            ).remove()

            val bodyDescription = content.select("p")
                .map { it.text().trim() }
                .filter { it.length >= 40 }
                .maxByOrNull { it.length }

            if (!bodyDescription.isNullOrBlank()) return bodyDescription
        }

        return listOf(
            selectFirst("meta[name=description]")?.attr("content"),
            selectFirst("meta[property=og:description]")?.attr("content"),
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun Document.movieData(vararg labels: String): Element? =
        select("div.gmr-moviedata").firstOrNull { element ->
            val label = element.selectFirst("strong")?.text()?.trim()?.removeSuffix(":") ?: return@firstOrNull false
            labels.any { it.equals(label, ignoreCase = true) }
        }

    private fun Element.getImageAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        else -> attr("abs:src")
    }

    private fun Element.getIframeUrl(baseUrl: String): String? {
        val rawUrl = attr("data-litespeed-src").takeIf { it.isNotBlank() }
            ?: attr("src").takeIf { it.isNotBlank() }
            ?: return null
        return resolveHttpUrl(baseUrl, rawUrl)
    }

    private fun resolveHttpUrl(baseUrl: String, rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank() || trimmed.startsWith("#")) return null
        if (trimmed.startsWith("javascript:", ignoreCase = true)) return null
        if (trimmed.startsWith("data:", ignoreCase = true)) return null

        return runCatching {
            val resolved = URI(baseUrl).resolve(trimmed)
            if (resolved.scheme.equals("http", ignoreCase = true) || resolved.scheme.equals("https", ignoreCase = true)) {
                resolved.toString()
            } else null
        }.getOrNull()
    }

    private fun isSameHost(baseUrl: String, targetUrl: String): Boolean = runCatching {
        URI(baseUrl).host.equals(URI(targetUrl).host, ignoreCase = true)
    }.getOrDefault(false)

    private fun syncMainUrl(url: String) {
        normalizeHttpBaseUrl(url)?.let { mainUrl = it }
    }

    private fun String?.orIfBlank(fallback: () -> String?): String? =
        if (this.isNullOrBlank()) fallback() else this

    private fun String.firstIntOrNull(): Int? = Regex("\\d+").find(this)?.value?.toIntOrNull()

    private fun String.firstYearOrNull(): Int? =
        Regex("(?<!\\d)(19|20)\\d{2}(?!\\d)").find(this)?.value?.toIntOrNull()

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val suffix = Regex("-\\d+x\\d*(?=\\.)").find(this)?.value ?: return this
        return replace(suffix, "")
    }

    companion object {
        private const val DEFAULT_MAIN_URL = "https://onperfect.com"
        private const val MAIN_URL_JSON = "https://raw.githubusercontent.com/mj1Per127/agoosecloudstream/main/Website.json"
        private const val REMOTE_CONFIG_KEY = "indomax21"
        private const val ITEM_SELECTOR = "article.item-infinite"
        private const val RECOMMENDATION_SELECTOR = "article.item.col-md-20"
        private const val PLAYER_IFRAME_SELECTOR = "div.gmr-embed-responsive iframe"
        private const val SERVER_TAB_SELECTOR = "ul.muvipro-player-tabs li a"
    }
}
