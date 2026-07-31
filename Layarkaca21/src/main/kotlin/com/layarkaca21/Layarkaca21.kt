package com.layarkaca21

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class Layarkaca21 : MainAPI() {
    override var mainUrl = MOVIE_URL
    override var name = "Layarkaca21"
    override var lang = "id"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override var sequentialMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        MOVIE_URL to "Film Terbaru",
        "$MOVIE_URL/populer" to "Film Terpopuler",
        "$MOVIE_URL/quality/bluray" to "Film BluRay",
        SERIES_URL to "Serial Terbaru",
        "$SERIES_URL/populer" to "Serial Terpopuler",
        "$SERIES_URL/series/ongoing" to "Serial Ongoing",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = pagedUrl(request.data, page)
        val document = app.get(pageUrl, referer = originOf(request.data)).document.cleanPage()
        val sourceType = typeForUrl(request.data)
        val results = document.select(CARD_SELECTOR)
            .mapNotNull { it.toCard(originOf(request.data), sourceType) }
            .distinctBy { it.url }
        return newHomePageResponse(request, results, hasNext = results.size >= PAGE_SIZE)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val results = mutableListOf<SearchResponse>()
        for ((base, type) in listOf(MOVIE_URL to TvType.Movie, SERIES_URL to TvType.TvSeries)) {
            val url = "$base/search/?s=$encoded" + if (page > 1) "&page=$page" else ""
            val document = runCatching {
                app.get(url, referer = "$base/").document.cleanPage()
            }.getOrNull() ?: continue
            results += document.select(SEARCH_CARD_SELECTOR)
                .mapNotNull { it.toCard(base, type) }
        }
        val deduped = results.distinctBy { it.url }
        return newSearchResponseList(deduped, hasNext = deduped.size >= PAGE_SIZE)
    }

    override suspend fun load(url: String): LoadResponse? {
        var normalizedUrl = normalizeContentUrl(url) ?: return null
        var base = originOf(normalizedUrl)
        var document = app.get(normalizedUrl, referer = "$base/").documentLarge.cleanPage()

        // Some listing/search pages can point directly to an episode. Resolve the parent
        // series before creating the Cloudstream TV response.
        if (base == SERIES_URL && isEpisodePage(document)) {
            val parentUrl = findSeriesParentUrl(document, normalizedUrl)
            if (parentUrl != null && parentUrl != normalizedUrl) {
                normalizedUrl = parentUrl
                base = SERIES_URL
                document = app.get(parentUrl, referer = "$SERIES_URL/").documentLarge.cleanPage()
            }
        }

        val pageType = if (
            base == SERIES_URL ||
            document.selectFirst("script#season-data, ul.episode-list a[href]") != null ||
            document.selectFirst("meta[property=og:type]")?.attr("content")
                ?.equals("series", ignoreCase = true) == true
        ) TvType.TvSeries else TvType.Movie

        val title = document.selectFirst(".movie-info h1")?.text()?.trim()
            ?: cleanTitle(document.selectFirst("meta[property=og:title]")?.attr("content"))
            ?: cleanTitle(document.title())
            ?: return null
        val poster = firstValidUrl(
            document.selectFirst("meta[property=og:image]")?.attr("content"),
            document.selectFirst("meta[name=twitter:image]")?.attr("content"),
        )
        val description = document.selectFirst(".synopsis[data-full]")?.attr("data-full")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()
        val year = YEAR_REGEX.find(title)?.value?.toIntOrNull()
        val score = document.selectFirst(".rating-number")
            ?.attr("data-base-rating")
            ?.ifBlank { document.selectFirst(".rating-number")?.text() }
        val tags = document.select(".tag-list a").map { it.text().trim() }.filter { it.isNotBlank() }
        val actors = labeledLinks(document, "Bintang Film")
        val trailer = firstValidUrl(
            document.selectFirst("a[href*=youtube.com], a[href*=youtu.be]")?.attr("href"),
            document.selectFirst("iframe[src*=youtube.com], iframe[src*=youtu.be]")?.attr("src"),
        )
        val recommendations = parseRecommendations(document, base, pageType)

        return if (pageType == TvType.TvSeries) {
            val episodes = parseEpisodes(document)
            newTvSeriesLoadResponse(title, normalizedUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(score)
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            val duration = parseDurationMinutes(document.select(".info-tag span").joinToString(" ") { it.text() })
            newMovieLoadResponse(title, normalizedUrl, TvType.Movie, normalizedUrl) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.duration = duration
                this.score = Score.from10(score)
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var pageUrl = normalizeContentUrl(data) ?: return false
        var pageOrigin = originOf(pageUrl)
        var page = app.get(pageUrl, referer = "$pageOrigin/").documentLarge.cleanPage()

        // Defensive fallback: if Cloudstream receives a parent-series URL instead of an
        // Episode.data URL, open the first real episode link before looking for players.
        var playerUrls = extractPlayerUrls(page, pageUrl)
        if (playerUrls.isEmpty() && pageOrigin == SERIES_URL) {
            val firstEpisodeUrl = parseEpisodeCandidates(page).firstOrNull()?.url
            if (firstEpisodeUrl != null && firstEpisodeUrl != pageUrl) {
                pageUrl = firstEpisodeUrl
                page = app.get(pageUrl, referer = "$SERIES_URL/").documentLarge.cleanPage()
                playerUrls = extractPlayerUrls(page, pageUrl)
            }
        }

        val emitted = linkedSetOf<String>()
        val safeCallback: (ExtractorLink) -> Unit = { link ->
            if (isSafeMediaUrl(link.url) && emitted.add(link.url)) callback(link)
        }

        for (playerUrl in playerUrls.sortedBy(::playerPriority)) {
            if (isDirectMedia(playerUrl)) {
                emitDirect(playerUrl, pageUrl, "LK21", safeCallback)
                continue
            }

            // Registered extractors take priority. Version 2 registers a dedicated
            // VideoNode extractor for /iframe/p2p, /hydrax, /turbovip and /cast.
            val beforeExtractor = emitted.size
            loadExtractor(playerUrl, pageUrl, subtitleCallback, safeCallback)
            if (emitted.size > beforeExtractor) continue

            // Generic fallback for mirrors that expose a nested iframe or direct media.
            scrapePlayerPage(playerUrl, pageUrl, subtitleCallback, safeCallback, emitted)
        }
        return emitted.isNotEmpty()
    }

    private suspend fun scrapePlayerPage(
        playerUrl: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        emitted: Set<String>,
    ) {
        val response = runCatching {
            app.get(
                playerUrl,
                referer = referer,
                headers = PLAYER_HEADERS,
                allowRedirects = true,
            )
        }.getOrNull() ?: return
        val playerDocument = response.document.cleanPlayerPage()

        playerDocument.select("track[kind=subtitles][src], track[kind=captions][src]").forEach { track ->
            absoluteUrl(response.url, track.attr("src"))?.let { subtitleUrl ->
                if (isSafeMediaUrl(subtitleUrl)) {
                    subtitleCallback(
                        newSubtitleFile(
                            track.attr("srclang").ifBlank { track.attr("label").ifBlank { "Unknown" } },
                            subtitleUrl,
                        )
                    )
                }
            }
        }

        val scriptTexts = playerDocument.select("script:not([src])").flatMap { script ->
            val raw = script.data()
            listOf(raw, getAndUnpack(raw))
        }.distinct()

        val directSources = buildList {
            playerDocument.select("video source[src], video[src], source[src]").forEach { add(it.attr("src")) }
            for (text in scriptTexts) {
                MEDIA_REGEX.findAll(text).forEach { add(it.value) }
                URL_ASSIGNMENT_REGEX.findAll(text).forEach { add(it.groupValues[1]) }
                ATOB_REGEX.findAll(text).forEach { match ->
                    decodeBase64Url(match.groupValues[1])?.let(::add)
                }
            }
        }.map(::decodeEscapedUrl)
            .mapNotNull { absoluteUrl(response.url, it) }
            .filter(::isSafeMediaUrl)
            .filter(::isDirectMedia)
            .distinct()

        for (directSource in directSources) {
            emitDirect(directSource, playerUrl, serverName(playerUrl), callback)
        }

        val nestedUrls = buildList {
            playerDocument.select("iframe[src], [data-url]").forEach {
                add(it.attr("src").ifBlank { it.attr("data-url") })
            }
            playerDocument.select("meta[http-equiv=refresh]").forEach { meta ->
                REFRESH_URL_REGEX.find(meta.attr("content"))?.groupValues?.getOrNull(1)?.let(::add)
            }
            for (text in scriptTexts) {
                URL_ASSIGNMENT_REGEX.findAll(text).forEach { add(it.groupValues[1]) }
            }
        }.map(::decodeEscapedUrl)
            .mapNotNull { absoluteUrl(response.url, it) }
            .filter(::isSafePlayerUrl)
            .filterNot(::isDirectMedia)
            .filter { hostOf(it) != hostOf(playerUrl) || it != playerUrl }
            .distinct()

        for (nested in nestedUrls) {
            val before = emitted.size
            loadExtractor(nested, response.url, subtitleCallback, callback)
            if (emitted.size > before) continue
        }
    }

    private fun extractPlayerUrls(document: Document, pageUrl: String): List<String> = buildList {
        document.select("#player-list a[data-url], #player-list a[href]").forEach {
            add(it.attr("data-url").ifBlank { it.attr("href") })
        }
        document.select("#player-select option[value]").forEach { add(it.attr("value")) }
        document.select("iframe#main-player[src]").forEach { add(it.attr("src")) }
    }.mapNotNull { absoluteUrl(pageUrl, it) }
        .filter(::isSafePlayerUrl)
        .distinct()

    private fun Element.toCard(base: String, forcedType: TvType): SearchResponse? {
        val link = if (tagName() == "a") this else selectFirst("a[href]") ?: return null
        val href = absoluteUrl(base, link.attr("href")) ?: return null
        if (!isContentUrl(href, base)) return null
        val title = selectFirst("h3.poster-title, h1.grid-title, h3")?.text()?.trim()
            ?: link.attr("title").trim().takeIf { it.isNotBlank() }
            ?: return null
        val image = selectFirst("img")
        val poster = firstValidUrl(
            image?.attr("data-src"), image?.attr("src"),
            selectFirst("source[type=image/jpeg]")?.attr("data-srcset")?.substringBefore(" "),
            selectFirst("source[type=image/jpeg]")?.attr("srcset")?.substringBefore(" "),
        )
        val score = selectFirst("span[itemprop=ratingValue]")?.text()?.trim()
        return if (forcedType == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.score = Score.from10(score)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.score = Score.from10(score)
            }
        }
    }

    private fun parseEpisodes(document: Document): List<Episode> {
        return parseEpisodeCandidates(document).map { item ->
            newEpisode(item.url) {
                this.name = item.title
                this.season = item.season
                this.episode = item.episode
            }
        }
    }

    /**
     * The real click flow is parent series -> ul.episode-list -> episode page -> player.
     * DOM links are therefore authoritative. season-data is retained as a fallback and
     * to supply episodes belonging to seasons that are not rendered initially.
     */
    private fun parseEpisodeCandidates(document: Document): List<EpisodeCandidate> {
        val jsonItems = parseJsonEpisodes(document)
        val jsonByKey = jsonItems.associateBy { it.season to it.episode }

        val domItems = document.select("ul.episode-list a[href]").mapNotNull { link ->
            val href = absoluteUrl(SERIES_URL, link.attr("href")) ?: return@mapNotNull null
            if (originOf(href) != SERIES_URL) return@mapNotNull null
            val match = EPISODE_PATH_REGEX.find(runCatching { URI(href).path.orEmpty() }.getOrDefault(""))
            val season = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: link.attr("data-season").toIntOrNull()
                ?: 1
            val episode = match?.groupValues?.getOrNull(2)?.toIntOrNull()
                ?: link.attr("data-episode").toIntOrNull()
                ?: link.text().trim().toIntOrNull()
                ?: return@mapNotNull null
            val title = jsonByKey[season to episode]?.title
                ?: link.attr("title").trim().takeIf { it.isNotBlank() }
                ?: "Season $season Episode $episode"
            EpisodeCandidate(href, season, episode, title)
        }.distinctBy { Triple(it.season, it.episode, it.url) }

        val domKeys = domItems.map { it.season to it.episode }.toSet()
        return (domItems + jsonItems.filterNot { (it.season to it.episode) in domKeys })
            .distinctBy { Triple(it.season, it.episode, it.url) }
            .sortedWith(compareBy<EpisodeCandidate> { it.season }.thenBy { it.episode })
    }

    private fun parseJsonEpisodes(document: Document): List<EpisodeCandidate> {
        val json = document.selectFirst("script#season-data")?.data()?.trim().orEmpty()
        if (json.isBlank()) return emptyList()
        val seasons = runCatching {
            mapper.readValue<Map<String, List<SeasonEpisode>>>(json)
        }.getOrDefault(emptyMap())
        return seasons.values.flatten().mapNotNull { item ->
            val slug = item.slug?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val episode = item.episodeNo ?: return@mapNotNull null
            val season = item.season ?: 1
            val href = absoluteUrl(SERIES_URL, slug) ?: return@mapNotNull null
            EpisodeCandidate(
                url = href,
                season = season,
                episode = episode,
                title = item.title?.trim()?.takeIf { it.isNotBlank() } ?: "Season $season Episode $episode",
            )
        }.distinctBy { Triple(it.season, it.episode, it.url) }
    }

    private fun parseRecommendations(document: Document, base: String, type: TvType): List<SearchResponse> {
        return document.select("ul.video-list li, .related-content li, .mob-related-series li.slider")
            .mapNotNull { it.toRecommendation(base, type) }
            .distinctBy { it.url }
            .take(24)
    }

    private fun Element.toRecommendation(base: String, type: TvType): SearchResponse? {
        val link = selectFirst("a[href]") ?: return null
        val href = absoluteUrl(base, link.attr("href")) ?: return null
        if (!isContentUrl(href, base)) return null
        val title = selectFirst(".video-title, h3, .poster-title")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: link.attr("title").trim().takeIf { it.isNotBlank() }
            ?: return null
        val img = selectFirst("img")
        val poster = firstValidUrl(img?.attr("data-src"), img?.attr("src"))
        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
    }

    private fun Document.cleanPage(): Document = apply {
        select(AD_NODE_SELECTOR).remove()
        select("script[src]").remove()
    }

    private fun Document.cleanPlayerPage(): Document = apply {
        select(AD_NODE_SELECTOR).remove()
        select("script[src]").remove()
    }

    private fun isEpisodePage(document: Document): Boolean {
        val ogType = document.selectFirst("meta[property=og:type]")?.attr("content")
        return ogType.equals("episode", ignoreCase = true) ||
            document.selectFirst(".main-player[data-related_type=series], iframe#main-player") != null
    }

    private fun findSeriesParentUrl(document: Document, pageUrl: String): String? {
        val labeled = document.select(".detail p, .meta-info p, .content-left p")
            .firstOrNull { it.text().trim().startsWith("Series", ignoreCase = true) }
            ?.selectFirst("a[href]")
            ?.attr("href")
            ?.let { absoluteUrl(pageUrl, it) }
        if (labeled != null && originOf(labeled) == SERIES_URL) return labeled

        return document.select("script[type=application/ld+json]")
            .asSequence()
            .map { it.data() }
            .mapNotNull { PART_OF_SERIES_URL_REGEX.find(it)?.groupValues?.getOrNull(1) }
            .mapNotNull { absoluteUrl(pageUrl, decodeEscapedUrl(it)) }
            .firstOrNull { originOf(it) == SERIES_URL }
    }

    private fun labeledLinks(document: Document, label: String): List<String> {
        return document.select(".detail p, .meta-info p")
            .firstOrNull { it.text().trim().startsWith("$label:", ignoreCase = true) }
            ?.select("a")
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    private suspend fun emitDirect(
        url: String,
        referer: String,
        source: String,
        callback: (ExtractorLink) -> Unit,
    ) {
        callback(newExtractorLink(source, source, url) {
            this.referer = referer
            this.type = if (url.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            this.quality = getQualityFromName(url)
            this.headers = mapOf("Referer" to referer, "User-Agent" to USER_AGENT)
        })
    }

    private fun pagedUrl(base: String, page: Int): String =
        if (page <= 1) base else "${base.trimEnd('/')}/page/$page"

    private fun typeForUrl(url: String): TvType =
        if (originOf(url) == SERIES_URL) TvType.TvSeries else TvType.Movie

    private fun normalizeContentUrl(url: String): String? {
        val normalized = absoluteUrl(if (url.contains("nontondrama", true)) SERIES_URL else MOVIE_URL, url) ?: return null
        val origin = originOf(normalized)
        return normalized.takeIf { origin == MOVIE_URL || origin == SERIES_URL }
    }

    private fun isContentUrl(url: String, base: String): Boolean {
        if (originOf(url) != originOf(base)) return false
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        if (path == "/" || path.isBlank()) return false
        return BLOCKED_CONTENT_PREFIXES.none { path.startsWith(it) }
    }

    private fun absoluteUrl(base: String, value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (
            raw.startsWith("javascript:", true) || raw.startsWith("data:", true) ||
            raw.startsWith("blob:", true) || raw.startsWith("about:", true) || raw == "#"
        ) return null
        return runCatching {
            val uri = URI(base).resolve(raw)
            if (uri.scheme !in setOf("http", "https")) null else uri.toString()
        }.getOrNull()
    }

    private fun firstValidUrl(vararg values: String?): String? = values.firstNotNullOfOrNull { value ->
        value?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun originOf(url: String): String = runCatching {
        URI(url).let { "${it.scheme}://${it.host}" }
    }.getOrDefault(MOVIE_URL)

    private fun hostOf(url: String): String =
        runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")

    private fun isSafePlayerUrl(url: String): Boolean {
        val host = hostOf(url)
        return host.isNotBlank() && BLOCKED_HOSTS.none { host == it || host.endsWith(".$it") }
    }

    private fun isSafeMediaUrl(url: String): Boolean = isSafePlayerUrl(url)

    private fun isDirectMedia(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return DIRECT_EXTENSIONS.any(clean::endsWith)
    }

    private fun decodeEscapedUrl(value: String): String = value
        .replace("\\/", "/")
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("&amp;", "&")
        .trim()

    private fun decodeBase64Url(value: String): String? = runCatching {
        base64Decode(value).trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }.getOrNull()

    private fun serverName(url: String): String =
        URI(url).path.orEmpty().split('/').getOrNull(2)?.uppercase()
            ?: hostOf(url).substringBefore('.').ifBlank { "LK21" }.uppercase()

    private fun playerPriority(url: String): Int = when {
        "/hydrax/" in url -> 0
        "/turbovip/" in url -> 1
        "/cast/" in url -> 2
        "/p2p/" in url -> 3
        else -> 4
    }

    private fun parseDurationMinutes(value: String): Int? {
        val hours = Regex("(\\d+)\\s*h", RegexOption.IGNORE_CASE)
            .find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(\\d+)\\s*m", RegexOption.IGNORE_CASE)
            .find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return (hours * 60 + minutes).takeIf { it > 0 }
    }

    private fun cleanTitle(value: String?): String? = value?.trim()?.takeIf { it.isNotBlank() }
        ?.replace(Regex("^Lk21\\s+Nonton\\s+", RegexOption.IGNORE_CASE), "")
        ?.substringBefore(" | Streaming")
        ?.substringBefore(" Sub Indo")
        ?.trim()

    private data class EpisodeCandidate(
        val url: String,
        val season: Int,
        val episode: Int,
        val title: String,
    )

    private data class SeasonEpisode(
        @JsonProperty("s") val season: Int? = null,
        @JsonProperty("episode_no") val episodeNo: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("slug") val slug: String? = null,
    )

    companion object {
        private const val MOVIE_URL = "https://tv12.lk21official.cc"
        private const val SERIES_URL = "https://tv6.nontondrama.my"
        private const val PAGE_SIZE = 24
        private const val CARD_SELECTOR = "div.gallery-grid article"
        private const val SEARCH_CARD_SELECTOR = "div.gallery-grid article, article.mega-item, div.search-item"
        private const val AD_NODE_SELECTOR = "#adContainer, #adsLink, #openPopup, #adHomeTop, #nativeAds, .chordyes, .popup, .popunder, .ads, .advertisement, iframe[src*=doubleclick], iframe[src*=histats]"
        private val PLAYER_HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
            "Upgrade-Insecure-Requests" to "1",
        )
        private val YEAR_REGEX = Regex("(?<!\\d)(?:19|20)\\d{2}(?!\\d)")
        private val EPISODE_PATH_REGEX = Regex("season[-_ ]?(\\d+).*?episode[-_ ]?(\\d+)", RegexOption.IGNORE_CASE)
        private val MEDIA_REGEX = Regex("https?://[^\\s\\\"'<>]+?\\.(?:m3u8|mp4|mkv|webm)(?:\\?[^\\s\\\"'<>]*)?", RegexOption.IGNORE_CASE)
        private val URL_ASSIGNMENT_REGEX = Regex("""(?:file|src|url|source)\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val ATOB_REGEX = Regex("""atob\(["']([A-Za-z0-9+/=_-]+)["']\)""", RegexOption.IGNORE_CASE)
        private val REFRESH_URL_REGEX = Regex("""url\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
        private val PART_OF_SERIES_URL_REGEX = Regex(""""partOfSeries"[\s\S]*?"url"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
        private val DIRECT_EXTENSIONS = setOf(".m3u8", ".mp4", ".mkv", ".webm")
        private val BLOCKED_HOSTS = setOf(
            "donasi.showcdnx.com", "s.id", "chordyes.com", "histats.com", "sstatic1.histats.com",
            "googletagmanager.com", "google-analytics.com", "doubleclick.net", "facebook.com",
            "instagram.com", "x.com", "tele.lk21.de", "youtube.com", "youtu.be",
        )
        private val BLOCKED_CONTENT_PREFIXES = listOf(
            "/genre/", "/country/", "/year/", "/quality/", "/artist/", "/director/", "/translator/",
            "/search", "/populer", "/latest", "/rating", "/release", "/privacy-policy", "/dmca",
            "/faq", "/cara-install-vpn", "/rekomendasi-film-pintar", "/series/", "/nontondrama",
        )
    }
}
