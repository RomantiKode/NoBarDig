package com.layarkaca21

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

open class Lk21Hownetwork : ExtractorApi() {
    override val name = "Hownetwork"
    override val mainUrl = "https://stream.hownetwork.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = url.substringAfter("id=", "").substringBefore('&')
        if (id.isBlank()) return
        val response = app.post(
            "$mainUrl/api2.php?id=$id",
            data = mapOf("r" to (referer ?: ""), "d" to mainUrl),
            referer = url,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
        ).parsedSafe<HownetworkResponse>() ?: return
        val file = response.file?.takeIf { it.startsWith("http") } ?: return
        callback(newExtractorLink(name, name, file) {
            this.referer = url
            this.type = if (file.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            this.quality = Qualities.Unknown.value
        })
    }

    private data class HownetworkResponse(@JsonProperty("file") val file: String? = null)
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
        val target = first.headers["location"] ?: "$mainUrl/$path"
        val html = app.get(target, headers = headers).text
        val encrypted = Regex("""const\s+datas\s*=\s*\"([^\"]+)\"""").find(html)?.groupValues?.getOrNull(1) ?: return
        val payload = """{"text":"$encrypted","agent":"$userAgent"}"""
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val decoded = app.post(
            "https://enc-dec.app/api/dec-abyss",
            requestBody = payload,
            headers = mapOf("Content-Type" to "application/json", "Origin" to "https://enc-dec.app", "User-Agent" to userAgent),
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
