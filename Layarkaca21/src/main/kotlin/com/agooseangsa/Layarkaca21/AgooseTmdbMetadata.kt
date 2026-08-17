package com.agooseangsa.Layarkaca21

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val TMDB_BASE = "https://api.themoviedb.org/3"
private val TMDB_READ_ACCESS_TOKEN: String
    get() = BuildConfig.TMDB_READ_ACCESS_TOKEN
private val TMDB_API_KEY: String
    get() = BuildConfig.TMDB_API_KEY
private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w500"
private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/original"
private const val _d14 = "https://image.tmdb.org/t/p/w342"
internal const val _d13 = 20

internal data class _d0(
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val originalTitle: String? = null,
    val displayTitle: String,
    val year: Int? = null,
    val isTv: Boolean,
)

internal data class _d1(
    val name: String,
    val imageUrl: String? = null,
    val character: String? = null,
    val order: Int = Int.MAX_VALUE,
)

internal data class _d2(
    val tmdbId: Int,
    val imdbId: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val year: Int? = null,
    val runtimeMinutes: Int? = null,
    val voteAverage: Double? = null,
    val genres: List<String> = emptyList(),
    val actors: List<_d1> = emptyList(),
    val trailerUrls: List<String> = emptyList(),
    val contentRating: String? = null,
)

internal enum class _m0 { VALID, SUSPECT, INVALID }
internal enum class _m1 { ID, NON_ID, UNKNOWN }

internal data class _m2(
    val original: String?,
    val cleaned: String?,
    val quality: _m0,
    val language: _m1,
    val sanitized: Boolean,
    val reasons: List<String> = emptyList(),
)

internal data class _m3(
    val value: String?,
    val source: String,
    val reason: String,
    val assessment: _m2,
)

private val _d3 = Mutex()
private val _d4 = mutableMapOf<String, _d2?>()

private fun _d10(): Boolean =
    TMDB_READ_ACCESS_TOKEN.isNotBlank() || TMDB_API_KEY.isNotBlank()

private fun _d11(): Map<String, String> = mutableMapOf(
    _q9("SaxiX4UA") to _q9("Sb9xVpwXR1iRr7LefeH5lw=="),
).apply {
    if (TMDB_READ_ACCESS_TOKEN.isNotBlank()) {
        this[_q9("abp1UpoGT1aZtLWeeQ==")] = "Bearer $TMDB_READ_ACCESS_TOKEN"
    }
}

private fun _d12(vararg values: Pair<String, String>): Map<String, String> =
    mutableMapOf(*values).apply {
        if (TMDB_READ_ACCESS_TOKEN.isBlank() && TMDB_API_KEY.isNotBlank()) {
            this[_q9("Sb9oZZ4RXw==")] = TMDB_API_KEY
        }
    }

internal suspend fun _d5(
    identity: _d0,
    profile: _k8 = _j0.metadata.tmdb,
): _d2? {
    if (!profile.enabled || !_d10()) return null
    val cacheKey = listOf(
        identity.tmdbId?.toString().orEmpty(),
        identity.imdbId.orEmpty(),
        identity.originalTitle.orEmpty(),
        identity.displayTitle,
        identity.year?.toString().orEmpty(),
        identity.isTv.toString(),
        profile.language,
    ).joinToString("|")

    _d3.withLock {
        if (_d4.containsKey(cacheKey)) return _d4[cacheKey]
    }

    val metadata = runCatching {
        val tmdbId = identity.tmdbId
            ?: identity.imdbId?.let { _d6(it, identity.isTv, profile.language) }
            ?: _d7(identity, profile.language)
            ?: return@runCatching null
        _d9(tmdbId, identity.isTv, profile.language)
    }.getOrNull()

    _d3.withLock { _d4[cacheKey] = metadata }
    return metadata
}

private suspend fun _d6(imdbId: String, isTv: Boolean, language: String): Int? {
    if (!Regex(_q9("drt1ZpFfAg==")).matches(imdbId)) return null
    val json = JSONObject(
        app.get(
            "$TMDB_BASE/find/$imdbId",
            headers = _d11(),
            params = _d12(
                _q9("Tbd1X4caR0Cns7OEZfHz") to _q9("QaJlWKodQg=="),
                _q9("RK5vXYAVQUk=") to language,
            ),
        ).text,
    )
    val key = if (isTv) _q9("XLleSJAHU0CMsw==") else _q9("RaB3U5ArVEmLtbCFZA==")
    return json.optJSONArray(key)?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
}

private suspend fun _d7(identity: _d0, language: String): Int? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val typePath = if (identity.isTv) "tv" else _q9("RaB3U5A=")
    val yearKey = if (identity.isTv) _q9("TqZzSYErR0WKn7iQY/fJgO5r3w==") else _q9("UapgSA==")

    for (query in queries) {
        val params = _d12(
            _q9("RK5vXYAVQUk=") to language,
            _q9("WbpkSIw=") to query,
        ).toMutableMap()
        identity.year?.let { params[yearKey] = it.toString() }
        val results = JSONObject(
            app.get(
                "$TMDB_BASE/search/$typePath",
                headers = _d11(),
                params = params,
            ).text,
        )
            .optJSONArray(_q9("WqpyT5kAVQ==")) ?: continue
        for (index in 0 until minOf(results.length(), 5)) {
            val candidate = results.optJSONObject(index) ?: continue
            if (candidate._d8(identity)) {
                return candidate.optInt("id").takeIf { it > 0 }
            }
        }
    }
    return null
}

private fun JSONObject._d8(identity: _d0): Boolean {
    val titleKey = if (identity.isTv) _q9("Rq5sXw==") else _q9("XKZ1VpA=")
    val originalKey = if (identity.isTv) _q9("R71oXZwaR0Cnrr2ccg==") else _q9("R71oXZwaR0CntLWFe/c=")
    val dateKey = if (identity.isTv) _q9("TqZzSYErR0WKn7iQY/c=") else _q9("WqptX5QHQ3OcoaiU")

    val expectedTitles = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map(::_e7)
        .filter { it.isNotBlank() }
        .toSet()
    val candidateTitles = listOfNotNull(optStringOrNull(titleKey), optStringOrNull(originalKey))
        .map(::_e7)
        .filter { it.isNotBlank() }
        .toSet()
    if (expectedTitles.intersect(candidateTitles).isEmpty()) return false

    if (identity.year != null) {
        val candidateYear = optString(dateKey).take(4).toIntOrNull()
        if (candidateYear != identity.year) return false
    }
    return true
}

private suspend fun _d9(tmdbId: Int, isTv: Boolean, language: String): _d2? {
    val typePath = if (isTv) "tv" else _q9("RaB3U5A=")
    val append = if (isTv) {
        _q9("Tbd1X4caR0CnqbiCO/PxnvlvytnekWqms+U/m++pdJhBq2RVhlhPQZmnuYI78fmX/2/DzPWGVLGo7jyB")
    } else {
        _q9("Tbd1X4caR0CnqbiCO/HknO9j2cuGglyhpO8o3vK3OYlNvC1IkBhDTYulg5V25vOK")
    }
    val json = JSONObject(
        app.get(
            "$TMDB_BASE/$typePath/$tmdbId",
            headers = _d11(),
            params = _d12(
                _q9("RK5vXYAVQUk=") to language,
                _q9("Sb9xX5sQeViXn66UZOL5l/hv") to append,
                _q9("QaFiVoAQQ3ORrb2Wcs36mOVt2NnNkQ==") to _q9("QastVIAYSgCdrg=="),
            ),
        ).text,
    )

    val release = json.optString(if (isTv) _q9("TqZzSYErR0WKn7iQY/c=") else _q9("WqptX5QHQ3OcoaiU"))
    val imdbId = if (isTv) {
        json.optJSONObject(_q9("Tbd1X4caR0CnqbiC"))?.optStringOrNull(_q9("QaJlWKodQg=="))
    } else {
        json.optStringOrNull(_q9("QaJlWKodQg==")) ?: json.optJSONObject(_q9("Tbd1X4caR0CnqbiC"))?.optStringOrNull(_q9("QaJlWKodQg=="))
    }
    val runtime = if (isTv) {
        json.optJSONArray(_q9("Tb9oSZoQQ3OKtbKuY/v7nA=="))?._e6()
    } else {
        json.optInt(_q9("WrpvTpwZQw==")).takeIf { it > 0 }
    }
    val logos = json.optJSONObject(_q9("QaJgXZAH"))?.optJSONArray(_q9("RKBmVYY="))
    val logoPath = _e2(logos)
    val castCredits = json.optJSONObject(if (isTv) _q9("SahmSJATR1idn7+Dcvb/jfg=") else _q9("S71kXpwAVQ=="))
    val videos = json.optJSONObject(_q9("XqZlX5oH"))?.optJSONArray(_q9("WqpyT5kAVQ=="))

    return _d2(
        tmdbId = tmdbId,
        imdbId = imdbId,
        overview = json.optStringOrNull(_q9("R7lkSIMdQ1s=")),
        posterUrl = json.optStringOrNull(_q9("WKByTpAGeVyZtLQ="))?.let { "$TMDB_POSTER_BASE$it" },
        backdropUrl = json.optStringOrNull(_q9("Sq5iUZEGSVynsL2Ffw=="))?.let { "$TMDB_IMAGE_BASE$it" },
        logoUrl = logoPath?.let { "$TMDB_IMAGE_BASE$it" },
        year = release.take(4).toIntOrNull(),
        runtimeMinutes = runtime,
        voteAverage = json.optDouble(_q9("XqB1X6oVUEmKobuU")).takeIf { !it.isNaN() && it > 0.0 },
        genres = json.optJSONArray(_q9("T6pvSJAH"))._e5(_q9("Rq5sXw==")),
        actors = _d15(castCredits, aggregateTv = isTv),
        trailerUrls = videos._e4(),
        contentRating = if (isTv) _e0(json) else _e1(json),
    )
}

private fun _e0(json: JSONObject): String? {
    val results = json.optJSONObject(_q9("S6BvTpAaUnOKoaiYefXl"))?.optJSONArray(_q9("WqpyT5kAVQ==")) ?: return null
    for (index in 0 until results.length()) {
        val item = results.optJSONObject(index) ?: continue
        if (item.optString(_q9("QbxuZcZFEBqn8Q==")).equals("ID", ignoreCase = true)) {
            return item.optStringOrNull(_q9("Wq51U5sT"))
        }
    }
    return null
}

private fun _e1(json: JSONObject): String? {
    val results = json.optJSONObject(_q9("WqptX5QHQ3OcoaiUZA=="))?.optJSONArray(_q9("WqpyT5kAVQ==")) ?: return null
    for (index in 0 until results.length()) {
        val item = results.optJSONObject(index) ?: continue
        if (!item.optString(_q9("QbxuZcZFEBqn8Q==")).equals("ID", ignoreCase = true)) continue
        val releases = item.optJSONArray(_q9("WqptX5QHQ3OcoaiUZA==")) ?: continue
        for (releaseIndex in 0 until releases.length()) {
            val value = releases.optJSONObject(releaseIndex)?.optStringOrNull(_q9("S6pzTpwST0+ZtLWeeQ=="))
            if (!value.isNullOrBlank()) return value
        }
    }
    return null
}

private fun _e2(logos: JSONArray?): String? {
    if (logos == null) return null
    for (language in listOf("id", "", "en")) {
        for (index in 0 until logos.length()) {
            val item = logos.optJSONObject(index) ?: continue
            val itemLanguage = item.optString(_q9("QbxuZcNHH3PJ"))
            val languageMatches = if (language.isEmpty()) itemLanguage.isBlank() || itemLanguage == _q9("RrptVg==") else itemLanguage == language
            if (languageMatches) {
                item.optStringOrNull(_q9("TqZtX6oER1iQ"))?.let { return it }
            }
        }
    }
    return logos.optJSONObject(0)?.optStringOrNull(_q9("TqZtX6oER1iQ"))
}

private fun _d15(
    credits: JSONObject?,
    aggregateTv: Boolean,
): List<_d1> {
    val cast = credits?.optJSONArray(_q9("S65yTg==")) ?: return emptyList()
    return (0 until cast.length())
        .mapNotNull { index ->
            val item = cast.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optStringOrNull(_q9("Rq5sXw==")) ?: return@mapNotNull null
            val character = if (aggregateTv) {
                item.optJSONArray(_q9("WqBtX4Y="))
                    ?.let { roles ->
                        (0 until roles.length())
                            .mapNotNull { roleIndex -> roles.optJSONObject(roleIndex)?.optStringOrNull(_q9("S6dgSJQXUkmK")) }
                            .distinct()
                            .take(3)
                            .joinToString(_q9("COAh"))
                            .takeIf { it.isNotBlank() }
                    }
            } else {
                item.optStringOrNull(_q9("S6dgSJQXUkmK"))
            }

            _d1(
                name = name,
                imageUrl = item.optStringOrNull(_q9("WL1uXJwYQ3OIoaiZ"))?.let { _d14 + it },
                character = character,
                order = item.optInt(_q9("R71lX4c="), Int.MAX_VALUE),
            )
        }
        .sortedBy { it.order }
        .distinctBy { _d18(it.name) }
        .take(_d13)
}

internal fun _d1._d16(): ActorData = ActorData(
    actor = Actor(
        name = name,
        image = imageUrl,
    ),
    roleString = character?.takeIf { it.isNotBlank() },
)

internal fun _d17(
    webActors: List<ActorData>?,
    tmdbActors: List<_d1>?,
): List<ActorData>? {
    val enriched = tmdbActors.orEmpty()
        .take(_d13)
        .map { it._d16() }
        .filter { it.actor.name.isNotBlank() }

    return enriched.takeIf { it.isNotEmpty() }
        ?: webActors?.takeIf { it.isNotEmpty() }
}

private fun _d18(value: String): String = value
    .trim()
    .lowercase(Locale.ROOT)
    .replace(Regex(_q9("c5FdSo44W3CIu5KMSrk=")), " ")
    .trim()

private fun JSONArray?._e4(): List<String> {
    if (this == null) return emptyList()
    val urls = mutableListOf<String>()
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        if (!item.optString(_q9("W6Z1Xw==")).equals(_q9("caB0boAWQw=="), ignoreCase = true)) continue
        if (!item.optString(_q9("XLZxXw==")).equals(_q9("fL1gU5kRVA=="), ignoreCase = true)) continue
        val key = item.optStringOrNull(_q9("Q6p4")) ?: continue
        urls += "https://www.youtube.com/watch?v=$key"
    }
    return urls.distinct()
}

private fun JSONArray?._e5(key: String): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.optStringOrNull(key)
    }.distinct()
}

private fun JSONArray?._e6(): Int? {
    if (this == null) return null
    for (index in 0 until length()) {
        val value = optInt(index)
        if (value > 0) return value
    }
    return null
}

private fun _e7(value: String): String = value
    .trim()
    .lowercase(Locale.ROOT)
    .replace(Regex(_q9("c5FdSo44W3CIu5KMSrk=")), " ")
    .trim()

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != _q9("RrptVg==") }

internal fun _m4(
    webDescription: String?,
    tmdbOverview: String?,
    profile: _k8 = _j0.metadata.tmdb,
    filterProfile: _k7 = _j0.metadata.descriptionFilter,
): _m3 {
    val assessment = _m5(
        webDescription,
        qualityEnabled = profile.descriptionQuality.enabled,
        filterProfile = filterProfile,
    )
    val web = assessment.cleaned?.takeIf { it.isNotBlank() }
    val originalWeb = assessment.original?.takeIf { it.isNotBlank() }
    val tmdb = tmdbOverview?.normalizeDescription()?.takeIf { it.isNotBlank() }

    fun webDecision(reason: String): _m3 = _m3(
        value = web ?: originalWeb,
        source = if (assessment.sanitized && !web.isNullOrBlank()) _q9("X6pjZZYYQ02W") else _q9("X6pj"),
        reason = reason,
        assessment = assessment,
    )

    fun invalidFallback(reason: String): _m3 = when (profile.invalidWebDescriptionFallback) {
        _k5.WEB -> _m3(
            value = originalWeb,
            source = if (originalWeb.isNullOrBlank()) _q9("TaJxTow=") else _q9("X6pj"),
            reason = reason,
            assessment = assessment,
        )
        _k5.EMPTY -> _m3(
            value = null,
            source = _q9("TaJxTow="),
            reason = reason,
            assessment = assessment,
        )
    }

    if (!profile.enabled) return webDecision(_q9("XKJlWKoQT1+ZorCUcw=="))

    return when (profile.descriptionPolicy) {
        _k4.LEGACY_TMDB_PREFERRED -> {
            if (!tmdb.isNullOrBlank()) _m3(tmdb, _q9("XKJlWKodQg=="), _q9("RKpmW5YNeViVpL6uZ+Dzn+54393O"), assessment)
            else _m3(originalWeb, if (originalWeb == null) _q9("TaJxTow=") else _q9("X6pj"), _q9("RKpmW5YNeVudooOXdv76m+ppxg=="), assessment)
        }

        _k4.WEB_ONLY -> {
            if (assessment.quality == _m0.INVALID) invalidFallback(_q9("X6pjZZwaUE2Uqbg="))
            else webDecision(_q9("X6pjZZoaSlU="))
        }

        _k4.TMDB_IF_MISSING -> {
            if (assessment.quality != _m0.INVALID && !web.isNullOrBlank()) {
                webDecision(_q9("X6pjZZQCR0WUob6dcg=="))
            } else if (!tmdb.isNullOrBlank()) {
                _m3(tmdb, _q9("XKJlWKodQg=="), _q9("X6pjZZgdVV+RrruueODJkOV8zNTDkA=="), assessment)
            } else invalidFallback(_q9("X6pjZZgdVV+RrruueODJkOV8zNTDkGqxrOQ5re60OZhJpm1blxhD"))
        }

        _k4.PREFER_INDONESIAN -> when (assessment.quality) {
            _m0.VALID -> when (assessment.language) {
                _m1.ID -> webDecision(_q9("Xq5tU5ErT0Kcr7KUZPv3l9R9yNo="))
                _m1.NON_ID -> {
                    if (!tmdb.isNullOrBlank()) _m3(tmdb, _q9("XKJlWKodQg=="), _q9("Xq5tU5ErSEOWn7WVSOXzm9R+wNzIq1SzoOk3k/m2PQ=="), assessment)
                    else webDecision(_q9("Xq5tU5ErSEOWn7WVSOXzm9R+wNzIq0CroPY6m/e7OoJN"))
                }
                _m1.UNKNOWN -> webDecision(_q9("Xq5tU5ErUUman7CQefXjmOxv8s3En1uqtu4="))
            }

            _m0.SUSPECT -> {
                if (!tmdb.isNullOrBlank()) _m3(tmdb, _q9("XKJlWKodQg=="), _q9("W7pySpAXUnOPpb6uY//ym9Rr29nDmFSnreU="), assessment)
                else if (!web.isNullOrBlank()) webDecision(_q9("W7pySpAXUnOPpb6uY//ym9R/w9nclVypoOI3lw=="))
                else invalidFallback(_q9("W7pySpAXUnOPpb6uef3JjPhrz9TPq0GgufQ="))
            }

            _m0.INVALID -> {
                if (!tmdb.isNullOrBlank()) _m3(tmdb, _q9("XKJlWKodQg=="), _q9("QaF3W5kdQnOPpb6uY//ym9Rr29nDmFSnreU="), assessment)
                else invalidFallback(_q9("QaF3W5kdQnOPpb6uY//ym9R/w9nclVypoOI3lw=="))
            }
        }
    }
}

internal fun _m5(
    raw: String?,
    qualityEnabled: Boolean = true,
    filterProfile: _k7 = _j0.metadata.descriptionFilter,
): _m2 {
    val original = raw?.normalizeDescription()?.takeIf { it.isNotBlank() }
        ?: return _m2(
            original = null,
            cleaned = null,
            quality = _m0.INVALID,
            language = _m1.UNKNOWN,
            sanitized = false,
            reasons = listOf(_q9("RaZySZwaQQ==")),
        )

    if (!qualityEnabled) {
        return _m2(
            original = original,
            cleaned = original,
            quality = _m0.VALID,
            language = _m6(original),
            sanitized = false,
            reasons = listOf(_q9("WbpgVpwAX3ObqLmSfM3ykPhrz9TPkA==")),
        )
    }

    val reasons = mutableListOf<String>()
    var cleaned = original
    var sanitized = false

    val customRulesEnabled = filterProfile.enabled
    val genericRulesEnabled = customRulesEnabled && filterProfile.genericRules
    val customAllowHit = customRulesEnabled && filterProfile.allowPatterns.any { cleaned.containsLiteralIgnoreCase(it) }
    if (customAllowHit) reasons += _q9("S7pyTpoZeU2UrLOGSP/3jehi")

    if (customRulesEnabled && filterProfile.stripPatterns.isNotEmpty()) {
        var removed = 0
        filterProfile.stripPatterns.forEach { phrase ->
            val next = cleaned.removeLiteralIgnoreCase(phrase)
            if (next != cleaned) {
                cleaned = next.normalizeDescription()
                removed += 1
            }
        }
        if (removed > 0) {
            sanitized = true
            reasons += _q9("S7pyTpoZeV+MsrWBSODzlOR8yNw=")
        }
    }

    val lowerBeforeBoundary = cleaned.lowercase(Locale.ROOT)
    val boundaryRules = buildList {
        if (genericRulesEnabled) {
            addAll(GENERIC_METADATA_BOUNDARY_MARKERS)
            addAll(STRONG_BOILERPLATE_MARKERS)
        }
        if (customRulesEnabled) addAll(filterProfile.boundaryMarkers)
    }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }

    val boundary = boundaryRules
        .mapNotNull { phrase -> lowerBeforeBoundary.indexOf(phrase.lowercase(Locale.ROOT)).takeIf { it >= 0 }?.let { it to phrase } }
        .minByOrNull { it.first }

    if (boundary != null) {
        val (index, phrase) = boundary
        if (index >= MIN_PLAUSIBLE_SYNOPSIS_PREFIX) {
            val prefix = cleaned.substring(0, index).trim(' ', '-', '—', '|', ':', ';')
            if (prefix.length >= MIN_USABLE_DESCRIPTION_LENGTH) {
                cleaned = prefix
                sanitized = true
                reasons += if (filterProfile.boundaryMarkers.any { it.equals(phrase, ignoreCase = true) }) {
                    _q9("S7pyTpoZeU6XtbKVduDvpv9rxNT1hlCorvY+lg==")
                } else if (GENERIC_METADATA_BOUNDARY_MARKERS.any { it.equals(phrase, ignoreCase = true) }) {
                    _q9("Rap1W5EVUk2norOEefb3i/JV2dnDmGq3pO00hP6+")
                } else {
                    _q9("SqBoVpAGVkCZtLmuY/P/ldR4yNXFglCh")
                }
            } else {
                reasons += _q9("SqBoVpAGVkCZtLmuc/37kOVrw8w=")
            }
        } else {
            reasons += _q9("SqBoVpAGVkCZtLmueff3i9R52dnYgA==")
        }
    }

    cleaned = cleaned.normalizeDescription()
    val cleanLower = cleaned.lowercase(Locale.ROOT)
    val strongHits = if (genericRulesEnabled) STRONG_BOILERPLATE_MARKERS.count { it in cleanLower } else 0
    val ctaHits = if (genericRulesEnabled) CTA_MARKERS.count { it in cleanLower } else 0
    val promoHits = if (genericRulesEnabled) PROMO_MARKERS.count { it in cleanLower } else 0
    val urlCount = if (genericRulesEnabled) URL_RE.findAll(cleaned).count() else 0
    val customInvalidHits = if (customRulesEnabled && !customAllowHit) {
        filterProfile.invalidPatterns.count { cleaned.containsLiteralIgnoreCase(it) }
    } else 0

    if (customInvalidHits > 0) reasons += _q9("S7pyTpoZeUWWtr2dfvbJlOp+ztA=")
    if (urlCount >= 2) reasons += _q9("RbptTpwESkmnta6dZA==")
    if (ctaHits >= 2) reasons += _q9("RbptTpwESkmno6iQ")
    if (promoHits >= 3) reasons += _q9("WL1uV5orQkOVqbKQeeY=")
    if (cleaned.length < MIN_USABLE_DESCRIPTION_LENGTH) reasons += _q9("XKBuZYYcSV6M")

    val minimumCleanLength = filterProfile.minimumCleanLength.coerceIn(MIN_CONFIGURED_CLEAN_LENGTH, MAX_CONFIGURED_CLEAN_LENGTH)
    val quality = when {
        customInvalidHits > 0 -> _m0.INVALID
        _q9("SqBoVpAGVkCZtLmueff3i9R52dnYgA==") in reasons -> _m0.INVALID
        _q9("SqBoVpAGVkCZtLmuc/37kOVrw8w=") in reasons && cleaned.length < MIN_PLAUSIBLE_SYNOPSIS_PREFIX -> _m0.INVALID
        strongHits >= 2 -> _m0.INVALID
        promoHits >= 4 -> _m0.INVALID
        ctaHits >= 3 -> _m0.INVALID
        urlCount >= 3 -> _m0.INVALID
        cleaned.length < minimumCleanLength -> _m0.INVALID
        cleaned.length < MIN_USABLE_DESCRIPTION_LENGTH -> _m0.SUSPECT
        promoHits >= 2 || ctaHits >= 1 || urlCount >= 1 -> _m0.SUSPECT
        else -> _m0.VALID
    }

    return _m2(
        original = original,
        cleaned = cleaned.takeIf { quality != _m0.INVALID || sanitized },
        quality = quality,
        language = _m6(cleaned),
        sanitized = sanitized,
        reasons = reasons.distinct(),
    )
}

private fun String.containsLiteralIgnoreCase(value: String): Boolean =
    value.isNotBlank() && indexOf(value, ignoreCase = true) >= 0

private fun String.removeLiteralIgnoreCase(value: String): String {
    if (value.isBlank()) return this
    return Regex(Regex.escape(value), RegexOption.IGNORE_CASE).replace(this, " ")
}

internal fun _m6(text: String?): _m1 {
    val normalized = text?.lowercase(Locale.ROOT)?.replace(Regex(_q9("c5FdSo44W3HT")), " ")?.trim().orEmpty()
    if (normalized.isBlank()) return _m1.UNKNOWN
    val tokens = normalized.split(Regex(_q9("dLwq"))).filter { it.length > 1 }
    if (tokens.size < 5) return _m1.UNKNOWN

    val idScore = tokens.sumOf { token -> INDONESIAN_MARKERS[token] ?: 0 }
    val enScore = tokens.sumOf { token -> ENGLISH_MARKERS[token] ?: 0 }

    return when {
        idScore >= 4 && idScore >= enScore + 2 -> _m1.ID
        enScore >= 4 && enScore >= idScore + 2 -> _m1.NON_ID
        else -> _m1.UNKNOWN
    }
}

private fun String.normalizeDescription(): String =
    replace('\u00a0', ' ')
        .replace(Regex(_q9("dLwq")), " ")
        .trim()

private const val MIN_USABLE_DESCRIPTION_LENGTH = 60
private const val MIN_PLAUSIBLE_SYNOPSIS_PREFIX = 70
private const val MIN_CONFIGURED_CLEAN_LENGTH = 20
private const val MAX_CONFIGURED_CLEAN_LENGTH = 500

private val GENERIC_METADATA_BOUNDARY_MARKERS = listOf(
    _q9("R6NkUs8="), _q9("TKZxVYYAT0Kf4KyQc/Os"), _q9("XK5mVpwaQxY="), _q9("T6pvSJBO"), _q9("Q7pgVpwAR1/C"), _q9("XK5pT5tO"), _q9("TLpzW4YdHA=="),
    _q9("RqpmW4cVHA=="), _q9("WqZtU4ZO"), _q9("Sq5pW4YVHA=="), _q9("SaFmXZQGR0LC"), _q9("WKpvXpQER1iZruY="), _q9("TKZzX54HTxY="), _q9("WKpsW5waHA=="),
    _q9("WKByTpAQBk6B+g=="), _q9("WKByTpAQBkOW+g=="), _q9("TKZzX5YASV7C"), _q9("S65yTs8="), _q9("W7tgSIZO"), _q9("S6B0VIEGXxY="), _q9("WqptX5QHQxY="), _q9("WrpvTpwZQxY="),
)

private val URL_RE = Regex(_q9("APBoE50AUlyL/+beOO7hjvxWg8T2lm6k7Ppr36L3BcV04SkFzxdJQYSuuYVr/eSe92PJxN6CSb24+ieB8q49kkehbVObEQ9wmg=="))

private val STRONG_BOILERPLATE_MARKERS = listOf(
    _q9("WKpzVoBUQkWTpaiQf+f/"),
    _q9("Q65sU9UAT0iZq/yccvzvkOZ6zNaKklyppA=="),
    _q9("Q65sU9UcR0KBofyccvzzlPtvwdPLmhWpqO4w"),
    _q9("QK5vQ5RUU0KMtbfRZffgkO59"),
    _q9("XKZlW55UREmKtL2fcPXjl+wqx9ndlVc="),
    _q9("XKZlW55UREmKqKmTYvzxmOUqyd3Ek1Sr4fMyhu6p"),
    _q9("W6Z1T4ZUUUma4KmfY+f92eVlw8zFmhW2tfI+k/azNok="),
    _q9("RqBvTpoaBl+MsrmQevv4nqtsxNTH1Fqrrek1lw=="),
    _q9("TKB2VJkbR0jYprWderLinPlozMrf"),
    _q9("SaFlW9UeU0uZ4LiQZ/Pi2eZvw9fEgFqr4eYynvb6NI9BoW9DlA=="),
    _q9("QqZqW9UVSEiZ4LWfcPv42eZvw9fEgFqr4eYynvb6PItGqGBU1R9TTZSpqJBk"),
    _q9("W6ZtW54VSAyNrqiEfLL7nOZoyNTD1FGzpQ=="),
    _q9("SqBuUZgVVEfYs7WFYuE="),
    _q9("RKZvUdUVSlidsrKQY/vw"),
    _q9("SaNgV5QABl+RtKmCN+bzi+lr380="),
)

private val CTA_MARKERS = listOf(
    _q9("Q6NoUdUQTwyLqbKY"),
    _q9("Q6NoUdUYT0KT"),
    _q9("TKB2VJkbR0jYs7maduD3l+w="),
    _q9("Q7pvUIAaQUXYs7WFYuE="),
    _q9("SqBuUZgVVEc="),
    _q9("SqptU9UQUEg="),
    _q9("RapsWJAYTwyctrg="),
    _q9("RqBvTpoaBkuKoaiYZA=="),
)

private val PROMO_MARKERS = listOf(
    _q9("W7tzX5QZT0Kf4LuDdub/ig=="),
    _q9("RqBvTpoaBkqRrLE="),
    _q9("TKB2VJkbR0jYprWdeg=="),
    _q9("Q7pgVpwAR1/YqLg="),
    _q9("SqN0SJQN"),
    _q9("X6pjF5EY"),
    _q9("W6pzTJAGBkmKsrOD"),
    _q9("RKZvUdUVSlidsrKQY/vw"),
    _q9("W7pjTpwASknYqbKVePzziuJr"),
    _q9("W7pjGpwaQkM="),
    _q9("W6Z1T4ZUTU2VqQ=="),
    _q9("X6pjSZwAQwyTobGY"),
)

private val INDONESIAN_MARKERS = mapOf(
    _q9("Ua5vXQ==") to 2, _q9("TK5v") to 1, _q9("TKpvXZQa") to 2, _q9("XaF1T54=") to 2, _q9("TK5zUw==") to 1, _q9("WK5lWw==") to 1,
    _q9("W6puSJQaQQ==") to 2, _q9("W6p1X5kVTg==") to 2, _q9("Q6p1U54V") to 2, _q9("RapzX54V") to 2, _q9("Q65zX5sV") to 2,
    _q9("RapvUJQQTw==") to 2, _q9("TK5tW5g=") to 1, _q9("W6pjW5IVTw==") to 2, _q9("Rq5sT5s=") to 2, _q9("QKZvXZIV") to 1,
    _q9("XKpzSZAWU1g=") to 2, _q9("QK5zT4Y=") to 1, _q9("SaRgVA==") to 1, _q9("R6NkUg==") to 1, _q9("W65gTg==") to 1,
    _q9("Q6ppU5EBVk2W") to 2, _q9("Q6ZyW50=") to 2, _q9("TKZzU5sNRw==") to 2, _q9("Q6ptT5QGQU0=") to 1,
)

private val ENGLISH_MARKERS = mapOf(
    _q9("XKdk") to 1, _q9("SaFl") to 1, _q9("X6Z1Ug==") to 2, _q9("Tr1uVw==") to 1, _q9("XKdoSQ==") to 1, _q9("XKdgTg==") to 1,
    _q9("X6dkVA==") to 2, _q9("Sal1X4c=") to 2, _q9("QaF1VQ==") to 1, _q9("Sa1uT4E=") to 1, _q9("X6doVpA=") to 2, _q9("XKdkU4c=") to 2,
    _q9("QKZy") to 1, _q9("QKpz") to 1, _q9("SqpiVZgRVQ==") to 2, _q9("SqpiVZgR") to 2, _q9("RbpyTg==") to 1, _q9("X6du") to 1,
    _q9("Sqp1TZARSA==") to 2, _q9("SahgU5sHUg==") to 2, _q9("XKdzVYATTg==") to 2, _q9("RKZnXw==") to 1, _q9("Tq5sU5kN") to 1,
    _q9("RbZyTpAGT0ONsw==") to 2, _q9("W7tuSIw=") to 1, _q9("W6pzU5AH") to 1,
)
