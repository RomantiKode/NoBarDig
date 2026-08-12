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
    override val name = _q9("TkvcJ8MX9Nz9wRPTvQ==")
    override val mainUrl = _q9("dlPJLvNEtZbgzAbDvXSBtvy/M71iYoSyBg==")
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val response = app.get(url, referer = referer)
        val unpacked = getAndUnpack(response.text)
        val baseUrl = response.url

        TRACK_OBJECT.findAll(unpacked).forEach { match ->
            val body = match.value
            val kind = readObjectString(body, _q9("dU7TOg==")) ?: return@forEach
            if (!kind.equals(_q9("fUbNKukR9Mo="), ignoreCase = true) &&
                !kind.equals(_q9("bVLfKukK9tzj"), ignoreCase = true)
            ) return@forEach

            val file = readObjectString(body, _q9("eE7ROw==")) ?: return@forEach
            val label = readObjectString(body, _q9("ckbfO+w="))?.ifBlank { null } ?: _q9("TVLfKukK9tw=")
            subtitleCallback(SubtitleFile(label, resolveUrl(baseUrl, file)))
        }

        SOURCE_OBJECT.findAll(unpacked).forEach { match ->
            val body = match.value
            if (readObjectString(body, _q9("dU7TOg==")) != null) return@forEach
            val file = readObjectString(body, _q9("eE7ROw==")) ?: return@forEach
            val label = readObjectString(body, _q9("ckbfO+w="))?.ifBlank { null } ?: name
            val mime = readObjectString(body, _q9("al7NOw=="))?.lowercase().orEmpty()
            val resolved = resolveUrl(baseUrl, file)
            val type = when {
                mime.contains(_q9("c1fYOfUM9g==")) || resolved.contains(_q9("MEqOK7g="), ignoreCase = true) ->
                    ExtractorLinkType.M3U8
                mime.contains(_q9("c1eJ")) -> ExtractorLinkType.VIDEO
                else -> null
            }

            callback(
                newExtractorLink(
                    source = name,
                    name = "$name $label",
                    url = resolved,
                    type = type,
                ) {
                    this.referer = baseUrl
                    this.quality = getQualityFromName(label)
                },
            )
        }
    }

    private fun readObjectString(body: String, key: String): String? {
        val keyRegex = Regex(
            """[\"']?${Regex.escape(key)}[\"']?\s*:\s*[\"']([^\"']+)[\"']""",
            RegexOption.IGNORE_CASE,
        )
        return keyRegex.find(body)?.groupValues?.getOrNull(1)
    }

    private fun resolveUrl(base: String, value: String): String = runCatching {
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
