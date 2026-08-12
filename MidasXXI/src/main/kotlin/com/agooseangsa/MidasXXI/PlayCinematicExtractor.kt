package com.agooseangsa.MidasXXI

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

class PlayCinematicExtractor : ExtractorApi() {
    override val name = _q9("L9t0Dz1r4VLOdGlF2g==")
    override val mainUrl = _q9("F8NhBg04oBjTeXxV2sw+q3nhv6lem3WGNw==")
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val response = app.get(url, referer = referer)
        val unpacked = getAndUnpack(response.text).ifBlank { response.text }
        val responseUrl = response.url

        TRACK_OBJECT.findAll(unpacked).forEach { match ->
            val body = match.value
            val kind = _b9(body, _q9("FN57Eg==")) ?: return@forEach
            if (!kind.equals(_q9("HNZlAhdt4UQ="), ignoreCase = true) &&
                !kind.equals(_q9("DMJ3Ahd241LQ"), ignoreCase = true)
            ) return@forEach

            val file = _b9(body, _q9("Gd55Ew==")) ?: return@forEach
            val label = _b9(body, _q9("E9Z3ExI="))?.ifBlank { null } ?: _q9("LMJ3Ahd241I=")
            subtitleCallback(SubtitleFile(label, _c0(responseUrl, file)))
        }

        SOURCE_OBJECT.findAll(unpacked).forEach { match ->
            val body = match.value
            if (_b9(body, _q9("FN57Eg==")) != null) return@forEach

            val file = _b9(body, _q9("Gd55Ew==")) ?: return@forEach
            val label = _b9(body, _q9("E9Z3ExI="))?.ifBlank { null } ?: name
            val mime = _b9(body, _q9("C85lEw=="))?.lowercase().orEmpty()
            val resolved = _c0(responseUrl, file)
            val linkType = when {
                mime.contains(_q9("EsdwEQtw4w==")) || resolved.contains(_q9("UdomA0Y="), ignoreCase = true) ->
                    ExtractorLinkType.M3U8
                mime.contains(_q9("Esch")) || resolved.contains(_q9("UdplQg=="), ignoreCase = true) ->
                    ExtractorLinkType.VIDEO
                else -> return@forEach
            }

            callback(
                newExtractorLink(
                    source = name,
                    name = "$name $label",
                    url = resolved,
                    type = linkType,
                ) {
                    this.referer = responseUrl
                    this.quality = getQualityFromName(label)
                },
            )
        }
    }

    private fun _b9(body: String, key: String): String? = Regex(
        """[\"']?${Regex.escape(key)}[\"']?\s*:\s*[\"']([^\"']+)[\"']""",
        RegexOption.IGNORE_CASE,
    ).find(body)?.groupValues?.getOrNull(1)?.replace("\\/", "/")

    private fun _c0(base: String, value: String): String = runCatching {
        URI(base).resolve(value.replace("\\/", "/")).toString()
    }.getOrElse { value.replace("\\/", "/") }

    companion object {
        private val SOURCE_OBJECT = Regex(
            """\{[^{}]*[\"']?file[\"']?\s*:\s*[\"'][^\"']+[\"'][^{}]*}""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val TRACK_OBJECT = Regex(
            """\{[^{}]*[\"']?kind[\"']?\s*:\s*[\"'](?:captions|subtitles)[\"'][^{}]*}""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    }
}
