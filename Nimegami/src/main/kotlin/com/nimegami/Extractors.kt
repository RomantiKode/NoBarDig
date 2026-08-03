package com.nimegami

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.*
import java.net.URI
import java.net.URLDecoder
import org.jsoup.Jsoup

private const val NIMEGAMI_REFERER = "https://nimegami.id/"

private const val MITEDRIVE_CSRF_TOKEN =
    "ZXlKcGNDSTZJak0yTGpneExqWTFMakUyTWlJc0ltUmxkbWxqWlNJNklrMXZlbWxzYkdFdk5TNHdJQ2hYYVc1a2IzZHpJRTVVSURFd0xqQTdJRmRwYmpZME95QjROalE3SUhKMk9qRXdNUzR3S1NCSFpXTnJieTh5TURFd01ERXdNU0JHYVhKbFptOTRMekV3TVM0d0lpd2lZbkp2ZDNObGNpSTZJazF2ZW1sc2JHRWlMQ0pqYjI5cmFXVWlPaUlpTENKeVpXWmxjbkpsY2lJNklpSjk="

/**
 * Player yang dipakai Nimegami untuk URL stordl.halahgan.com.
 *
 * URL pada atribut episode adalah URL iframe, bukan URL yang aman untuk langsung
 * diasumsikan sebagai berkas MP4. Extractor ini menangani dua kemungkinan:
 * 1. endpoint mengembalikan video secara langsung; atau
 * 2. endpoint mengembalikan halaman player yang berisi URL MP4/HLS sebenarnya.
 */
open class Halahgan : ExtractorApi() {
    override val name = "Halahgan"
    override val mainUrl = "https://stordl.halahgan.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val pageReferer = referer?.takeIf { it.isNotBlank() } ?: NIMEGAMI_REFERER
        val requestHeaders =
            mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/json,video/*;q=0.9,*/*;q=0.8",
                // Membatasi respons jika endpoint ternyata merupakan video langsung.
                "Range" to "bytes=0-262143",
            )

        val headResponse =
            runCatching {
                app.head(
                    url,
                    referer = pageReferer,
                    headers = mapOf("Accept" to "*/*"),
                )
            }.getOrNull()
        val headContentType =
            headResponse?.headers?.get("Content-Type")
                ?: headResponse?.headers?.get("content-type")
                ?: ""

        if (headContentType.isDirectMediaContentType()) {
            emitDirectFallback(url, pageReferer, callback)
            return
        }

        val response =
            runCatching {
                app.get(
                    url,
                    referer = pageReferer,
                    headers = requestHeaders,
                )
            }.getOrNull()

        if (response == null) {
            emitDirectFallback(url, pageReferer, callback)
            return
        }

        val contentType =
            response.headers["Content-Type"]
                ?: response.headers["content-type"]
                ?: ""

        if (contentType.isDirectMediaContentType()) {
            emitDirectFallback(url, pageReferer, callback)
            return
        }

        val body = runCatching { response.text }.getOrNull().orEmpty()
        val mediaUrls = extractMediaUrls(body, url)

        if (mediaUrls.isEmpty()) {
            // Fallback tetap diberikan karena beberapa endpoint menyajikan video langsung
            // tetapi mengirim Content-Type yang tidak konsisten.
            emitDirectFallback(url, pageReferer, callback)
            return
        }

        mediaUrls.forEach { mediaUrl ->
            callback(
                newExtractorLink(
                    name,
                    name,
                    mediaUrl,
                    if (mediaUrl.isM3u8Url()) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                ) {
                    this.referer = url
                    this.quality = qualityFromUrl(url)
                    this.headers = mapOf("Accept" to "*/*")
                }
            )
        }
    }

    private suspend fun emitDirectFallback(
        url: String,
        referer: String,
        callback: (ExtractorLink) -> Unit,
    ) {
        callback(
            newExtractorLink(name, name, url, ExtractorLinkType.VIDEO) {
                this.referer = referer
                this.quality = qualityFromUrl(url)
                this.headers = mapOf("Accept" to "*/*")
            }
        )
    }

    private fun extractMediaUrls(body: String, baseUrl: String): List<String> {
        if (body.isBlank()) return emptyList()

        val urls = linkedSetOf<String>()
        val document = Jsoup.parse(body, baseUrl)

        document
            .select("video[src], video source[src], source[src], [data-file], [data-video]")
            .mapNotNull { element ->
                sequenceOf("abs:src", "src", "data-file", "data-video")
                    .map { element.attr(it) }
                    .firstOrNull { it.isNotBlank() }
                    ?.normalizeMediaUrl(baseUrl)
            }
            .filterTo(urls) { it.startsWith("http://") || it.startsWith("https://") }

        val patterns =
            listOf(
                Regex(
                    """(?is)(?:file|src|source|url)\s*[:=]\s*[\"']([^\"']+?(?:\.m3u8|\.mp4|\.mkv)(?:\?[^\"']*)?)[\"']"""
                ),
                Regex(
                    """(?is)[\"'](https?:\\?/\\?/[^\"']+?(?:\.m3u8|\.mp4|\.mkv)(?:\?[^\"']*)?)[\"']"""
                ),
            )

        patterns.forEach { regex ->
            regex.findAll(body).forEach { match ->
                match.groupValues
                    .getOrNull(1)
                    ?.normalizeMediaUrl(baseUrl)
                    ?.takeIf { it.isMediaUrl() }
                    ?.let(urls::add)
            }
        }

        Regex("""["']([A-Za-z0-9+/]{40,}={0,2})["']""")
            .findAll(body)
            .forEach { match ->
                runCatching { base64Decode(match.groupValues[1]) }
                    .getOrNull()
                    ?.normalizeMediaUrl(baseUrl)
                    ?.takeIf { it.isMediaUrl() }
                    ?.let(urls::add)
            }

        return urls.toList()
    }

    private fun String.normalizeMediaUrl(baseUrl: String): String? {
        val value =
            trim()
                .trim('"', '\'', ' ')
                .replace("\\/", "/")
                .replace(Regex("""(?i)\\u0026"""), "&")
                .replace("&amp;", "&")

        if (value.isBlank()) return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") || value.startsWith("https://") -> value
            else -> runCatching { URI(baseUrl).resolve(value).toString() }.getOrNull()
        }
    }

    private fun String.isDirectMediaContentType(): Boolean {
        val normalized = lowercase()
        return normalized.startsWith("video/") ||
            normalized.startsWith("audio/") ||
            normalized.contains("application/vnd.apple.mpegurl") ||
            normalized.contains("application/x-mpegurl") ||
            normalized.contains("application/octet-stream")
    }

    private fun String.isMediaUrl(): Boolean =
        Regex("""(?i)\.(?:m3u8|mp4|mkv)(?:$|[?&#])""").containsMatchIn(this)

    private fun String.isM3u8Url(): Boolean =
        Regex("""(?i)\.m3u8(?:$|[?&#])""").containsMatchIn(this)

    private fun qualityFromUrl(url: String): Int {
        val decoded = runCatching { URLDecoder.decode(url, Charsets.UTF_8.name()) }.getOrDefault(url)
        val label = Regex("""(?i)(\d{3,4})p""").find(decoded)?.groupValues?.getOrNull(1)
        return getQualityFromName(label)
    }
}

open class Mitedrive : ExtractorApi() {
    override val name = "Mitedrive"
    override val mainUrl = "https://mitedrive.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = url.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() } ?: return
        val video =
            app.post(
                "https://api.mitedrive.com/api/view/$id",
                referer = "$mainUrl/",
                data = mapOf("csrf_token" to MITEDRIVE_CSRF_TOKEN, "slug" to id),
            ).parsedSafe<Responses>()?.data?.url?.takeIf { it.isNotBlank() } ?: return

        callback(
            newExtractorLink(name, name, video) {
                this.referer = "$mainUrl/"
            }
        )
    }

    data class Data(@JsonProperty("original_url") val url: String? = null)

    data class Responses(@JsonProperty("data") val data: Data? = null)
}

open class Berkasdrive : ExtractorApi() {
    override val name = "Berkasdrive"
    override val mainUrl = "https://dl.berkasdrive.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val document = app.get(url, referer = referer ?: NIMEGAMI_REFERER).document
        val rawVideo =
            document.selectFirst("video#player source[src], video source[src]")
                ?.attr("src")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return
        val video =
            when {
                rawVideo.startsWith("//") -> "https:$rawVideo"
                rawVideo.startsWith("http://") || rawVideo.startsWith("https://") -> rawVideo
                else -> runCatching { URI(url).resolve(rawVideo).toString() }.getOrNull() ?: return
            }

        callback(
            newExtractorLink(name, name, video) {
                this.referer = url
            }
        )
    }
}

open class Videogami : ExtractorApi() {
    override val name = "Videogami"
    override val mainUrl = "https://video.nimegami.id"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val encoded =
            url.substringAfter("url=", "").substringBefore('&').takeIf { it.isNotBlank() } ?: return
        val urlDecoded = runCatching { URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrNull()
        var decoded: String? = null
        for (value in listOfNotNull(encoded, urlDecoded).distinct()) {
            decoded = runCatching { base64Decode(value) }.getOrNull()
            if (decoded != null) break
        }
        val decodedUrl = decoded ?: return
        val id = decodedUrl.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() } ?: return
        loadExtractor("https://hxfile.co/embed-$id.html", "$mainUrl/", subtitleCallback, callback)
    }
}
