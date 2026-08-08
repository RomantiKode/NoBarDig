package com.agooseangsa.DrakorKita

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

object DrakorKitaResolver {
    data class ApiPayload(
        val detailUrl: String,
        val title: String,
        val movieId: String,
        val episodeId: String,
        val serverXid: String,
        val category: String,
        val language: String,
        val c: String,
        val t: String,
        val cApiHost: String,
        val isMob: String,
        val isUc: String,
        val mediaType: String,
        val servers: String,
    ) {
        fun toPayloadJson(): String = JSONObject().apply {
            put("detailUrl", detailUrl)
            put("title", title)
            put("movieId", movieId)
            put("episodeId", episodeId)
            put("serverXid", serverXid)
            put("category", category)
            put("language", language)
            put("c", c)
            put("t", t)
            put("cApiHost", cApiHost)
            put("isMob", isMob)
            put("isUc", isUc)
            put("mediaType", mediaType)
            put("servers", servers)
        }.toString()
    }

    fun parsePayload(data: String): ApiPayload? {
        val trimmed = data.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val json = JSONObject(trimmed)
            ApiPayload(
                detailUrl = json.optString("detailUrl"),
                title = json.optString("title"),
                movieId = json.optString("movieId"),
                episodeId = json.optString("episodeId"),
                serverXid = json.optString("serverXid"),
                category = json.optString("category"),
                language = json.optString("language"),
                c = json.optString("c"),
                t = json.optString("t"),
                cApiHost = json.optString("cApiHost"),
                isMob = json.optString("isMob", "0"),
                isUc = json.optString("isUc", "0"),
                mediaType = json.optString("mediaType", "web"),
                servers = json.optString("servers"),
            )
        }.getOrNull()
    }

    fun normalizeUrl(url: String, mainUrl: String): String {
        val value = url.trim()
            .removeSurrounding("\"")
            .replace("\\/", "/")
            .replace("&amp;", "&")

        if (value.isBlank() ||
            value.startsWith("javascript:", ignoreCase = true) ||
            value.startsWith("data:", ignoreCase = true) ||
            value.startsWith("#")
        ) return ""

        return runCatching {
            when {
                value.startsWith("//") -> "https:$value"
                value.startsWith("http://") || value.startsWith("https://") -> value
                value.startsWith("/") -> mainUrl.trimEnd('/') + value
                else -> URI("${mainUrl.trimEnd('/')}/").resolve(value).toString()
            }
        }.getOrDefault("")
    }

    /** Parses player containers only; page JavaScript and ad onclick handlers are never executed. */
    fun extractEmbedCandidates(document: Document, mainUrl: String): List<String> {
        val candidates = linkedSetOf<String>()

        fun add(raw: String, trustedPlayerElement: Boolean = false) {
            val fixed = normalizeUrl(raw, mainUrl)
            if (isSafeCandidate(fixed) && (trustedPlayerElement || isLikelyVideoOrEmbed(fixed))) {
                candidates += fixed
            }
        }

        document.select(
            "#ploader iframe[src], .embed-player iframe[src], .apicodes-container iframe[src], " +
                ".single-content.video iframe[src], #server_lists iframe[src], main video[src], main source[src]"
        ).forEach { element ->
            add(element.attr("src").ifBlank { element.attr("data-src") }, true)
        }

        document.select(
            "#server_lists [data-src], #server_lists [data-url], #server_lists [data-video], " +
                ".btn-sv[data-src], .btn-sv[data-url], .btn-sv[data-video]"
        ).forEach { element ->
            listOf("data-src", "data-url", "data-video", "data-link", "data-embed")
                .forEach { attribute -> add(element.attr(attribute), true) }
        }

        document.select("main option[value], #sidebar_left option[value]").forEach { option ->
            val raw = option.attr("value")
            add(raw)
            decodeBase64(raw)?.let { decoded ->
                val decodedDocument = Jsoup.parse(decoded)
                decodedDocument.select("iframe[src], video[src], source[src], a[href]").forEach { element ->
                    add(element.attr("src").ifBlank { element.attr("href") }, true)
                }
                extractUrlsFromText(decoded, mainUrl).forEach { add(it) }
            }
        }

        extractUnpackedUrls(document, mainUrl).forEach { add(it) }

        document.select("script:not([src])").forEach { script ->
            val text = script.data().ifBlank { script.html() }
            if (KNOWN_VIDEO_MARKERS.any { text.contains(it, ignoreCase = true) } ||
                text.contains(".m3u8", ignoreCase = true) ||
                text.contains(".mp4", ignoreCase = true)
            ) {
                extractUrlsFromText(text, mainUrl).forEach { add(it) }
            }
        }

        return candidates.take(MAX_CANDIDATES)
    }

    suspend fun extractSubtitles(document: Document, mainUrl: String): List<SubtitleFile> {
        return document.select("track[src], a[href$=.srt], a[href$=.vtt]")
            .mapNotNull { element ->
                val url = normalizeUrl(element.attr("src").ifBlank { element.attr("href") }, mainUrl)
                if (!isSafeCandidate(url)) return@mapNotNull null
                val label = element.attr("srclang")
                    .ifBlank { element.attr("label") }
                    .ifBlank { element.text() }
                    .ifBlank { "Indonesia" }
                newSubtitleFile(label, url)
            }
            .distinctBy { it.url }
    }

    suspend fun resolveApiPlayback(
        providerName: String,
        mainUrl: String,
        payload: ApiPayload,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val candidates = linkedSetOf<String>()
        val detailOrigin = originOf(payload.detailUrl, mainUrl)
        val candidateApiHosts = linkedSetOf<String>().apply {
            payload.cApiHost.takeIf { it.isNotBlank() }?.trimEnd('/')?.let(::add)
            add("$detailOrigin/c_api")
            add("${mainUrl.trimEnd('/')}/c_api")
            add(DEFAULT_C_API)
        }

        for (cApiHost in candidateApiHosts) {
            var episodeId = payload.episodeId
            var serverXid = payload.serverXid.ifBlank { "f1" }
            var category = payload.category
            var language = payload.language

            // Historical fallback for pages that expose only initEpisodeList(movieId, cat, lang).
            if (episodeId.isBlank() && payload.movieId.isNotBlank()) {
                val episodeJson = apiGetJson(
                    url = "${cApiHost.trimEnd('/')}/episode_mob.php" +
                        "?is_mob=${encode(payload.isMob)}" +
                        "&is_uc=${encode(payload.isUc)}" +
                        "&movie_id=${encode(payload.movieId)}" +
                        "&tag=${encode(category)}" +
                        "&c=${encode(payload.c)}" +
                        "&t=${encode(payload.t)}" +
                        "&ver=${encode(language)}",
                    referer = payload.detailUrl,
                )
                episodeId = episodeJson?.optString("first_ep_id").orEmpty()
                serverXid = episodeJson?.optString("server_xid").orEmpty().ifBlank { serverXid }
                category = episodeJson?.optString("tag").orEmpty().ifBlank { category }
                language = episodeJson?.optString("ver").orEmpty().ifBlank { language }
            }

            if (episodeId.isBlank()) continue

            val serverSpecs = parseServerSpecs(payload.servers).ifEmpty { DEFAULT_SERVER_SPECS }
            serverSpecs.forEach { spec ->
                val endpoint = when (spec.kind) {
                    "p2p" -> "video_p2p.php" to "p2p_url"
                    "hydrax" -> "video_hydrax.php" to "hydrax_url"
                    "sb" -> "video_sb.php" to "sb_url"
                    else -> null
                } ?: return@forEach

                val json = apiGetJson(
                    url = buildVideoEndpoint(
                        cApiHost = cApiHost,
                        endpoint = endpoint.first,
                        payload = payload,
                        episodeId = episodeId,
                        serverXid = serverXid,
                        category = category,
                        language = language,
                        quality = spec.quality,
                    ),
                    referer = payload.detailUrl,
                )
                addApiResponseCandidates(candidates, json, endpoint.second, mainUrl)
            }

            // Generic endpoint is a cheap compatibility fallback used by older mirrors.
            val genericJson = apiGetJson(
                url = buildVideoEndpoint(
                    cApiHost = cApiHost,
                    endpoint = "video.php",
                    payload = payload,
                    episodeId = episodeId,
                    serverXid = serverXid,
                    category = category,
                    language = language,
                    quality = serverSpecs.firstOrNull()?.quality ?: "720",
                ),
                referer = payload.detailUrl,
            )
            genericJson?.let { json ->
                listOf("file", "source", "video", "hls", "url", "download")
                    .forEach { field -> addApiCandidate(candidates, json.optString(field), mainUrl) }
                json.optJSONObject("dl")?.let { downloads ->
                    downloads.keys().forEach { key -> addApiCandidate(candidates, downloads.optString(key), mainUrl) }
                }
            }

            if (candidates.isNotEmpty()) break
        }

        if (candidates.isEmpty()) return false
        return resolveCandidates(
            providerName = providerName,
            mainUrl = mainUrl,
            pageUrl = payload.detailUrl,
            candidates = candidates.toList(),
            subtitleCallback = subtitleCallback,
            callback = callback,
        )
    }

    suspend fun resolveCandidates(
        providerName: String,
        mainUrl: String,
        pageUrl: String,
        candidates: List<String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val seen = linkedSetOf<String>()
        var handled = false

        suspend fun resolve(link: String, referer: String, depth: Int) {
            val fixed = normalizeUrl(link, mainUrl)
            if (depth > MAX_DEPTH || !isSafeCandidate(fixed) || !seen.add(fixed)) return

            when {
                fixed.contains(".m3u8", ignoreCase = true) -> {
                    callback(
                        newExtractorLink(providerName, providerName, fixed, ExtractorLinkType.M3U8) {
                            this.referer = referer
                            this.quality = parseQuality(fixed)
                        }
                    )
                    handled = true
                }

                fixed.contains(".mp4", ignoreCase = true) -> {
                    callback(
                        newExtractorLink(providerName, providerName, fixed, ExtractorLinkType.VIDEO) {
                            this.referer = referer
                            this.quality = parseQuality(fixed)
                        }
                    )
                    handled = true
                }

                else -> {
                    val extractorHandled = runCatching {
                        loadExtractor(fixed, referer, subtitleCallback, callback)
                    }.getOrDefault(false)
                    if (extractorHandled) handled = true

                    if (!extractorHandled && depth < MAX_DEPTH && shouldScanNestedPage(fixed)) {
                        runCatching {
                            val response = app.get(
                                url = fixed,
                                headers = pageHeaders(),
                                referer = referer,
                            )
                            if (!response.isSuccessful || isUnavailable(response.text)) return@runCatching

                            val nestedDocument = response.document
                            extractSubtitles(nestedDocument, mainUrl).forEach(subtitleCallback)
                            extractEmbedCandidates(nestedDocument, mainUrl).forEach { nested ->
                                resolve(nested, fixed, depth + 1)
                            }
                        }
                    }
                }
            }
        }

        candidates.take(MAX_CANDIDATES).forEach { resolve(it, pageUrl, 0) }
        return handled
    }

    private fun buildVideoEndpoint(
        cApiHost: String,
        endpoint: String,
        payload: ApiPayload,
        episodeId: String,
        serverXid: String,
        category: String,
        language: String,
        quality: String,
    ): String {
        return "${cApiHost.trimEnd('/')}/$endpoint" +
            "?is_mob=${encode(payload.isMob)}" +
            "&is_uc=${encode(payload.isUc)}" +
            "&id=${encode(episodeId)}" +
            "&type=${encode(payload.mediaType.ifBlank { "web" })}" +
            "&qua=${encode(quality)}" +
            "&server_id=${encode(serverXid)}" +
            "&cat=${encode(category)}" +
            "&tag=${encode(language)}" +
            "&c=${encode(payload.c)}" +
            "&t=${encode(payload.t)}"
    }

    private fun addApiResponseCandidates(
        target: MutableSet<String>,
        json: JSONObject?,
        preferredField: String,
        mainUrl: String,
    ) {
        if (json == null) return
        listOf(preferredField, "url", "src", "file", "source", "video", "hls", "embed")
            .distinct()
            .forEach { field -> addApiCandidate(target, json.optString(field), mainUrl) }

        extractUrlsFromText(json.toString(), mainUrl)
            .filter(::isLikelyVideoOrEmbed)
            .forEach { addApiCandidate(target, it, mainUrl) }
    }

    private fun addApiCandidate(target: MutableSet<String>, rawUrl: String, mainUrl: String) {
        val normalized = normalizeUrl(rawUrl, mainUrl)
        if (isSafeCandidate(normalized)) target += normalized
    }

    private fun parseServerSpecs(raw: String): List<ServerSpec> {
        return raw.split('|')
            .mapNotNull { item ->
                val parts = item.split(':', limit = 2)
                val kind = parts.getOrNull(0)?.trim()?.lowercase().orEmpty()
                val quality = parts.getOrNull(1)?.trim().orEmpty()
                if (kind in setOf("p2p", "hydrax", "sb") && quality.isNotBlank()) {
                    ServerSpec(kind, quality)
                } else null
            }
            .distinct()
    }

    private fun isSafeCandidate(url: String): Boolean {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) return false
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank()) return false
        return AD_HOST_MARKERS.none { host.contains(it) }
    }

    private fun isLikelyVideoOrEmbed(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains(".m3u8") || lower.contains(".mp4")) return true
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (KNOWN_VIDEO_MARKERS.any { host.contains(it) }) return true
        return listOf("/embed", "/e/", "/player", "/stream", "/watch", "/video")
            .any { lower.contains(it) }
    }

    private fun shouldScanNestedPage(url: String): Boolean {
        if (!isSafeCandidate(url)) return false
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return NESTED_SCAN_HOSTS.any { host.contains(it) }
    }

    private fun decodeBase64(value: String): String? {
        val normalized = value.trim()
        if (normalized.isBlank()) return null
        val padded = normalized.padEnd(normalized.length + ((4 - normalized.length % 4) % 4), '=')
        return runCatching { String(Base64.getDecoder().decode(padded)) }
            .getOrElse { runCatching { String(Base64.getUrlDecoder().decode(padded)) }.getOrNull() }
    }

    private suspend fun apiGetJson(url: String, referer: String): JSONObject? {
        return runCatching {
            val response = app.get(
                url = url,
                headers = ajaxHeaders(originOf(referer, url)),
                referer = referer,
            )
            if (!response.isSuccessful) return@runCatching null
            JSONObject(response.text)
        }.getOrNull()
    }

    private fun ajaxHeaders(siteOrigin: String): Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "application/json,text/plain,*/*",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "Origin" to siteOrigin,
        "X-Requested-With" to "XMLHttpRequest",
    )

    private fun pageHeaders(): Map<String, String> = mapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
    )

    private fun extractUnpackedUrls(document: Document, mainUrl: String): List<String> {
        val results = linkedSetOf<String>()
        document.select("script:not([src])").forEach { script ->
            val text = script.data().ifBlank { script.html() }
            if (!text.contains("function(p,a,c,k,e,d)")) return@forEach
            runCatching { getAndUnpack(text) }.getOrNull()?.let { unpacked ->
                extractUrlsFromText(unpacked, mainUrl).forEach(results::add)
                buildDqtHlsCandidates(unpacked).forEach(results::add)
            }
        }
        return results.filter(::isSafeCandidate)
    }

    private fun buildDqtHlsCandidates(unpacked: String): List<String> {
        Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            .find(unpacked)?.value?.replace("\\/", "/")?.let { return listOf(it) }

        val tokens = Regex("""['\"]([A-Za-z0-9_-]{8,})['\"]""")
            .findAll(unpacked).map { it.groupValues[1] }.toList()
        val streamId = tokens.firstOrNull { it.length >= 20 }
        val folder = tokens.firstOrNull { it.length >= 12 && it != streamId }
        val expires = Regex("""\b(17\d{8,})\b""").find(unpacked)?.groupValues?.getOrNull(1)
        val fileId = Regex("""file_code['\"]?\s*[:=]\s*['\"]?([A-Za-z0-9_-]+)""")
            .find(unpacked)?.groupValues?.getOrNull(1)

        if (streamId.isNullOrBlank() || folder.isNullOrBlank() || expires.isNullOrBlank() || fileId.isNullOrBlank()) {
            return emptyList()
        }
        return listOf("https://dqt.my.id/stream/$streamId/$folder/$expires/$fileId/master.m3u8")
    }

    private fun extractUrlsFromText(text: String, mainUrl: String): List<String> {
        val normalized = text.replace("\\/", "/").replace("&amp;", "&")
        return Regex("""https?://[^'"\\\s<>]+|//[^'"\s<>]+""")
            .findAll(normalized)
            .map { match ->
                normalizeUrl(match.value, mainUrl).trimEnd(',', '.', ';', ')', ']', '}')
            }
            .filter(::isSafeCandidate)
            .distinct()
            .take(MAX_CANDIDATES)
            .toList()
    }

    private fun isUnavailable(body: String): Boolean {
        return listOf(
            "Video not found",
            "expired or has been deleted",
            "File is no longer available",
        ).any { body.contains(it, ignoreCase = true) }
    }

    private fun parseQuality(url: String): Int {
        return Regex("""(2160|1440|1080|720|480|360|240)p""", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private fun originOf(url: String, fallback: String): String {
        return runCatching {
            val uri = URI(url)
            if (uri.scheme.isNullOrBlank() || uri.rawAuthority.isNullOrBlank()) {
                val fallbackUri = URI(fallback)
                "${fallbackUri.scheme}://${fallbackUri.rawAuthority}"
            } else {
                "${uri.scheme}://${uri.rawAuthority}"
            }
        }.getOrDefault("https://drakor.kita.mobi")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private data class ServerSpec(val kind: String, val quality: String)

    private const val DEFAULT_C_API = "https://api.nonton.bid/c_api"
    private const val MAX_CANDIDATES = 32
    private const val MAX_DEPTH = 2
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val DEFAULT_SERVER_SPECS = listOf(
        ServerSpec("p2p", "720"),
        ServerSpec("p2p", "480"),
        ServerSpec("hydrax", "1080"),
        ServerSpec("hydrax", "720"),
        ServerSpec("hydrax", "480"),
    )

    private val AD_HOST_MARKERS = listOf(
        "dtscout",
        "histats",
        "madurird",
        "onenessparmack",
        "poptrkflow",
        "unspanrepunch",
        "doubleclick",
        "googlesyndication",
        "adservice",
        "propellerads",
    )

    private val KNOWN_VIDEO_MARKERS = listOf(
        "drakorkita.stream",
        "abysscdn",
        "dqt.my.id",
        "handal.bid",
        "streamsb",
        "sbembed",
        "strp2p",
        "p2pstream",
        "upn.one",
        "uyeshare",
        "filelions",
        "streamwish",
        "vidhide",
        "streamtape",
        "dood",
        "mixdrop",
        "mp4upload",
    )

    private val NESTED_SCAN_HOSTS = listOf(
        "drakorkita.stream",
        "abysscdn",
        "dqt.my.id",
        "handal.bid",
        "strp2p",
        "p2pstream",
        "upn.one",
        "uyeshare",
    )
}
