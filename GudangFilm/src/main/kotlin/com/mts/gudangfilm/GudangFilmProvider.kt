package com.mts.gudangfilm

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

class GudangFilmProvider : MainAPI() {
    override var mainUrl = "https://www.huazai6.com"
    override var name = "GudangFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    companion object {
        private const val TAG = "GudangFilmProvider"
        private const val CARD_SELECTOR = "article.item-infinite, article.item"
        private const val PLAYER_IFRAME_SELECTOR = ".gmr-server-wrap iframe"
        private const val PLAYER_MEDIA_SELECTOR =
            ".gmr-server-wrap video[src], .gmr-server-wrap source[src]"

        private val YEAR_REGEX = Regex("""\b(?:19|20)\d{2}\b""")
        private val SEASON_EPISODE_REGEX = Regex(
            """(?i)\bS(?:eason)?\s*[-_. ]*(\d+)\s*E(?:p(?:isode|s)?)?\s*[-_. ]*(\d+)\b"""
        )
        private val SEASON_REGEX = Regex("""(?i)\b(?:Season|S)\s*[-_. ]*(\d+)\b""")
        private val EPISODE_REGEX = Regex("""(?i)\b(?:Episode|Eps?|Ep)\s*[-_. ]*(\d+)\b""")

        private val BLOCKED_HOST_PARTS = setOf(
            "doubleclick",
            "googlesyndication",
            "googleadservices",
            "dtscout",
            "popads",
            "popcash",
            "adsterra",
            "histats",
            "cloudflareinsights",
        )
    }

    override val mainPage = mainPageOf(
        "" to "Terbaru",
        "tv/" to "TV Series",
        "ongoing/" to "Ongoing",
        "country/korea/" to "Korea",
        "country/japan/" to "Japan",
        "country/hong-kong/" to "Hong Kong",
        "country/italy/" to "Italy",
        "country/usa/" to "USA",
        "country/germany/" to "Germany",
        "country/france/" to "France",
        "country/china/" to "China",
        "genre/semi-jepang/" to "18+",
        "genre/action/" to "Action",
        "genre/horror/" to "Horror",
        "genre/adventure/" to "Adventure",
        "genre/comedy/" to "Comedy",
        "genre/crime/" to "Crime",
        "genre/drama/" to "Drama",
        "genre/fantasy/" to "Fantasy",
        "genre/mystery/" to "Mystery",
        "genre/romance/" to "Romance",
        "genre/science-fiction/" to "Science Fiction",
        "genre/thriller/" to "Thriller",
        "genre/history/" to "History",
        "genre/war/" to "War",
        "genre/tv-movie/" to "TV Movie",
        "genre/animation/" to "Animation",
        "genre/family/" to "Family",
        "genre/music/" to "Music",
        "genre/uncategory/" to "Uncategory",
        "year/2026/" to "2026",
        "year/2025/" to "2025",
        "year/2024/" to "2024",
        "year/2023/" to "2023",
        "year/2022/" to "2022",
        "year/2021/" to "2021",
        "year/2020/" to "2020",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = buildPageUrl(request.data, page)
        val document = app.get(pageUrl, timeout = 20).document
        val items = document.select(CARD_SELECTOR)
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        val hasNext = document.selectFirst("a.next.page-numbers, a[rel=next]") != null
        return newHomePageResponse(request.name, items, hasNext = hasNext)
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
        val anchor = selectFirst(
            ".content-thumbnail a[href], h2.entry-title a[href], h3.entry-title a[href], a[itemprop=url]"
        ) ?: return null

        val href = safeUrl(anchor.attr("href")) ?: return null
        val titleElement = selectFirst("h2.entry-title, h3.entry-title, .entry-title, .title")
        val rawTitle = titleElement?.text()
            ?.ifBlank { null }
            ?: anchor.attr("title").ifBlank { null }
            ?: selectFirst("img")?.attr("alt").orEmpty()

        val title = cleanTitle(rawTitle)
        if (title.isBlank()) return null

        val posterUrl = selectFirst(".content-thumbnail img, img[itemprop=image], img")
            ?.bestImageUrl()
        val typeText = selectFirst(".gmr-posttype-item")?.text().orEmpty()
        val isSeries = href.contains("/tv/", ignoreCase = true) ||
            typeText.contains("TV", ignoreCase = true) ||
            selectFirst(".gmr-numbeps") != null

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun cleanTitle(value: String): String = value
        .removePrefix("Permalink ke: ")
        .removePrefix("Permalink to: ")
        .removePrefix("Download ")
        .substringBefore(" Sub Indo")
        .substringBefore(" Full Movie")
        .substringBefore(" Full Episode")
        .trim()

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val document = app.get("$mainUrl/?s=$encodedQuery", timeout = 20).document
        return document.select(CARD_SELECTOR)
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, timeout = 20).document
        val title = document.selectFirst("h1.entry-title, .gmr-movie-data h1, .title-content")
            ?.text()
            ?.trim()
            ?.ifBlank { null }
            ?: document.meta("meta[property=og:title]")
                ?.substringBefore(" - GUDANG FILM")
            ?: document.title().substringBefore(" - GUDANG FILM").trim()

        if (title.isBlank()) throw ErrorLoadingException("Judul tidak ditemukan")

        val poster = document.selectFirst(
            ".gmr-movie-data img[itemprop=image], .gmr-poster-img img, .poster img, img.wp-post-image"
        )?.bestImageUrl() ?: safeUrl(document.meta("meta[property=og:image]"))

        val plot = document.selectFirst(".entry-content.entry-content-single > p, .entry-content > p")
            ?.text()
            ?.trim()
            ?.ifBlank { null }
            ?: document.meta("meta[name=description]")
            ?: document.meta("meta[property=og:description]")

        val year = document.selectFirst("a[href*='/year/']")
            ?.text()
            ?.trim()
            ?.toIntOrNull()
            ?: YEAR_REGEX.find(title)?.value?.toIntOrNull()

        val tags = document.select("a[href*='/genre/']")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val score = Score.from10(
            document.selectFirst("[itemprop=ratingValue]")
                ?.text()
                ?.trim()
        )

        val bodyClasses = document.body()?.classNames().orEmpty()
        val isSeries = url.contains("/tv/", ignoreCase = true) ||
            bodyClasses.contains("single-tv") ||
            document.selectFirst(".gmr-listseries a[href*='/eps/']") != null

        if (isSeries) {
            val episodes = parseEpisodes(document)
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.score = score
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            this.score = score
        }
    }

    private fun parseEpisodes(document: Document): List<Episode> {
        val primary = document.select(".gmr-listseries a[href*='/eps/']")
        val links = if (primary.isNotEmpty()) primary else document.select("a[href*='/eps/']")

        return links.mapIndexedNotNull { index, anchor ->
            val episodeUrl = safeUrl(anchor.attr("href")) ?: return@mapIndexedNotNull null
            val label = anchor.text().trim()
            val sourceText = listOf(label, anchor.attr("title"), episodeUrl).joinToString(" ")
            val combined = SEASON_EPISODE_REGEX.find(sourceText)
            val season = combined?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: SEASON_REGEX.find(sourceText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 1
            val episode = combined?.groupValues?.getOrNull(2)?.toIntOrNull()
                ?: EPISODE_REGEX.find(sourceText)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: (index + 1)

            newEpisode(episodeUrl) {
                this.season = season
                this.episode = episode
                this.name = "Season $season Episode $episode"
            }
        }
            .distinctBy { it.data }
            .sortedWith(compareBy<Episode> { it.season ?: 1 }.thenBy { it.episode ?: 0 })
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        return runCatching {
            val document = app.get(data, timeout = 20).document
            var found = extractPlayerLinks(document, data, subtitleCallback, callback)

            // Hanya membuka tab server internal yang benar-benar berada di daftar player.
            // Link menu, trailer, komentar, onclick, dan domain eksternal tidak pernah diikuti.
            if (!found) {
                val siteHost = URI(mainUrl).host.lowercase()
                val tabUrls = document.select(".muvipro-player-tabs a[href]")
                    .mapNotNull { safeUrl(it.attr("href")) }
                    .filter { tabUrl ->
                        val uri = runCatching { URI(tabUrl) }.getOrNull()
                        uri?.host?.lowercase() == siteHost && tabUrl.trimEnd('/') != data.trimEnd('/')
                    }
                    .distinct()
                    .take(4)

                for (tabUrl in tabUrls) {
                    val tabDocument = app.get(
                        tabUrl,
                        referer = data,
                        timeout = 15,
                    ).document
                    if (extractPlayerLinks(tabDocument, tabUrl, subtitleCallback, callback)) {
                        found = true
                    }
                }
            }
            found
        }.getOrElse { error ->
            Log.e(TAG, "loadLinks gagal: ${error.message}")
            false
        }
    }

    private suspend fun extractPlayerLinks(
        document: Document,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var found = false

        val iframeUrls = document.select(PLAYER_IFRAME_SELECTOR)
            .mapNotNull { iframe ->
                listOf(
                    "data-litespeed-src",
                    "data-lazy-src",
                    "data-src",
                    "data-video",
                    "data-embed",
                    "data-url",
                    "data-iframe",
                    "src",
                ).firstNotNullOfOrNull { attribute ->
                    iframe.attr(attribute).takeIf { it.isNotBlank() }
                }
            }
            .mapNotNull(::safeUrl)
            .filterNot(::isTrailerUrl)
            .distinct()

        for (embedUrl in iframeUrls) {
            if (loadExtractor(embedUrl, referer, subtitleCallback, callback)) {
                found = true
            }
        }

        val directMediaUrls = document.select(PLAYER_MEDIA_SELECTOR)
            .mapNotNull { safeUrl(it.attr("src")) }
            .distinct()

        for (mediaUrl in directMediaUrls) {
            when {
                mediaUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> {
                    generateM3u8(name, mediaUrl, referer).forEach(callback)
                    found = true
                }
                mediaUrl.substringBefore('?').endsWith(".mp4", ignoreCase = true) -> {
                    callback(
                        newExtractorLink(name, name, mediaUrl, ExtractorLinkType.VIDEO) {
                            this.referer = referer
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    found = true
                }
            }
        }

        return found
    }

    private fun isTrailerUrl(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host.contains("youtube.com") || host.contains("youtu.be")
    }

    private fun safeUrl(rawUrl: String?): String? {
        val value = rawUrl
            ?.trim()
            ?.replace("\\/", "/")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        if (
            value.startsWith("javascript:", ignoreCase = true) ||
            value.startsWith("data:", ignoreCase = true) ||
            value.equals("about:blank", ignoreCase = true) ||
            value.startsWith("#")
        ) return null

        val normalized = if (value.startsWith("//")) "https:$value" else value
        val fixed = fixUrlNull(normalized) ?: return null
        val uri = runCatching { URI(fixed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null

        if (scheme != "http" && scheme != "https") return null
        if (BLOCKED_HOST_PARTS.any(host::contains)) return null
        return fixed
    }

    private fun Element.bestImageUrl(): String? {
        val srcSetCandidate = attr("srcset")
            .split(',')
            .mapNotNull { candidate ->
                val parts = candidate.trim().split(Regex("""\s+"""))
                val url = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val width = parts.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
                url to width
            }
            .maxByOrNull { it.second }
            ?.first

        val raw = listOf(
            attr("data-litespeed-src"),
            attr("data-lazy-src"),
            attr("data-original"),
            srcSetCandidate.orEmpty(),
            attr("src"),
        ).firstOrNull { it.isNotBlank() && !it.startsWith("data:", ignoreCase = true) }

        return safeUrl(raw)
    }

    private fun Document.meta(selector: String): String? = selectFirst(selector)
        ?.attr("content")
        ?.trim()
        ?.ifBlank { null }
}
