package com.filmlokal

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

class Filmlokal : MainAPI() {
    override var mainUrl = "https://tv1.filmlokal.me"
    override var name = "Filmlokal"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "page/%d/" to "Terbaru",
        "film-series/page/%d/" to "Film Series",
        "action/page/%d/" to "Action",
        "adventure/page/%d/" to "Adventure",
        "animation/page/%d/" to "Animation",
        "comedy/page/%d/" to "Comedy",
        "crime/page/%d/" to "Crime",
        "drama/page/%d/" to "Drama",
        "horror/page/%d/" to "Horror",
        "mystery/page/%d/" to "Mystery",
        "sci-fi/page/%d/" to "Sci-Fi",
        "thriller/page/%d/" to "Thriller",
        "country/philippines/page/%d/" to "Philippines",
        "country/korea/page/%d/" to "Korea",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val path = request.data.format(page).trimStart('/')
        val response = app.get("$mainUrl/$path")
        updateMainUrl(response.url)

        val items = response.document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val response = app.get("$mainUrl/?s=$encodedQuery")
        updateMainUrl(response.url)
        return response.document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleAnchor = selectFirst("h2.entry-title > a") ?: return null
        val title = titleAnchor.text().trim().takeIf { it.isNotEmpty() } ?: return null
        val href = titleAnchor.attr("abs:href").ifBlank { titleAnchor.attr("href") }
            .takeIf { it.isNotBlank() } ?: return null
        val poster = selectFirst("div.content-thumbnail img, a > img")
            ?.getImageAttr()?.fixImageQuality()
        val ratingText = selectFirst("div.gmr-rating-item")?.ownText()?.trim()
        val quality = select("div.gmr-qual, div.gmr-quality-item > a")
            .text().trim().replace("-", "")

        return if (isSeriesUrl(href)) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                applyCardMetadata(poster, quality, ratingText)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                applyCardMetadata(poster, quality, ratingText)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        updateMainUrl(response.url)
        val document = response.document
        val pageUrl = response.url

        if (isEpisodeUrl(pageUrl)) {
            val parentSeries = document.selectFirst("div.gmr-listseries a[href*=/tv/]")
                ?.attr("abs:href")?.takeIf { it.isNotBlank() }
            if (parentSeries != null && parentSeries != pageUrl) return load(parentSeries)
        }

        val metadata = document.readMetadata()
        val episodes = document.select("div.gmr-listseries a[href*=/eps/]")
            .mapNotNull { it.toEpisode(metadata.poster) }
            .distinctBy { it.data }
            .sortedWith(compareBy<Episode> { it.season ?: 0 }.thenBy { it.episode ?: 0 })

        val isSeries = isSeriesUrl(pageUrl) || episodes.isNotEmpty()
        return if (isSeries) {
            newTvSeriesLoadResponse(
                metadata.title,
                pageUrl,
                TvType.TvSeries,
                episodes,
            ) {
                applyMetadata(metadata)
            }
        } else {
            newMovieLoadResponse(metadata.title, pageUrl, TvType.Movie, pageUrl) {
                applyMetadata(metadata)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val response = app.get(data)
        updateMainUrl(response.url)
        val document = response.document
        val pageUrl = response.url
        val postId = document.selectFirst("#muvipro_player_content_id")
            ?.attr("data-id")?.takeIf { it.isNotBlank() }
        val embedUrls = linkedSetOf<String>()

        document.select("#muvipro_player_content_id iframe, .muvipro_player_content iframe")
            .mapNotNullTo(embedUrls) { it.getIframeAttr()?.toPlayableUrl() }

        if (postId != null) {
            val loadedTabs = document.select("#muvipro_player_content_id .tab-content-ajax[id]:has(iframe)")
                .map { it.id() }.toSet()
            val tabIds = buildList {
                addAll(document.select("#muvipro_player_content_id .tab-content-ajax[id]").map { it.id() })
                addAll(document.select("ul.muvipro-player-tabs a[href^=#]").map { it.attr("href").removePrefix("#") })
            }.filter { it.isNotBlank() && it !in loadedTabs }.distinct()

            for (tabId in tabIds) {
                try {
                    val ajaxDocument = app.post(
                        "${getBaseUrl(pageUrl)}/wp-admin/admin-ajax.php",
                        referer = pageUrl,
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                        data = mapOf(
                            "action" to "muvipro_player_content",
                            "tab" to tabId,
                            "post_id" to postId,
                        ),
                    ).document
                    ajaxDocument.select("iframe")
                        .mapNotNullTo(embedUrls) { it.getIframeAttr()?.toPlayableUrl() }
                } catch (_: Exception) {
                    // Abaikan satu server yang gagal agar server lain tetap diproses.
                }
            }
        }

        embedUrls.forEach { embedUrl ->
            loadExtractor(embedUrl, pageUrl, subtitleCallback, callback)
        }

        return embedUrls.isNotEmpty()
    }

    private fun Element.toEpisode(seriesPoster: String?): Episode? {
        val href = attr("abs:href").ifBlank { attr("href") }.takeIf { it.isNotBlank() } ?: return null
        val label = listOf(text(), attr("title"), href).joinToString(" ")
        val numbers = EPISODE_PATTERN.find(label)
        val seasonNumber = numbers?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episodeNumber = numbers?.groupValues?.getOrNull(2)?.toIntOrNull()
        val episodeName = attr("title")
            .removePrefix("Permalink to ")
            .ifBlank { text().trim() }

        return newEpisode(href) {
            name = episodeName
            season = seasonNumber
            episode = episodeNumber
            posterUrl = seriesPoster
        }
    }

    private fun SearchResponse.applyCardMetadata(
        poster: String?,
        quality: String,
        ratingText: String?,
    ) {
        posterUrl = poster
        addQuality(quality)
        score = Score.from10(ratingText?.toDoubleOrNull())
    }

    private fun Document.readMetadata(): PageMetadata {
        val title = selectFirst("h1.entry-title")?.text()?.trim()
            ?: selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: title().takeIf { it.isNotBlank() }?.substringBeforeLast(" - Filmlokal")?.trim()
            ?: "Filmlokal"
        val poster = (selectFirst("figure.pull-left img")?.getImageAttr()
            ?: selectFirst("meta[property=og:image]")?.attr("content"))
            ?.fixImageQuality()
        val description = selectFirst("div[itemprop=description] > p")?.text()?.trim()
            ?: selectFirst("meta[name=description]")?.attr("content")?.trim()
        val genres = findMovieData("Genre")?.select("a")?.map { it.text().trim() }
            ?.filter { it.isNotEmpty() }.orEmpty()
        val year = findMovieData("Year")?.selectFirst("a")?.text()?.trim()?.toIntOrNull()
            ?: YEAR_PATTERN.find(title)?.value?.trim('(', ')')?.toIntOrNull()
        val duration = getDurationFromString(findMovieData("Duration")?.text())
        val rating = selectFirst("div.gmr-meta-rating span[itemprop=ratingValue]")?.text()?.trim()
        val actors = select("span[itemprop=actors] a").map { it.text().trim() }.filter { it.isNotEmpty() }
        val trailer = selectFirst("a.gmr-trailer-popup[href]")?.attr("abs:href")
            ?.takeIf { it.isNotBlank() }
            ?: selectFirst("iframe[src*=youtube.com/embed], iframe[src*=youtu.be]")
                ?.getIframeAttr()?.toPlayableUrl()
        val recommendations = select("article.item").mapNotNull { it.toSearchResult() }

        return PageMetadata(
            title = title,
            poster = poster,
            year = year,
            plot = description,
            tags = genres,
            rating = rating,
            actors = actors,
            duration = duration,
            trailer = trailer,
            recommendations = recommendations,
        )
    }

    private fun Document.findMovieData(label: String): Element? =
        select("div.gmr-moviedata").firstOrNull {
            it.selectFirst("strong")?.text()?.trim()?.startsWith(label, ignoreCase = true) == true
        }

    private suspend fun LoadResponse.applyMetadata(metadata: PageMetadata) {
        posterUrl = metadata.poster
        year = metadata.year
        plot = metadata.plot
        tags = metadata.tags
        addScore(metadata.rating)
        addActors(metadata.actors)
        recommendations = metadata.recommendations
        duration = metadata.duration
        addTrailer(metadata.trailer)
    }

    private fun Element.getImageAttr(): String? = when {
        hasAttr("data-src") -> attr("abs:data-src").ifBlank { attr("data-src") }
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src").ifBlank { attr("data-lazy-src") }
        hasAttr("srcset") -> attr("srcset").substringBefore(',').substringBefore(' ').trim()
            .let { raw -> if (raw.startsWith("http")) raw else attr("abs:src") }
        else -> attr("abs:src").ifBlank { attr("src") }
    }.takeIf { !it.isNullOrBlank() }

    private fun Element.getIframeAttr(): String? =
        attr("data-litespeed-src").takeIf { it.isNotBlank() }
            ?: attr("data-src").takeIf { it.isNotBlank() }
            ?: attr("src").takeIf { it.isNotBlank() }

    private fun String.toPlayableUrl(): String? {
        val value = trim()
        if (value.isBlank() || value.startsWith("#") || value.startsWith("javascript:", true) ||
            value.startsWith("about:", true)
        ) return null

        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") || value.startsWith("https://") -> value
            else -> fixUrl(value)
        }
    }

    private fun String?.fixImageQuality(): String? = this?.replace(WORDPRESS_IMAGE_SIZE, "")

    private fun updateMainUrl(url: String) {
        runCatching { getBaseUrl(url) }
            .getOrNull()
            ?.takeIf { it.startsWith("http") }
            ?.let { mainUrl = it }
    }

    private fun getBaseUrl(url: String): String = URI(url).let { uri ->
        "${uri.scheme}://${uri.authority}"
    }

    private fun isSeriesUrl(url: String): Boolean = runCatching {
        URI(url).path.orEmpty().contains("/tv/")
    }.getOrDefault(url.contains("/tv/"))

    private fun isEpisodeUrl(url: String): Boolean = runCatching {
        URI(url).path.orEmpty().contains("/eps/")
    }.getOrDefault(url.contains("/eps/"))

    private data class PageMetadata(
        val title: String,
        val poster: String?,
        val year: Int?,
        val plot: String?,
        val tags: List<String>,
        val rating: String?,
        val actors: List<String>,
        val duration: Int?,
        val trailer: String?,
        val recommendations: List<SearchResponse>,
    )

    companion object {
        private val YEAR_PATTERN = Regex("\\((?:19|20)\\d{2}\\)")
        private val EPISODE_PATTERN = Regex(
            "(?i)(?:S|Season\\s*)(\\d+)\\s*(?:Eps?|Episode)\\s*(\\d+)",
        )
        private val WORDPRESS_IMAGE_SIZE = Regex(
            "-\\d+x\\d+(?=\\.(?:jpe?g|png|webp)(?:[?#].*)?$)",
            RegexOption.IGNORE_CASE,
        )
    }
}
