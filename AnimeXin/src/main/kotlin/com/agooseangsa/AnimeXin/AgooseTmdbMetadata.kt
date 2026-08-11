package com.agooseangsa.AnimeXin

import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject

private const val TMDB_BASE = "https://api.themoviedb.org/3"
private const val TMDB_LANGUAGE = "id-ID"
private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"
private val TMDB_READ_ACCESS_TOKEN: String get() = BuildConfig.TMDB_READ_ACCESS_TOKEN
private val TMDB_API_KEY: String get() = BuildConfig.TMDB_API_KEY

internal data class AgooseTmdbIdentity(
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val originalTitle: String? = null,
    val displayTitle: String,
    val year: Int? = null,
    val isTv: Boolean,
)

internal data class AgooseTmdbActor(
    val name: String,
    val character: String? = null,
    val profileUrl: String? = null,
)

internal data class AgooseTmdbMetadata(
    val tmdbId: Int,
    val imdbId: String? = null,
    val localizedTitle: String? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val runtimeMinutes: Int? = null,
    val voteAverage: Double? = null,
    val actors: List<AgooseTmdbActor> = emptyList(),
    val trailers: List<String> = emptyList(),
)

private fun hasTmdbCredential(): Boolean =
    TMDB_READ_ACCESS_TOKEN.isNotBlank() || TMDB_API_KEY.isNotBlank()

private fun tmdbHeaders(): Map<String, String> = mutableMapOf(
    _q9("cslgx3vH") to _q9("ctpzzmLQE28razjjh3f60w=="),
).apply {
    if (TMDB_READ_ACCESS_TOKEN.isNotBlank()) this[_q9("Ut93ymTBG2EjcD+jgw==")] = "Bearer $TMDB_READ_ACCESS_TOKEN"
}

private fun tmdbParams(vararg values: Pair<String, String>): Map<String, String> =
    mutableMapOf(*values).apply {
        if (TMDB_READ_ACCESS_TOKEN.isBlank() && TMDB_API_KEY.isNotBlank()) this[_q9("ctpq/WDWCw==")] = TMDB_API_KEY
    }

internal suspend fun fetchAgooseTmdbMetadata(identity: AgooseTmdbIdentity): AgooseTmdbMetadata? {
    if (!hasTmdbCredential()) return null
    return runCatching {
        val tmdbId = identity.tmdbId
            ?: identity.imdbId?.let { findTmdbIdByImdb(it, identity.isTv) }
            ?: searchTmdbId(identity)
            ?: return@runCatching null

        val typePath = if (identity.isTv) "tv" else _q9("fsV1y24=")
        val json = JSONObject(
            app.get(
                "$TMDB_BASE/$typePath/$tmdbId",
                headers = tmdbHeaders(),
                params = tmdbParams(
                    _q9("f8ttxX7SFX4=") to TMDB_LANGUAGE,
                    _q9("ctpzx2XXLW8tWySpnnT600rA") to _q9("dtJ3x3ndE3cdbTK/wWfn2F3MyEJisUc9YO6i"),
                ),
            ).text,
        )

        val release = if (identity.isTv) json.optString(_q9("dcNx0X/sE3IwWzKtmWE=")) else json.optString(_q9("Yc9vx2rAF0QmZSKp"))
        val imdbId = if (identity.isTv) {
            json.optJSONObject(_q9("dtJ3x3ndE3cdbTK/"))?.optStringOrNull(_q9("esdnwFTaFg=="))
        } else {
            json.optStringOrNull(_q9("esdnwFTaFg=="))
        }
        val runtime = if (identity.isTv) {
            json.optJSONArray(_q9("dtpq0WTXF0QwcTiTmW342A=="))?.optInt(0)?.takeIf { it > 0 }
        } else {
            json.optInt(_q9("Yd9t1mLeFw==")).takeIf { it > 0 }
        }

        AgooseTmdbMetadata(
            tmdbId = tmdbId,
            imdbId = imdbId,
            localizedTitle = json.optStringOrNull(if (identity.isTv) _q9("fctuxw==") else _q9("Z8N3zm4=")),
            overview = json.optStringOrNull(_q9("fNxm0H3aF2w=")),
            posterUrl = json.optStringOrNull(_q9("Y8Vw1m7BLWsjcD4="))?.let { "$TMDB_IMAGE_BASE/w500$it" },
            backdropUrl = json.optStringOrNull(_q9("cctgyW/BHWsddDe4hQ=="))?.let { "$TMDB_IMAGE_BASE/original$it" },
            year = release.take(4).toIntOrNull(),
            runtimeMinutes = runtime,
            voteAverage = json.optDouble(_q9("ZcV3x1TSBH4wZTGp")).takeIf { !it.isNaN() && it > 0.0 },
            actors = json.optJSONObject(_q9("cNhmxmLHAQ=="))?.optJSONArray(_q9("cMtw1g==")).toActors(),
            trailers = json.optJSONObject(_q9("ZcNnx2TA"))?.optJSONArray(_q9("Yc9w12fHAQ==")).toTrailers(),
        )
    }.getOrNull()
}

private suspend fun findTmdbIdByImdb(imdbId: String, isTv: Boolean): Int? {
    if (!Regex(_q9("Td53/m+YVg==")).matches(imdbId)) return null
    val json = JSONObject(
        app.get(
            "$TMDB_BASE/find/$imdbId",
            headers = tmdbHeaders(),
            params = tmdbParams(
                _q9("dtJ3x3ndE3cddzm5n2fw") to _q9("esdnwFTaFg=="),
                _q9("f8ttxX7SFX4=") to TMDB_LANGUAGE,
            ),
        ).text,
    )
    val key = if (isTv) _q9("Z9xc0G7AB3c2dw==") else _q9("fsV1y27sAH4xcTq4ng==")
    return json.optJSONArray(key)?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
}

private suspend fun searchTmdbId(identity: AgooseTmdbIdentity): Int? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val typePath = if (identity.isTv) "tv" else _q9("fsV1y24=")
    val yearParam = if (identity.isTv) _q9("dcNx0X/sE3IwWzKtmWHKxFzEzg==") else _q9("as9i0A==")

    for (query in queries) {
        val params = tmdbParams(
            _q9("f8ttxX7SFX4=") to TMDB_LANGUAGE,
            _q9("Yt9m0HI=") to query,
        ).toMutableMap()
        identity.year?.let { params[yearParam] = it.toString() }

        val results = JSONObject(
            app.get(
                "$TMDB_BASE/search/$typePath",
                headers = tmdbHeaders(),
                params = params,
            ).text,
        ).optJSONArray(_q9("Yc9w12fHAQ==")) ?: continue

        for (index in 0 until minOf(results.length(), 5)) {
            val candidate = results.optJSONObject(index) ?: continue
            if (candidate.matchesIdentity(identity)) return candidate.optInt("id").takeIf { it > 0 }
        }
    }
    return null
}

private fun JSONObject.matchesIdentity(identity: AgooseTmdbIdentity): Boolean {
    val titleKey = if (identity.isTv) _q9("fctuxw==") else _q9("Z8N3zm4=")
    val originalKey = if (identity.isTv) _q9("fNhqxWLdE3cdajehiA==") else _q9("fNhqxWLdE3cdcD+4gWE=")
    val dateKey = if (identity.isTv) _q9("dcNx0X/sE3IwWzKtmWE=") else _q9("Yc9vx2rAF0QmZSKp")

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
    .replace(Regex(_q9("SPRf0nD/D0cyfxixsC8=")), " ")
    .trim()

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != _q9("fd9vzg==") }

private fun JSONArray?.toActors(): List<AgooseTmdbActor> {
    if (this == null) return emptyList()
    return (0 until minOf(length(), 20)).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        val name = item.optStringOrNull(_q9("fctuxw==")) ?: return@mapNotNull null
        AgooseTmdbActor(
            name = name,
            character = item.optStringOrNull(_q9("cMJi0GrQBn4w")),
            profileUrl = item.optStringOrNull(_q9("Y9hsxGLfF0QyZSKk"))?.let { "$TMDB_IMAGE_BASE/w185$it" },
        )
    }
}

private fun JSONArray?.toTrailers(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        val site = item.optString(_q9("YMN3xw=="))
        val type = item.optString(_q9("Z9Nzxw=="))
        val key = item.optStringOrNull(_q9("eM96")) ?: return@mapNotNull null
        if (!site.equals(_q9("SsV29n7RFw=="), ignoreCase = true)) return@mapNotNull null
        if (!type.equals(_q9("R9hiy2fWAA=="), ignoreCase = true) && !type.equals(_q9("R89i0W7B"), ignoreCase = true)) return@mapNotNull null
        "https://www.youtube.com/watch?v=$key"
    }.distinct()
}
