package com.klikxxi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

class KlikXXi : MainAPI() {
    override var mainUrl = "https://klikxxi.shop"
    override var name = "KlikXXi"
    override var lang = "id"
    override val hasMainPage = true

    // Mencegah banyak section beranda membanjiri situs secara bersamaan.
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 100L
    override var sequentialMainPageScrollDelay = 100L

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama,
    )

    override val mainPage = mainPageOf(
        "year/2026/page/%d/" to "Terbaru",
        "tv/page/%d/" to "TV Series",
        "category/action/page/%d/" to "Action",
        "category/adventure/page/%d/" to "Adventure",
        "category/comedy/page/%d/" to "Comedy",
        "category/cartoon/page/%d/" to "Cartoon",
        "category/crime/page/%d/" to "Crime",
        "category/drama/page/%d/" to "Drama",
        "category/fantasy/page/%d/" to "Fantasy",
        "category/family/page/%d/" to "Family",
        "category/horror/page/%d/" to "Horror",
        "category/mystery/page/%d/" to "Mystery",
        "category/roman/page/%d/" to "Romance",
        "category/science-fiction/page/%d/" to "Science Fiction",
        "category/thriller/page/%d/" to "Thriller",
        "category/war/page/%d/" to "War",
    )

    private val mainUrlJson =
        "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/Website.json"
    private val urlMutex = Mutex()
    private var urlResolutionAttempted = false

    private val blockedEmbedHosts = setOf(
        "dtscout.com",
        "histats.com",
        "mrktmtrcs.net",
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "cloudflare.com",
        "challenges.cloudflare.com",
        "youtube.com",
        "www.youtube.com",
        "youtu.be",
    )

    private val blockedEmbedFragments = listOf(
        "javascript:",
        "about:blank",
        "/cdn-cgi/challenge-platform/",
        "popunder",
        "popup",
        "redirect.php",
        "/ads/",
    )

    private suspend fun ensureMainUrl() {
        if (urlResolutionAttempted) return

        urlMutex.withLock {
            if (urlResolutionAttempted) return@withLock
            urlResolutionAttempted = true

            runCatching {
                val json = JSONObject(app.get(mainUrlJson).text)
                json.optJSONArray("klikxxi")
                    ?.optString(0)
                    ?.trim()
                    ?.removeSuffix("/")
                    ?.takeIf { it.startsWith("https://") && URI(it).host != null }
            }.getOrNull()?.let { mainUrl = it }
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        ensureMainUrl()
        val document = app.get("$mainUrl/${request.data.format(page)}").document
        val items = document.select(CARD_SELECTOR)
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(request, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val document = app.get(
            "$mainUrl/?s=$encodedQuery&post_type[]=post&post_type[]=tv",
        ).document

        return document.select(CARD_SELECTOR)
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = selectFirst("h2.entry-title a[href]") ?: return null
        val title = titleElement.text().trim().takeIf { it.isNotEmpty() } ?: return null
        val href = fixUrlNull(titleElement.attr("href")) ?: return null
        val poster = selectFirst("img.wp-post-image, div.content-thumbnail img")
            ?.bestImageUrl()
            ?.fixImageQuality()

        val quality = selectFirst(".gmr-quality-item a")
            ?.text()
            ?.trim()
            ?.replace("-", "")
            ?.takeIf { it.isNotEmpty() }
        val rating = selectFirst(".gmr-rating-item")
            ?.ownText()
            ?.trim()
            ?.toDoubleOrNull()
        val episodeCount = selectFirst(".gmr-numbeps span")
            ?.text()
            ?.filter(Char::isDigit)
            ?.toIntOrNull()
        val isSeries = href.contains("/tv/", ignoreCase = true) ||
            selectFirst(".gmr-numbeps") != null

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                episodes = episodeCount
                score = Score.from10(rating)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                quality?.let { addQuality(it) }
                score = Score.from10(rating)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val response = app.get(url)
        val document = response.document

        // Mengikuti redirect/cermin situs tanpa menyimpan state terpisah yang mudah basi.
        mainUrl = baseUrl(response.url)

        val detailContent = document.selectFirst(".entry-content.entry-content-single")

        val title = (document.selectFirst("h1.entry-title")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: document.selectFirst("meta[property=\"og:title\"]")
                ?.attr("content")
                ?.substringBefore(" – ")
                ?.trim()
            ?: document.title().substringBefore(" – ").trim())
            .ifBlank { throw ErrorLoadingException("Judul tidak ditemukan") }

        val poster = document.selectFirst("article.hentry img.wp-post-image")
            ?.bestImageUrl()
            ?.fixImageQuality()
            ?: document.selectFirst("meta[property=\"og:image\"]")
                ?.attr("content")
                ?.takeIf { it.isNotBlank() }

        val description = document.metadataDescription()
            ?: detailContent?.selectFirst("p")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        val year = detailContent?.selectFirst("time[itemprop=dateCreated]")
            ?.attr("datetime")
            ?.take(4)
            ?.toIntOrNull()
            ?: detailContent?.selectFirst("a[href*=\"/year/\"]")
                ?.text()
                ?.filter(Char::isDigit)
                ?.toIntOrNull()
        val rating = document.selectFirst(".gmr-movie-data [itemprop=ratingValue]")
            ?.text()
            ?.trim()
        val duration = detailContent?.selectFirst("[property=duration]")
            ?.text()
            ?.filter(Char::isDigit)
            ?.toIntOrNull()
        val tags = detailContent?.select(".gmr-moviedata a[href*=\"/category/\"]")
            .orEmpty()
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val actors = detailContent?.select("span[itemprop=actors] span[itemprop=name]")
            .orEmpty()
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val trailer = document.selectFirst("#muvipro_player_content_id a.gmr-trailer-popup[href]")
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
        val recommendations = document.select("article.item.col-md-20")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
        val seasonBlocks = document.select("div.gmr-season-block")

        if (seasonBlocks.isNotEmpty()) {
            val episodes = seasonBlocks.flatMap { season ->
                val seasonNumber = SEASON_REGEX.find(
                    season.selectFirst(".season-title")?.text().orEmpty(),
                )?.groupValues?.getOrNull(1)?.toIntOrNull()

                season.select("div.gmr-season-episodes a[href]")
                    .mapNotNull { episodeElement ->
                        val label = listOf(
                            episodeElement.attr("title"),
                            episodeElement.text(),
                        ).joinToString(" ").trim()

                        if (label.contains("batch", ignoreCase = true)) return@mapNotNull null

                        val episodeNumber = EPISODE_REGEX.find(label)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toIntOrNull()
                        val episodeUrl = fixUrlNull(episodeElement.attr("href"))
                            ?: return@mapNotNull null

                        newEpisode(episodeUrl) {
                            name = episodeNumber?.let { "Episode $it" }
                                ?: episodeElement.text().trim().takeIf { it.isNotEmpty() }
                                ?: "Episode"
                            episode = episodeNumber
                            this.season = seasonNumber
                            posterUrl = poster
                        }
                    }
            }.distinctBy { it.data }
                .sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
                addScore(rating)
                addActors(actors)
                this.recommendations = recommendations
                duration?.let { this.duration = it }
                addTrailer(trailer)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            posterUrl = poster
            this.year = year
            plot = description
            this.tags = tags
            addScore(rating)
            addActors(actors)
            this.recommendations = recommendations
            duration?.let { this.duration = it }
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        ensureMainUrl()
        val pageResponse = app.get(data)
        val document = pageResponse.document
        val siteBase = baseUrl(pageResponse.url)
        val seenEmbeds = linkedSetOf<String>()
        var extractedAny = false

        suspend fun extract(rawUrl: String?) {
            val embedUrl = sanitizeEmbedUrl(rawUrl) ?: return
            if (!seenEmbeds.add(embedUrl)) return

            loadExtractor(
                embedUrl,
                "$siteBase/",
                subtitleCallback,
            ) { link ->
                extractedAny = true
                callback(link)
            }
        }

        // Hanya membaca iframe di area player. Script, onclick, tracker, dan iframe
        // challenge di luar player tidak pernah dieksekusi atau diikuti.
        val playerRoot = document.selectFirst("#muvipro_player_content_id")
        playerRoot?.select(".tab-content-ajax iframe, .gmr-embed-responsive iframe")
            ?.forEach { extract(it.iframeUrl()) }

        val postId = playerRoot?.attr("data-id")?.takeIf { it.isNotBlank() }
        if (postId != null) {
            val populatedTabs = playerRoot.select(".tab-content-ajax:has(iframe)[id]")
                .map { it.id() }
                .toSet()
            val tabIds = linkedSetOf<String>().apply {
                addAll(playerRoot.select(".tab-content-ajax[id]").map { it.id() })
                addAll(
                    playerRoot.select("ul.muvipro-player-tabs a[href^=#]")
                        .map { it.attr("href").removePrefix("#") },
                )
            }.filter { it.isNotBlank() && it !in populatedTabs }

            for (tabId in tabIds) {
                val ajaxDocument = runCatching {
                    app.post(
                        "$siteBase/wp-admin/admin-ajax.php",
                        data = mapOf(
                            "action" to "muvipro_player_content",
                            "tab" to tabId,
                            "post_id" to postId,
                        ),
                    ).document
                }.getOrNull() ?: continue

                ajaxDocument.select(".gmr-embed-responsive iframe, iframe")
                    .forEach { extract(it.iframeUrl()) }
            }
        } else {
            document.select(
                ".player-wrap .gmr-embed-responsive iframe, " +
                    ".muvipro_player_content .gmr-embed-responsive iframe",
            ).forEach { extract(it.iframeUrl()) }
        }

        return extractedAny
    }

    private fun Document.metadataDescription(): String? =
        selectFirst("meta[name=description]")
            ?.attr("content")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: selectFirst("meta[property=\"og:description\"]")
                ?.attr("content")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

    private fun Element.bestImageUrl(): String? {
        val srcSet = sequenceOf(
            attr("data-lazy-srcset"),
            attr("data-srcset"),
            attr("srcset"),
        ).firstOrNull { it.isNotBlank() }

        val fromSrcSet = srcSet
            ?.split(',')
            ?.mapNotNull { candidate ->
                val parts = candidate.trim().split(Regex("\\s+"))
                val candidateUrl = parts.firstOrNull()?.takeIf { it.isNotBlank() }
                val width = parts.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
                candidateUrl?.let { it to width }
            }
            ?.maxByOrNull { it.second }
            ?.first

        val raw = fromSrcSet ?: sequenceOf(
            attr("data-lazy-src"),
            attr("data-src"),
            attr("src"),
        ).firstOrNull { it.isNotBlank() && !it.startsWith("data:image") }

        return normalizeUrl(raw)
    }

    private fun Element.iframeUrl(): String? = sequenceOf(
        attr("data-litespeed-src"),
        attr("data-src"),
        attr("src"),
    ).firstOrNull { it.isNotBlank() }

    private fun sanitizeEmbedUrl(rawUrl: String?): String? {
        val normalized = normalizeUrl(rawUrl) ?: return null
        val lowercase = normalized.lowercase()
        if (blockedEmbedFragments.any(lowercase::contains)) return null

        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https")) return null
        val host = uri.host?.lowercase() ?: return null
        if (blockedEmbedHosts.any { host == it || host.endsWith(".$it") }) return null

        return normalized
    }

    private fun normalizeUrl(rawUrl: String?): String? {
        val value = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("/") -> fixUrl(value)
            else -> fixUrlNull(value)
        }
    }

    private fun String?.fixImageQuality(): String? {
        val value = this ?: return null
        return value.replace(Regex("-\\d+x\\d+(?=\\.[A-Za-z0-9]+(?:\\?.*)?$)"), "")
    }

    private fun baseUrl(url: String): String = URI(url).let { uri ->
        "${uri.scheme}://${uri.host}${if (uri.port == -1) "" else ":${uri.port}"}"
    }

    private companion object {
        const val CARD_SELECTOR =
            "div.gmr-item-modulepost, article.item-infinite, article.item.col-md-20"
        val SEASON_REGEX = Regex("(?:Season|S)\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val EPISODE_REGEX = Regex("(?:Episode|Eps?|E)\\s*[-:]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    }
}
