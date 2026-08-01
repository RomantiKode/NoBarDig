package com.rebahin

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Rebahin : MainAPI() {
    override var mainUrl = "https://165.232.44.215"
    override var name = "Rebahin"
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val requestHeaders = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
    )

    override val mainPage = mainPageOf(
        "" to "Terbaru",
        "movies" to "Film Terbaru",
        "tv" to "Serial TV",
        "genre/action" to "Action",
        "genre/animation" to "Animasi",
        "genre/comedy" to "Komedi",
        "genre/drama" to "Drama",
        "genre/horror" to "Horor",
        "genre/romance" to "Romantis",
        "genre/thriller" to "Thriller",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = buildPageUrl(request.data, page)
        val document = app.get(url, headers = requestHeaders).document
        return newHomePageResponse(request.name, document.toSearchResults())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val document = app.get("$mainUrl/search?q=$encoded", headers = requestHeaders).document
        return document.toSearchResults()
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = requestHeaders + ("Referer" to mainUrl)).document
        val isSeries = URI(url).path.startsWith("/tv/")
        val schema = document.findSchema(if (isSeries) "TVSeries" else "Movie")

        val title = schema?.optString("name").orEmpty().ifBlank {
            document.selectFirst("h1")?.text()?.trim().orEmpty().ifBlank {
                document.metaContent("og:title").ifBlank {
                    document.title().substringBefore(" - REBAHIN").trim()
                }
            }
        }
        if (title.isBlank()) return null

        val poster = schema?.optString("image").orEmpty().ifBlank {
            document.metaContent("og:image")
        }.normalizeUrl(url)
        val plot = schema?.optString("description").orEmpty().ifBlank {
            document.metaContent("description").ifBlank {
                document.metaContent("og:description")
            }
        }
        val year = schema?.optString("datePublished")
            ?.take(4)
            ?.toIntOrNull()
            ?: Regex("(?:19|20)\\d{2}").find(document.title())?.value?.toIntOrNull()
        val genres = schema.readStringList("genre")
        val actors = schema.readPeople("actor")
        val rating = schema?.optJSONObject("aggregateRating")
            ?.optDouble("ratingValue", Double.NaN)
            ?.takeUnless { it.isNaN() }
            ?.toString()
        val duration = schema?.optString("duration")?.toDurationMinutes()
        val trailer = schema?.optJSONObject("trailer")
            ?.optString("embedUrl")
            .orEmpty()
            .ifBlank { schema?.optJSONObject("trailer")?.optString("url").orEmpty() }

        return if (isSeries) {
            val episodes = document.toEpisodes()
            if (episodes.isEmpty()) return null

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
                this.duration = duration
                if (!rating.isNullOrBlank()) addScore(rating)
                if (actors.isNotEmpty()) addActors(actors)
                if (trailer.isNotBlank()) addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = genres
                this.duration = duration
                if (!rating.isNullOrBlank()) addScore(rating)
                if (actors.isNotEmpty()) addActors(actors)
                if (trailer.isNotBlank()) addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val response = app.get(
            data,
            headers = requestHeaders + mapOf(
                "Referer" to mainUrl,
                "X-Requested-With" to "XMLHttpRequest",
            ),
        )
        val document = response.document
        val candidates = linkedSetOf<String>()
        candidates += document.extractPlayerUrls(data)

        var emitted = emitPlayerCandidates(
            candidates = candidates,
            pageUrl = data,
            subtitleCallback = subtitleCallback,
            callback = callback,
        )

        // The initial Next.js HTML renders an empty <video>. Clicking the exact
        // Play overlay hydrates the player and assigns the real MP4 URL. Run the
        // WebView only as a final fallback, suppress popups, and stop as soon as
        // the first media request is intercepted.
        if (!emitted) {
            val captured = resolveHydratedPlayer(data)
            if (captured != null && captured.url.isAllowedPlayerUrl()) {
                emitDirectLink(
                    mediaUrl = captured.url,
                    pageUrl = data,
                    headers = captured.headers,
                    callback = callback,
                )
                emitted = true
            }
        }

        return emitted
    }

    private suspend fun emitPlayerCandidates(
        candidates: Collection<String>,
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var emitted = false
        for (candidate in candidates.map { it.cleanEscapes().normalizeUrl(pageUrl) }.distinct()) {
            if (!candidate.isAllowedPlayerUrl()) continue

            if (candidate.isDirectMedia()) {
                emitDirectLink(candidate, pageUrl, emptyMap(), callback)
                emitted = true
            } else {
                val extracted = runCatching {
                    loadExtractor(candidate, pageUrl, subtitleCallback, callback)
                }.getOrDefault(false)
                emitted = emitted || extracted
            }
        }
        return emitted
    }

    private suspend fun emitDirectLink(
        mediaUrl: String,
        pageUrl: String,
        headers: Map<String, String>,
        callback: (ExtractorLink) -> Unit,
    ) {
        val isM3u8 = mediaUrl.contains(".m3u8", ignoreCase = true) ||
            mediaUrl.contains("/hls/", ignoreCase = true)
        val playbackHeaders = headers
            .filterKeys { key ->
                key.lowercase() !in UNSAFE_FORWARD_HEADERS
            }
            .toMutableMap()
            .apply {
                putIfAbsent("User-Agent", USER_AGENT)
                putIfAbsent("Referer", pageUrl)
                putIfAbsent("Range", "bytes=0-")
            }

        callback(
            newExtractorLink(
                source = name,
                name = if (isM3u8) "$name HLS" else "$name Direct",
                url = mediaUrl,
                type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
            ) {
                this.referer = pageUrl
                this.headers = playbackHeaders
                this.quality = mediaUrl.findQuality()
            },
        )
    }

    private suspend fun resolveHydratedPlayer(pageUrl: String): CapturedMedia? {
        val resolver = WebViewResolver(
            interceptUrl = DIRECT_MEDIA_REQUEST,
            userAgent = USER_AGENT,
            useOkhttp = false,
            script = PLAYER_CLICK_SCRIPT,
            timeout = WEBVIEW_TIMEOUT_MS,
        )
        val request = runCatching {
            resolver.resolveUsingWebView(
                url = pageUrl,
                referer = mainUrl,
                headers = requestHeaders + ("Referer" to mainUrl),
            ).first
        }.getOrNull() ?: return null

        return CapturedMedia(
            url = request.url.toString().cleanEscapes(),
            headers = request.headers.toMap(),
        )
    }

    private fun String.findQuality(): Int {
        return when {
            contains("2160", ignoreCase = true) || contains("4k", ignoreCase = true) -> Qualities.P2160.value
            contains("1080", ignoreCase = true) -> Qualities.P1080.value
            contains("720", ignoreCase = true) -> Qualities.P720.value
            contains("480", ignoreCase = true) -> Qualities.P480.value
            contains("360", ignoreCase = true) -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }

    private fun buildPageUrl(path: String, page: Int): String {
        val normalizedPath = path.trim('/')
        val base = if (normalizedPath.isBlank()) mainUrl else "$mainUrl/$normalizedPath"
        return if (page <= 1) base else "$base?page=$page"
    }

    private fun Document.toSearchResults(): List<SearchResponse> {
        return select("a.group[href^='/movies/'], a.group[href^='/tv/']")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = attr("href").normalizeUrl(mainUrl)
        val path = runCatching { URI(href).path }.getOrNull().orEmpty()
        if (!CONTENT_PATH.matches(path)) return null

        val image = selectFirst("img") ?: return null
        val title = image.attr("alt").trim().ifBlank {
            selectFirst("h2, h3, p.font-semibold")?.text()?.trim().orEmpty()
        }
        if (title.isBlank()) return null

        val poster = image.bestImageUrl().normalizeUrl(href)
        return if (path.startsWith("/tv/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    private fun Document.toEpisodes(): List<Episode> {
        return select("a.group[href^='/tv/'][href*='/season-'][href*='/episode-']")
            .filter { it.selectFirst("img") != null && it.text().isNotBlank() }
            .mapNotNull { anchor ->
                val href = anchor.attr("href").normalizeUrl(mainUrl)
                val match = EPISODE_PATH.find(URI(href).path) ?: return@mapNotNull null
                val season = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val episode = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val name = anchor.selectFirst("p.font-semibold")?.text()?.trim().orEmpty()
                    .ifBlank { "Episode $episode" }
                val poster = anchor.selectFirst("img")?.bestImageUrl()?.normalizeUrl(href)
                val description = anchor.select("p")
                    .map { it.text().trim() }
                    .firstOrNull { it.isNotBlank() && it != name && !it.startsWith("S$season") }

                newEpisode(href) {
                    this.name = name
                    this.season = season
                    this.episode = episode
                    this.posterUrl = poster
                    this.description = description
                }
            }
            .distinctBy { it.data }
            .sortedWith(compareBy<Episode> { it.season ?: 0 }.thenBy { it.episode ?: 0 })
    }

    private fun Document.findSchema(type: String): JSONObject? {
        for (script in select("script[type='application/ld+json']")) {
            val raw = script.data().ifBlank { script.html() }.trim()
            if (raw.isBlank()) continue
            val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            if (parsed.optString("@type").equals(type, ignoreCase = true)) return parsed

            val graph = parsed.optJSONArray("@graph") ?: continue
            for (index in 0 until graph.length()) {
                val item = graph.optJSONObject(index) ?: continue
                if (item.optString("@type").equals(type, ignoreCase = true)) return item
            }
        }
        return null
    }

    private fun Document.metaContent(key: String): String {
        return selectFirst("meta[name='$key'], meta[property='$key']")
            ?.attr("content")
            ?.trim()
            .orEmpty()
    }

    private fun Element.bestImageUrl(): String {
        val attributes = listOf("src", "data-src", "data-lazy-src", "data-original")
        attributes.firstNotNullOfOrNull { attribute ->
            attr(attribute).trim().takeIf { it.isNotBlank() && !it.startsWith("data:") }
        }?.let { return it }

        return attr("srcset")
            .split(',')
            .map { it.trim().substringBefore(' ') }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun JSONObject?.readStringList(key: String): List<String> {
        if (this == null || !has(key)) return emptyList()
        return when (val value = opt(key)) {
            is JSONArray -> (0 until value.length()).mapNotNull { index ->
                value.optString(index).trim().takeIf { it.isNotBlank() }
            }
            is String -> listOf(value.trim()).filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun JSONObject?.readPeople(key: String): List<String> {
        if (this == null || !has(key)) return emptyList()
        return when (val value = opt(key)) {
            is JSONArray -> (0 until value.length()).mapNotNull { index ->
                value.optJSONObject(index)?.optString("name")?.trim()?.takeIf { it.isNotBlank() }
            }
            is JSONObject -> listOfNotNull(value.optString("name").trim().takeIf { it.isNotBlank() })
            else -> emptyList()
        }
    }

    private fun String.toDurationMinutes(): Int? {
        val match = ISO_DURATION.matchEntire(this) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        return (hours * 60 + minutes).takeIf { it > 0 }
    }

    private fun Document.extractPlayerUrls(baseUrl: String): Set<String> {
        val urls = linkedSetOf<String>()
        select("video[src], source[src], iframe[src], iframe[data-src], iframe[data-lazy-src]")
            .forEach { element ->
                val raw = element.attr("src").ifBlank {
                    element.attr("data-src").ifBlank { element.attr("data-lazy-src") }
                }
                if (raw.isNotBlank()) urls += raw.normalizeUrl(baseUrl)
            }

        val scripts = select("script").joinToString("\n") { script ->
            script.data().ifBlank { script.html() }
        }
        urls += extractUrlsFromText(scripts, baseUrl)
        return urls
    }

    private fun extractUrlsFromText(text: String, baseUrl: String): Set<String> {
        val urls = linkedSetOf<String>()
        KEYED_URL.findAll(text).forEach { match ->
            urls += match.groupValues[1].cleanEscapes().normalizeUrl(baseUrl)
        }
        DIRECT_MEDIA_URL.findAll(text).forEach { match ->
            urls += match.value.cleanEscapes().normalizeUrl(baseUrl)
        }
        return urls
    }

    private fun String.normalizeUrl(baseUrl: String): String {
        val value = cleanEscapes().trim()
        if (value.isBlank()) return ""
        return runCatching {
            when {
                value.startsWith("//") -> "https:$value"
                URI(value).isAbsolute -> value
                else -> URI(baseUrl).resolve(value).toString()
            }
        }.getOrDefault(value)
    }

    private fun String.cleanEscapes(): String {
        return replace("\\u0026", "&", ignoreCase = true)
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .trim('"', '\'', ' ', '\n', '\r', '\t')
    }

    private fun String.isDirectMedia(): Boolean {
        val lowered = lowercase()
        return lowered.contains(".m3u8") || lowered.contains(".mp4") ||
            lowered.contains(".mkv") || lowered.contains("/hls/")
    }

    private fun String.isAllowedPlayerUrl(): Boolean {
        if (!startsWith("http", ignoreCase = true)) return false
        val lowered = lowercase()
        if (BLOCKED_URL_PARTS.any { lowered.contains(it) }) return false
        val path = lowered.substringBefore('?').substringBefore('#')
        if (STATIC_ASSET_EXTENSIONS.any { path.endsWith(it) }) return false
        return isDirectMedia() || PLAYER_URL_HINTS.any { lowered.contains(it) }
    }

    private data class CapturedMedia(
        val url: String,
        val headers: Map<String, String>,
    )

    companion object {
        private const val WEBVIEW_TIMEOUT_MS = 20_000L

        private val UNSAFE_FORWARD_HEADERS = setOf(
            "host", "connection", "content-length", "accept-encoding",
        )
        private val DIRECT_MEDIA_REQUEST = Regex(
            """(?i)^https?://.+(?:\.m3u8|\.mp4|\.mkv)(?:[?#].*)?$""",
        )
        private val CONTENT_PATH = Regex("^/(movies|tv)/[^/]+/?$")
        private val EPISODE_PATH = Regex("/season-(\\d+)/episode-(\\d+)", RegexOption.IGNORE_CASE)
        private val ISO_DURATION = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?", RegexOption.IGNORE_CASE)
        private val KEYED_URL = Regex(
            """(?i)(?:file|src|source|stream|streamUrl|videoUrl|embedUrl|playbackUrl)[\"']?\s*[:=]\s*[\"'](https?:\\?/\\?/[^\"']+)[\"']""",
        )
        private val DIRECT_MEDIA_URL = Regex(
            """https?:\\?/\\?/[^\"'<>\s]+?(?:\.m3u8|\.mp4|\.mkv)(?:\?[^\"'<>\s]*)?""",
            RegexOption.IGNORE_CASE,
        )
        private val PLAYER_CLICK_SCRIPT = """
            (() => {
                try {
                    if (!window.__csRebahinPrepared) {
                        window.__csRebahinPrepared = true;
                        window.open = () => null;
                        document.addEventListener('click', event => {
                            const anchor = event.target?.closest?.('a[href]');
                            if (!anchor) return;
                            const target = new URL(anchor.href, location.href);
                            if (target.origin !== location.origin) {
                                event.preventDefault();
                                event.stopImmediatePropagation();
                            }
                        }, true);
                    }

                    const video = document.querySelector('video');
                    if (video?.src) return video.src;

                    if (!window.__csRebahinClickTimer) {
                        window.__csRebahinClickTimer = setInterval(() => {
                            const currentVideo = document.querySelector('video');
                            if (currentVideo?.src) {
                                clearInterval(window.__csRebahinClickTimer);
                                window.__csRebahinClickTimer = null;
                                return;
                            }

                            const play = document.querySelector('[role="button"][aria-label="Play"]')
                                || document.querySelector('button[aria-label="Play"]');
                            play?.click();
                        }, 500);
                    }
                    return '';
                } catch (_) {
                    return '';
                }
            })();
        """.trimIndent()

        private val PLAYER_URL_HINTS = listOf(
            "/embed/", "/e/", "/v/", "/player/", "streamwish", "filemoon", "vidhide",
            "abyss", "seekplayer", "playcinematic", "morencius", "gofile",
        )
        private val BLOCKED_URL_PARTS = listOf(
            "advertisement.", "histats", "dtscout", "doubleclick", "googletagmanager",
            "google-analytics", "facebook.com", "youtube.com", "youtu.be", "/login",
            "orangarab.fun", "goid.space", "menujupenta.site", "kegz.site", "bergurukecina.fun",
            "image.tmdb.org", "iphone17.b-cdn.net",
        )
        private val STATIC_ASSET_EXTENSIONS = listOf(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg", ".ico",
            ".css", ".woff", ".woff2", ".ttf", ".otf",
        )
    }
}
