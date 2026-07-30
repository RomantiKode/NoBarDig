package com.nomat

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Nomat : MainAPI() {
    override var mainUrl = "https://nomat.shop"
    override var name = "Nomat"
    override var lang = "id"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val loadLinksTimeoutMs = 30_000L

    // One homepage request produces all visible rows. This is substantially
    // lighter than requesting every category independently during app startup.
    override val mainPage = mainPageOf("/" to "Beranda")

    private val blockedHosts = setOf(
        "jalur.win",
        "tantegacor88.link",
        "image-cdn.link",
        "amp.analytics-debugger.com",
        "google-analytics.com",
        "googletagmanager.com",
        "doubleclick.net",
        "cdn.ampproject.org",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(emptyList<HomePageList>(), false)

        val document = app.get(mainUrl).document.removeAdNodes()
        val rows = document.select("div.section:has(.head h2):has(.body .item-content)")
            .mapNotNull { section ->
                val rowName = section.selectFirst(".head h2")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                val items = section
                    .select(".body > a[href]:has(.item-content)")
                    .mapNotNull { it.toSearchResult() }
                    .distinctBy { it.url }

                items.takeIf { it.isNotEmpty() }?.let { HomePageList(rowName, it) }
            }

        return newHomePageResponse(rows, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val document = app.get("$mainUrl/search/?searchval=$encoded").document.removeAdNodes()

        return document
            .select("div.section .body > a[href]:has(.item-content)")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val contentUrl = normalizeContentUrl(attr("href")) ?: return null
        val title = selectFirst(".title")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = normalizeImage(extractCssUrl(selectFirst(".poster")?.attr("style")))
        val qualityText = selectFirst(".qual")?.text()?.trim().orEmpty()
        val rating = selectFirst(".rtg")?.ownText()?.trim()?.toDoubleOrNull()
        val episodeCount = Regex("""(?i)Eps\.?\s*(\d+)""")
            .find(qualityText)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val isSeries = episodeCount != null ||
            title.contains("Season", ignoreCase = true) ||
            title.contains("Episode", ignoreCase = true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, contentUrl, TvType.TvSeries) {
                posterUrl = poster
                addQuality(qualityText)
                score = Score.from10(rating)
            }
        } else {
            newMovieSearchResponse(title, contentUrl, TvType.Movie) {
                posterUrl = poster
                addQuality(qualityText)
                score = Score.from10(rating)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val contentUrl = normalizeContentUrl(url) ?: throw ErrorLoadingException("Invalid Nomat URL")
        val document = app.get(contentUrl).document.removeAdNodes()

        val title = getTitle(document) ?: throw ErrorLoadingException("Title not found")
        val poster = normalizeImage(
            extractCssUrl(document.selectFirst(".video-poster")?.attr("style"))
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        )?.fixImageQuality()
        val tags = document.select(".video-genre a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val year = document
            .selectFirst(".video-duration a[href*=/category/year/]")
            ?.text()
            ?.trim()
            ?.toIntOrNull()
        val duration = parseDuration(
            document.select(".video-duration")
                .firstOrNull { it.text().contains("Durasi", ignoreCase = true) }
                ?.text()
        )
        val plot = document.selectFirst(".video-synopsis")
            ?.text()
            ?.removeLabel("Sinopsis")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[name=description]")
                ?.attr("content")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        val trailer = document.selectFirst(".video-trailer amp-youtube[data-videoid]")
            ?.attr("data-videoid")
            ?.takeIf { it.isNotBlank() }
            ?.let { "https://www.youtube.com/watch?v=$it" }
        val rating = parseRating(document.selectFirst(".video-rating")?.text())
        val actors = document.select(".video-actor a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val recommendations = document
            .select("div.section:has(.head h2) .body > a[href]:has(.item-content)")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
            .take(24)

        val episodeElements = document.select(".video-episodes a[href]:has(.episode)")
        val isSeries = episodeElements.isNotEmpty()
        val seasonNumber = Regex("""(?i)Season\s*(\d+)""")
            .find(title)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        return if (isSeries) {
            val episodes = episodeElements.mapNotNull { element ->
                val episodeUrl = normalizeContentUrl(element.attr("href")) ?: return@mapNotNull null
                val episodeText = element.selectFirst(".episode")?.text()?.trim().orEmpty()
                val episodeNumber = Regex("""(?i)Eps\.?\s*(\d+)""")
                    .find(episodeText)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()

                newEpisode(episodeUrl, initializer = {
                    name = episodeNumber?.let { "Episode $it" } ?: episodeText.ifBlank { "Episode" }
                    episode = episodeNumber
                    season = seasonNumber
                    posterUrl = poster
                })
            }.distinctBy { it.data }.sortedBy { it.episode ?: Int.MAX_VALUE }

            newTvSeriesLoadResponse(title, contentUrl, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.year = year
                this.duration = duration
                this.plot = plot
                this.tags = tags
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
                addScore(rating?.toString())
            }
        } else {
            val gatewayUrl = findGatewayUrl(document)

            newMovieLoadResponse(title, contentUrl, TvType.Movie, gatewayUrl ?: contentUrl) {
                posterUrl = poster
                this.year = year
                this.duration = duration
                this.plot = plot
                this.tags = tags
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
                addScore(rating?.toString())
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val gatewayUrl = resolveGatewayUrl(data) ?: return false
        val gatewayDocument = safeRequest {
            app.get(gatewayUrl, referer = mainUrl).document.removeAdNodes()
        } ?: return false

        val serverUrls = gatewayDocument
            .select("div.server-item[data-url]")
            .mapNotNull { decodeServerUrl(it.attr("data-url")) }
            .distinct()

        if (serverUrls.isEmpty()) return false

        var emitted = false
        val emittedUrls = linkedSetOf<String>()
        val safeCallback: (ExtractorLink) -> Unit = { link ->
            if (emittedUrls.add(link.url)) {
                emitted = true
                callback(link)
            }
        }

        serverUrls.forEach { serverUrl ->
            loadExtractor(
                serverUrl,
                gatewayUrl,
                subtitleCallback,
                safeCallback,
            )
        }

        return emitted
    }

    private suspend fun resolveGatewayUrl(data: String): String? {
        val normalized = normalizeHttpUrl(data) ?: return null
        if (!isContentPageUrl(normalized)) return normalized.takeUnless(::isBlockedUrl)

        val document = safeRequest { app.get(normalized).document.removeAdNodes() } ?: return null
        return findGatewayUrl(document)
    }

    private fun findGatewayUrl(document: Document): String? {
        val primary = document.selectFirst(".video-player > a[href*='nontonhemat.link']")
            ?.attr("href")
            ?.let(::normalizeHttpUrl)
            ?.takeUnless(::isBlockedUrl)
        if (primary != null) return primary

        return document.select(".video-player > a[href]")
            .asSequence()
            .mapNotNull { normalizeHttpUrl(it.attr("href")) }
            .firstOrNull { !isBlockedUrl(it) }
    }

    private fun decodeServerUrl(value: String): String? {
        val raw = value.trim()
        if (raw.isBlank()) return null

        return sequenceOf(
            raw,
            runCatching { base64Decode(raw) }.getOrNull(),
        )
            .filterNotNull()
            .mapNotNull(::normalizeHttpUrl)
            .firstOrNull { !isBlockedUrl(it) }
    }

    /** Removes only ad/analytics containers proven by the target fixtures. */
    private fun Document.removeAdNodes(): Document = apply {
        select(".popup, .video-player-ad, amp-analytics, amp-auto-ads, amp-story-auto-ads")
            .forEach { it.remove() }
    }

    private fun normalizeContentUrl(value: String?): String? {
        val normalized = value?.trim()?.takeIf { it.isNotBlank() }?.let(::fixUrl) ?: return null
        if (!isContentPageUrl(normalized)) return null
        return normalized
    }

    private fun isContentPageUrl(url: String): Boolean {
        val uri = parseUri(url) ?: return false
        val mainHost = parseUri(mainUrl)?.host ?: return false
        return uri.scheme.equals("https", true) &&
            uri.host.equals(mainHost, true) &&
            uri.path.orEmpty().startsWith("/play/")
    }

    private fun normalizeHttpUrl(value: String?): String? {
        val cleaned = value
            ?.trim()
            ?.replace("\\/", "/")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val normalized = if (cleaned.startsWith("//")) "https:$cleaned" else cleaned
        val uri = parseUri(normalized) ?: return null

        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return normalized
    }

    private fun isBlockedUrl(url: String): Boolean {
        val host = parseUri(url)?.host?.lowercase() ?: return true
        return blockedHosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }
    }

    private fun normalizeImage(value: String?): String? {
        val cleaned = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (
            cleaned.startsWith("data:", true) ||
            cleaned.startsWith("javascript:", true) ||
            cleaned.startsWith("blob:", true)
        ) return null

        return fixUrlNull(cleaned)
    }

    private fun extractCssUrl(style: String?): String? {
        val raw = style?.substringAfter("url(", "")
            ?.substringBefore(")", "")
            ?.trim()
            ?.trim('\'', '"')
        return raw?.takeIf { it.isNotBlank() }
    }

    private fun getTitle(document: Document): String? {
        val direct = document.selectFirst(".video-title h1")?.text()?.trim()
        if (!direct.isNullOrBlank()) return direct

        val metadataTitle = document.selectFirst("meta[property=og:title]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: document.title()

        return metadataTitle
            .trim()
            .removePrefix("Nonton ")
            .substringBefore(" Subtitle Indonesia", metadataTitle)
            .substringBefore(" | NOMAT")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun parseRating(value: String?): Double? = Regex("""(?i)Rating\s*:\s*([0-9]+(?:\.[0-9]+)?)""")
        .find(value.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()

    private fun parseDuration(value: String?): Int? {
        val text = value.orEmpty()
        val hours = Regex("""(?i)(\d+)\s*(?:h|hour|hours|jam)""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
        val minutes = Regex("""(?i)(\d+)\s*(?:m|min|mins|minute|minutes|menit)\b""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0

        return (hours * 60 + minutes).takeIf { it > 0 }
    }

    private fun String.removeLabel(label: String): String = replaceFirst(
        Regex("""^\s*${Regex.escape(label)}\s*:\s*""", RegexOption.IGNORE_CASE),
        "",
    ).trim()

    private fun String.fixImageQuality(): String = replace(Regex("""-\d+x\d+(?=\.[a-zA-Z]{2,5}(?:\?|$))"""), "")

    private suspend fun <T> safeRequest(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun parseUri(url: String): URI? = runCatching { URI(url) }.getOrNull()
}
