package com.agooseangsa.Layarkaca21

import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val TMDB_BASE = "https://api.themoviedb.org/3"
private const val TMDB_LANGUAGE = "id-ID"
private val TMDB_READ_ACCESS_TOKEN: String
    get() = BuildConfig.TMDB_READ_ACCESS_TOKEN
private val TMDB_API_KEY: String
    get() = BuildConfig.TMDB_API_KEY
private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w500"
private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/original"

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

internal suspend fun _d5(identity: _d0): _d2? {
    if (!_d10()) return null
    val cacheKey = listOf(
        identity.tmdbId?.toString().orEmpty(),
        identity.imdbId.orEmpty(),
        identity.originalTitle.orEmpty(),
        identity.displayTitle,
        identity.year?.toString().orEmpty(),
        identity.isTv.toString(),
    ).joinToString("|")

    _d3.withLock {
        if (_d4.containsKey(cacheKey)) return _d4[cacheKey]
    }

    val metadata = runCatching {
        val tmdbId = identity.tmdbId
            ?: identity.imdbId?.let { _d6(it, identity.isTv) }
            ?: _d7(identity)
            ?: return@runCatching null
        _d9(tmdbId, identity.isTv)
    }.getOrNull()

    _d3.withLock { _d4[cacheKey] = metadata }
    return metadata
}

private suspend fun _d6(imdbId: String, isTv: Boolean): Int? {
    if (!Regex(_q9("drt1ZpFfAg==")).matches(imdbId)) return null
    val json = JSONObject(
        app.get(
            "$TMDB_BASE/find/$imdbId",
            headers = _d11(),
            params = _d12(
                _q9("Tbd1X4caR0Cns7OEZfHz") to _q9("QaJlWKodQg=="),
                _q9("RK5vXYAVQUk=") to TMDB_LANGUAGE,
            ),
        ).text,
    )
    val key = if (isTv) _q9("XLleSJAHU0CMsw==") else _q9("RaB3U5ArVEmLtbCFZA==")
    return json.optJSONArray(key)?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
}

private suspend fun _d7(identity: _d0): Int? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val typePath = if (identity.isTv) "tv" else _q9("RaB3U5A=")
    val yearKey = if (identity.isTv) _q9("TqZzSYErR0WKn7iQY/fJgO5r3w==") else _q9("UapgSA==")

    for (query in queries) {
        val params = _d12(
            _q9("RK5vXYAVQUk=") to TMDB_LANGUAGE,
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

private suspend fun _d9(tmdbId: Int, isTv: Boolean): _d2? {
    val typePath = if (isTv) "tv" else _q9("RaB3U5A=")
    val append = if (isTv) {
        _q9("Tbd1X4caR0CnqbiCO/HknO9j2cuGglyhpO8o3vK3OYlNvC1ZmhpSSZa0g4N25v+X7Hk=")
    } else {
        _q9("Tbd1X4caR0CnqbiCO/HknO9j2cuGglyhpO8o3vK3OYlNvC1IkBhDTYulg5V25vOK")
    }
    val json = JSONObject(
        app.get(
            "$TMDB_BASE/$typePath/$tmdbId",
            headers = _d11(),
            params = _d12(
                _q9("RK5vXYAVQUk=") to TMDB_LANGUAGE,
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
    val cast = json.optJSONObject(_q9("S71kXpwAVQ=="))?.optJSONArray(_q9("S65yTg=="))
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
        actors = cast._e3(),
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

private fun JSONArray?._e3(): List<_d1> {
    if (this == null) return emptyList()
    val actors = mutableListOf<_d1>()
    for (index in 0 until minOf(length(), 20)) {
        val item = optJSONObject(index) ?: continue
        val name = item.optStringOrNull(_q9("Rq5sXw==")) ?: continue
        val image = item.optStringOrNull(_q9("WL1uXJwYQ3OIoaiZ"))?.let { "$TMDB_POSTER_BASE$it" }
        actors += _d1(name, image)
    }
    return actors
}

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
