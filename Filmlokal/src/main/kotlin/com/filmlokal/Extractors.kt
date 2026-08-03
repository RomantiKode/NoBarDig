package com.filmlokal

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.StreamTape as StreamTapeExtractor
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked
import java.net.URI

open class Dingtezuni : ExtractorApi() {
    override val name = "Earnvids"
    override val mainUrl = "https://dingtezuni.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val response = app.get(getEmbedUrl(url), referer = referer ?: "$mainUrl/")
        val packed = getPacked(response.text)
        val script = if (!packed.isNullOrBlank()) {
            val unpacked = getAndUnpack(response.text)
            unpacked.substringAfter("var links", unpacked)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        val headers = mapOf(
            "Origin" to mainUrl,
            "Referer" to "$mainUrl/",
            "User-Agent" to USER_AGENT,
        )

        Regex(":\\s*[\"'](.*?\\.m3u8.*?)[\"']")
            .findAll(script)
            .map { it.groupValues[1].replace("\\/", "/") }
            .distinct()
            .forEach { streamUrl ->
                generateM3u8(
                    name,
                    resolveStreamUrl(streamUrl),
                    referer = "$mainUrl/",
                    headers = headers,
                ).forEach(callback)
            }
    }

    private fun resolveStreamUrl(url: String): String = runCatching {
        URI("${mainUrl.trimEnd('/')}/").resolve(url.trim()).toString()
    }.getOrElse {
        when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") || url.startsWith("https://") -> url
            else -> "${mainUrl.trimEnd('/')}/${url.trimStart('/')}"
        }
    }

    private fun getEmbedUrl(url: String): String = when {
        "/d/" in url -> url.replace("/d/", "/v/")
        "/download/" in url -> url.replace("/download/", "/v/")
        "/file/" in url -> url.replace("/file/", "/v/")
        "/f/" in url -> url.replace("/f/", "/v/")
        else -> url
    }
}

class Movearnpre : Dingtezuni() {
    override var name = "Movearnpre"
    override var mainUrl = "https://movearnpre.com"
}

class Mivalyo : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://mivalyo.com"
}

class Ryderjet : Dingtezuni() {
    override var name = "Ryderjet"
    override var mainUrl = "https://ryderjet.com"
}

class Morencius : Dingtezuni() {
    override var name = "Morencius"
    override var mainUrl = "https://morencius.com"
}

class Bingezove : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://bingezove.com"
}

class Hglink : StreamWishExtractor() {
    override val name = "Hglink"
    override val mainUrl = "https://hglink.to"
}

class Ghbrisk : StreamWishExtractor() {
    override val name = "Ghbrisk"
    override val mainUrl = "https://ghbrisk.com"
}

class Dhcplay : StreamWishExtractor() {
    override var name = "DHC Play"
    override var mainUrl = "https://dhcplay.com"
}

class StreamTape : StreamTapeExtractor() {
    override var name = "StreamTape"
    override var mainUrl = "https://streamtape.xyz"
}

class Streamcasthub : VidStack() {
    override var name = "Streamcasthub"
    override var mainUrl = "https://live.streamcasthub.store"
    override var requiresReferer = true
}

class Dm21upns : VidStack() {
    override var name = "Dm21upns"
    override var mainUrl = "https://dm21.upns.live"
    override var requiresReferer = true
}
