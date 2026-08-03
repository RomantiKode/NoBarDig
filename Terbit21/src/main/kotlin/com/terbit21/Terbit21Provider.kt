package com.terbit21

import com.lagradost.cloudstream3.fixUrlNull

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Terbit21Provider : MainAPI() {
    override var mainUrl = "https://162.244.95.227"
    override var name = "Terbit21"
    override var lang = "id"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasChromecastSupport = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "Terbaru",
        "on-going/" to "Ongoing",
        "tv/" to "Serial TV",
        "completed/" to "Serial Tamat",
        "drama-korea/" to "Drama Korea",
        "film-action-terbaru/" to "Action",
        "adventure/" to "Adventure",
        "west-series/" to "West Series",
        "batch/" to "Batch",
        "year/2026/" to "2026",
        "year/2025/" to "2025",
        "year/2024/" to "2024"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(buildPageUrl(request.data, page), timeout = REQUEST_TIMEOUT).document
        val items = document.select(CARD_SELECTOR)
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
        val hasNext = document.selectFirst(NEXT_PAGE_SELECTOR) != null
        return newHomePageResponse(request.name, items, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        return app.get("$mainUrl/?s=$encodedQuery", timeout = REQUEST_TIMEOUT).document
            .select(CARD_SELECTOR)
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = runCatching {
            app.get(url, timeout = REQUEST_TIMEOUT).document
        }.getOrNull() ?: return null

        val title = document.selectFirst(TITLE_SELECTOR)
            ?.text()
            ?.cleanTitle()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = document.selectFirst(POSTER_SELECTOR)?.bestImageUrl()
        val plot = document.selectFirst(PLOT_SELECTOR)
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val episodeElements = document.select(EPISODE_SELECTOR)
            .filter { it.attr("href").contains("/eps/") }
        val isSeries = url.contains("/tv/", ignoreCase = true) ||
            document.selectFirst("body.single-tv") != null ||
            episodeElements.isNotEmpty()

        if (!isSeries) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }

        val episodes = episodeElements
            .distinctBy { it.attr("href") }
            .mapIndexedNotNull { index, element -> element.toEpisode(index) }
            .sortedWith(compareBy<Episode>({ it.season ?: 0 }, { it.episode ?: 0 }))

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val rootDocument = runCatching {
            app.get(data, timeout = REQUEST_TIMEOUT).document
        }.getOrNull() ?: return false

        val playerPages = linkedMapOf(data to rootDocument)

        // Only regular server-tab hrefs are followed. Player navigation, onclick,
        // javascript links, comments, trailers, share links and popup-download are ignored.
        rootDocument.select(SERVER_TAB_SELECTOR)
            .mapNotNull { resolveUrl(it.attr("href")) }
            .filter { it != data && isSafeServerPage(it) }
            .distinct()
            .take(MAX_SERVER_PAGES)
            .forEach { serverUrl ->
                val serverDocument = runCatching {
                    app.get(
                        serverUrl,
                        timeout = SERVER_TIMEOUT,
                        headers = mapOf("Referer" to data)
                    ).document
                }.getOrNull()
                if (serverDocument != null) playerPages[serverUrl] = serverDocument
            }

        val embeds = linkedMapOf<String, String>()
        playerPages.forEach { (pageUrl, document) ->
            document.select(PLAYER_IFRAME_SELECTOR).forEach { iframe ->
                val embedUrl = iframe.playerSource()?.let(::resolveUrl)
                if (embedUrl != null && isSafePlayerUrl(embedUrl)) {
                    embeds.putIfAbsent(embedUrl, pageUrl)
                }
            }
        }

        var found = false
        for ((embedUrl, referer) in embeds) {
            val extracted = runCatching {
                loadExtractor(embedUrl, referer, subtitleCallback, callback)
            }.getOrDefault(false)
            if (extracted) found = true
        }
        return found
    }

    private fun buildPageUrl(path: String, page: Int): String {
        val cleanPath = path.trim('/')
        return when {
            page <= 1 && cleanPath.isEmpty() -> "$mainUrl/"
            page <= 1 -> "$mainUrl/$cleanPath/"
            cleanPath.isEmpty() -> "$mainUrl/page/$page/"
            else -> "$mainUrl/$cleanPath/page/$page/"
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = selectFirst(CARD_LINK_SELECTOR) ?: return null
        val url = resolveUrl(anchor.attr("href")) ?: return null
        val image = selectFirst("img")
        val title = selectFirst("h2.entry-title, h3.entry-title, .entry-title")
            ?.text()
            .orEmpty()
            .ifBlank { anchor.attr("title") }
            .ifBlank { image?.attr("alt").orEmpty() }
            .cleanTitle()
        if (title.isBlank()) return null

        val poster = image?.bestImageUrl()
        val isSeries = url.contains("/tv/", ignoreCase = true) ||
            url.contains("/series/", ignoreCase = true) ||
            title.contains("season", ignoreCase = true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, url, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    private fun Element.toEpisode(index: Int): Episode? {
        val episodeUrl = resolveUrl(attr("href")) ?: return null
        val sourceText = listOf(text(), attr("title"), episodeUrl).joinToString(" ")
        val seasonNumber = SEASON_REGEX.find(sourceText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episodeNumber = EPISODE_REGEX.find(sourceText)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: index + 1
        val label = text().trim().ifBlank {
            if (seasonNumber != null) "S$seasonNumber E$episodeNumber" else "Episode $episodeNumber"
        }

        return newEpisode(episodeUrl) {
            this.season = seasonNumber
            this.episode = episodeNumber
            this.name = label
        }
    }

    private fun Element.bestImageUrl(): String? {
        val srcsetUrl = attr("srcset")
            .split(',')
            .mapNotNull { entry ->
                val parts = entry.trim().split(Regex("\\s+"))
                val url = parts.firstOrNull().orEmpty()
                if (url.isBlank()) return@mapNotNull null
                val width = parts.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
                url to width
            }
            .maxByOrNull { it.second }
            ?.first

        return listOf(
            attr("data-lazy-src"),
            attr("data-src"),
            attr("data-original"),
            srcsetUrl.orEmpty(),
            attr("src")
        ).firstOrNull { candidate ->
            candidate.isNotBlank() &&
                !candidate.startsWith("data:", ignoreCase = true) &&
                !candidate.contains("placeholder", ignoreCase = true)
        }?.let(::resolveUrl)
    }

    private fun Element.playerSource(): String? = listOf(
        attr("data-litespeed-src"),
        attr("data-lazy-src"),
        attr("data-src"),
        attr("src")
    ).firstOrNull { source ->
        source.isNotBlank() &&
            !source.equals("about:blank", ignoreCase = true) &&
            !source.startsWith("javascript:", ignoreCase = true)
    }

    private fun resolveUrl(rawUrl: String): String? {
        val value = rawUrl.trim()
        if (value.isBlank() || value.startsWith("#") || value.startsWith("javascript:", true)) {
            return null
        }
        return if (value.startsWith("//")) "https:$value" else fixUrlNull(value)
    }

    private fun isSafeServerPage(url: String): Boolean =
        url.startsWith(mainUrl, ignoreCase = true) &&
            !url.contains("javascript:", ignoreCase = true)

    private fun isSafePlayerUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.startsWith("http://") || lower.startsWith("https://")) &&
            BLOCKED_PLAYER_MARKERS.none(lower::contains)
    }

    private fun String.cleanTitle(): String = this
        .removePrefix("Permalink ke: ")
        .removePrefix("Permalink to: ")
        .removePrefix("Download ")
        .replace(TITLE_SUFFIX_REGEX, "")
        .trim()

    companion object {
        private const val REQUEST_TIMEOUT : Long = 20L
        private const val SERVER_TIMEOUT : Long = 15L
        private const val MAX_SERVER_PAGES = 4

        private const val CARD_SELECTOR =
            "article.item-infinite, article.item, div.gmr-box-item"
        private const val CARD_LINK_SELECTOR =
            "h2.entry-title a[href], h3.entry-title a[href], .entry-title a[href], a[rel=bookmark][href], .content-thumbnail a[href]"
        private const val NEXT_PAGE_SELECTOR =
            "a.next.page-numbers, a[rel=next], .nav-previous a"
        private const val TITLE_SELECTOR =
            "h1.entry-title, h1[itemprop=name], .title-content"
        private const val POSTER_SELECTOR =
            ".gmr-movie-data figure img[itemprop=image], .gmr-movie-data img.wp-post-image, article img.wp-post-image, .poster img"
        private const val PLOT_SELECTOR =
            ".entry-content[itemprop=description] > p:first-of-type, .entry-content > p:first-of-type, [itemprop=description] > p:first-of-type, .synopsis p:first-of-type"
        private const val EPISODE_SELECTOR =
            ".gmr-listseries a[href*='/eps/'], a.button[href*='/eps/'], .gmr-listepisode a[href*='/eps/'], .list-episode a[href*='/eps/']"
        private const val SERVER_TAB_SELECTOR =
            "ul.muvipro-player-tabs a[href], ul.gmr-player-tabs a[href], ul#gmr-tab a[href]"
        private const val PLAYER_IFRAME_SELECTOR =
            ".gmr-server-wrap .gmr-embed-responsive iframe, .gmr-server-wrap iframe, .gmr-embed-responsive iframe"

        private val TITLE_SUFFIX_REGEX = Regex(
            """\s+(?:Sub\s*Indo|Subtitle\s+Indonesia|Full\s+Movie|Full\s+Episode).*$""",
            RegexOption.IGNORE_CASE
        )
        private val SEASON_REGEX = Regex(
            """(?:\bseason|\bs)\s*[-_ ]*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        private val EPISODE_REGEX = Regex(
            """(?:\bepisode|\beps?)\s*[-_ ]*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        private val BLOCKED_PLAYER_MARKERS = listOf(
            "facebook.com",
            "twitter.com",
            "x.com/intent",
            "youtube.com",
            "youtu.be",
            "whatsapp.com",
            "t.me/share",
            "doubleclick.net",
            "googlesyndication.com",
            "javascript:"
        )
    }
}

