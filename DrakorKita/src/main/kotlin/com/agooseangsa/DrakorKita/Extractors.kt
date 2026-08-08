package com.agooseangsa.DrakorKita

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

open class P2pStreamExtractor(
    override val name: String,
    override val mainUrl: String
) : ExtractorApi() {
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(
            url = url,
            headers = mapOf("User-Agent" to USER_AGENT),
            referer = referer ?: "$mainUrl/"
        )
        if (!response.isSuccessful) return

        val unpacked = runCatching { getAndUnpack(response.text) }.getOrDefault("")
        val source = unpacked.ifBlank { response.text }.replace("\\/", "/")

        Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            .findAll(source)
            .map { it.value }
            .distinct()
            .forEach { streamUrl ->
                generateM3u8(name, streamUrl, referer ?: mainUrl).forEach(callback)
            }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}

/**
 * Player shown by the target HTML as https://drakorkita.stream/#HASH.
 * The fragment is resolved through the player's own JSON endpoint without
 * loading its browser UI or any advertising JavaScript.
 */
class DrakorKitaStream : ExtractorApi() {
    override val name = "DrakorKitaP2P"
    override val mainUrl = "https://drakorkita.stream"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val hash = url.substringAfter('#', "").substringBefore('&').trim()
        if (hash.length < 4) return

        val response = runCatching {
            app.get(
                url = "$mainUrl/api/v1/folder?id=$hash",
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "application/json,text/plain,*/*"
                ),
                referer = referer ?: "$mainUrl/"
            )
        }.getOrNull() ?: return

        if (!response.isSuccessful) return
        val body = response.text.replace("\\/", "/")

        Regex("""https?://[^'"\s<>]+\.m3u8[^'"\s<>]*""")
            .findAll(body)
            .map { it.value }
            .distinct()
            .forEach { streamUrl ->
                generateM3u8(name, streamUrl, mainUrl).forEach(callback)
            }

        Regex("""https?://[^'"\s<>]+\.mp4[^'"\s<>]*""")
            .findAll(body)
            .map { it.value }
            .distinct()
            .forEach { streamUrl ->
                callback(
                    newExtractorLink(name, name, streamUrl, ExtractorLinkType.VIDEO) {
                        this.referer = mainUrl
                        this.quality = parseQuality(streamUrl)
                    }
                )
            }
    }

    private fun parseQuality(url: String): Int {
        return Regex("""(2160|1440|1080|720|480|360|240)p""", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}

class AbyssCdn : P2pStreamExtractor("AbyssCDN", "https://abysscdn.com")
class StbP2P : P2pStreamExtractor("STBP2P", "https://stb.strp2p.com")
class Playerupnone : P2pStreamExtractor("UPNP2P", "https://player.upn.one")
class FastdlP2P : P2pStreamExtractor("FastDLP2P", "https://fastdl.p2pstream.online")
class P2PStreamOnline : P2pStreamExtractor("P2PStream", "https://p2pstream.online")
class Strp2pCom : P2pStreamExtractor("STRP2P", "https://strp2p.com")
class UpnOneCom : P2pStreamExtractor("UPNOne", "https://upn.one")
