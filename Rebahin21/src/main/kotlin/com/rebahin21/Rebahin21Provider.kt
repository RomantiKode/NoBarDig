package com.rebahin21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.getAndUnpack
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.CancellationException

class Rebahin21Provider : MainAPI() {
    override var mainUrl = "http://154.93.73.212"
    override var name = "Rebahin21"
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        HOME_MOVIES to "Movies",
        HOME_TV_SERIES to "TV Series",
        HOME_DRAMA_SHORT to "Drama Short",
        "drama-korea" to "Drama Korea",
        "drama-china" to "Drama China",
        "west-series" to "West Series",
        "film-action-terbaru" to "Action",
        "tag/thriller" to "Thriller",
        "drama" to "Drama",
        "comedy" to "Comedy",
        "romance" to "Romance",
        "anime" to "Anime"
    )

    private fun requestHeaders(referer: String = "$mainUrl/"): Map<String, String> {
        return mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to referer
        )
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val results = when (request.data) {
                HOME_MOVIES -> loadHomeSection(
                    page = page,
                    selector = "#muvipro-posts-6 .gmr-item-modulepost",
                    archivePaths = listOf("movie"),
                    forcedType = ContentKind.MOVIE
                )
                HOME_TV_SERIES -> loadHomeSection(
                    page = page,
                    selector = "#muvipro-posts-2 .gmr-item-modulepost",
                    archivePaths = listOf("series-update", "tv"),
                    forcedType = ContentKind.SERIES
                )
                HOME_DRAMA_SHORT -> loadHomeSection(
                    page = page,
                    selector = "#muvipro-posts-5 .gmr-item-modulepost",
                    archivePaths = listOf("drashot"),
                    forcedType = ContentKind.SERIES
                )
                else -> {
                    val path = request.data.trim('/')
                    val base = if (path.isBlank()) mainUrl else "$mainUrl/$path"
                    val pageUrl = if (page <= 1) {
                        "${base.trimEnd('/')}/"
                    } else {
                        "${base.trimEnd('/')}/page/$page/"
                    }
                    val forcedType = when (path) {
                        "drama-korea", "drama-china", "west-series" -> ContentKind.SERIES
                        else -> null
                    }
                    app.get(pageUrl, headers = requestHeaders()).document.toSearchResults(
                        forcedType = forcedType
                    )
                }
            }
            newHomePageResponse(request.name, results)
        } catch (error: Exception) {
            error.rethrowCancellation()
            newHomePageResponse(request.name, emptyList())
        }
    }

    private suspend fun loadHomeSection(
        page: Int,
        selector: String,
        archivePaths: List<String>,
        forcedType: ContentKind
    ): List<SearchResponse> {
        if (page <= 1) {
            val homepage = app.get("$mainUrl/", headers = requestHeaders()).document
            val sectionResults = homepage.toSearchResults(selector, forcedType)
            if (sectionResults.isNotEmpty()) return sectionResults
        }

        for (path in archivePaths) {
            val base = "$mainUrl/${path.trim('/')}"
            val pageUrl = if (page <= 1) "$base/" else "$base/page/$page/"
            val results = try {
                app.get(pageUrl, headers = requestHeaders()).document.toSearchResults(
                    forcedType = forcedType
                )
            } catch (error: Exception) {
                error.rethrowCancellation()
                emptyList()
            }
            if (results.isNotEmpty()) return results
        }
        return emptyList()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            app.get("$mainUrl/?s=$encoded", headers = requestHeaders()).document.toSearchResults()
        } catch (error: Exception) {
            error.rethrowCancellation()
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val pageUrl = url.substringBefore(DATA_SEPARATOR)
        val document = app.get(pageUrl, headers = requestHeaders()).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: throw ErrorLoadingException("Judul tidak ditemukan")

        val poster = document.selectFirst(
            ".gmr-movie-data img, .content-thumbnail img, img.wp-post-image"
        )?.imageUrl(pageUrl)
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
                ?.trim()?.takeIf { it.isNotBlank() }

        val plot = document.selectFirst("meta[name=description]")?.attr("content")
            ?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")
                ?.trim()?.takeIf { it.isNotBlank() }

        val year = YEAR_REGEX.find(title)?.value?.toIntOrNull()
        val tags = document.select(
            ".content-moviedata a[rel='category tag'], .tags-links-content a[rel=tag]"
        ).map { it.text().trim() }.filter { it.isNotBlank() }.distinct()

        val categoryText = document.select(
            ".content-moviedata a[rel='category tag'], a[rel='category tag']"
        ).text().lowercase()

        val playerUrl = document.firstSafePlayerUrl(pageUrl)
        val episodeNumbers = linkedSetOf<Int>()

        if (playerUrl != null) {
            try {
                val playerHtml = app.get(
                    playerUrl,
                    headers = requestHeaders(pageUrl)
                ).text
                episodeNumbers.addAll(parseEpisodeNumbers(playerHtml))
            } catch (error: Exception) {
                error.rethrowCancellation()
            }
        }

        val isSeries = episodeNumbers.size > 1 ||
            pageUrl.contains("/tv/", ignoreCase = true) ||
            categoryText.contains("series") ||
            categoryText.contains("drama short") ||
            document.select(".muvipro-player-tabs").text().contains("ALL EPISODE", true)

        if (isSeries) {
            if (episodeNumbers.isEmpty()) episodeNumbers.add(1)
            val episodes = episodeNumbers.sorted().map { number ->
                val data = encodeData(pageUrl, playerUrl, number)
                newEpisode(data) {
                    name = "Episode $number"
                    episode = number
                    season = 1
                    posterUrl = poster
                }
            }

            return newTvSeriesLoadResponse(title, pageUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        }

        return newMovieLoadResponse(
            title,
            pageUrl,
            TvType.Movie,
            encodeData(pageUrl, playerUrl, 1)
        ) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val decoded = decodeData(data)
        val pageUrl = decoded.pageUrl
        val episode = decoded.episode
        val players = linkedSetOf<String>()
        decoded.playerUrl?.let(players::add)

        try {
            val detailDocument = app.get(pageUrl, headers = requestHeaders()).document
            players.addAll(detailDocument.safePlayerUrls(pageUrl))
        } catch (error: Exception) {
            error.rethrowCancellation()
        }

        if (players.isEmpty()) return false

        for (initialPlayer in players) {
            for (candidate in episodeCandidateUrls(initialPlayer, episode)) {
                if (isDirectVideo(candidate)) {
                    callback(createLegacyLink(candidate, pageUrl))
                    return true
                }

                var extractorEmitted = false
                try {
                    loadExtractor(
                        url = candidate,
                        referer = pageUrl,
                        subtitleCallback = subtitleCallback
                    ) { link ->
                        extractorEmitted = true
                        callback(link)
                    }
                } catch (error: Exception) {
                    error.rethrowCancellation()
                }
                if (extractorEmitted) return true

                val manuallyEmitted = try {
                    extractFromPlayer(
                        playerUrl = candidate,
                        pageReferer = pageUrl,
                        requestedEpisode = episode,
                        subtitleCallback = subtitleCallback,
                        callback = callback
                    )
                } catch (error: Exception) {
                    error.rethrowCancellation()
                    false
                }
                if (manuallyEmitted) return true
            }
        }
        return false
    }

    private suspend fun extractFromPlayer(
        playerUrl: String,
        pageReferer: String,
        requestedEpisode: Int,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(playerUrl, headers = requestHeaders(pageReferer)).text
        if (html.length > MAX_PLAYER_HTML_SIZE) return false

        val document = Jsoup.parse(html, playerUrl)
        val activeEpisode = detectActiveEpisode(document)
        val episodeMatches = requestedEpisode <= 1 || activeEpisode == requestedEpisode ||
            playerUrl.contains("ep=$requestedEpisode") ||
            playerUrl.contains("episode=$requestedEpisode")

        val targets = linkedSetOf<String>()
        targets.addAll(collectEpisodeTargets(document, playerUrl, requestedEpisode))
        if (episodeMatches || targets.isNotEmpty()) {
            targets.addAll(collectGlobalTargets(document, playerUrl))
            collectSubtitles(document, playerUrl, subtitleCallback)
        }

        var emitted = false
        for (target in targets) {
            if (!isSafeMediaUrl(target)) continue
            if (isDirectVideo(target)) {
                callback(createLegacyLink(target, playerUrl))
                emitted = true
                continue
            }

            var extractorEmitted = false
            try {
                loadExtractor(
                    url = target,
                    referer = playerUrl,
                    subtitleCallback = subtitleCallback
                ) { link ->
                    extractorEmitted = true
                    callback(link)
                }
            } catch (error: Exception) {
                error.rethrowCancellation()
            }
            if (extractorEmitted) {
                emitted = true
                continue
            }

            // Hanya satu lapis wrapper. Tidak menjalankan JavaScript dan tidak mengikuti popup.
            val nested = try {
                app.get(target, headers = requestHeaders(playerUrl)).text
            } catch (error: Exception) {
                error.rethrowCancellation()
                continue
            }
            if (nested.length > MAX_WRAPPER_HTML_SIZE) continue

            val nestedDocument = Jsoup.parse(nested, target)
            collectSubtitles(nestedDocument, target, subtitleCallback)
            val nestedTargets = collectGlobalTargets(nestedDocument, target)
            for (nestedTarget in nestedTargets) {
                if (!isSafeMediaUrl(nestedTarget)) continue
                if (isDirectVideo(nestedTarget)) {
                    callback(createLegacyLink(nestedTarget, target))
                    emitted = true
                    continue
                }

                var nestedExtractorEmitted = false
                try {
                    loadExtractor(
                        url = nestedTarget,
                        referer = target,
                        subtitleCallback = subtitleCallback
                    ) { link ->
                        nestedExtractorEmitted = true
                        callback(link)
                    }
                } catch (error: Exception) {
                    error.rethrowCancellation()
                }
                if (nestedExtractorEmitted) emitted = true
            }
        }
        return emitted
    }

    private fun Document.toSearchResults(
        selector: String = ".gmr-item-modulepost",
        forcedType: ContentKind? = null
    ): List<SearchResponse> {
        return select(selector)
            .mapNotNull { it.toSearchResponse(forcedType) }
            .distinctBy { it.url }
    }

    private fun Element.toSearchResponse(forcedType: ContentKind? = null): SearchResponse? {
        val link = selectFirst("h2.entry-title a[href], a[rel=bookmark][href]") ?: return null
        val href = resolveUrl(mainUrl, link.attr("href")) ?: return null
        if (!isSiteContentUrl(href)) return null

        val titleAttribute = link.attr("title").removePrefix("Permalink ke:").trim()
        val title = if (titleAttribute.isNotBlank()) titleAttribute else link.text().trim()
        if (title.isBlank()) return null

        val poster = selectFirst(".content-thumbnail img, img")?.imageUrl(href)
        val category = select(".gmr-movie-on, a[rel='category tag']").text().lowercase()
        val hrefLower = href.lowercase()
        val titleLower = title.lowercase()
        val series = when (forcedType) {
            ContentKind.SERIES -> true
            ContentKind.MOVIE -> false
            null -> category.contains("series") ||
                category.contains("drama short") ||
                hrefLower.contains("/tv/") ||
                hrefLower.contains("/series/") ||
                titleLower.contains(" season ") ||
                EPISODE_TITLE_REGEX.containsMatchIn(titleLower)
        }

        return if (series) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
            }
        }
    }

    private fun Document.safePlayerUrls(baseUrl: String): List<String> {
        val urls = linkedSetOf<String>()
        val frames = select(
            ".gmr-server-wrap iframe[src], .gmr-server-wrap iframe[data-src], " +
                ".gmr-server-wrap iframe[data-lazy-src], .gmr-server-wrap iframe[data-litespeed-src]"
        )
        for (frame in frames) {
            if (frame.isHiddenFrame()) continue
            val raw = frame.attr("src")
                .ifBlank { frame.attr("data-src") }
                .ifBlank { frame.attr("data-lazy-src") }
                .ifBlank { frame.attr("data-litespeed-src") }
            val resolved = resolveUrl(baseUrl, raw) ?: continue
            if (isSafeMediaUrl(resolved)) urls.add(resolved)
        }
        return urls.toList()
    }

    private fun Document.firstSafePlayerUrl(baseUrl: String): String? {
        return safePlayerUrls(baseUrl).firstOrNull()
    }

    private fun collectEpisodeTargets(
        document: Document,
        baseUrl: String,
        episode: Int
    ): Set<String> {
        val targets = linkedSetOf<String>()
        val elements = document.select("[data-ep='$episode'], [data-episode='$episode']")
        for (element in elements) {
            if (element.hasClass("off") || element.attr("aria-disabled") == "true") continue
            for (attribute in MEDIA_ATTRIBUTES) {
                val raw = element.attr(attribute)
                if (raw.isBlank()) continue
                val resolved = resolveUrl(baseUrl, raw)
                if (resolved != null) targets.add(resolved)
            }
            val onclick = element.attr("onclick")
            targets.addAll(extractUrlsFromText(onclick, baseUrl))
        }

        for (script in document.select("script")) {
            val text = script.data()
            if (text.length > MAX_SCRIPT_SIZE || !containsMediaHint(text)) continue
            val episodeBlock = EPISODE_BLOCK_REGEX(episode).find(text)?.value ?: continue
            targets.addAll(extractUrlsFromText(episodeBlock, baseUrl))
        }
        return targets
    }

    private fun collectGlobalTargets(document: Document, baseUrl: String): Set<String> {
        val targets = linkedSetOf<String>()

        for (element in document.select("video[src], video source[src], source[src]")) {
            val resolved = resolveUrl(baseUrl, element.attr("src"))
            if (resolved != null) targets.add(resolved)
        }

        for (element in document.select(
            "iframe[src], iframe[data-src], iframe[data-lazy-src], iframe[data-litespeed-src]"
        )) {
            if (element.isHiddenFrame()) continue
            val raw = element.attr("src")
                .ifBlank { element.attr("data-src") }
                .ifBlank { element.attr("data-lazy-src") }
                .ifBlank { element.attr("data-litespeed-src") }
            val resolved = resolveUrl(baseUrl, raw)
            if (resolved != null) targets.add(resolved)
        }

        for (element in document.select("[data-file], [data-video], [data-stream], [data-url], [data-link]")) {
            for (attribute in MEDIA_ATTRIBUTES) {
                val raw = element.attr(attribute)
                if (!looksLikeMediaOrPlayer(raw)) continue
                val resolved = resolveUrl(baseUrl, raw)
                if (resolved != null) targets.add(resolved)
            }
        }

        for (script in document.select("script")) {
            val text = script.data()
            if (text.length > MAX_SCRIPT_SIZE) continue

            if (containsMediaHint(text)) {
                targets.addAll(extractUrlsFromText(text, baseUrl))
            }

            if (text.contains("eval(function(p,a,c,k,e", ignoreCase = true)) {
                val unpacked = try {
                    getAndUnpack(text)
                } catch (_: Exception) {
                    ""
                }
                if (unpacked.isNotBlank() && unpacked.length <= MAX_SCRIPT_SIZE) {
                    targets.addAll(extractUrlsFromText(unpacked, baseUrl))
                }
            }
        }
        return targets
    }

    private fun collectSubtitles(
        document: Document,
        baseUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        val seen = hashSetOf<String>()
        for (track in document.select("track[src]")) {
            val url = resolveUrl(baseUrl, track.attr("src")) ?: continue
            if (seen.add(url)) {
                val label = track.attr("label").ifBlank { "Indonesia" }
                subtitleCallback(SubtitleFile(label, url))
            }
        }

        for (script in document.select("script")) {
            val text = script.data()
            if (text.length > MAX_SCRIPT_SIZE) continue
            for (match in SUBTITLE_URL_REGEX.findAll(normalizeEscapedUrl(text))) {
                val url = match.value
                if (seen.add(url)) subtitleCallback(SubtitleFile("Indonesia", url))
            }
        }
    }

    private fun parseEpisodeNumbers(html: String): Set<Int> {
        val numbers = linkedSetOf<Int>()
        if (html.length > MAX_PLAYER_HTML_SIZE) return numbers

        val document = Jsoup.parse(html)
        for (element in document.select(".ep-cell[data-ep], [data-episode]")) {
            if (element.hasClass("off") || element.attr("aria-disabled") == "true") continue
            val raw = element.attr("data-ep").ifBlank { element.attr("data-episode") }
            val number = raw.toIntOrNull()
            if (number != null && number > 0) numbers.add(number)
        }

        // Fallback satu lintasan untuk fragment HTML yang tidak lengkap.
        for (match in EPISODE_TAG_REGEX.findAll(html)) {
            val openingTag = match.value
            if (openingTag.contains(" off", true) || openingTag.contains("aria-disabled", true)) continue
            val number = DATA_EP_REGEX.find(openingTag)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (number != null && number > 0) numbers.add(number)
        }
        return numbers
    }

    private fun detectActiveEpisode(document: Document): Int? {
        val active = document.selectFirst(
            ".ep-cell.on[data-ep], .ep-cell.active[data-ep], [data-episode].active, [aria-current=true][data-ep]"
        )
        if (active != null) {
            val raw = active.attr("data-ep").ifBlank { active.attr("data-episode") }
            val number = raw.toIntOrNull()
            if (number != null) return number
        }
        val label = document.selectFirst("#epBtnTxt")?.text() ?: return null
        return DIGIT_REGEX.find(label)?.value?.toIntOrNull()
    }

    private fun episodeCandidateUrls(playerUrl: String, episode: Int): List<String> {
        if (episode <= 1) return listOf(playerUrl)
        val urls = mutableListOf(playerUrl)
        addQuery(playerUrl, "ep", episode)?.let { urls.add(it) }
        addQuery(playerUrl, "episode", episode)?.let { urls.add(it) }
        return urls.distinct()
    }

    private fun extractUrlsFromText(text: String, baseUrl: String): Set<String> {
        val urls = linkedSetOf<String>()
        if (text.isBlank() || text.length > MAX_SCRIPT_SIZE) return urls
        val normalized = normalizeEscapedUrl(text)

        for (match in KEYED_MEDIA_REGEX.findAll(normalized)) {
            val raw = match.groupValues[1]
            val resolved = resolveUrl(baseUrl, raw)
            if (resolved != null) urls.add(resolved)
        }
        for (match in DIRECT_URL_REGEX.findAll(normalized)) {
            val raw = match.value.trimEnd('"', '\'', ')', ']', '}', ';', ',')
            val resolved = resolveUrl(baseUrl, raw)
            if (resolved != null) urls.add(resolved)
        }
        return urls
    }

    @Suppress("DEPRECATION_ERROR")
    private fun createLegacyLink(url: String, referer: String): ExtractorLink {
        return ExtractorLink(
            name,
            name,
            url,
            referer,
            qualityFromUrl(url),
            url.contains(".m3u8", true) || url.contains("/hls/", true),
            mapOf("User-Agent" to USER_AGENT, "Referer" to referer)
        )
    }

    private fun Element.imageUrl(baseUrl: String): String? {
        for (attribute in IMAGE_ATTRIBUTES) {
            val raw = attr(attribute).trim()
            if (raw.isBlank()) continue
            val resolved = resolveUrl(baseUrl, raw)
            if (resolved != null) return resolved
        }
        return null
    }

    private fun Element.isHiddenFrame(): Boolean {
        val src = attr("src")
            .ifBlank { attr("data-src") }
            .ifBlank { attr("data-lazy-src") }
            .ifBlank { attr("data-litespeed-src") }
            .trim()
        val style = attr("style").lowercase()
        return src.isBlank() || src.startsWith("javascript:", true) || src == "about:blank" ||
            attr("width") == "0" || attr("height") == "0" ||
            style.contains("display:none") || style.contains("display: none") ||
            style.contains("visibility:hidden") || style.contains("visibility: hidden")
    }

    private fun containsMediaHint(value: String): Boolean {
        val lower = value.lowercase()
        return lower.contains(".m3u8") || lower.contains(".mp4") ||
            lower.contains("file:") || lower.contains("source:") ||
            lower.contains("video:") || lower.contains("stream:") ||
            lower.contains("embed")
    }

    private fun looksLikeMediaOrPlayer(value: String): Boolean {
        val lower = value.trim().lowercase()
        if (lower.isBlank() || lower.startsWith("javascript:") || lower.startsWith("data:")) {
            return false
        }
        return isDirectVideo(lower) || lower.contains("/e/") || lower.contains("embed") ||
            lower.contains("player") || lower.contains("watch") || lower.contains("stream") ||
            lower.startsWith("/") || lower.startsWith("//")
    }

    private fun isDirectVideo(value: String): Boolean {
        return DIRECT_VIDEO_REGEX.containsMatchIn(value)
    }

    private fun isSiteContentUrl(url: String): Boolean {
        return try {
            val sourceHost = URI(mainUrl).host
            val targetHost = URI(url).host
            sourceHost != null && sourceHost.equals(targetHost, true) &&
                !url.contains("/author/") && !url.contains("/tag/") && !url.contains("/page/")
        } catch (_: Exception) {
            false
        }
    }

    private fun isSafeMediaUrl(url: String): Boolean {
        val clean = url.trim()
        if (clean.isBlank() || clean.startsWith("javascript:", true) ||
            clean.startsWith("data:", true) || clean == "about:blank") {
            return false
        }
        val lower = clean.lowercase()
        for (marker in AD_HOST_MARKERS) {
            if (lower.contains(marker)) return false
        }
        return true
    }

    private fun resolveUrl(baseUrl: String, rawUrl: String): String? {
        val cleaned = normalizeEscapedUrl(rawUrl.trim())
        if (cleaned.isBlank() || cleaned.startsWith("#") || cleaned.startsWith("javascript:", true)) {
            return null
        }
        return try {
            URI(baseUrl).resolve(cleaned).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun addQuery(url: String, key: String, value: Int): String? {
        return try {
            val uri = URI(url)
            val existing = uri.rawQuery.orEmpty()
            val separator = if (existing.isBlank()) "" else "&"
            val query = "$existing$separator$key=$value"
            URI(uri.scheme, uri.authority, uri.path, query, uri.fragment).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeEscapedUrl(value: String): String {
        return value
            .replace("\\/", "/")
            .replace("\\u0026", "&", true)
            .replace("&amp;", "&")
            .replace("\\x3a", ":", true)
            .replace("\\x2f", "/", true)
    }

    private fun qualityFromUrl(url: String): Int {
        return when {
            url.contains("2160", true) || url.contains("4k", true) -> Qualities.P2160.value
            url.contains("1080", true) -> Qualities.P1080.value
            url.contains("720", true) -> Qualities.P720.value
            url.contains("480", true) -> Qualities.P480.value
            url.contains("360", true) -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }

    private fun encodeData(pageUrl: String, playerUrl: String?, episode: Int): String {
        return listOf(pageUrl, playerUrl.orEmpty(), episode.toString()).joinToString(DATA_SEPARATOR)
    }

    private fun decodeData(data: String): LinkData {
        val parts = data.split(DATA_SEPARATOR)
        return LinkData(
            pageUrl = parts.getOrNull(0).orEmpty(),
            playerUrl = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
            episode = parts.getOrNull(2)?.toIntOrNull() ?: 1
        )
    }

    private fun Exception.rethrowCancellation() {
        if (this is CancellationException) throw this
    }

    private data class LinkData(
        val pageUrl: String,
        val playerUrl: String?,
        val episode: Int
    )

    private enum class ContentKind {
        MOVIE,
        SERIES
    }

    companion object {
        private const val HOME_MOVIES = "__home_movies__"
        private const val HOME_TV_SERIES = "__home_tv_series__"
        private const val HOME_DRAMA_SHORT = "__home_drama_short__"
        private const val DATA_SEPARATOR = "|cs3|"
        private const val MAX_PLAYER_HTML_SIZE = 1_500_000
        private const val MAX_WRAPPER_HTML_SIZE = 750_000
        private const val MAX_SCRIPT_SIZE = 250_000

        private val YEAR_REGEX = Regex("(?:19|20)\\d{2}")
        private val DIGIT_REGEX = Regex("\\d+")
        private val EPISODE_TITLE_REGEX = Regex("(?:^|\\s)(?:ep|episode)\\s*\\d+", RegexOption.IGNORE_CASE)
        private val DATA_EP_REGEX = Regex(
            """data-(?:ep|episode)\s*=\s*[\"']?(\d+)""",
            RegexOption.IGNORE_CASE
        )
        private val EPISODE_TAG_REGEX = Regex(
            """(?is)<[^>]+data-(?:ep|episode)\s*=\s*[\"']?\d+[^>]*>"""
        )
        private val DIRECT_VIDEO_REGEX = Regex(
            """(?i)\.(?:m3u8|mp4|mkv|webm)(?:\?|$)|/hls/"""
        )
        private val SUBTITLE_URL_REGEX = Regex(
            """https?://[^\s\"']+\.(?:vtt|srt)(?:\?[^\s\"']*)?""",
            RegexOption.IGNORE_CASE
        )
        private val DIRECT_URL_REGEX = Regex(
            """https?://[^\s\"'<>]+\.(?:m3u8|mp4|mkv|webm|vtt|srt)(?:\?[^\s\"'<>]*)?""",
            RegexOption.IGNORE_CASE
        )
        private val KEYED_MEDIA_REGEX = Regex(
            """(?i)\b(?:file|source|url|video|stream|embed)\s*[:=]\s*[\"']([^\"']+)[\"']"""
        )

        private fun EPISODE_BLOCK_REGEX(episode: Int): Regex {
            return Regex(
                """(?is)(?:episode|ep|number|index)\s*[\"']?\s*[:=]\s*[\"']?$episode[\"']?.{0,1200}?"""
            )
        }

        private val IMAGE_ATTRIBUTES = listOf("data-src", "data-lazy-src", "data-original", "src")
        private val MEDIA_ATTRIBUTES = listOf(
            "data-src", "data-lazy-src", "data-litespeed-src", "data-url", "data-file",
            "data-video", "data-stream", "data-link", "src", "href"
        )
        private val AD_HOST_MARKERS = listOf(
            "doubleclick.", "googlesyndication.", "googleadservices.", "adservice.",
            "histats.", "semuadisini.", "tapioni.", "tagivi.", "dtscout.",
            "popads.", "popcash.", "propellerads.", "clickadu.", "onclicka.",
            "smallestpawsmention.", "detoxifylagoonsnugness.", "llvpn.com/",
            "facebook.com/tr", "googletagmanager.", "connect.facebook.net/"
        )
    }
}
