package com.agooseangsa.Filmlokal

import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject

private const val TMDB_BASE = "https://api.themoviedb.org/3"
private const val TMDB_LANGUAGE = "id-ID"
private val TMDB_READ_ACCESS_TOKEN: String
    get() = BuildConfig.TMDB_READ_ACCESS_TOKEN
private val TMDB_API_KEY: String
    get() = BuildConfig.TMDB_API_KEY

private fun hasTmdbCredential(): Boolean =
    TMDB_READ_ACCESS_TOKEN.isNotBlank() || TMDB_API_KEY.isNotBlank()

private fun tmdbHeaders(): Map<String, String> = mutableMapOf(
    _q9("ucFKLX6S") to _q9("udJZJGeFi3kImU2Ouuoaiw=="),
).apply {
    if (TMDB_READ_ACCESS_TOKEN.isNotBlank()) {
        this[_q9("mdddIGGUg3cAgkrOvg==")] = "Bearer $TMDB_READ_ACCESS_TOKEN"
    }
}

private fun tmdbParams(vararg values: Pair<String, String>): Map<String, String> =
    mutableMapOf(*values).apply {
        if (TMDB_READ_ACCESS_TOKEN.isBlank() && TMDB_API_KEY.isNotBlank()) {
            this[_q9("udJAF2WDkw==")] = TMDB_API_KEY
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

suspend fun _a5(identity: AgooseTmdbIdentity): AgooseTmdbMetadata? {
    if (!hasTmdbCredential()) return null

    return runCatching {
        val tmdbId = identity.tmdbId
            ?: identity.imdbId?.let { _a6(it, identity.isTv) }
            ?: _a7(identity)
            ?: return@runCatching null

        val typePath = if (identity.isTv) "tv" else _q9("tc1fIWs=")
        val json = JSONObject(
            app.get(
                "$TMDB_BASE/$typePath/$tmdbId",
                headers = tmdbHeaders(),
                params = tmdbParams(
                    _q9("tMNHL3uHjWg=") to TMDB_LANGUAGE,
                    _q9("udJZLWCCtXkOqVHEo+kai+us") to _q9("vdpdLXyIi2E+n0fS"),
                ),
            ).text,
        )

        val release = if (identity.isTv) json.optString(_q9("vstbO3q5i2QTqUfApPw=")) else json.optString(_q9("qsdFLW+Vj1IFl1fE"))
        val externalIds = json.optJSONObject(_q9("vdpdLXyIi2E+n0fS"))
        val imdbId = if (identity.isTv) externalIds?.optStringOrNull(_q9("sc9NKlGPjg==")) else json.optStringOrNull(_q9("sc9NKlGPjg=="))

        AgooseTmdbMetadata(
            tmdbId = tmdbId,
            imdbId = imdbId,
            localizedTitle = json.optStringOrNull(if (identity.isTv) _q9("tsNELQ==") else _q9("rMtdJGs=")),
            originalTitle = json.optStringOrNull(if (identity.isTv) _q9("t9BAL2eIi2E+mELMtQ==") else _q9("t9BAL2eIi2E+gkrVvPw=")),
            overview = json.optStringOrNull(_q9("t9RMOniPj3o=")),
            posterPath = json.optStringOrNull(_q9("qM1aPGuUtX0Agks=")),
            backdropPath = json.optStringOrNull(_q9("usNKI2qUhX0+hkLVuA==")),
            year = release.take(4).toIntOrNull(),
            runtimeMinutes = if (identity.isTv) null else json.optInt(_q9("qtdHPGeLjw==")).takeIf { it > 0 },
            voteAverage = json.optDouble(_q9("rs1dLVGHnGgTl0TE")).takeIf { !it.isNaN() && it > 0.0 },
            genres = json.optJSONArray(_q9("v8dHOmuV")).stringValues(_q9("tsNELQ==")),
        )
    }.getOrNull()
}

private suspend fun _a6(imdbId: String, isTv: Boolean): Int? {
    if (!Regex(_q9("htZdFGrNzg==")).matches(imdbId)) return null

    val json = JSONObject(
        app.get(
            "$TMDB_BASE/find/$imdbId",
            headers = tmdbHeaders(),
            params = tmdbParams(
                _q9("vdpdLXyIi2E+hUzUovoQ") to _q9("sc9NKlGPjg=="),
                _q9("tMNHL3uHjWg=") to TMDB_LANGUAGE,
            ),
        ).text,
    )

    val key = if (isTv) _q9("rNR2OmuVn2EVhQ==") else _q9("tc1fIWu5mGgSg0/Vow==")
    return json.optJSONArray(key)?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
}

private suspend fun _a7(identity: AgooseTmdbIdentity): Int? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    val typePath = if (identity.isTv) "tv" else _q9("tc1fIWs=")
    val yearParam = if (identity.isTv) _q9("vstbO3q5i2QTqUfApPwqnP2oQw==") else _q9("ocdIOg==")

    for (query in queries) {
        val params = tmdbParams(
            _q9("tMNHL3uHjWg=") to TMDB_LANGUAGE,
            _q9("qddMOnc=") to query,
        ).toMutableMap()
        identity.year?.let { params[yearParam] = it.toString() }

        val results = JSONObject(
            app.get(
                "$TMDB_BASE/search/$typePath",
                headers = tmdbHeaders(),
                params = params,
            ).text,
        ).optJSONArray(_q9("qsdaPWKSmQ==")) ?: continue

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
    val titleKey = if (identity.isTv) _q9("tsNELQ==") else _q9("rMtdJGs=")
    val originalKey = if (identity.isTv) _q9("t9BAL2eIi2E+mELMtQ==") else _q9("t9BAL2eIi2E+gkrVvPw=")
    val dateKey = if (identity.isTv) _q9("vstbO3q5i2QTqUfApPw=") else _q9("qsdFLW+Vj1IFl1fE")

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
    .replace(Regex(_q9("g/x1OHWql1ERjW3cjbI=")), " ")
    .trim()

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != _q9("ttdFJA==") }

private fun JSONArray?.stringValues(key: String): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.optString(key)?.trim()?.takeIf { it.isNotBlank() }
    }
}
