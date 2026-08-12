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
    val typePath = if (identity.isTv) "tv" else _q9("EthjHxs=")
    val json = JSONObject(
        app.get(
            "$TMDB_BASE/$typePath/$tmdbId",
            headers = tmdbHeaders(),
            params = tmdbParams(
                _q9("E9Z7EQtj6FI=") to TMDB_LANGUAGE,
                _q9("HsdlExBm0EPMSm9JytU/oGfl") to _q9("Gs9hEwxs7lv8fHlfldM5qnHvuA=="),
            ),
        ).text,
    )

    val releaseDate = if (identity.isTv) {
        json.optString(_q9("Gd5nBQpd7l7RSnlNzcA="))
    } else {
        json.optString(_q9("DdJ5Ex9x6mjHdGlJ"))
    }
    val imdbId = if (identity.isTv) {
        json.optJSONObject(_q9("Gs9hEwxs7lv8fHlf"))?.optStringOrNull(_q9("FtpxFCFr6w=="))
    } else {
        json.optStringOrNull(_q9("FtpxFCFr6w=="))
    }
    val runtime = if (identity.isTv) {
        json.optJSONArray(_q9("Gsd8BRFm6mjRYHNzzcw9qw=="))?.optInt(0)?.takeIf { it > 0 }
    } else {
        json.optInt(_q9("DcJ7Ahdv6g==")).takeIf { it > 0 }
    }
    val trailers = json.optJSONObject(_q9("Cd5xExFx"))
        ?.optJSONArray(_q9("DdJmAxJ2/A=="))
        .objects()
        .filter { item ->
            item.optString(_q9("DN5hEw==")).equals(_q9("JthgIgtg6g=="), ignoreCase = true) &&
                item.optString(_q9("C85lEw==")).equals(_q9("K8V0HxJn/Q=="), ignoreCase = true)
        }
        .mapNotNull { it.optStringOrNull(_q9("FNJs")) }
        .distinct()
        .map { "https://www.youtube.com/watch?v=$it" }

    return AgooseTmdbMetadata(
        tmdbId = tmdbId,
        imdbId = imdbId,
        overview = json.optStringOrNull(_q9("EMFwBAhr6kA=")),
        posterUrl = json.optStringOrNull(_q9("D9hmAhtw0EfCYXU="))?.let { imageUrl(_q9("CIIlRg=="), it) },
        backdropUrl = json.optStringOrNull(_q9("HdZ2HRpw4Ef8ZXxY0Q=="))?.let { imageUrl(_q9("CIYnTk4="), it) },
        year = releaseDate.take(4).toIntOrNull(),
        runtimeMinutes = runtime,
        voteAverage = json.optDouble(_q9("CdhhEyFj+VLRdHpJ")).takeIf { !it.isNaN() && it > 0.0 },
        genres = json.optJSONArray(_q9("GNJ7BBtx")).stringValues(_q9("EdZ4Ew==")),
        trailers = trailers,
    )
}

private suspend fun _b7(identity: AgooseTmdbIdentity): Int? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val typePath = if (identity.isTv) "tv" else _q9("EthjHxs=")
    val yearParam = if (identity.isTv) _q9("Gd5nBQpd7l7RSnlNzcAPt3HhuQ==") else _q9("BtJ0BA==")

    for (query in queries) {
        val params = tmdbParams(
            _q9("E9Z7EQtj6FI=") to TMDB_LANGUAGE,
            _q9("DsJwBAc=") to query,
        ).toMutableMap()
        identity.year?.let { params[yearParam] = it.toString() }

        val results = JSONObject(
            app.get(
                "$TMDB_BASE/search/$typePath",
                headers = tmdbHeaders(),
                params = params,
            ).text,
        ).optJSONArray(_q9("DdJmAxJ2/A==")) ?: continue

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
    val titleKey = if (identity.isTv) _q9("EdZ4Ew==") else _q9("C95hGhs=")
    val originalKey = if (identity.isTv) _q9("EMV8ERds7lv8e3xB3A==") else _q9("EMV8ERds7lv8YXRY1cA=")
    val dateKey = if (identity.isTv) _q9("Gd5nBQpd7l7RSnlNzcA=") else _q9("DdJ5Ex9x6mjHdGlJ")

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
    _q9("HtR2Ew52") to _q9("HsdlGhdh7kPKenMD09Y/oA=="),
).apply {
    if (tmdbReadAccessToken.isNotBlank()) this[_q9("PsJhHhFw5k3CYXRD1w==")] = "Bearer $tmdbReadAccessToken"
}

private fun tmdbParams(vararg values: Pair<String, String>): Map<String, String> =
    mutableMapOf(*values).apply {
        if (tmdbReadAccessToken.isBlank() && tmdbApiKey.isNotBlank()) this[_q9("Hsd8KRVn9g==")] = tmdbApiKey
    }

private fun normalizeTitle(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex(_q9("JOlJBgVO8mvTblNR5I4=")), " ")
    .trim()

private fun imageUrl(size: String, path: String): String = "$TMDB_IMAGE_BASE/$size$path"

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != _q9("EcJ5Gg==") }

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull(::optJSONObject)
}

private fun JSONArray?.stringValues(key: String): List<String> =
    objects().mapNotNull { it.optStringOrNull(key) }
