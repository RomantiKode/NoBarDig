package com.agooseangsa.Layarkaca21

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.utils.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class _g0 : ExtractorApi() {
    override val name = _q9("fqZlX5o6SUid")
    override val mainUrl = _q9("QLt1SoZOCQOOqbiUePz5ne4kyd0=")
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val emitted = linkedSetOf<String>()
        val visited = linkedSetOf<String>()
        val safeCallback: (ExtractorLink) -> Unit = { link ->
            if (_i2(link.url) && emitted.add(link.url)) callback(link)
        }

        _h4(
            url = url,
            referer = referer ?: SERIES_REFERER,
            depth = 0,
            visited = visited,
            emitted = emitted,
            subtitleCallback = subtitleCallback,
            callback = safeCallback,
        )

        if (emitted.isEmpty()) {
            for (candidate in _h7(url)) {
                val before = emitted.size
                loadExtractor(candidate, referer ?: url, subtitleCallback, safeCallback)
                if (emitted.size > before) break
            }
        }
    }

    private suspend fun _h4(
        url: String,
        referer: String,
        depth: Int,
        visited: MutableSet<String>,
        emitted: Set<String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (depth > MAX_DEPTH || !visited.add(url) || !_i2(url)) return

        val referers = listOf(
            referer,
            _i3(referer),
            SERIES_REFERER,
            MOVIE_REFERER,
        ).filter { it.isNotBlank() }.distinct()

        for (requestReferer in referers.take(3)) {

            val plainResponse = runCatching {
                app.get(url, referer = requestReferer, allowRedirects = true)
            }.getOrNull()
            val browserResponse = runCatching {
                app.get(
                    url,
                    referer = requestReferer,
                    headers = PLAYER_HEADERS,
                    allowRedirects = true,
                )
            }.getOrNull()

            val responses = listOfNotNull(plainResponse, browserResponse)
                .distinctBy { it.url to it.text.hashCode() }

            for (response in responses) {
            if (response.url != url && _i2(response.url)) {
                val before = emitted.size
                _h5(
                    candidate = response.url,
                    referer = url,
                    depth = depth + 1,
                    visited = visited,
                    emitted = emitted,
                    subtitleCallback = subtitleCallback,
                    callback = callback,
                )
                if (emitted.size > before) return
            }

            val document = response.document._h8()
            _h6(document, response.url, subtitleCallback)

            val scriptTexts = document.select(_q9("W6xzU4UAHEKXtPSqZOD1pKI=")).flatMap { script ->
                val raw = script.data()
                listOf(raw, getAndUnpack(raw))
            }.distinct()

            val candidates = linkedSetOf<String>()
            document.select(
                _q9("QalzW5gRfV+Ko4HdN+T/ne5l9svYl2jp4fYylv61eJ1HunNZkC9VXpud8NFk/eOL6G/2y9iXaOnh") +
                    _q9("c6tgTpRZU16UnfDRTPb3jeon3srJqRnlrOUvk8CyLJpY4mRLgB1QEYqluoNy4f6k")
            ).forEach { element ->
                when {
                    element.tagName().equals(_q9("Rap1Ww=="), ignoreCase = true) -> {
                        REFRESH_URL_REGEX.find(element.attr(_q9("S6BvTpAaUg==")))
                            ?.groupValues?.getOrNull(1)?.let(candidates::add)
                    }
                    else -> {
                        listOf(_q9("W71i"), _q9("TK51W9gBVEA="), _q9("TK51W9gHVE8="))
                            .firstNotNullOfOrNull { key -> element.attr(key).takeIf { it.isNotBlank() } }
                            ?.let(candidates::add)
                    }
                }
            }

            for (text in scriptTexts) {
                MEDIA_REGEX.findAll(text).forEach { candidates += it.value }
                URL_ASSIGNMENT_REGEX.findAll(text).forEach { candidates += it.groupValues[1] }
                LOCATION_REGEX.findAll(text).forEach { candidates += it.groupValues[1] }
                ATOB_REGEX.findAll(text).forEach { match ->
                    _i0(match.groupValues[1])?.let(candidates::add)
                }
            }

            for (rawCandidate in candidates) {
                val candidate = _c3(response.url, _h9(rawCandidate)) ?: continue
                val before = emitted.size
                _h5(
                    candidate = candidate,
                    referer = response.url,
                    depth = depth + 1,
                    visited = visited,
                    emitted = emitted,
                    subtitleCallback = subtitleCallback,
                    callback = callback,
                )
                if (emitted.size > before && _i1(candidate)) continue
            }

            if (emitted.isNotEmpty()) return
            }
        }
    }

    private suspend fun _h5(
        candidate: String,
        referer: String,
        depth: Int,
        visited: MutableSet<String>,
        emitted: Set<String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        if (!_i2(candidate)) return
        if (_i1(candidate)) {
            callback(newExtractorLink(name, _i5(referer), candidate) {
                this.referer = referer
                this.type = if (candidate.contains(_q9("BqIyT80="), true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                this.quality = getQualityFromName(candidate)
                this.headers = mapOf(_q9("eqpnX4cRVA==") to referer, _q9("fbxkSNg1QUmWtA==") to USER_AGENT)
            })
            return
        }

        if (_i4(candidate) == _i4(mainUrl)) {
            _h4(candidate, referer, depth, visited, emitted, subtitleCallback, callback)
            return
        }

        val before = emitted.size
        loadExtractor(candidate, referer, subtitleCallback, callback)
        if (emitted.size > before) return
    }

    private suspend fun _h6(
        document: Document,
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        document.select(_q9("XL1gWZ4vTUWWpOGCYvDikP9myMv3r0a3ot130u+oOY1DlGpTmxAbT5mwqJh4/OWk0Hnf2/c=")).forEach { track ->
            val subtitleUrl = _c3(pageUrl, track.attr(_q9("W71i"))) ?: return@forEach
            if (!_i2(subtitleUrl)) return@forEach
            subtitleCallback(
                newSubtitleFile(
                    track.attr(_q9("W71iVpQaQQ==")).ifBlank { track.attr(_q9("RK5jX5k=")).ifBlank { _q9("faFqVJoDSA==") } },
                    subtitleUrl,
                )
            )
        }
    }

    private fun _h7(url: String): List<String> {
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        val parts = path.trim('/').split('/')
        if (parts.size < 3 || parts[0] != _q9("QalzW5gR")) return emptyList()
        val server = parts[1].lowercase()
        val id = parts.drop(2).joinToString("/").takeIf { it.isNotBlank() } ?: return emptyList()
        return when (server) {
            _q9("QLZlSJQM") -> listOf("https://short.ink/$id", "https://abyssplayer.com/$id")
            _q9("XLpzWJoCT1w=") -> listOf("https://turbovidhls.com/e/$id", "https://turbovidhls.com/$id")
            _q9("S65yTg==") -> listOf("https://co4nxtrl.com/e/$id", "https://furher.in/e/$id")

            _q9("WP1x") -> emptyList()
            else -> emptyList()
        }
    }

    private fun Document._h8(): Document = apply {
        select(
            _q9("C65leZoaUk2RrrmDO7K1mO954dHEnxnl4u8rl/WKN55dvy0a1hpHWJG2ubBz4brZpXrCyN+EGeXv8DSC7rQ8i1rjIQ==") +
                _q9("Bq5lSdlUCE2ctrmDY/vlnOZvw8yG1Fyjs+E2l8CpKo0C8mVVgBZKSZustZJ8z7rZ4mzf2ceRbraz43HP87Mrmkm7cmfZVFVPiqmshUzh5JrW")
        ).remove()
    }

    private fun _c3(base: String, value: String?): String? {
        val raw = value?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() } ?: return null
        if (
            raw.startsWith(_q9("Qq53W4YXVEWItOY="), true) || raw.startsWith(_q9("TK51W88="), true) ||
            raw.startsWith(_q9("SqNuWM8="), true) || raw.startsWith(_q9("Sa1uT4FO"), true) || raw == "#"
        ) return null
        return runCatching {
            val uri = URI(base).resolve(raw)
            if (uri.scheme !in setOf(_q9("QLt1Sg=="), _q9("QLt1SoY="))) null else uri.toString()
        }.getOrNull()
    }

    private fun _h9(value: String): String = value
        .replace("\\/", "/")
        .replace(_q9("dLoxCsdC"), "&", ignoreCase = true)
        .replace(_q9("Dq5sSs4="), "&")
        .trim()

    private fun _i0(value: String): String? = runCatching {
        base64Decode(value).trim().takeIf { it.startsWith(_q9("QLt1Ss9bCQ==")) || it.startsWith(_q9("QLt1SoZOCQM=")) }
    }.getOrNull()

    private fun _i1(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return DIRECT_EXTENSIONS.any(clean::endsWith)
    }

    private fun _i2(url: String): Boolean {
        val host = _i4(url)
        return host.isNotBlank() && BLOCKED_HOSTS.none { host == it || host.endsWith(".$it") }
    }

    private fun _i3(url: String): String = runCatching {
        URI(url).let { "${it.scheme}://${it.host}/" }
    }.getOrDefault("")

    private fun _i4(url: String): String =
        runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")

    private fun _i5(url: String): String {
        val parts = runCatching { URI(url).path.orEmpty().trim('/').split('/') }.getOrDefault(emptyList())
        return parts.getOrNull(1)?.uppercase() ?: name
    }

    companion object {
        private val MAX_DEPTH = 2
        private val SERIES_REFERER = _q9("QLt1SoZOCQOMturfef34jeRkycrLmVTrrPl0")
        private val MOVIE_REFERER = _q9("QLt1SoZOCQOMtu3DOf79y7ply97Dl1ykra44kbQ=")
        private val PLAYER_HEADERS = mapOf(
            _q9("fbxkSNg1QUmWtA==") to USER_AGENT,
            _q9("aaxiX4UA") to _q9("XKp5TtocUkGU7L2BZ/7/mup+xNfE202tte032eO3NMJJv3FWnBdHWJGvst5v//rC+jedlpPYH+rruyrPq/Rg"),
            _q9("aaxiX4UAC2CZrruEdvXz") to _q9("Qassc7FYT0jDseHBOau6nOUn+OuRhQj177h3l/XhKdMY4TY="),
            _q9("e6piF7MRUk+Q7ZiUZOY=") to _q9("QalzW5gR"),
            _q9("e6piF7MRUk+Q7ZGec/c=") to _q9("Rq53U5IVUkk="),
            _q9("e6piF7MRUk+Q7Y+YY/c=") to _q9("S71uSYZZVUWMpQ=="),
            _q9("fb9mSJQQQwGxrq+UdOfknKZYyMnfkUaxsg==") to "1",
        )
        private val MEDIA_REGEX = Regex(
            _q9("QLt1SoZLHAPXm4KtZM603rc08JOVqBvt/ro2we7iJINY+31XngJaW52isdg/rayltFHz5NmoF+L9vgbYsuU="),
            RegexOption.IGNORE_CASE,
        )
        private val URL_ASSIGNMENT_REGEX = Regex(
            _q9("APA7XJwYQ1CLsr+NYuD6hfhl2MrJkRyZsqoAyKaHBJ0ClCMdqFx9ctrngdo+ybTe1g=="),
            RegexOption.IGNORE_CASE,
        )
        private val LOCATION_REGEX = Regex(
            _q9("APA7TZwaQkOPnPLYKLqpw+9lzs3HkVuxna5yzfe1O49cpm5U3UsccNaorpRxu6ml+CCQ5Nnebufm3XOpxfh/swPmWhjSKQ=="),
            RegexOption.IGNORE_CASE,
        )
        private val ATOB_REGEX = Regex(
            _q9("SbtuWKlcfQ7fnfSqVr/MmKZwnZWT3xr4nq0G2bKBesl1kyg="),
            RegexOption.IGNORE_CASE,
        )
        private val REFRESH_URL_REGEX = Regex(_q9("Xb1tZoZeG3CL6vSqSanL0qI="), RegexOption.IGNORE_CASE)
        private val DIRECT_EXTENSIONS = setOf(_q9("BqIyT80="), _q9("BqJxDg=="), _q9("BqJqTA=="), _q9("BrhkWJg="))
        private val BLOCKED_HOSTS = setOf(
            _q9("TKBvW4YdCF+Qr6uSc/zu1+hlwA=="), _q9("W+FoXg=="), _q9("QKZyTpQAVQKbr7E="), _q9("W7x1W4EdRR3WqLWCY/PiiqVpwtU="),
            _q9("T6BuXZkRUk2frb2fdvXzi6VpwtU="), _q9("T6BuXZkRC02WobCIY/v1iqVpwtU="), _q9("TKB0WJkRRUCRo7ffeffi"),
            _q9("Tq5iX5cbSUfWo7Oc"), _q9("QaFyTpQTVE2V7r+eeg=="), _q9("UOFiVZg="), _q9("UaB0ToAWQwKbr7E="), _q9("UaB0ToBaREk="),
        )
    }
}

open class _g1 : ExtractorApi() {
    override val name = _q9("YKB2VJAAUUOKqw==")
    override val mainUrl = _q9("QLt1SoZOCQOLtK6Udv+4keR9w93eg1q3qq4ji+E=")
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = url.substringAfter(_q9("Qas8"), "").substringBefore('&')
        if (id.isBlank()) return

        val host = runCatching { URI(mainUrl).host.orEmpty() }.getOrDefault("")
        if (host.isBlank()) return
        val encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8.toString())
        val browserUserAgent =
            _q9("ZaB7U5kYRwPN7uzRP97/l/5ylpjrmlG3ruk/0qrqY85j5iF7hQRKSa+lvrp+5rnMuD2Di5zU") +
                _q9("AIRJbrg4CgyUqbeUN9XzmuBlhJjpnEeqrOV0w6jtdt4G/y8K1TlJTpGsudFE8/CY+WOCjZnDG/b3")
        val headers = mapOf(
            _q9("fbxkSNg1QUmWtA==") to browserUserAgent,
            _q9("eqpnX4cRVA==") to url,
            _q9("Z71oXZwa") to mainUrl,
            _q9("cOJTX4QBQ1+MpbjcQPvikQ==") to _q9("cIJNcoEAVn6dsamUZOY="),
            _q9("aaxiX4UA") to _q9("Sb9xVpwXR1iRr7LefeH5l6cq2d3SgBqvoPY6gfioMZ5c4yEQ2l4dDIn97N8now=="),
        )

        val requestBodies = listOf(
            mapOf("r" to _q9("QLt1SoZOCQOIrL2IcuD/n/lrwN2Eh1e27g=="), "d" to host),
            mapOf("r" to _q9("QLt1SoZOCQOIrL2IcuD/n/lrwN2Eh1e27g=="), "d" to _q9("S6NuT5FaTkOPrrmFYP3kkqVy1MI=")),
            mapOf("r" to _q9("QLt1SoZOCQOIrL2IcuD/n/lrwN2Eh1e27g=="), "d" to _q9("W7tzX5QZCESXt7KUY+X5i+Ak1cHQ")),
            mapOf("r" to (referer ?: _q9("QLt1SoZOCQOIrL2IcuD/n/lrwN2Eh1e27g==")), "d" to host),
        ).distinct()
        val endpoints = listOf(_q9("Sb9oCNsETlw="), _q9("Sb9oFIUcVg=="))

        for (endpoint in endpoints) {
            for (body in requestBodies) {
                val apiResponse = runCatching {
                    app.post(
                        "$mainUrl/$endpoint?id=$encodedId",
                        data = body,
                        referer = url,
                        headers = headers,
                    )
                }.getOrNull() ?: continue

                val sources = buildList {
                    apiResponse.parsedSafe<HownetworkResponse>()?.let(::add)
                    addAll(apiResponse.parsedSafe<HownetworkEnvelope>()?.data.orEmpty())
                    addAll(apiResponse.parsedSafe<List<HownetworkResponse>>().orEmpty())
                }.distinctBy { (it.file ?: it.link).orEmpty() }

                var emitted = false
                for (source in sources) {
                    val file = (source.file ?: source.link)
                        ?.trim()
                        ?.takeIf { it.startsWith(_q9("QLt1Ss9bCQ==")) || it.startsWith(_q9("QLt1SoZOCQM=")) }
                        ?: continue
                    callback(newExtractorLink(name, source.label?.takeIf { it.isNotBlank() } ?: name, file) {
                        this.referer = mainUrl
                        this.type = if (file.contains(_q9("BqIyT80="), true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        this.quality = getQualityFromName(source.label)
                            .takeIf { it != Qualities.Unknown.value }
                            ?: getQualityFromName(file)
                        this.headers = mapOf(
                            _q9("fbxkSNg1QUmWtA==") to browserUserAgent,
                            _q9("eqpnX4cRVA==") to mainUrl,
                            _q9("Z71oXZwa") to mainUrl,
                        )
                    })
                    emitted = true
                }
                if (emitted) return
            }
        }
    }

    private data class HownetworkResponse(
        @JsonProperty(_q9("TqZtXw==")) val file: String? = null,
        @JsonProperty(_q9("RKZvUQ==")) val link: String? = null,
        @JsonProperty(_q9("RK5jX5k=")) val label: String? = null,
    )

    private data class HownetworkEnvelope(
        @JsonProperty(_q9("TK51Ww==")) val data: List<HownetworkResponse>? = null,
    )
}

class _g2 : _g1() {
    override val mainUrl = _q9("QLt1SoZOCQObrLOEc7z+lvxkyMzdm0eu7/giiA==")
}

class _g3 : Filesim() {
    override val mainUrl = _q9("QLt1SoZOCQObr+ifb+bklaVpwtU=")
    override val name = _q9("a6A1VI0AVEA=")
    override val requiresReferer = true
}

class _g4 : Filesim() {
    override val mainUrl = _q9("QLt1SoZOCQOeta6ZcuC4kOU=")
    override val name = _q9("brpzUpAG")
}

class _g5 : Filesim() {
    override val mainUrl = _q9("QLt1SoZOCQPP8u+AZfqniaVs2NY=")
    override val name = _q9("brpzUpAGBm2UtA==")
}

class _g6 : Filesim() {
    override val mainUrl = _q9("QLt1SoZOCQOMta6TeOT/neNm3pbJm1g=")
    override val name = _q9("fLpzWJoCT0iQrK8=")
}

open class _g7 : ExtractorApi() {
    override val name = _q9("aa14SYY=")
    override val mainUrl = _q9("QLt1SoZOCQOZoqWCZOL6mPJv35bJm1g=")
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val path = url.substringAfter(_q9("EuAu")).substringAfter('/')
        if (path.isBlank()) return
        val userAgent = _q9("ZaB7U5kYRwPN7uzRP8X/l+9l2suKumHl8LB1wqD6D4dG+TUB1QwQGMPgrocto6LNpTqEmO2RVq6ur2nCqupo3xj+IXycBkNKl7jzwCOmuMk=")
        val headers = mapOf(_q9("fbxkSNg1QUmWtA==") to userAgent, _q9("eqpnX4cRVA==") to mainUrl)
        val first = app.get("$mainUrl/$path", headers = headers, allowRedirects = false)
        val target = first.headers[_q9("RKBiW4EdSUI=")] ?: first.headers[_q9("ZKBiW4EdSUI=")] ?: "$mainUrl/$path"
        val html = app.get(target, headers = headers).text
        val encrypted = Regex(_q9("S6BvSYEoVQecoaiQZM7l07ZW3pKI3G6b491w27k=")).find(html)?.groupValues?.getOrNull(1) ?: return
        val payload = "{\"text\":\"$encrypted\",\"agent\":\"$userAgent\"}"
            .toRequestBody(_q9("Sb9xVpwXR1iRr7LefeH5l7AqztDLhkagtb0uhv33YA==").toMediaTypeOrNull())
        val decoded = app.post(
            _q9("QLt1SoZOCQOdrr/cc/f11+p63ZfLhFzqpeU43/q4IZ1b"),
            requestBody = payload,
            headers = mapOf(
                _q9("a6BvTpAaUgGsuayU") to _q9("Sb9xVpwXR1iRr7LefeH5lw=="),
                _q9("Z71oXZwa") to _q9("QLt1SoZOCQOdrr/cc/f11+p63Q=="),
                _q9("fbxkSNg1QUmWtA==") to userAgent,
            ),
        ).parsedSafe<AbyssResponse>() ?: return
        for (source in decoded.result?.sources.orEmpty()) {
            val media = source.url?.takeIf { it.startsWith(_q9("QLt1Sg==")) } ?: continue
            callback(newExtractorLink(name, name, media) {
                this.referer = _q9("QLt1SoZOCQOIrL2If+vyi+pyg9vFmQ==")
                this.type = if (media.contains(_q9("BqIyT80="), true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                this.quality = getQualityFromName(source.type)
                this.headers = mapOf(_q9("fbxkSNg1QUmWtA==") to userAgent, _q9("eqpnX4cRVA==") to _q9("QLt1SoZOCQOIrL2If+vyi+pyg9vFmQ=="))
            })
        }
    }

    private data class AbyssResponse(@JsonProperty(_q9("WqpyT5kA")) val result: AbyssResult? = null)
    private data class AbyssResult(@JsonProperty(_q9("W6B0SJYRVQ==")) val sources: List<AbyssSource>? = null)
    private data class AbyssSource(
        @JsonProperty(_q9("Xb1t")) val url: String? = null,
        @JsonProperty(_q9("XLZxXw==")) val type: String? = null,
    )
}

class _g8 : _g7() {
    override val mainUrl = _q9("QLt1SoZOCQOLqLODY7z/l+A=")
    override val name = _q9("e6duSIE9SEc=")
}
