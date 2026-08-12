package com.agooseangsa.DrakorKita

import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject

private val TMDB_BASE = _q9("MfvBXY8Pdx6bPunARsT9nVTR31K2t/h9/1CSwQ==")
private val TMDB_LANGUAGE = _q9("MOuYZLg=")
private val TMDB_READ_ACCESS_TOKEN: String
    get() = BuildConfig.TMDB_READ_ACCESS_TOKEN
private val TMDB_API_KEY: String
    get() = BuildConfig.TMDB_API_KEY

private fun hasTmdbCredential(): Boolean =
    TMDB_READ_ACCESS_TOKEN.isNotBlank() || TMDB_API_KEY.isNotBlank()

private fun tmdbHeaders(): Map<String, String> = mutableMapOf(
    _q9("OOzWSIxB") to _q9("OP/FQZVWOUWTIe7BWN/3ng=="),
).apply {
    if (TMDB_READ_ACCESS_TOKEN.isNotBlank()) {
        this[_q9("GPrBRZNHMUubOumBXA==")] = "Bearer $TMDB_READ_ACCESS_TOKEN"
    }
}

private fun tmdbParams(vararg values: Pair<String, String>): Map<String, String> =
    mutableMapOf(*values).apply {
        if (TMDB_READ_ACCESS_TOKEN.isBlank() && TMDB_API_KEY.isNotBlank()) {
            this[_q9("OP/ccpdQIQ==")] = TMDB_API_KEY
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
)

suspend fun fetchAgooseTmdbMetadata(identity: AgooseTmdbIdentity): AgooseTmdbMetadata? {
    if (!hasTmdbCredential()) return null

    return runCatching {
        val tmdbId = identity.tmdbId
            ?: identity.imdbId?.let { findTmdbIdByImdb(it, identity.isTv) }
            ?: searchTmdbId(identity)
            ?: return@runCatching null

        val typePath = if (identity.isTv) "tv" else _q9("NODDRJk=")
        val json = JSONObject(
            app.get(
                "$TMDB_BASE/$typePath/$tmdbId",
                headers = tmdbHeaders(),
                params = tmdbParams(
                    _q9("Ne7bSolUP1Q=") to TMDB_LANGUAGE,
                    _q9("OP/FSJJRB0WVEfKLQdz3nkjC") to _q9("PPfBSI5bOV2lJ+Sd"),
                ),
            ).text,
        )

        val release = if (identity.isTv) json.optString(_q9("P+bHXohqOViIEeSPRsk=")) else json.optString(_q9("K+rZSJ1GPW6eL/SL"))
        val externalIds = json.optJSONObject(_q9("PPfBSI5bOV2lJ+Sd"))
        val imdbId = if (identity.isTv) externalIds?.optStringOrNull(_q9("MOLRT6NcPA==")) else json.optStringOrNull(_q9("MOLRT6NcPA=="))

        AgooseTmdbMetadata(
            tmdbId = tmdbId,
            imdbId = imdbId,
            localizedTitle = json.optStringOrNull(if (identity.isTv) _q9("N+7YSA==") else _q9("LebBQZk=")),
            originalTitle = json.optStringOrNull(if (identity.isTv) _q9("Nv3cSpVbOV2lIOGDVw==") else _q9("Nv3cSpVbOV2lOumaXsk=")),
            overview = json.optStringOrNull(_q9("NvnQX4pcPUY=")),
            posterPath = json.optStringOrNull(_q9("KeDGWZlHB0GbOug=")),
            backdropPath = json.optStringOrNull(_q9("O+7WRphHN0GlPuGaWg==")),
            year = release.take(4).toIntOrNull(),
            runtimeMinutes = if (identity.isTv) null else json.optInt(_q9("K/rbWZVYPQ==")).takeIf { it > 0 },
            voteAverage = json.optDouble(_q9("L+DBSKNULlSIL+eL")).takeIf { !it.isNaN() && it > 0.0 },
            genres = json.optJSONArray(_q9("PurbX5lG")).stringValues(_q9("N+7YSA==")),
        )
    }.getOrNull()
}

private suspend fun findTmdbIdByImdb(imdbId: String, isTv: Boolean): Int? {
    if (!Regex(_q9("B/vBcZgefA==")).matches(imdbId)) return null

    val json = JSONObject(
        app.get(
            "$TMDB_BASE/find/$imdbId",
            headers = tmdbHeaders(),
            params = tmdbParams(
                _q9("PPfBSI5bOV2lPe+bQM/9") to _q9("MOLRT6NcPA=="),
                _q9("Ne7bSolUP1Q=") to TMDB_LANGUAGE,
            ),
        ).text,
    )

    val key = if (isTv) _q9("LfnqX5lGLV2OPQ==") else _q9("NODDRJlqKlSJO+yaQQ==")
    return json.optJSONArray(key)?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
}

private suspend fun searchTmdbId(identity: AgooseTmdbIdentity): Int? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    val typePath = if (identity.isTv) "tv" else _q9("NODDRJk=")
    val yearParam = if (identity.isTv) _q9("P+bHXohqOViIEeSPRsnHiV7GxA==") else _q9("IOrUXw==")

    for (query in queries) {
        val params = tmdbParams(
            _q9("Ne7bSolUP1Q=") to TMDB_LANGUAGE,
            _q9("KPrQX4U=") to query,
        ).toMutableMap()
        identity.year?.let { params[yearParam] = it.toString() }

        val results = JSONObject(
            app.get(
                "$TMDB_BASE/search/$typePath",
                headers = tmdbHeaders(),
                params = params,
            ).text,
        ).optJSONArray(_q9("K+rGWJBBKw==")) ?: continue

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
    val titleKey = if (identity.isTv) _q9("N+7YSA==") else _q9("LebBQZk=")
    val originalKey = if (identity.isTv) _q9("Nv3cSpVbOV2lIOGDVw==") else _q9("Nv3cSpVbOV2lOumaXsk=")
    val dateKey = if (identity.isTv) _q9("P+bHXohqOViIEeSPRsk=") else _q9("K+rZSJ1GPW6eL/SL")

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
    .replace(Regex(_q9("AtHpXYd5JW2KNc6Tb4c=")), " ")
    .trim()

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != _q9("N/rZQQ==") }

private fun JSONArray?.stringValues(key: String): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.optString(key)?.trim()?.takeIf { it.isNotBlank() }
    }
}
