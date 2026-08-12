package com.agooseangsa.Terbit21

import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

private const val TMDB_BASE = "https://api.themoviedb.org/3"
private const val TMDB_LANGUAGE = "id-ID"
private const val TMDB_IMAGE_W500 = "https://image.tmdb.org/t/p/w500"
private const val TMDB_IMAGE_W1280 = "https://image.tmdb.org/t/p/w1280"

private val TMDB_READ_ACCESS_TOKEN: String
    get() = BuildConfig.TMDB_READ_ACCESS_TOKEN
private val TMDB_API_KEY: String
    get() = BuildConfig.TMDB_API_KEY

private val tmdbCache = ConcurrentHashMap<String, AgooseTmdbMetadata>()
private val tmdbMisses = ConcurrentHashMap.newKeySet<String>()

private fun hasTmdbCredential(): Boolean =
    TMDB_READ_ACCESS_TOKEN.isNotBlank() || TMDB_API_KEY.isNotBlank()

private fun tmdbHeaders(): Map<String, String> = mutableMapOf(
    _q9("BlnOEyod") to _q9("BkrdGjMKPcKpU/f49dzDlQ=="),
).apply {
    if (TMDB_READ_ACCESS_TOKEN.isNotBlank()) {
        this[_q9("Jk/ZHjUbNcyhSPC48Q==")] = "Bearer $TMDB_READ_ACCESS_TOKEN"
    }
}

private fun tmdbParams(vararg values: Pair<String, String>): Map<String, String> =
    mutableMapOf(*values).apply {
        if (TMDB_READ_ACCESS_TOKEN.isBlank() && TMDB_API_KEY.isNotBlank()) {
            this[_q9("BkrEKTEMJQ==")] = TMDB_API_KEY
        }
    }

data class AgooseTmdbIdentity(
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val originalTitle: String? = null,
    val displayTitle: String,
    val year: Int? = null,
    val isTv: Boolean,
)

data class AgooseTmdbMetadata(
    val tmdbId: Int,
    val imdbId: String? = null,
    val localizedTitle: String? = null,
    val originalTitle: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val year: Int? = null,
    val runtimeMinutes: Int? = null,
    val voteAverage: Double? = null,
    val genres: List<String> = emptyList(),
) {
    fun posterUrl(): String? = posterPath?.takeIf { it.startsWith('/') }?.let { "$TMDB_IMAGE_W500$it" }
    fun backdropUrl(): String? = backdropPath?.takeIf { it.startsWith('/') }?.let { "$TMDB_IMAGE_W1280$it" }
}

suspend fun fetchAgooseTmdbMetadata(identity: AgooseTmdbIdentity): AgooseTmdbMetadata? {
    if (!hasTmdbCredential()) return null

    val cacheKey = listOf(
        identity.tmdbId?.toString().orEmpty(),
        identity.imdbId.orEmpty(),
        identity.originalTitle.orEmpty(),
        identity.displayTitle,
        identity.year?.toString().orEmpty(),
        if (identity.isTv) "tv" else _q9("ClXbHz8="),
    ).joinToString("|")

    tmdbCache[cacheKey]?.let { return it }
    if (cacheKey in tmdbMisses) return null

    val result = runCatching {
        val tmdbId = identity.tmdbId
            ?: identity.imdbId?.let { findTmdbIdByImdb(it, identity.isTv) }
            ?: searchTmdbId(identity)
            ?: return@runCatching null

        val typePath = if (identity.isTv) "tv" else _q9("ClXbHz8=")
        val json = JSONObject(
            app.get(
                "$TMDB_BASE/$typePath/$tmdbId",
                headers = tmdbHeaders(),
                params = tmdbParams(
                    _q9("C1vDES8IO9M=") to TMDB_LANGUAGE,
                    _q9("BkrdEzQNA8KvY+uy7N/DlSf9") to _q9("AkLZEygHPdqfVf2k"),
                ),
            ).text,
        )

        val release = if (identity.isTv) json.optString(_q9("AVPfBS42Pd+yY/2268o=")) else json.optString(_q9("FV/BEzsaOemkXe2y"))
        val externalIds = json.optJSONObject(_q9("AkLZEygHPdqfVf2k"))
        val imdbId = if (identity.isTv) {
            externalIds?.optStringOrNull(_q9("DlfJFAUAOA=="))
        } else {
            json.optStringOrNull(_q9("DlfJFAUAOA=="))
        }

        AgooseTmdbMetadata(
            tmdbId = tmdbId,
            imdbId = imdbId,
            localizedTitle = json.optStringOrNull(if (identity.isTv) _q9("CVvAEw==") else _q9("E1PZGj8=")),
            originalTitle = json.optStringOrNull(if (identity.isTv) _q9("CEjEETMHPdqfUvi6+g==") else _q9("CEjEETMHPdqfSPCj88o=")),
            overview = json.optStringOrNull(_q9("CEzIBCwAOcE=")),
            posterPath = json.optStringOrNull(_q9("F1XeAj8bA8ahSPE=")),
            backdropPath = json.optStringOrNull(_q9("BVvOHT4bM8afTPij9w==")),
            year = release.take(4).toIntOrNull(),
            runtimeMinutes = if (identity.isTv) null else json.optInt(_q9("FU/DAjMEOQ==")).takeIf { it > 0 },
            voteAverage = json.optDouble(_q9("EVXZEwUIKtOyXf6y")).takeIf { !it.isNaN() && it > 0.0 },
            genres = json.optJSONArray(_q9("AF/DBD8a")).stringValues(_q9("CVvAEw==")),
        )
    }.getOrNull()

    if (result == null) tmdbMisses += cacheKey else tmdbCache[cacheKey] = result
    return result
}

private suspend fun findTmdbIdByImdb(imdbId: String, isTv: Boolean): Int? {
    if (!Regex(_q9("OU7ZKj5CeA==")).matches(imdbId)) return null
    val json = JSONObject(
        app.get(
            "$TMDB_BASE/find/$imdbId",
            headers = tmdbHeaders(),
            params = tmdbParams(
                _q9("AkLZEygHPdqfT/ai7czJ") to _q9("DlfJFAUAOA=="),
                _q9("C1vDES8IO9M=") to TMDB_LANGUAGE,
            ),
        ).text,
    )
    val key = if (isTv) _q9("E0zyBD8aKdq0Tw==") else _q9("ClXbHz82LtOzSfWj7A==")
    return json.optJSONArray(key)?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
}

private suspend fun searchTmdbId(identity: AgooseTmdbIdentity): Int? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val typePath = if (identity.isTv) "tv" else _q9("ClXbHz8=")
    val yearParam = if (identity.isTv) _q9("AVPfBS42Pd+yY/2268rzgjH5QA==") else _q9("Hl/MBA==")

    for (query in queries) {
        val params = tmdbParams(
            _q9("C1vDES8IO9M=") to TMDB_LANGUAGE,
            _q9("Fk/IBCM=") to query,
        ).toMutableMap()
        identity.year?.let { params[yearParam] = it.toString() }

        val results = JSONObject(
            app.get(
                "$TMDB_BASE/search/$typePath",
                headers = tmdbHeaders(),
                params = params,
            ).text,
        ).optJSONArray(_q9("FV/eAzYdLw==")) ?: continue

        for (index in 0 until minOf(results.length(), 5)) {
            val candidate = results.optJSONObject(index) ?: continue
            if (candidate.matchesIdentity(identity)) {
                return candidate.optInt("id").takeIf { it > 0 }
            }
        }
    }
    return null
}

private fun JSONObject.matchesIdentity(identity: AgooseTmdbIdentity): Boolean {
    val titleKey = if (identity.isTv) _q9("CVvAEw==") else _q9("E1PZGj8=")
    val originalKey = if (identity.isTv) _q9("CEjEETMHPdqfUvi6+g==") else _q9("CEjEETMHPdqfSPCj88o=")
    val dateKey = if (identity.isTv) _q9("AVPfBS42Pd+yY/2268o=") else _q9("FV/BEzsaOemkXe2y")

    val candidateTitles = listOfNotNull(optStringOrNull(titleKey), optStringOrNull(originalKey))
        .map(::normalizeTitleForTmdbMatch)
        .filter { it.isNotBlank() }
        .toSet()
    val expectedTitles = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map(::normalizeTitleForTmdbMatch)
        .filter { it.isNotBlank() }
        .toSet()

    if (candidateTitles.intersect(expectedTitles).isEmpty()) return false
    val candidateYear = optString(dateKey).take(4).toIntOrNull()
    if (identity.year != null && candidateYear != null && identity.year != candidateYear) return false
    return true
}

private fun normalizeTitleForTmdbMatch(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex(_q9("PGTxBiElIeqwR9eqwoQ=")), " ")
    .trim()

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != _q9("CU/BGg==") }

private fun JSONArray?.stringValues(key: String): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.optString(key)?.trim()?.takeIf { it.isNotBlank() }
    }
}
