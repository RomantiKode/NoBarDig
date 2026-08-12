package com.agooseangsa.Donghub

import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private const val TMDB_BASE = "https://api.themoviedb.org/3"
private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"
private const val TMDB_LANGUAGE = "id-ID"

private val TMDB_READ_ACCESS_TOKEN: String
    get() = BuildConfig.TMDB_READ_ACCESS_TOKEN
private val TMDB_API_KEY: String
    get() = BuildConfig.TMDB_API_KEY

private val tmdbCacheMutex = Mutex()
private val tmdbCache = mutableMapOf<String, AgooseTmdbMetadata?>()

internal data class AgooseTmdbIdentity(
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val originalTitle: String? = null,
    val displayTitle: String,
    val year: Int? = null,
    val isTv: Boolean,
)

internal data class AgooseTmdbMetadata(
    val tmdbId: Int,
    val imdbId: String? = null,
    val localizedTitle: String? = null,
    val originalTitle: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val runtimeMinutes: Int? = null,
    val voteAverage: Double? = null,
    val genres: List<String> = emptyList(),
    val trailerUrls: List<String> = emptyList(),
)

internal suspend fun _a8(identity: AgooseTmdbIdentity): AgooseTmdbMetadata? {
    if (!hasTmdbCredential()) return null

    val cacheKey = listOf(
        if (identity.isTv) "tv" else _qD9("GC0qkV0="),
        identity.tmdbId?.toString().orEmpty(),
        identity.imdbId.orEmpty(),
        identity.originalTitle.orEmpty(),
        identity.displayTitle,
        identity.year?.toString().orEmpty(),
    ).joinToString("|")

    tmdbCacheMutex.withLock {
        if (tmdbCache.containsKey(cacheKey)) return tmdbCache[cacheKey]
    }

    val metadata = runCatching {
        val tmdbId = identity.tmdbId
            ?: identity.imdbId?.let { findTmdbIdByImdb(it, identity.isTv) }
            ?: _a9(identity)
            ?: return@runCatching null

        val typePath = if (identity.isTv) "tv" else _qD9("GC0qkV0=")
        val json = JSONObject(
            app.get(
                "$TMDB_BASE/$typePath/$tmdbId",
                headers = tmdbHeaders(),
                params = tmdbParams(
                    _qD9("GSMyn01RSZ4=") to TMDB_LANGUAGE,
                    _qD9("FDIsnVZUcY9VogirC3135T2u") to _qD9("EDoonUpeT5dllB69VHtx7yukfg=="),
                ),
            ).text,
        )

        if (!detailMatchesIdentity(json, identity)) return@runCatching null

        val release = if (identity.isTv) json.optString(_qD9("Eysui0xvT5JIoh6vDGg=")) else json.optString(_qD9("BycwnVlDS6RenA6r"))
        val externalIds = json.optJSONObject(_qD9("EDoonUpeT5dllB69"))
        val imdbId = if (identity.isTv) externalIds?.optStringOrNull(_qD9("HC84mmdZSg==")) else json.optStringOrNull(_qD9("HC84mmdZSg=="))
        val runtime = if (identity.isTv) {
            json.optJSONArray(_qD9("EDI1i1dUS6RIiBSRDGR17g=="))?.intValues()?.firstOrNull { it > 0 }
        } else {
            json.optInt(_qD9("BzcyjFFdSw==")).takeIf { it > 0 }
        }

        AgooseTmdbMetadata(
            tmdbId = tmdbId,
            imdbId = imdbId,
            localizedTitle = json.optStringOrNull(if (identity.isTv) _qD9("GyMxnQ==") else _qD9("ASsolF0=")),
            originalTitle = json.optStringOrNull(if (identity.isTv) _qD9("GjA1n1FeT5dlkxujHQ==") else _qD9("GjA1n1FeT5dliRO6FGg=")),
            overview = json.optStringOrNull(_qD9("GjQ5ik5ZS4w=")),
            posterUrl = tmdbImage(json.optStringOrNull(_qD9("BS0vjF1CcYtbiRI=")), _qD9("AnVkyA==")),
            backdropUrl = tmdbImage(json.optStringOrNull(_qD9("FyM/k1xCQYtljRu6EA==")), _qD9("AnNuwAg=")),
            year = release.take(4).toIntOrNull(),
            runtimeMinutes = runtime,
            voteAverage = json.optDouble(_qD9("Ay0onWdRWJ5InB2r")).takeIf { !it.isNaN() && it > 0.0 },
            genres = json.optJSONArray(_qD9("Eicyil1D")).stringValues(_qD9("GyMxnQ==")),
            trailerUrls = json.optJSONObject(_qD9("Ays4nVdD"))?.optJSONArray(_qD9("BycvjVREXQ==")).youtubeTrailerUrls(),
        )
    }.getOrNull()

    tmdbCacheMutex.withLock { tmdbCache[cacheKey] = metadata }
    return metadata
}

private fun hasTmdbCredential(): Boolean =
    TMDB_READ_ACCESS_TOKEN.isNotBlank() || TMDB_API_KEY.isNotBlank()

private fun tmdbHeaders(): Map<String, String> = mutableMapOf(
    _qD9("FCE/nUhE") to _qD9("FDIslFFTT49TkhThEn535Q=="),
).apply {
    if (TMDB_READ_ACCESS_TOKEN.isNotBlank()) {
        this[_qD9("NDcokFdCR4FbiROhFg==")] = "Bearer $TMDB_READ_ACCESS_TOKEN"
    }
}

private fun tmdbParams(vararg values: Pair<String, String>): Map<String, String> =
    mutableMapOf(*values).apply {
        if (TMDB_READ_ACCESS_TOKEN.isBlank() && TMDB_API_KEY.isNotBlank()) {
            this[_qD9("FDI1p1NVVw==")] = TMDB_API_KEY
        }
    }

private suspend fun findTmdbIdByImdb(imdbId: String, isTv: Boolean): Int? {
    if (!Regex(_qD9("KzYopFwbCg==")).matches(imdbId)) return null

    val json = JSONObject(
        app.get(
            "$TMDB_BASE/find/$imdbId",
            headers = tmdbHeaders(),
            params = tmdbParams(
                _qD9("EDoonUpeT5dljhW7Cm59") to _qD9("HC84mmdZSg=="),
                _qD9("GSMyn01RSZ4=") to TMDB_LANGUAGE,
            ),
        ).text,
    )

    val key = if (isTv) _qD9("ATQDil1DW5dOjg==") else _qD9("GC0qkV1vXJ5JiBa6Cw==")
    return json.optJSONArray(key)?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
}

private suspend fun _a9(identity: AgooseTmdbIdentity): Int? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val typePath = if (identity.isTv) "tv" else _qD9("GC0qkV0=")
    val yearParam = if (identity.isTv) _qD9("Eysui0xvT5JIoh6vDGhH8iuqfw==") else _qD9("DCc9ig==")

    for (query in queries) {
        val params = tmdbParams(
            _qD9("GSMyn01RSZ4=") to TMDB_LANGUAGE,
            _qD9("BDc5ikE=") to query,
        ).toMutableMap()
        identity.year?.let { params[yearParam] = it.toString() }

        val results = JSONObject(
            app.get(
                "$TMDB_BASE/search/$typePath",
                headers = tmdbHeaders(),
                params = params,
            ).text,
        ).optJSONArray(_qD9("BycvjVREXQ==")) ?: continue

        for (index in 0 until minOf(results.length(), 5)) {
            val candidate = results.optJSONObject(index) ?: continue
            if (candidate._b0(identity)) {
                return candidate.optInt("id").takeIf { it > 0 }
            }
        }
    }
    return null
}

private fun JSONObject._b0(identity: AgooseTmdbIdentity): Boolean {
    val titleKey = if (identity.isTv) _qD9("GyMxnQ==") else _qD9("ASsolF0=")
    val originalKey = if (identity.isTv) _qD9("GjA1n1FeT5dlkxujHQ==") else _qD9("GjA1n1FeT5dliRO6FGg=")
    val dateKey = if (identity.isTv) _qD9("Eysui0xvT5JIoh6vDGg=") else _qD9("BycwnVlDS6RenA6r")

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

private fun detailMatchesIdentity(json: JSONObject, identity: AgooseTmdbIdentity): Boolean {
    val titleKey = if (identity.isTv) _qD9("GyMxnQ==") else _qD9("ASsolF0=")
    val originalKey = if (identity.isTv) _qD9("GjA1n1FeT5dlkxujHQ==") else _qD9("GjA1n1FeT5dliRO6FGg=")
    val dateKey = if (identity.isTv) _qD9("Eysui0xvT5JIoh6vDGg=") else _qD9("BycwnVlDS6RenA6r")
    val expectedTitles = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map(::normalizeTitleForTmdbMatch)
        .filter { it.isNotBlank() }
        .toSet()
    val actualTitles = listOfNotNull(json.optStringOrNull(titleKey), json.optStringOrNull(originalKey))
        .map(::normalizeTitleForTmdbMatch)
        .filter { it.isNotBlank() }
        .toSet()
    if (expectedTitles.intersect(actualTitles).isEmpty()) return false

    val tmdbYear = json.optString(dateKey).take(4).toIntOrNull()
    if (identity.year != null && tmdbYear != null && identity.year != tmdbYear) return false
    return true
}

private fun normalizeTitleForTmdbMatch(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex(_qD9("LhwAiEN8U6dKhjSzJSY=")), " ")
    .trim()

private fun tmdbImage(path: String?, size: String): String? =
    path?.takeIf { it.startsWith("/") }?.let { "$TMDB_IMAGE_BASE/$size$it" }

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != _qD9("GzcwlA==") }

private fun JSONArray?.stringValues(key: String): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.optString(key)?.trim()?.takeIf { it.isNotBlank() }
    }
}

private fun JSONArray.intValues(): List<Int> =
    (0 until length()).mapNotNull { index -> optInt(index).takeIf { it > 0 } }

private fun JSONArray?.youtubeTrailerUrls(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        val site = item.optString(_qD9("BisonQ=="))
        val type = item.optString(_qD9("ATssnQ=="))
        val key = item.optString(_qD9("Hicl")).trim()
        if (!site.equals(_qD9("LC0prE1SSw=="), ignoreCase = true) ||
            !type.equals(_qD9("ITA9kVRVXA=="), ignoreCase = true) ||
            key.isBlank()
        ) {
            null
        } else {
            "https://www.youtube.com/watch?v=$key"
        }
    }.distinct()
}
