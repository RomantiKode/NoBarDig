package com.agooseangsa.MidasXXI

import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private const val TMDB_BASE = "https://api.themoviedb.org/3"
private const val TMDB_LANGUAGE = "id-ID"
private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"

private val tmdbReadAccessToken: String
    get() = BuildConfig.TMDB_READ_ACCESS_TOKEN
private val tmdbApiKey: String
    get() = BuildConfig.TMDB_API_KEY

private val tmdbCacheMutex = Mutex()
private val tmdbCache = mutableMapOf<String, AgooseTmdbMetadata?>()

internal data class AgooseTmdbIdentity(
    val originalTitle: String?,
    val displayTitle: String,
    val year: Int?,
    val isTv: Boolean,
)

internal data class AgooseTmdbMetadata(
    val tmdbId: Int,
    val imdbId: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val runtimeMinutes: Int?,
    val voteAverage: Double?,
    val genres: List<String>,
    val trailers: List<String>,
)

internal suspend fun fetchAgooseTmdbMetadata(identity: AgooseTmdbIdentity): AgooseTmdbMetadata? {
    if (!hasTmdbCredential()) return null

    val key = listOf(
        identity.isTv.toString(),
        identity.originalTitle.orEmpty(),
        identity.displayTitle,
        identity.year?.toString().orEmpty(),
    ).joinToString("|")

    tmdbCacheMutex.withLock {
        if (tmdbCache.containsKey(key)) return tmdbCache[key]
    }

    val result = runCatching { _b6(identity) }.getOrNull()
    tmdbCacheMutex.withLock { tmdbCache[key] = result }
    return result
}

private suspend fun _b6(identity: AgooseTmdbIdentity): AgooseTmdbMetadata? {
    val tmdbId = _b7(identity) ?: return null
    val typePath = if (identity.isTv) "tv" else _q9("ZeAmpV8=")
    val json = JSONObject(
        app.get(
            "$TMDB_BASE/$typePath/$tmdbId",
            headers = tmdbHeaders(),
            params = tmdbParams(
                _q9("ZO4+q08eaiU=") to TMDB_LANGUAGE,
                _q9("af8gqVQbUjQ3aSAIIHNhMi2P") to _q9("bfckqUgRbCwHXzYef3VnODuFrA=="),
            ),
        ).text,
    )

    val releaseDate = if (identity.isTv) {
        json.optString(_q9("buYiv04gbCkqaTYMJ2Y="))
    } else {
        json.optString(_q9("euo8qVsMaB88VyYI"))
    }
    val imdbId = if (identity.isTv) {
        json.optJSONObject(_q9("bfckqUgRbCwHXzYe"))?.optStringOrNull(_q9("YeI0rmUWaQ=="))
    } else {
        json.optStringOrNull(_q9("YeI0rmUWaQ=="))
    }
    val runtime = if (identity.isTv) {
        json.optJSONArray(_q9("bf85v1UbaB8qQzwyJ2pjOQ=="))?.optInt(0)?.takeIf { it > 0 }
    } else {
        json.optInt(_q9("evo+uFMSaA==")).takeIf { it > 0 }
    }
    val trailers = json.optJSONObject(_q9("fuY0qVUM"))
        ?.optJSONArray(_q9("euojuVYLfg=="))
        .objects()
        .filter { item ->
            item.optString(_q9("e+YkqQ==")).equals(_q9("UeAlmE8daA=="), ignoreCase = true) &&
                item.optString(_q9("fPYgqQ==")).equals(_q9("XP0xpVYafw=="), ignoreCase = true)
        }
        .mapNotNull { it.optStringOrNull(_q9("Y+op")) }
        .distinct()
        .map { "https://www.youtube.com/watch?v=$it" }

    return AgooseTmdbMetadata(
        tmdbId = tmdbId,
        imdbId = imdbId,
        overview = json.optStringOrNull(_q9("Z/k1vkwWaDc=")),
        posterUrl = json.optStringOrNull(_q9("eOAjuF8NUjA5Qjo="))?.let { imageUrl(_q9("f7pg/A=="), it) },
        backdropUrl = json.optStringOrNull(_q9("au4zp14NYjAHRjMZOw=="))?.let { imageUrl(_q9("f75i9Ao="), it) },
        year = releaseDate.take(4).toIntOrNull(),
        runtimeMinutes = runtime,
        voteAverage = json.optDouble(_q9("fuAkqWUeeyUqVzUI")).takeIf { !it.isNaN() && it > 0.0 },
        genres = json.optJSONArray(_q9("b+o+vl8M")).stringValues(_q9("Zu49qQ==")),
        trailers = trailers,
    )
}

private suspend fun _b7(identity: AgooseTmdbIdentity): Int? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val typePath = if (identity.isTv) "tv" else _q9("ZeAmpV8=")
    val yearParam = if (identity.isTv) _q9("buYiv04gbCkqaTYMJ2ZRJTuLrQ==") else _q9("ceoxvg==")

    for (query in queries) {
        val params = tmdbParams(
            _q9("ZO4+q08eaiU=") to TMDB_LANGUAGE,
            _q9("efo1vkM=") to query,
        ).toMutableMap()
        identity.year?.let { params[yearParam] = it.toString() }

        val results = JSONObject(
            app.get(
                "$TMDB_BASE/search/$typePath",
                headers = tmdbHeaders(),
                params = params,
            ).text,
        ).optJSONArray(_q9("euojuVYLfg==")) ?: continue

        for (index in 0 until minOf(results.length(), 5)) {
            val candidate = results.optJSONObject(index) ?: continue
            if (candidate._b8(identity)) {
                return candidate.optInt("id").takeIf { it > 0 }
            }
        }
    }
    return null
}

private fun JSONObject._b8(identity: AgooseTmdbIdentity): Boolean {
    val titleKey = if (identity.isTv) _q9("Zu49qQ==") else _q9("fOYkoF8=")
    val originalKey = if (identity.isTv) _q9("Z/05q1MRbCwHWDMANg==") else _q9("Z/05q1MRbCwHQjsZP2Y=")
    val dateKey = if (identity.isTv) _q9("buYiv04gbCkqaTYMJ2Y=") else _q9("euo8qVsMaB88VyYI")

    val candidateTitles = listOfNotNull(optStringOrNull(titleKey), optStringOrNull(originalKey))
        .map(::normalizeTitle)
        .filter { it.isNotBlank() }
        .toSet()
    val expectedTitles = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map(::normalizeTitle)
        .filter { it.isNotBlank() }
        .toSet()

    if (candidateTitles.intersect(expectedTitles).isEmpty()) return false

    val candidateYear = optString(dateKey).take(4).toIntOrNull()
    if (identity.year != null && candidateYear != null && identity.year != candidateYear) return false
    return true
}

private fun hasTmdbCredential(): Boolean =
    tmdbReadAccessToken.isNotBlank() || tmdbApiKey.isNotBlank()

private fun tmdbHeaders(): Map<String, String> = mutableMapOf(
    _q9("aewzqUoL") to _q9("af8goFMcbDQxWTxCOXBhMg=="),
).apply {
    if (tmdbReadAccessToken.isNotBlank()) this[_q9("SfokpFUNZDo5QjsCPQ==")] = "Bearer $tmdbReadAccessToken"
}

private fun tmdbParams(vararg values: Pair<String, String>): Map<String, String> =
    mutableMapOf(*values).apply {
        if (tmdbReadAccessToken.isBlank() && tmdbApiKey.isNotBlank()) this[_q9("af85k1EadA==")] = tmdbApiKey
    }

private fun normalizeTitle(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex(_q9("U9EMvEEzcBwoTRwQDig=")), " ")
    .trim()

private fun imageUrl(size: String, path: String): String = "$TMDB_IMAGE_BASE/$size$path"

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != _q9("Zvo8oA==") }

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull(::optJSONObject)
}

private fun JSONArray?.stringValues(key: String): List<String> =
    objects().mapNotNull { it.optStringOrNull(key) }
