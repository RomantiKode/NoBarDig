package com.agooseangsa.Terbit21

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

class Terbit21VidPlayerLiveExtractor : ExtractorApi() {
    override var name = _q9("M1/fFDMdbofgavCzz8PNgjHq")
    override var mainUrl = _q9("D07ZBilTc5mzWqvmsdnFnyT0U5HlGsnHTn+C")
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val uri = runCatching { URI(url) }.getOrNull() ?: return
        if (!uri.host.equals(EXACT_TARGET_HOST, ignoreCase = true)) return

        val resolver = WebViewResolver(
            interceptUrl = FINAL_MEDIA_REQUEST,
            useOkhttp = false,
            timeout = WEBVIEW_TIMEOUT_MS,
        )
        val (finalRequest, additionalRequests) = resolver.resolveUsingWebView(
            url = url,
            referer = referer,
        )

        val requests = buildList {
            finalRequest?.let(::add)
            addAll(additionalRequests)
        }.distinctBy { it.url.toString() }

        for (request in requests) {
            val mediaUrl = request.url.toString()
            if (!FINAL_MEDIA_REQUEST.containsMatchIn(mediaUrl)) continue

            val requestHeaders = request.headers.toMap()
            val mediaReferer = requestHeaders.entries
                .firstOrNull { it.key.equals(_q9("NV/LEygMLg=="), ignoreCase = true) }
                ?.value
                ?: url.substringBefore('#')
            val playbackHeaders = requestHeaders.filterKeys { header ->
                FORWARDED_HEADERS.any { it.equals(header, ignoreCase = true) }
            }

            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = mediaUrl,
                ) {
                    this.referer = mediaReferer
                    this.quality = Qualities.Unknown.value
                    this.headers = playbackHeaders
                }
            )
        }
    }

    private companion object {
        private const val EXACT_TARGET_HOST = "sf21.vidplayer.live"
        private const val WEBVIEW_TIMEOUT_MS = 30_000L
        private val FINAL_MEDIA_REQUEST = Regex(
            _q9("TwXEX3JWZuruUaqip9Pw1TnoBpTcRorbQyDPG3NJWeQ6Rolf")
        )
        private val FORWARDED_HEADERS = setOf(
            _q9("MknIBHcoO9OuSA=="),
            _q9("KEjEETMH"),
            _q9("JFXCHTMM"),
            _q9("Jk/ZHjUbNcyhSPC48Q=="),
        )
    }
}
