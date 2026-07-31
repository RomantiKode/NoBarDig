package com.donghub

import android.util.Base64
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

class DonghubProvider : MainAPI() {
    override var mainUrl = "https://donghub.vip"
    override var name = "Donghub"
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.OVA)

    override val mainPage = mainPageOf(
        "" to "Rilis Terbaru",
        "anime/?status=ongoing" to "Ongoing",
        "anime/?status=completed" to "Completed",
        "anime" to "Semua Donghua",
    )

    private val requestHeaders = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    )

    private val blockedHostParts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googletagmanager.com",
        "google-analytics.com",
        "dtscout.com",
        "histats.com",
        "jufferplatens.cfd",
        "runative-syndicate.com",
        "onesignal.com",
        "orblessbugan.shop",
        "peulmiring.shop",
        "dornickmunsiff.cyou",
        "v2006.com",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = buildPageUrl(request.data, page)
        val items = parseCards(fetchDocument(pageUrl))
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        return parseCards(fetchDocument("$mainUrl/?s=$encoded"))
    }

    override suspend fun load(url: String): LoadResponse? {
        val initialDocument = fetchDocument(url)
        val parentUrl = findSeriesUrl(initialDocument, url)
        val pageUrl = parentUrl ?: url
        val document = if (parentUrl != null) fetchDocument(parentUrl, url) else initialDocument

        val title = document.selectFirst("h1.entry-title, .bigcontent h1, .entry-title")
            ?.text()
            ?.cleanTitle()
            ?.takeIf(String::isNotBlank)
            ?: return null

        val poster = document.selectFirst(
            ".thumb img, .seriesthumb img, .bigcontent .thumb img, img.wp-post-image, .poster img"
        )?.imageUrl(pageUrl)

        val plot = document.selectFirst(
            ".desc.mindes, .desc, .mindes, .bixbox.synp .entry-content, " +
                ".entry-content[itemprop=description], [itemprop=description]"
        )?.text()?.trim()?.takeIf(String::isNotBlank)
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()

        val genres = document.select(
            ".genxed a[href*=/genres/], a[rel=tag][href*=/genres/]"
        ).map { it.text().trim() }.filter(String::isNotBlank).distinct()

        val informationText = document.select(".spe, .info-content, .tsinfo").text()
        val year = Regex("\\b(19|20)\\d{2}\\b").find(informationText)?.value?.toIntOrNull()
        val status = when {
            informationText.contains("completed", ignoreCase = true) -> ShowStatus.Completed
            informationText.contains("ongoing", ignoreCase = true) -> ShowStatus.Ongoing
            else -> null
        }

        val episodes = parseEpisodes(document, pageUrl)
        if (episodes.isNotEmpty()) {
            return newTvSeriesLoadResponse(title, pageUrl, TvType.Anime, episodes) {
                posterUrl = poster
                this.plot = plot
                tags = genres
                this.year = year
                showStatus = status
            }
        }

        return newMovieLoadResponse(title, pageUrl, TvType.Movie, pageUrl) {
            posterUrl = poster
            this.plot = plot
            tags = genres
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val document = fetchDocument(data)
        val embedUrls = linkedSetOf<String>()
        val directUrls = linkedSetOf<String>()

        document.select(
            "#embed_holder iframe, #pembed iframe, iframe#video-player, " +
                ".player-embed iframe, .video-content iframe"
        ).forEach { iframe ->
            iframe.firstUrlAttribute()?.let { classifyPlayerUrl(it, data, embedUrls, directUrls) }
        }

        document.select(
            "#embed_holder source[src], #pembed source[src], .player-embed source[src], " +
                ".video-content source[src], .video-content video[src]"
        ).forEach { media ->
            media.firstUrlAttribute()?.let { classifyPlayerUrl(it, data, embedUrls, directUrls) }
        }

        document.select("select.mirror option[value], .mirror option[value], .mobius option[value]")
            .forEach { option ->
                decodeMirrorValue(option.attr("value"), data)
                    .forEach { classifyPlayerUrl(it, data, embedUrls, directUrls) }
            }

        var emitted = false
        val trackedCallback: (ExtractorLink) -> Unit = { link ->
            emitted = true
            callback(link)
        }

        directUrls.forEach { streamUrl ->
            val isHls = streamUrl.contains(".m3u8", ignoreCase = true) ||
                streamUrl.contains("/hls/", ignoreCase = true)
            emitted = true
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

        var extractorMatched = false
        embedUrls.forEach { embedUrl ->
            extractorMatched = loadExtractor(
                embedUrl,
                data,
                subtitleCallback,
                trackedCallback,
            ) || extractorMatched
        }

        return emitted || extractorMatched
    }

    private suspend fun fetchDocument(url: String, referer: String = mainUrl): Document {
        return app.get(url, headers = requestHeaders + ("Referer" to referer)).document
    }

    private fun buildPageUrl(path: String, page: Int): String {
        val cleanPath = path.trim().trim('/')
        if (cleanPath.isEmpty()) {
            return if (page <= 1) "$mainUrl/" else "$mainUrl/page/$page/"
        }

        val basePath = cleanPath.substringBefore('?').trimEnd('/')
        val query = cleanPath.substringAfter('?', "").takeIf(String::isNotBlank)
        val pagedPath = if (page <= 1) basePath else "$basePath/page/$page"
        return buildString {
            append(mainUrl).append('/').append(pagedPath).append('/')
            if (query != null) append('?').append(query)
        }
    }

    private fun parseCards(document: Document): List<SearchResponse> {
        val primaryCards = document.select(
            ".releases.latesthome + .listupd article.bs, " +
                ".listupd.normal article.bs, .listupd .excstf > article.bs"
        )
        val cards = if (primaryCards.isNotEmpty()) {
            primaryCards
        } else {
            document.select(".postbody .listupd article.bs, .listupd article.bs")
        }

        return cards.mapNotNull { card ->
            val anchor = card.selectFirst(".bsx > a[href], a.tip[href], a[href]")
                ?: return@mapNotNull null
            val href = resolveUrl(anchor.attr("href"), mainUrl)
                ?.takeUnless(::isBlockedUrl)
                ?: return@mapNotNull null

            val title = card.selectFirst(".eggtitle")?.text()?.trim()
                ?.takeIf(String::isNotBlank)
                ?: card.selectFirst(".tt h2, h2[itemprop=headline], h2")?.text()?.cleanTitle()
                ?: anchor.attr("title").cleanTitle()

            if (title.isBlank()) return@mapNotNull null
            val poster = card.selectFirst("img")?.imageUrl(href)

            newTvSeriesSearchResponse(title, href, TvType.Anime) {
                posterUrl = poster
            }
        }.distinctBy { it.url }
    }

    private fun findSeriesUrl(document: Document, sourceUrl: String): String? {
        val breadcrumbSeries = document.select(".ts-breadcrumb a[href]").getOrNull(1)
        val candidates = listOfNotNull(
            document.selectFirst(".naveps .nvsc a[href]"),
            document.selectFirst(".item.meta .year a[href]"),
            document.selectFirst(".year a[href]"),
            breadcrumbSeries,
        )

        return candidates.asSequence()
            .mapNotNull { resolveUrl(it.attr("href"), sourceUrl) }
            .filterNot(::isBlockedUrl)
            .firstOrNull { normalizeUrl(it) != normalizeUrl(sourceUrl) }
    }

    private fun parseEpisodes(document: Document, pageUrl: String): List<Episode> {
        return document.select(
            ".eplister ul li a[href], .eplister a[href], " +
                ".episodelist ul li a[href], .ep-list li a[href]"
        ).mapNotNull { anchor ->
            val episodeUrl = resolveUrl(anchor.attr("href"), pageUrl)
                ?.takeUnless(::isBlockedUrl)
                ?: return@mapNotNull null

            val episodeTitle = anchor.selectFirst(".epl-title, .epl-num, .ep-title")
                ?.text()?.trim()?.takeIf(String::isNotBlank)
                ?: anchor.attr("title").trim().takeIf(String::isNotBlank)
                ?: anchor.text().trim()

            val episodeNumber = anchor.selectFirst(".epl-num .epcur, .epcur, .epl-num")
                ?.text()?.firstEpisodeNumber()
                ?: episodeTitle.firstEpisodeNumber()

            newEpisode(episodeUrl) {
                name = episodeTitle
                episode = episodeNumber
            }
        }.distinctBy { it.data }
            .sortedWith(compareBy<Episode> { it.episode ?: Int.MAX_VALUE }.thenBy { it.name })
    }

    private fun decodeMirrorValue(value: String, baseUrl: String): List<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("//")) {
            return listOfNotNull(resolveUrl(trimmed, baseUrl))
        }

        val decoded = runCatching {
            String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()?.trim().orEmpty()
        if (decoded.isEmpty()) return emptyList()

        val parsed = Jsoup.parse(decoded, baseUrl)
        val urls = parsed.select("iframe[src], iframe[data-src], source[src], video[src]")
            .mapNotNull { it.firstUrlAttribute() }
            .mapNotNull { resolveUrl(it, baseUrl) }
            .toMutableList()

        if (urls.isEmpty() && (decoded.startsWith("http://") || decoded.startsWith("https://") || decoded.startsWith("//"))) {
            resolveUrl(decoded, baseUrl)?.let(urls::add)
        }
        return urls
    }

    private fun classifyPlayerUrl(
        rawUrl: String,
        baseUrl: String,
        embeds: MutableSet<String>,
        direct: MutableSet<String>,
    ) {
        val url = resolveUrl(rawUrl, baseUrl) ?: return
        if (isBlockedUrl(url)) return
        if (url.contains(".m3u8", ignoreCase = true) ||
            url.contains(".mp4", ignoreCase = true) ||
            url.contains("/hls/", ignoreCase = true)
        ) {
            direct += url
        } else {
            embeds += url
        }
    }

    private fun Element.firstUrlAttribute(): String? {
        return listOf("src", "data-src", "data-litespeed-src", "data-lazy-src")
            .asSequence()
            .map { attr(it).trim() }
            .firstOrNull(String::isNotBlank)
    }

    private fun Element.imageUrl(baseUrl: String): String? {
        val raw = listOf("data-src", "data-lazy-src", "data-original", "src")
            .asSequence()
            .map { attr(it).trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("data:image") }
            ?: attr("srcset").substringBefore(',').trim().substringBefore(' ').takeIf(String::isNotBlank)
        return raw?.let { resolveUrl(it, baseUrl) }
    }

    private fun resolveUrl(rawUrl: String, baseUrl: String): String? {
        val cleaned = rawUrl.trim().replace("\\/", "/")
        if (cleaned.isEmpty() || cleaned.startsWith("javascript:", ignoreCase = true) || cleaned.startsWith('#')) {
            return null
        }
        return runCatching {
            when {
                cleaned.startsWith("//") -> "https:$cleaned"
                cleaned.startsWith("http://") || cleaned.startsWith("https://") -> cleaned
                else -> URI(baseUrl).resolve(cleaned).toString()
            }
        }.getOrNull()
    }

    private fun isBlockedUrl(url: String): Boolean {
        val lower = url.lowercase()
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return blockedHostParts.any { host.contains(it) || lower.contains(it) }
    }

    private fun normalizeUrl(url: String): String = url.substringBefore('#').trimEnd('/')

    private fun String.cleanTitle(): String = trim()
        .removeSuffix(" - Donghub")
        .replace(Regex("\\s+"), " ")

    private fun String.firstEpisodeNumber(): Int? {
        val explicit = Regex("(?i)\\b(?:episode|ep)\\s*0*(\\d+)")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (explicit != null) return explicit

        return Regex("^\\D*0*(\\d+)\\D*$")
            .find(trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }
}
