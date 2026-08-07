package com.indomax

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getPacked

open class ImaxStreams : ExtractorApi() {
    override val name = "ImaxStreams"
    override val mainUrl = "https://imaxstreams.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = getPlayerScript(response.text) ?: return
        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT,
        )

        Regex(":\\s*\"([^\"]+?\\.m3u8[^\"]*)\"")
            .findAll(script)
            .map { it.groupValues[1] }
            .distinct()
            .forEach { streamUrl ->
                generateM3u8(
                    name,
                    fixUrl(streamUrl),
                    referer = "$mainUrl/",
                    headers = headers,
                ).forEach(callback)
            }
    }

    private fun getPlayerScript(html: String): String? {
        val packed = getPacked(html)
        if (!packed.isNullOrEmpty()) {
            val unpacked = getAndUnpack(html)
            return unpacked
                .substringAfter("var links", unpacked)
                .takeIf { it.isNotBlank() }
        }

        return org.jsoup.Jsoup.parse(html)
            .selectFirst("script:containsData(sources:)")
            ?.data()
    }

    private fun getEmbedUrl(url: String): String = when {
        "/d/" in url -> url.replace("/d/", "/e/")
        "/download/" in url -> url.replace("/download/", "/e/")
        "/file/" in url -> url.replace("/file/", "/e/")
        "/f/" in url -> url.replace("/f/", "/e/")
        else -> url
    }
}

class ImaxStreamsCom : ImaxStreams() {
    override val name = "ImaxStreamsCom"
    override val mainUrl = "https://imaxstreams.com"
}
