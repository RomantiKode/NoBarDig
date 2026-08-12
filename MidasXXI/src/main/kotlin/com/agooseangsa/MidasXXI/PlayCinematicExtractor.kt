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
    override val name = _q9("NrOwLpZFEhz81/v8bg==")
    override val mainUrl = _q9("DqulJ6YWU1bh2u7sbo/PAAzjXJyYeSs1+A==")
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
            val kind = _b9(body, _q9("Dba/Mw==")) ?: return@forEach
            if (!kind.equals(_q9("Bb6hI7xDEgo="), ignoreCase = true) &&
                !kind.equals(_q9("FaqzI7xYEBzi"), ignoreCase = true)
            ) return@forEach

            val file = _b9(body, _q9("ALa9Mg==")) ?: return@forEach
            val label = _b9(body, _q9("Cr6zMrk="))?.ifBlank { null } ?: _q9("NaqzI7xYEBw=")
            subtitleCallback(SubtitleFile(label, _c0(responseUrl, file)))
        }

        SOURCE_OBJECT.findAll(unpacked).forEach { match ->
            val body = match.value
            if (_b9(body, _q9("Dba/Mw==")) != null) return@forEach

            val file = _b9(body, _q9("ALa9Mg==")) ?: return@forEach
            val label = _b9(body, _q9("Cr6zMrk="))?.ifBlank { null } ?: name
            val mime = _b9(body, _q9("EqahMg=="))?.lowercase().orEmpty()
            val resolved = _c0(responseUrl, file)
            val linkType = when {
                mime.contains(_q9("C6+0MKBeEA==")) || resolved.contains(_q9("SLLiIu0="), ignoreCase = true) ->
                    ExtractorLinkType.M3U8
                mime.contains(_q9("C6/l")) || resolved.contains(_q9("SLKhYw=="), ignoreCase = true) ->
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
