package com.nimegami

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.*
import java.net.URI
import java.net.URLDecoder

private const val MITEDRIVE_CSRF_TOKEN =
    "ZXlKcGNDSTZJak0yTGpneExqWTFMakUyTWlJc0ltUmxkbWxqWlNJNklrMXZlbWxzYkdFdk5TNHdJQ2hYYVc1a2IzZHpJRTVVSURFd0xqQTdJRmRwYmpZME95QjROalE3SUhKMk9qRXdNUzR3S1NCSFpXTnJieTh5TURFd01ERXdNU0JHYVhKbFptOTRMekV3TVM0d0lpd2lZbkp2ZDNObGNpSTZJazF2ZW1sc2JHRWlMQ0pqYjI5cmFXVWlPaUlpTENKeVpXWmxjbkpsY2lJNklpSjk="

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
        val document = app.get(url, referer = referer ?: "$mainUrl/").document
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
