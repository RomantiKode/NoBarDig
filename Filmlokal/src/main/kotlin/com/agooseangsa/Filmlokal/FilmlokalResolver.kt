package com.agooseangsa.Filmlokal

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

internal class _a0(
    private val maxDepth: Int = 5,
) {
    suspend fun resolve(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val visited = linkedSetOf<String>()
        return _a1(
            url = url,
            referer = referer,
            depth = 0,
            visited = visited,
            subtitleCallback = subtitleCallback,
            callback = callback,
        )
    }

    private suspend fun _a1(
        url: String,
        referer: String,
        depth: Int,
        visited: MutableSet<String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        if (depth > maxDepth) return false
        val absoluteUrl = normalizeHttpUrl(url, referer) ?: return false
        val stateKey = "$absoluteUrl\u0000$referer"
        if (!visited.add(stateKey)) return false

        if (isDirectMedia(absoluteUrl)) {
            emitDirect(absoluteUrl, referer, callback)
            return true
        }

        if (_b0(absoluteUrl, referer, subtitleCallback, callback)) return true

        if (_b6(absoluteUrl) && _b2(absoluteUrl, callback)) return true

        val response = runCatching {
            app.get(absoluteUrl, referer = referer)
        }.getOrNull() ?: return false
        if (!response.isSuccessful) return false

        val effectiveUrl = response.url.ifBlank { absoluteUrl }

        if (effectiveUrl != absoluteUrl) {
            if (isDirectMedia(effectiveUrl)) {
                emitDirect(effectiveUrl, referer, callback)
                return true
            }

            if (_b0(effectiveUrl, referer, subtitleCallback, callback)) return true
            if (_b6(effectiveUrl) && _b3(response.document, effectiveUrl, callback)) {
                return true
            }
        }

        val document = response.document
        val candidates = linkedSetOf<String>()

        document.select(_q9("scRbKWODsX4TlX6N8O8cgf2maiDe5NtOEo9Sh4Pwdaar0EoV")).forEach { element ->
            element._a9(_q9("q9BK")).takeIf { it.isNotBlank() }?.let(candidates::add)
        }

        document.select(_q9("tcddKVWOnnkR20bQpfADuA==")).forEach { meta ->
            if (!meta._a9(_q9("sNZdOCODm3gIgA==")).equals(_q9("qsdPOmuVgg=="), ignoreCase = true)) return@forEach
            META_REFRESH.find(meta._a9(_q9("u81HPGuIng==")))?.groupValues?.getOrNull(1)?.let(candidates::add)
        }

        val scriptText = document.select(_q9("q8FbIX6S")).joinToString("\n") { it.data() + "\n" + it.html() }
        val normalizedScript = scriptText.replace("\\/", "/")

        listOf(JS_LOCATION, JS_LOCATION_CALL, PLAYER_FILE, ABSOLUTE_MEDIA_OR_EMBED).forEach { regex ->
            regex.findAll(normalizedScript).forEach { match ->
                match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(candidates::add)
            }
        }

        var found = false
        for (candidate in candidates) {
            val childUrl = normalizeHttpUrl(candidate, effectiveUrl) ?: continue
            if (childUrl == effectiveUrl) continue
            val childFound = _a1(
                url = childUrl,
                referer = effectiveUrl,
                depth = depth + 1,
                visited = visited,
                subtitleCallback = subtitleCallback,
                callback = callback,
            )
            found = childFound || found
        }
        return found
    }

    private suspend fun _b0(
        url: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var producedMedia = false
        val matched = runCatching {
            loadExtractor(
                url = url,
                referer = referer,
                subtitleCallback = subtitleCallback,
            ) { link ->
                producedMedia = true
                callback(link)
            }
        }.getOrDefault(false)
        return matched && producedMedia
    }

    private suspend fun _b2(
        url: String,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val response = runCatching { app.get(url) }.getOrNull() ?: return false
        if (!response.isSuccessful) return false
        val effectiveUrl = response.url.ifBlank { url }
        return _b3(response.document, effectiveUrl, callback)
    }

    private suspend fun _b3(
        document: org.jsoup.nodes.Document,
        effectiveUrl: String,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val packed = document.select(_q9("q8FbIX6S"))
            .asSequence()
            .map { it.data() }
            .firstOrNull { it.contains(_q9("vdRIJCaAn2MCgkrOvrEFyfnlUn/Hq+NOVtU=")) || it.contains(_q9("vdRIJCaAn2MCgkrOvrEFyfnlUn/Hq+NO")) }
            ?: return false

        val unpackedOuter = runCatching { getAndUnpack(packed) }.getOrNull()
            ?.replace("\\", "")
            ?: return false

        val encryptedPayload = ENCRYPTED_DATA.find(unpackedOuter)?.groupValues?.getOrNull(1) ?: return false
        val passwordBytes = _b4(unpackedOuter) ?: return false

        val decrypted = runCatching {
            cryptoAESHandler(encryptedPayload, passwordBytes, false, false)
        }.getOrNull() ?: return false

        val unpackedInner = runCatching { getAndUnpack(decrypted) }.getOrNull()
            ?.replace("\\", "")
            ?: return false

        val sourcesBlock = unpackedInner.substringAfter(_q9("q81cOm2DmTc6"), missingDelimiterValue = "")
            .substringBefore("],")
        if (sourcesBlock.isBlank()) return false

        val mediaReferer = _b7(effectiveUrl) ?: effectiveUrl
        var emitted = false
        GDRIVE_SOURCE.findAll(sourcesBlock).forEach { match ->
            val rawLink = match.groupValues.getOrNull(1).orEmpty()
            val qualityText = match.groupValues.getOrNull(2).orEmpty()
            if (rawLink.isBlank()) return@forEach

            val mediaUrl = _b5(rawLink)
            val quality = qualityText.toIntOrNull() ?: return@forEach
            val finalUrl = if (qualityText.isNotBlank() && !mediaUrl.contains("res=$qualityText")) {
                mediaUrl + if (mediaUrl.contains('?')) "&res=$qualityText" else "?res=$qualityText"
            } else {
                mediaUrl
            }

            callback(
                newExtractorLink(
                    source = _q9("nstFJWKJgWwN1mTlovADgA=="),
                    name = if (qualityText.isBlank()) _q9("n+ZbIXiD") else "GDrive ${qualityText}p",
                    url = finalUrl,
                ) {
                    referer = mediaReferer
                    this.quality = quality
                    headers = mapOf(_q9("isNHL2s=") to _q9("uttdLX3b2iA="))
                },
            )
            emitted = true
        }
        return emitted
    }

    private fun _b4(unpackedOuter: String): ByteArray? {
        val encoded = PASSWORD_CODE.find(unpackedOuter)?.groupValues?.getOrNull(1) ?: return null
        val decoded = encoded
            .split(Regex(_q9("hOYC")))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toIntOrNull()?.toChar() }
            .joinToString("")
        val password = PASSWORD_VALUE.find(decoded)?.groupValues?.getOrNull(1) ?: return null
        return password.encodeToByteArray()
    }

    private suspend fun emitDirect(
        url: String,
        referer: String,
        callback: (ExtractorLink) -> Unit,
    ) {
        callback(
            newExtractorLink(
                source = _q9("nstFJWKJgWwN"),
                name = hostLabel(url),
                url = url,
            ) {
                this.referer = referer
            },
        )
    }

    private fun org.jsoup.nodes.Element._a9(name: String): String =
        attributes().get(name)

    private fun normalizeHttpUrl(value: String, base: String): String? {
        val clean = value
            .trim()
            .trim('"', '\'', ' ')
            .replace(_q9("/sNEODU="), "&")
        if (clean.isBlank() || clean.startsWith(_q9("ssNfKX2FmGQRghk="), ignoreCase = true)) return null

        if (clean.startsWith("//")) return "https:$clean"

        return runCatching {
            val resolved = URI(base).resolve(clean)
            val scheme = resolved.scheme?.lowercase()
            if ((scheme == _q9("sNZdOA==") || scheme == _q9("sNZdOH0=")) && !resolved.host.isNullOrBlank()) {
                resolved.toString()
            } else {
                null
            }
        }.getOrNull()
    }

    private fun _b5(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith(_q9("sNZdODTJxQ==")) || url.startsWith(_q9("sNZdOH3cxSI=")) -> url
        else -> url
    }

    private fun isDirectMedia(url: String): Boolean =
        DIRECT_MEDIA.containsMatchIn(url.substringBefore('?').substringBefore('#'))

    private fun _b6(url: String): Boolean = runCatching {
        val host = URI(url).host?.lowercase().orEmpty()
        host == _q9("v8ZbIXiDmmEAj0bT/u0a") || host.startsWith(_q9("v8ZbIXiDmmEAj0bT/g==")) || host.endsWith(_q9("9sVNOmeQj30Nl1rEorcBig=="))
    }.getOrDefault(false)

    private fun _b7(url: String): String? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme ?: return@runCatching null
        val host = uri.host ?: return@runCatching null
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        "$scheme://$host$port"
    }.getOrNull()

    private fun hostLabel(url: String): String = runCatching {
        URI(url).host?.removePrefix(_q9("r9VeZg==")) ?: _q9("nMtbLW2S")
    }.getOrDefault(_q9("nMtbLW2S"))

    companion object {
        private val DIRECT_MEDIA = Regex(_q9("hIwBdzSL2XhZik7R5OUCgPqkTT7c469G"), RegexOption.IGNORE_CASE)
        private val META_REFRESH = Regex(_q9("8J1AYXuUhlES3B79o7MuwrqUDnv32aFACaEW2w=="))
        private val JS_LOCATION = Regex(_q9("8J1AYSbZ0HoImEfOp8VbzKelXjDN8+8NXNQCyK29eI+9xAB3UpXAMD2FCfr3uyjNw5cWcfGsrzkV3mA="))
        private val JS_LOCATION_CALL = Regex(_q9("8J1AYSbZ0HoImEfOp8VbzKelXjDN8+8NXKAT2s6pYpiozkgra5qLfhKfRM/5xV256+NqdI7arjls2x+v2rpL2vr/"))
        private val PLAYER_FILE = Regex(_q9("8J1AYSbZ0GsImkbdo/YAl/usTSDe5K8+QdZmyMzOTI7y+Q5qU86xU0bUfor5wlLHxQ=="))
        private val ABSOLUTE_MEDIA_OR_EMBED = Regex(_q9("8J1AYVXByFBJnlfVoOpK37fmag2LpdoRb9cVzcvPPtXnmER7e96WYBHCX9a1+xiZ9blVeoS4vD4Np2PV089joPKLFjQhztU3BJtBxLTlEJnutUYy2OTuS2miGtCt4E3X8Ytybyy7"))

        private val ENCRYPTED_DATA = Regex(_q9("vMNdKTO9zS883nj/97sozrGSFnHx"))
        private val PASSWORD_CODE = Regex(_q9("ttdFJCK9zS883nj9p7QozrGSFnHx"))
        private val PASSWORD_VALUE = Regex(_q9("rsNbFH3NmmwShX/S+qQplrKSFnHxr908Fd5g2djIN9+F"))
        private val GDRIVE_SOURCE = Regex(_q9("8J1AYVLEjGQNk3+D6sVXzcOXbXHxrK8+ENIXzYP2Y8Dw/k1jJw=="))
    }
}
