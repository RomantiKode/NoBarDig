package com.rebahin21

import com.lagradost.cloudstream3.*
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
import java.util.concurrent.CancellationException

class Rebahin21Provider : MainAPI() {
    override var mainUrl = "http://154.93.73.212"
    override var name = "Rebahin21"
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "" to "Update Terbaru",
        "movie" to "Movie",
        "tv" to "TV Series",
        "drama-korea" to "Drama Korea",
        "drama-china" to "Drama China",
        "west-series" to "West Series",
        "film-action-terbaru" to "Action",
        "film-horror-terbaru" to "Horror",
        "drama" to "Drama",
        "comedy" to "Comedy",
        "romance" to "Romance",
        "anime" to "Anime",
    )

    private val pageHeaders: Map<String, String>
        get() = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to "$mainUrl/",
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val base = if (request.data.isBlank()) mainUrl else "$mainUrl/${request.data.trim('/')}"
        val pageUrl = if (page <= 1) "$base/" else "${base.trimEnd('/')}/page/$page/"
        val items = app.get(pageUrl, headers = pageHeaders).document.toSearchResults()
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        return app.get("$mainUrl/?s=$encoded", headers = pageHeaders).document.toSearchResults()
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url, headers = pageHeaders)
        val document = response.document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: throw ErrorLoadingException("Judul tidak ditemukan")

        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?.takeIf(String::isNotBlank)
            ?: document.selectFirst(".gmr-movie-data img, img.wp-post-image")?.imageUrl()

        val plot = document.selectFirst("meta[name=description]")?.attr("content")
            ?.trim()?.takeIf(String::isNotBlank)
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")
                ?.trim()?.takeIf(String::isNotBlank)

        val year = YEAR_REGEX.find(title)?.value?.toIntOrNull()
        val tags = document.select(
            ".content-moviedata a[rel='category tag'], .tags-links-content a[rel=tag]"
        ).map { it.text().trim() }.filter(String::isNotBlank).distinct()

        val playerUrls = document.playerFrames(url)
        val episodeNumbers = linkedSetOf<Int>()
        episodeNumbers += parseEpisodeNumbers(document.html())

        for (playerUrl in playerUrls.take(MAX_PLAYER_PAGES)) {
            val playerHtml = try {
                app.get(
                    playerUrl,
                    headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url),
                ).text
            } catch (error: Exception) {
                error.rethrowCancellation()
                continue
            }
            episodeNumbers += parseEpisodeNumbers(playerHtml)
        }

        val pageSignalsSeries = document.select(".muvipro-player-tabs").text()
            .contains("ALL EPISODE", ignoreCase = true)
        val isSeries = episodeNumbers.size > 1 || pageSignalsSeries

        return if (isSeries) {
            val numbers = episodeNumbers.ifEmpty { linkedSetOf(1) }.sorted()
            val episodes = numbers.map { number ->
                newEpisode("${url.substringBefore('#')}#$EPISODE_MARKER$number") {
                    name = "Episode $number"
                    episode = number
                    season = 1
                    posterUrl = poster
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val pageUrl = data.substringBefore("#$EPISODE_MARKER")
        val episode = data.substringAfter("#$EPISODE_MARKER", "1").toIntOrNull() ?: 1
        val document = app.get(pageUrl, headers = pageHeaders).document
        val playerUrls = document.playerFrames(pageUrl)

        var handled = false
        for (playerUrl in playerUrls.take(MAX_PLAYER_PAGES)) {
            handled = extractPlayer(
                playerUrl = playerUrl,
                pageReferer = pageUrl,
                episode = episode,
                subtitleCallback = subtitleCallback,
                callback = callback,
            ) || handled
        }
        return handled
    }

    private fun Document.toSearchResults(): List<SearchResponse> {
        return select(".gmr-item-modulepost")
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val link = selectFirst("h2.entry-title a[href], a[rel=bookmark][href]") ?: return null
        val href = resolveUrl(mainUrl, link.attr("href")) ?: return null
        if (!isSiteContentUrl(href)) return null

        val title = link.attr("title")
            .removePrefix("Permalink ke:")
            .trim()
            .ifBlank { link.text().trim() }
        if (title.isBlank()) return null

        val poster = selectFirst("img")?.imageUrl()
        val category = select(".gmr-movie-on, a[rel='category tag']").text().lowercase()
        val looksLikeSeries = category.contains("series") || category.contains("drama short")

        return if (looksLikeSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
            }
        }
    }

    private fun Document.playerFrames(baseUrl: String): List<String> {
        return select(".gmr-server-wrap iframe[src], .gmr-server-wrap iframe[data-src]")
            .asSequence()
            .filterNot { it.isHiddenFrame() }
            .mapNotNull { frame ->
                val raw = frame.attr("src").ifBlank { frame.attr("data-src") }
                resolveUrl(baseUrl, raw)
            }
            .filter(::isSafeMediaUrl)
            .distinct()
            .toList()
    }

    private suspend fun extractPlayer(
        playerUrl: String,
        pageReferer: String,
        episode: Int,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val candidateUrls = episodeCandidateUrls(playerUrl, episode)
        var baseFingerprint: Set<String>? = null
        var handled = false

        for ((index, candidateUrl) in candidateUrls.withIndex()) {
            val html = try {
                app.get(
                    candidateUrl,
                    headers = mapOf("User-Agent" to USER_AGENT, "Referer" to pageReferer),
                ).text
            } catch (error: Exception) {
                error.rethrowCancellation()
                continue
            }

            val playerDoc = Jsoup.parse(html, candidateUrl)
            val activeEpisode = detectActiveEpisode(playerDoc)
            val targetSpecific = collectEpisodeTargets(playerDoc, candidateUrl, episode)
            val globalTargets = collectGlobalPlayerTargets(playerDoc, candidateUrl)
            val fingerprint = globalTargets.filter(::isPlaybackFingerprintUrl).toSet()
            if (index == 0) baseFingerprint = fingerprint
            val changedPlaybackTarget = index > 0 && fingerprint.isNotEmpty() && fingerprint != baseFingerprint
            val candidateMatchesEpisode = episode == 1 || activeEpisode == episode ||
                targetSpecific.isNotEmpty() || changedPlaybackTarget

            val targets = linkedSetOf<String>()
            targets += targetSpecific
            if (candidateMatchesEpisode) {
                targets += globalTargets
                collectSubtitles(playerDoc, candidateUrl, subtitleCallback)
            }

            for (target in targets.filter(::isSafeMediaUrl)) {
                handled = emitOrDelegateTarget(
                    target = target,
                    referer = candidateUrl,
                    subtitleCallback = subtitleCallback,
                    callback = callback,
                ) || handled
            }

            if (!handled && candidateMatchesEpisode) {
                try {
                    handled = loadExtractor(
                        candidateUrl,
                        pageReferer,
                        subtitleCallback,
                        callback,
                    ) || handled
                } catch (error: Exception) {
                    error.rethrowCancellation()
                }
            }

            if (handled && candidateMatchesEpisode) break
        }

        // Jangan fallback ke player episode 1 untuk episode lain karena dapat menghasilkan video yang salah.
        if (!handled && episode == 1) {
            try {
                handled = loadExtractor(playerUrl, pageReferer, subtitleCallback, callback)
            } catch (error: Exception) {
                error.rethrowCancellation()
            }
        }
        return handled
    }

    private fun episodeCandidateUrls(playerUrl: String, episode: Int): List<String> {
        if (episode <= 1) return listOf(playerUrl)
        return buildList {
            add(playerUrl)
            addQuery(playerUrl, "ep", episode)?.let(::add)
            addQuery(playerUrl, "episode", episode)?.let(::add)
            addQuery(playerUrl, "e", episode)?.let(::add)
        }.distinct()
    }

    private suspend fun emitOrDelegateTarget(
        target: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val normalized = normalizeEscapedUrl(target)
        if (!isSafeMediaUrl(normalized)) return false

        if (DIRECT_VIDEO_REGEX.containsMatchIn(normalized)) {
            val type = if (normalized.contains(".m3u8", ignoreCase = true)) {
                ExtractorLinkType.M3U8
            } else {
                ExtractorLinkType.VIDEO
            }
            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = normalized,
                    type = type,
                ) {
                    this.referer = referer
                    this.headers = mapOf("User-Agent" to USER_AGENT, "Referer" to referer)
                    this.quality = qualityFromUrl(normalized)
                }
            )
            return true
        }

        try {
            if (loadExtractor(normalized, referer, subtitleCallback, callback)) return true
        } catch (error: Exception) {
            error.rethrowCancellation()
        }

        // Satu pemindaian wrapper HTML terkontrol; tidak merambat tanpa batas.
        val nestedHtml = try {
            app.get(
                normalized,
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to referer),
            ).text
        } catch (error: Exception) {
            error.rethrowCancellation()
            return false
        }
        val nestedDoc = Jsoup.parse(nestedHtml, normalized)
        collectSubtitles(nestedDoc, normalized, subtitleCallback)

        var emitted = false
        for (nestedTarget in collectGlobalPlayerTargets(nestedDoc, normalized).filter(::isSafeMediaUrl)) {
            if (nestedTarget == normalized) continue
            if (DIRECT_VIDEO_REGEX.containsMatchIn(nestedTarget)) {
                val type = if (nestedTarget.contains(".m3u8", true)) {
                    ExtractorLinkType.M3U8
                } else {
                    ExtractorLinkType.VIDEO
                }
                callback(
                    newExtractorLink(name, name, nestedTarget, type) {
                        this.referer = normalized
                        this.headers = mapOf("User-Agent" to USER_AGENT, "Referer" to normalized)
                        this.quality = qualityFromUrl(nestedTarget)
                    }
                )
                emitted = true
            } else {
                try {
                    emitted = loadExtractor(
                        nestedTarget,
                        normalized,
                        subtitleCallback,
                        callback,
                    ) || emitted
                } catch (error: Exception) {
                    error.rethrowCancellation()
                }
            }
        }
        return emitted
    }

    private fun collectEpisodeTargets(document: Document, baseUrl: String, episode: Int): Set<String> {
        val targets = linkedSetOf<String>()
        val selectors = listOf("[data-ep='$episode']", "[data-episode='$episode']")
        for (selector in selectors) {
            document.select(selector).filterNot { it.hasClass("off") }.forEach { element ->
                MEDIA_ATTRIBUTES.forEach { attribute ->
                    resolveUrl(baseUrl, element.attr(attribute))?.let(targets::add)
                }
                extractUrlsFromText(element.attr("onclick"), baseUrl).forEach(targets::add)
            }
        }

        val scripts = document.select("script").joinToString("\n") { it.data() }
        val episodeBlocks = EPISODE_OBJECT_REGEX(episode)
            .findAll(scripts)
            .joinToString("\n") { it.value }
        targets += extractUrlsFromText(episodeBlocks, baseUrl)
        targets += extractEpisodeMapTargets(scripts, baseUrl, episode)
        targets += extractIndexedEpisodeTargets(scripts, baseUrl, episode)
        return targets
    }

    private fun collectGlobalPlayerTargets(document: Document, baseUrl: String): Set<String> {
        val targets = linkedSetOf<String>()
        document.select("video[src], video source[src], source[src]").forEach { element ->
            resolveUrl(baseUrl, element.attr("src"))?.let(targets::add)
        }
        document.select("iframe[src], iframe[data-src]")
            .filterNot { it.isHiddenFrame() }
            .forEach { element ->
                val raw = element.attr("src").ifBlank { element.attr("data-src") }
                resolveUrl(baseUrl, raw)?.let(targets::add)
            }
        document.select("[data-file], [data-video], [data-stream], [data-url], [data-link]").forEach { element ->
            MEDIA_ATTRIBUTES.forEach { attribute ->
                val raw = element.attr(attribute)
                if (looksLikeMediaOrPlayer(raw)) {
                    resolveUrl(baseUrl, raw)?.let(targets::add)
                }
            }
        }
        document.select("script").forEach { script ->
            targets += extractUrlsFromText(script.data(), baseUrl)
        }
        return targets
    }

    private fun collectSubtitles(
        document: Document,
        baseUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val seen = hashSetOf<String>()
        document.select("track[src]").forEach { track ->
            val url = resolveUrl(baseUrl, track.attr("src")) ?: return@forEach
            if (seen.add(url)) {
                val label = track.attr("label").ifBlank { "Indonesia" }
                subtitleCallback(SubtitleFile(label, url))
            }
        }
        SUBTITLE_URL_REGEX.findAll(normalizeEscapedUrl(document.html())).forEach { match ->
            val url = match.value
            if (seen.add(url)) subtitleCallback(SubtitleFile("Indonesia", url))
        }
    }

    private fun parseEpisodeNumbers(html: String): Set<Int> {
        val numbers = linkedSetOf<Int>()
        val document = Jsoup.parse(html)
        val blobs = buildList {
            add(document.html())
            document.select("script").mapTo(this) { it.data() }
        }

        for (blob in blobs) {
            val normalized = blob.replace("\\\"", "\"").replace("\\/", "/")
            val parsed = Jsoup.parse(normalized)
            parsed.select("[data-ep], [data-episode]").forEach { element ->
                if (element.hasClass("off") || element.attr("aria-disabled") == "true") return@forEach
                val value = element.attr("data-ep").ifBlank { element.attr("data-episode") }
                value.toIntOrNull()?.takeIf { it > 0 }?.let(numbers::add)
            }

            HTML_TAG_REGEX.findAll(normalized).forEach { tagMatch ->
                val tag = tagMatch.value
                if (tag.contains(" off", true) || tag.contains("aria-disabled=\"true\"", true)) {
                    return@forEach
                }
                DATA_EP_REGEX.find(tag)?.groupValues?.getOrNull(1)
                    ?.toIntOrNull()?.takeIf { it > 0 }?.let(numbers::add)
            }
        }
        return numbers
    }

    private fun detectActiveEpisode(document: Document): Int? {
        val active = document.selectFirst(
            ".ep-cell.on[data-ep], .ep-cell.active[data-ep], [data-episode].active, " +
                "[aria-current=true][data-ep]"
        )
        val fromAttribute = active?.attr("data-ep")
            ?.ifBlank { active.attr("data-episode") }
            ?.toIntOrNull()
        if (fromAttribute != null) return fromAttribute
        return document.selectFirst("#epBtnTxt")?.text()
            ?.let { DIGIT_REGEX.find(it)?.value?.toIntOrNull() }
    }

    private fun extractEpisodeMapTargets(text: String, baseUrl: String, episode: Int): Set<String> {
        val normalized = normalizeEscapedUrl(text)
        val targets = linkedSetOf<String>()
        val regex = Regex(
            """(?is)[\"']?$episode[\"']?\s*:\s*[\"']([^\"']+)[\"']"""
        )
        regex.findAll(normalized).forEach { match ->
            val raw = match.groupValues[1]
            if (looksLikeMediaOrPlayer(raw)) {
                resolveUrl(baseUrl, raw)?.let(targets::add)
            }
        }
        return targets
    }

    private fun extractIndexedEpisodeTargets(text: String, baseUrl: String, episode: Int): Set<String> {
        if (episode <= 0) return emptySet()
        val normalized = normalizeEscapedUrl(text)
        val targets = linkedSetOf<String>()
        EPISODE_ARRAY_REGEX.findAll(normalized).forEach { arrayMatch ->
            val values = QUOTED_VALUE_REGEX.findAll(arrayMatch.groupValues[1])
                .map { it.groupValues[1] }
                .filter { looksLikeMediaOrPlayer(it) }
                .toList()
            values.getOrNull(episode - 1)?.let { raw ->
                resolveUrl(baseUrl, raw)?.let(targets::add)
            }
        }
        return targets
    }

    private fun extractUrlsFromText(text: String, baseUrl: String): Set<String> {
        if (text.isBlank()) return emptySet()
        val normalized = normalizeEscapedUrl(text)
        val urls = linkedSetOf<String>()

        KEYED_MEDIA_REGEX.findAll(normalized).forEach { match ->
            resolveUrl(baseUrl, match.groupValues[1])?.let(urls::add)
        }
        DIRECT_URL_REGEX.findAll(normalized).forEach { match ->
            val raw = match.value.trimEnd('"', '\'', ')', ']', '}', ';', ',')
            resolveUrl(baseUrl, raw)?.let(urls::add)
        }
        return urls
    }

    private fun looksLikeMediaOrPlayer(value: String): Boolean {
        val lower = value.trim().lowercase()
        if (lower.isBlank() || lower.startsWith("javascript:") || lower.startsWith("data:")) return false
        return DIRECT_VIDEO_REGEX.containsMatchIn(lower) || lower.contains("/e/") ||
            lower.contains("embed") || lower.contains("player") || lower.contains("watch") ||
            lower.contains("stream") || lower.startsWith("/") || lower.startsWith("//")
    }

    private fun isPlaybackFingerprintUrl(value: String): Boolean {
        val lower = value.lowercase()
        return DIRECT_VIDEO_REGEX.containsMatchIn(lower) || lower.contains("/e/") ||
            lower.contains("embed") || lower.contains("player") || lower.contains("watch")
    }

    private fun Element.imageUrl(): String? {
        val raw = IMAGE_ATTRIBUTES.firstNotNullOfOrNull { attribute ->
            attr(attribute).trim().takeIf(String::isNotBlank)
        } ?: return null
        return resolveUrl(mainUrl, raw)
    }

    private fun Element.isHiddenFrame(): Boolean {
        val src = attr("src").ifBlank { attr("data-src") }.trim()
        val style = attr("style").lowercase()
        return src.isBlank() || src.startsWith("javascript:", true) || src == "about:blank" ||
            attr("width") == "0" || attr("height") == "0" ||
            style.contains("display:none") || style.contains("display: none") ||
            style.contains("visibility:hidden") || style.contains("visibility: hidden")
    }

    private fun isSiteContentUrl(url: String): Boolean {
        return try {
            URI(url).host.equals(URI(mainUrl).host, ignoreCase = true) &&
                !url.contains("/author/") && !url.contains("/tag/") && !url.contains("/page/")
        } catch (_: Exception) {
            false
        }
    }

    private fun isSafeMediaUrl(url: String): Boolean {
        val clean = url.trim()
        if (clean.isBlank() || clean.startsWith("javascript:", true) ||
            clean.startsWith("data:", true) || clean == "about:blank"
        ) return false
        val lower = clean.lowercase()
        return AD_HOST_MARKERS.none(lower::contains)
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
            .replace("\\u0026", "&", ignoreCase = true)
            .replace("&amp;", "&")
            .replace("\\x3a", ":", ignoreCase = true)
            .replace("\\x2f", "/", ignoreCase = true)
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

    private fun Exception.rethrowCancellation() {
        if (this is CancellationException) throw this
    }

    companion object {
        private const val MAX_PLAYER_PAGES = 4
        private const val EPISODE_MARKER = "cs3-episode="

        private val YEAR_REGEX = Regex("(?:19|20)\\d{2}")
        private val DIGIT_REGEX = Regex("\\d+")
        private val DATA_EP_REGEX = Regex(
            """data-(?:ep|episode)\s*=\s*[\"']?(\d+)""",
            RegexOption.IGNORE_CASE,
        )
        private val HTML_TAG_REGEX = Regex("<[^>]+>")
        private val DIRECT_VIDEO_REGEX = Regex(
            """(?i)\.(?:m3u8|mp4|mkv|webm)(?:\?|$)|/hls/"""
        )
        private val SUBTITLE_URL_REGEX = Regex(
            """https?://[^\s\"']+\.(?:vtt|srt)(?:\?[^\s\"']*)?""",
            RegexOption.IGNORE_CASE,
        )
        private val DIRECT_URL_REGEX = Regex(
            """https?://[^\s\"'<>]+\.(?:m3u8|mp4|mkv|webm|vtt|srt)(?:\?[^\s\"'<>]*)?""",
            RegexOption.IGNORE_CASE,
        )
        private val KEYED_MEDIA_REGEX = Regex(
            """(?i)\b(?:file|source|url|video|stream|embed)\s*[:=]\s*[\"']([^\"']+)[\"']"""
        )
        private val EPISODE_ARRAY_REGEX = Regex(
            """(?is)(?:episodes?|eps|playlist|sources?)\s*[:=]\s*\[(.{1,50000}?)\]"""
        )
        private val QUOTED_VALUE_REGEX = Regex("""[\"']([^\"']+)[\"']""")

        private fun EPISODE_OBJECT_REGEX(episode: Int) = Regex(
            """(?is)(?:episode|ep|number|index)\s*[\"']?\s*[:=]\s*[\"']?$episode[\"']?.{0,1600}?"""
        )

        private val IMAGE_ATTRIBUTES = listOf("data-src", "data-lazy-src", "data-original", "src")
        private val MEDIA_ATTRIBUTES = listOf(
            "data-src", "data-url", "data-file", "data-video", "data-stream", "data-link", "src", "href"
        )
        private val AD_HOST_MARKERS = listOf(
            "doubleclick.", "googlesyndication.", "googleadservices.", "adservice.",
            "histats.", "semuadisini.", "tapioni.", "tagivi.", "dtscout.",
            "popads.", "popcash.", "propellerads.", "clickadu.", "onclicka.",
            "smallestpawsmention.", "detoxifylagoonsnugness.", "llvpn.com/",
            "facebook.com/tr", "googletagmanager.", "connect.facebook.net/",
        )
    }
}
