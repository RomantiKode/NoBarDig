package com.layarkaca21

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Resolves the intermediary player used by current LK21 movie and series pages.
 *
 * The source page exposes URLs such as:
 *   https://videonode.de/iframe/hydrax/{id}
 *   https://videonode.de/iframe/p2p/{token}
 *
 * This extractor does not execute site JavaScript. It follows HTTP redirects and
 * reads narrowly scoped iframe/source/file/src/url values from returned HTML.
 */
class Lk21VideoNode : ExtractorApi() {
    override val name = "VideoNode"
    override val mainUrl = "https://videonode.de"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val emitted = linkedSetOf<String>()
        val visited = linkedSetOf<String>()
        val safeCallback: (ExtractorLink) -> Unit = { link ->
            if (isSafeUrl(link.url) && emitted.add(link.url)) callback(link)
        }

        resolveNode(
            url = url,
            referer = referer ?: SERIES_REFERER,
            depth = 0,
            visited = visited,
            emitted = emitted,
            subtitleCallback = subtitleCallback,
            callback = safeCallback,
        )

        // Compatibility fallback for the server labels found in the current
        // player list. These are attempted only when the VideoNode response did
        // not expose a destination, keeping normal extraction lightweight.
        if (emitted.isEmpty()) {
            for (candidate in compatibilityCandidates(url)) {
                val before = emitted.size
                loadExtractor(candidate, referer ?: url, subtitleCallback, safeCallback)
                if (emitted.size > before) break
            }
        }
    }

    private suspend fun resolveNode(
        url: String,
        referer: String,
        depth: Int,
        visited: MutableSet<String>,
        emitted: Set<String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (depth > MAX_DEPTH || !visited.add(url) || !isSafeUrl(url)) return

        val referers = listOf(
            referer,
            originOf(referer),
            SERIES_REFERER,
            MOVIE_REFERER,
        ).filter { it.isNotBlank() }.distinct()

        for (requestReferer in referers.take(3)) {
            // Start with the same plain iframe navigation used by the active provider.
            // Some VideoNode routes reject the extra browser headers even when the
            // simple referer-only request succeeds.
            val plainResponse = runCatching {
                app.get(url, referer = requestReferer, allowRedirects = true)
            }.getOrNull()
            val browserResponse = runCatching {
                app.get(
                    url,
                    referer = requestReferer,
                    headers = PLAYER_HEADERS,
                    allowRedirects = true,
                )
            }.getOrNull()

            val responses = listOfNotNull(plainResponse, browserResponse)
                .distinctBy { it.url to it.text.hashCode() }

            for (response in responses) {
            if (response.url != url && isSafeUrl(response.url)) {
                val before = emitted.size
                dispatchCandidate(
                    candidate = response.url,
                    referer = url,
                    depth = depth + 1,
                    visited = visited,
                    emitted = emitted,
                    subtitleCallback = subtitleCallback,
                    callback = callback,
                )
                if (emitted.size > before) return
            }

            val document = response.document.cleanNodePage()
            emitSubtitles(document, response.url, subtitleCallback)

            val scriptTexts = document.select("script:not([src])").flatMap { script ->
                val raw = script.data()
                listOf(raw, getAndUnpack(raw))
            }.distinct()

            val candidates = linkedSetOf<String>()
            document.select(
                "iframe[src], video[src], video source[src], source[src], " +
                    "[data-url], [data-src], meta[http-equiv=refresh]"
            ).forEach { element ->
                when {
                    element.tagName().equals("meta", ignoreCase = true) -> {
                        REFRESH_URL_REGEX.find(element.attr("content"))
                            ?.groupValues?.getOrNull(1)?.let(candidates::add)
                    }
                    else -> {
                        listOf("src", "data-url", "data-src")
                            .firstNotNullOfOrNull { key -> element.attr(key).takeIf { it.isNotBlank() } }
                            ?.let(candidates::add)
                    }
                }
            }

            for (text in scriptTexts) {
                MEDIA_REGEX.findAll(text).forEach { candidates += it.value }
                URL_ASSIGNMENT_REGEX.findAll(text).forEach { candidates += it.groupValues[1] }
                LOCATION_REGEX.findAll(text).forEach { candidates += it.groupValues[1] }
                ATOB_REGEX.findAll(text).forEach { match ->
                    decodeBase64Url(match.groupValues[1])?.let(candidates::add)
                }
            }

            for (rawCandidate in candidates) {
                val candidate = absoluteUrl(response.url, decodeEscapedUrl(rawCandidate)) ?: continue
                val before = emitted.size
                dispatchCandidate(
                    candidate = candidate,
                    referer = response.url,
                    depth = depth + 1,
                    visited = visited,
                    emitted = emitted,
                    subtitleCallback = subtitleCallback,
                    callback = callback,
                )
                if (emitted.size > before && isDirectMedia(candidate)) continue
            }

            if (emitted.isNotEmpty()) return
            }
        }
    }

    private suspend fun dispatchCandidate(
        candidate: String,
        referer: String,
        depth: Int,
        visited: MutableSet<String>,
        emitted: Set<String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (!isSafeUrl(candidate)) return
        if (isDirectMedia(candidate)) {
            callback(newExtractorLink(name, serverLabel(referer), candidate) {
                this.referer = referer
                this.type = if (candidate.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                this.quality = getQualityFromName(candidate)
                this.headers = mapOf("Referer" to referer, "User-Agent" to USER_AGENT)
            })
            return
        }

        if (hostOf(candidate) == hostOf(mainUrl)) {
            resolveNode(candidate, referer, depth, visited, emitted, subtitleCallback, callback)
            return
        }

        val before = emitted.size
        loadExtractor(candidate, referer, subtitleCallback, callback)
        if (emitted.size > before) return
    }

    private suspend fun emitSubtitles(
        document: Document,
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        document.select("track[kind=subtitles][src], track[kind=captions][src]").forEach { track ->
            val subtitleUrl = absoluteUrl(pageUrl, track.attr("src")) ?: return@forEach
            if (!isSafeUrl(subtitleUrl)) return@forEach
            subtitleCallback(
                newSubtitleFile(
                    track.attr("srclang").ifBlank { track.attr("label").ifBlank { "Unknown" } },
                    subtitleUrl,
                )
            )
        }
    }

    private fun compatibilityCandidates(url: String): List<String> {
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        val parts = path.trim('/').split('/')
        if (parts.size < 3 || parts[0] != "iframe") return emptyList()
        val server = parts[1].lowercase()
        val id = parts.drop(2).joinToString("/").takeIf { it.isNotBlank() } ?: return emptyList()
        return when (server) {
            "hydrax" -> listOf("https://short.ink/$id", "https://abyssplayer.com/$id")
            "turbovip" -> listOf("https://turbovidhls.com/e/$id", "https://turbovidhls.com/$id")
            "cast" -> listOf("https://co4nxtrl.com/e/$id", "https://furher.in/e/$id")
            // P2P wrapper tokens are opaque VideoNode tokens, not Hownetwork ids.
            "p2p" -> emptyList()
            else -> emptyList()
        }
    }

    private fun Document.cleanNodePage(): Document = apply {
        select(
            "#adContainer, #adsLink, #openPopup, #nativeAds, .popup, .popunder, " +
                ".ads, .advertisement, iframe[src*=doubleclick], iframe[src*=histats], script[src]"
        ).remove()
    }

    private fun absoluteUrl(base: String, value: String?): String? {
        val raw = value?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() } ?: return null
        if (
            raw.startsWith("javascript:", true) || raw.startsWith("data:", true) ||
            raw.startsWith("blob:", true) || raw.startsWith("about:", true) || raw == "#"
        ) return null
        return runCatching {
            val uri = URI(base).resolve(raw)
            if (uri.scheme !in setOf("http", "https")) null else uri.toString()
        }.getOrNull()
    }

    private fun decodeEscapedUrl(value: String): String = value
        .replace("\\/", "/")
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("&amp;", "&")
        .trim()

    private fun decodeBase64Url(value: String): String? = runCatching {
        base64Decode(value).trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }.getOrNull()

    private fun isDirectMedia(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return DIRECT_EXTENSIONS.any(clean::endsWith)
    }

    private fun isSafeUrl(url: String): Boolean {
        val host = hostOf(url)
        return host.isNotBlank() && BLOCKED_HOSTS.none { host == it || host.endsWith(".$it") }
    }

    private fun originOf(url: String): String = runCatching {
        URI(url).let { "${it.scheme}://${it.host}/" }
    }.getOrDefault("")

    private fun hostOf(url: String): String =
        runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")

    private fun serverLabel(url: String): String {
        val parts = runCatching { URI(url).path.orEmpty().trim('/').split('/') }.getOrDefault(emptyList())
        return parts.getOrNull(1)?.uppercase() ?: name
    }

    companion object {
        private const val MAX_DEPTH = 2
        private const val SERIES_REFERER = "https://tv6.nontondrama.my/"
        private const val MOVIE_REFERER = "https://tv12.lk21official.cc/"
        private val PLAYER_HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
            "Upgrade-Insecure-Requests" to "1",
        )
        private val MEDIA_REGEX = Regex(
            "https?://[^\\s\\\"'<>]+?\\.(?:m3u8|mp4|mkv|webm)(?:\\?[^\\s\\\"'<>]*)?",
            RegexOption.IGNORE_CASE,
        )
        private val URL_ASSIGNMENT_REGEX = Regex(
            """(?:file|src|url|source)\s*[:=]\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
        private val LOCATION_REGEX = Regex(
            """(?:window\.)?(?:document\.)?location(?:\.href)?\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
        private val ATOB_REGEX = Regex(
            """atob\(["']([A-Za-z0-9+/=_-]+)["']\)""",
            RegexOption.IGNORE_CASE,
        )
        private val REFRESH_URL_REGEX = Regex("""url\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
        private val DIRECT_EXTENSIONS = setOf(".m3u8", ".mp4", ".mkv", ".webm")
        private val BLOCKED_HOSTS = setOf(
            "donasi.showcdnx.com", "s.id", "histats.com", "sstatic1.histats.com",
            "googletagmanager.com", "google-analytics.com", "doubleclick.net",
            "facebook.com", "instagram.com", "x.com", "youtube.com", "youtu.be",
        )
    }
}

open class Lk21Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://stream.hownetwork.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = url.substringAfter("id=", "").substringBefore('&')
        if (id.isBlank()) return

        val host = runCatching { URI(mainUrl).host.orEmpty() }.getOrDefault("")
        if (host.isBlank()) return
        val encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8.toString())
        val browserUserAgent =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
        val headers = mapOf(
            "User-Agent" to browserUserAgent,
            "Referer" to url,
            "Origin" to mainUrl,
            "X-Requested-With" to "XMLHttpRequest",
            "Accept" to "application/json, text/javascript, */*; q=0.01",
        )

        // Current deployments use api2.php with a single object, while older
        // Hownetwork variants used api.php and wrapped the sources in `data`.
        val requestBodies = listOf(
            mapOf("r" to "https://playeriframe.sbs/", "d" to host),
            mapOf("r" to "https://playeriframe.sbs/", "d" to "cloud.hownetwork.xyz"),
            mapOf("r" to "https://playeriframe.sbs/", "d" to "stream.hownetwork.xyz"),
            mapOf("r" to (referer ?: "https://playeriframe.sbs/"), "d" to host),
        ).distinct()
        val endpoints = listOf("api2.php", "api.php")

        for (endpoint in endpoints) {
            for (body in requestBodies) {
                val apiResponse = runCatching {
                    app.post(
                        "$mainUrl/$endpoint?id=$encodedId",
                        data = body,
                        referer = url,
                        headers = headers,
                    )
                }.getOrNull() ?: continue

                val sources = buildList {
                    apiResponse.parsedSafe<HownetworkResponse>()?.let(::add)
                    addAll(apiResponse.parsedSafe<HownetworkEnvelope>()?.data.orEmpty())
                    addAll(apiResponse.parsedSafe<List<HownetworkResponse>>().orEmpty())
                }.distinctBy { (it.file ?: it.link).orEmpty() }

                var emitted = false
                for (source in sources) {
                    val file = (source.file ?: source.link)
                        ?.trim()
                        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                        ?: continue
                    callback(newExtractorLink(name, source.label?.takeIf { it.isNotBlank() } ?: name, file) {
                        this.referer = mainUrl
                        this.type = if (file.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        this.quality = getQualityFromName(source.label)
                            .takeIf { it != Qualities.Unknown.value }
                            ?: getQualityFromName(file)
                        this.headers = mapOf(
                            "User-Agent" to browserUserAgent,
                            "Referer" to mainUrl,
                            "Origin" to mainUrl,
                        )
                    })
                    emitted = true
                }
                if (emitted) return
            }
        }
    }

    private data class HownetworkResponse(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    private data class HownetworkEnvelope(
        @JsonProperty("data") val data: List<HownetworkResponse>? = null,
    )
}

class Lk21CloudHownetwork : Lk21Hownetwork() {
    override val mainUrl = "https://cloud.hownetwork.xyz"
}

class Lk21Co4nxtrl : Filesim() {
    override val mainUrl = "https://co4nxtrl.com"
    override val name = "Co4nxtrl"
    override val requiresReferer = true
}

class Lk21Furher : Filesim() {
    override val mainUrl = "https://furher.in"
    override val name = "Furher"
}

class Lk21FurherAlt : Filesim() {
    override val mainUrl = "https://723qrh1p.fun"
    override val name = "Furher Alt"
}

class Lk21Turbovidhls : Filesim() {
    override val mainUrl = "https://turbovidhls.com"
    override val name = "Turbovidhls"
}

open class Lk21Abyss : ExtractorApi() {
    override val name = "Abyss"
    override val mainUrl = "https://abyssplayer.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val path = url.substringAfter("://").substringAfter('/')
        if (path.isBlank()) return
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0"
        val headers = mapOf("User-Agent" to userAgent, "Referer" to mainUrl)
        val first = app.get("$mainUrl/$path", headers = headers, allowRedirects = false)
        val target = first.headers["location"] ?: first.headers["Location"] ?: "$mainUrl/$path"
        val html = app.get(target, headers = headers).text
        val encrypted = Regex("""const\s+datas\s*=\s*\"([^\"]+)\"""").find(html)?.groupValues?.getOrNull(1) ?: return
        val payload = """{"text":"$encrypted","agent":"$userAgent"}"""
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val decoded = app.post(
            "https://enc-dec.app/api/dec-abyss",
            requestBody = payload,
            headers = mapOf(
                "Content-Type" to "application/json",
                "Origin" to "https://enc-dec.app",
                "User-Agent" to userAgent,
            ),
        ).parsedSafe<AbyssResponse>() ?: return
        for (source in decoded.result?.sources.orEmpty()) {
            val media = source.url?.takeIf { it.startsWith("http") } ?: continue
            callback(newExtractorLink(name, name, media) {
                this.referer = "https://playhydrax.com"
                this.type = if (media.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                this.quality = getQualityFromName(source.type)
                this.headers = mapOf("User-Agent" to userAgent, "Referer" to "https://playhydrax.com")
            })
        }
    }

    private data class AbyssResponse(@JsonProperty("result") val result: AbyssResult? = null)
    private data class AbyssResult(@JsonProperty("sources") val sources: List<AbyssSource>? = null)
    private data class AbyssSource(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("type") val type: String? = null,
    )
}

class Lk21ShortInk : Lk21Abyss() {
    override val mainUrl = "https://short.ink"
    override val name = "ShortInk"
}
