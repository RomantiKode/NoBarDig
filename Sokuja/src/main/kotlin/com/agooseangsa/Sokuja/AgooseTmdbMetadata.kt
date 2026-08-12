package com.agooseangsa.Sokuja

import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private const val TMDB_BASE = "https://api.themoviedb.org/3"
private const val TMDB_LANGUAGE = "id-ID"
private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/original"

private val TMDB_READ_ACCESS_TOKEN: String
    get() = BuildConfig.TMDB_READ_ACCESS_TOKEN
private val TMDB_API_KEY: String
    get() = BuildConfig.TMDB_API_KEY

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
    val profileUrl: String? = null,
)

internal data class AgooseTmdbMetadata(
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
    val actors: List<AgooseTmdbActor> = emptyList(),
    val trailerUrls: List<String> = emptyList(),
    val contentRating: String? = null,
    val status: String? = null,
)

private val tmdbCacheMutex = Mutex()
private val tmdbCache = mutableMapOf<String, AgooseTmdbMetadata?>()

private fun hasTmdbCredential(): Boolean =
    TMDB_READ_ACCESS_TOKEN.isNotBlank() || TMDB_API_KEY.isNotBlank()

private fun tmdbHeaders(): Map<String, String> = mutableMapOf(
    _q9("ow3cCXQk") to _q9("ox7PAG0zATNFVanwtpvbrQ=="),
).apply {
    if (TMDB_READ_ACCESS_TOKEN.isNotBlank()) {
        this[_q9("gxvLBGsiCT1NTq6wsg==")] = "Bearer $TMDB_READ_ACCESS_TOKEN"
    }
}

private fun tmdbParams(vararg values: Pair<String, String>): Map<String, String> =
    mutableMapOf(*values).apply {
        if (TMDB_READ_ACCESS_TOKEN.isBlank() && TMDB_API_KEY.isNotBlank()) {
            this[_q9("ox7WM281GQ==")] = TMDB_API_KEY
        }
    }

internal suspend fun _b0(identity: AgooseTmdbIdentity): AgooseTmdbMetadata? {
    if (!hasTmdbCredential()) return null

    val cacheKey = listOf(
        identity.tmdbId?.toString().orEmpty(),
        identity.imdbId.orEmpty(),
        identity.originalTitle.orEmpty(),
        identity.displayTitle,
        identity.year?.toString().orEmpty(),
        identity.isTv.toString(),
    ).joinToString("|")

    tmdbCacheMutex.withLock {
        if (tmdbCache.containsKey(cacheKey)) return tmdbCache[cacheKey]
    }

    val metadata = runCatching {
        val tmdbId = identity.tmdbId
            ?: identity.imdbId?.let { _b1(it, identity.isTv) }
            ?: _b2(identity)
            ?: return@runCatching null

        _b3(tmdbId, identity.isTv)
    }.getOrNull()

    tmdbCacheMutex.withLock {
        tmdbCache[cacheKey] = metadata
    }
    return metadata
}

private suspend fun _b1(imdbId: String, isTv: Boolean): Int? {
    if (!Regex(_q9("nBrLMGB7RA==")).matches(imdbId)) return null

    val response = app.get(
        "$TMDB_BASE/find/$imdbId",
        headers = tmdbHeaders(),
        params = tmdbParams(
            _q9("pxbLCXY+AStzSaiqrovR") to _q9("qwPbDls5BA=="),
            _q9("rg/RC3ExByI=") to TMDB_LANGUAGE,
        ),
    )
    if (!response.isSuccessful) return null

    val json = JSONObject(response.text)
    val key = if (isTv) _q9("thjgHmEjFStYSQ==") else _q9("rwHJBWEPEiJfT6urrw==")
    return json.optJSONArray(key)
        ?.optJSONObject(0)
        ?.optInt("id")
        ?.takeIf { it > 0 }
}

private suspend fun _b2(identity: AgooseTmdbIdentity): Int? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    val typePath = if (identity.isTv) "tv" else _q9("rwHJBWE=")
    val yearParam = if (identity.isTv) _q9("pAfNH3APAS5eZaO+qI3ruqchBg==") else _q9("uwveHg==")

    for (query in queries) {
        val params = tmdbParams(
            _q9("rg/RC3ExByI=") to TMDB_LANGUAGE,
            _q9("sxvaHn0=") to query,
        ).toMutableMap()
        identity.year?.let { params[yearParam] = it.toString() }

        val response = app.get(
            "$TMDB_BASE/search/$typePath",
            headers = tmdbHeaders(),
            params = params,
        )
        if (!response.isSuccessful) continue

        val results = JSONObject(response.text).optJSONArray(_q9("sAvMGWgkEw==")) ?: continue
        for (index in 0 until minOf(results.length(), 5)) {
            val candidate = results.optJSONObject(index) ?: continue
            if (candidate.matchesIdentity(identity)) {
                return candidate.optInt("id").takeIf { it > 0 }
            }
        }
    }
    return null
}

private suspend fun _b3(tmdbId: Int, isTv: Boolean): AgooseTmdbMetadata? {
    val typePath = if (isTv) "tv" else _q9("rwHJBWE=")
    val append = if (isTv) {
        _q9("pxbLCXY+AStzU6Os8J7dp6cvB99x3wjj6/Bbvh9sa/2nHZMPaz4UIkJOmK29nN2tpTM=")
    } else {
        _q9("pxbLCXY+AStzU6Os8J7dp6cvB99x3wjj6/Bbvh9sa/2nHZMeYTwFJl9fmLu9nNGw")
    }

    val response = app.get(
        "$TMDB_BASE/$typePath/$tmdbId",
        headers = tmdbHeaders(),
        params = tmdbParams(
            _q9("rg/RC3ExByI=") to TMDB_LANGUAGE,
            _q9("ox7PCWo0PzNDZbW6r5jbrbEl") to append,
            _q9("qwDcAHE0BRhFV6a4ubfYoqwnAZJ1yA==") to _q9("qwqTAnE8DGtJVA=="),
        ),
    )
    if (!response.isSuccessful) return null

    val json = JSONObject(response.text)
    val release = json.optString(if (isTv) _q9("pAfNH3APAS5eZaO+qI0=") else _q9("sAvTCWUjBRhIW7O6"))
    val externalIds = json.optJSONObject(_q9("pxbLCXY+AStzU6Os"))
    val imdbId = if (isTv) {
        externalIds?.optStringOrNull(_q9("qwPbDls5BA=="))
    } else {
        json.optStringOrNull(_q9("qwPbDls5BA==")) ?: externalIds?.optStringOrNull(_q9("qwPbDls5BA=="))
    }

    val runtime = if (isTv) {
        json.optJSONArray(_q9("px7WH2s0BRheT6mAqIHZpg=="))
            ?.optInt(0)
            ?.takeIf { it > 0 }
    } else {
        json.optInt(_q9("sBvRGG09BQ==")).takeIf { it > 0 }
    }

    return AgooseTmdbMetadata(
        tmdbId = tmdbId,
        imdbId = imdbId,
        overview = json.optStringOrNull(_q9("rRjaHnI5BTA=")),
        posterUrl = json.optStringOrNull(_q9("sgHMGGEiPzdNTq8="))?.toTmdbImageUrl(),
        backdropUrl = json.optStringOrNull(_q9("oA/cB2AiDzdzSqartA=="))?.toTmdbImageUrl(),
        logoUrl = json.optJSONObject(_q9("qwPeC2Ej"))?.optJSONArray(_q9("rgHYA3c=")).bestLogoPath()?.toTmdbImageUrl(),
        year = release.take(4).toIntOrNull(),
        runtimeMinutes = runtime,
        voteAverage = json.optDouble(_q9("tAHLCVsxFiJeW6C6")).takeIf { !it.isNaN() && it > 0.0 },
        genres = json.optJSONArray(_q9("pQvRHmEj")).stringValues(_q9("rA/SCQ==")),
        actors = json.optJSONObject(_q9("oRzaCG0kEw=="))?.optJSONArray(_q9("oQ/MGA==")).actors(),
        trailerUrls = json.optJSONObject(_q9("tAfbCWsj"))?.optJSONArray(_q9("sAvMGWgkEw==")).youtubeTrailers(),
        contentRating = if (isTv) {
            json.optJSONObject(_q9("oQHRGGE+FBheW7O2so/H"))?.optJSONArray(_q9("sAvMGWgkEw==")).tvContentRatingId()
        } else {
            json.optJSONObject(_q9("sAvTCWUjBRhIW7O6rw=="))?.optJSONArray(_q9("sAvMGWgkEw==")).movieCertificationId()
        },
        status = json.optStringOrNull(_q9("sRreGHEj")),
    )
}

private fun JSONObject.matchesIdentity(identity: AgooseTmdbIdentity): Boolean {
    val titleKey = if (identity.isTv) _q9("rA/SCQ==") else _q9("tgfLAGE=")
    val originalKey = if (identity.isTv) _q9("rRzWC20+AStzVKayuQ==") else _q9("rRzWC20+AStzTq6rsI0=")
    val dateKey = if (identity.isTv) _q9("pAfNH3APAS5eZaO+qI0=") else _q9("sAvTCWUjBRhIW7O6")

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
    .replace(Regex(_q9("mTDjHH8cHRtcQYmigcM=")), " ")
    .trim()

private fun String.toTmdbImageUrl(): String = "$TMDB_IMAGE_BASE$this"

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != _q9("rBvTAA==") }

private fun JSONArray?.stringValues(key: String): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.optString(key)?.trim()?.takeIf { it.isNotBlank() }
    }
}

private fun JSONArray?.actors(): List<AgooseTmdbActor> {
    if (this == null) return emptyList()
    return (0 until minOf(length(), 20)).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        val name = item.optString(_q9("rA/SCQ==")).trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        AgooseTmdbActor(
            name = name,
            profileUrl = item.optStringOrNull(_q9("shzQCm08BRhcW7O3"))?.toTmdbImageUrl(),
        )
    }
}

private fun JSONArray?.youtubeTrailers(): List<String> {
    if (this == null) return emptyList()
    val all = (0 until length()).mapNotNull { index -> optJSONObject(index) }
        .filter { it.optString(_q9("sQfLCQ==")).equals(_q9("mwHKOHEyBQ=="), ignoreCase = true) }
        .filter { it.optString(_q9("thfPCQ==")).equals(_q9("lhzeBWg1Eg=="), ignoreCase = true) }
        .sortedByDescending { it.optBoolean(_q9("rQjZBWc5ASs="), false) }
        .mapNotNull { it.optString(_q9("qQvG")).trim().takeIf(String::isNotBlank) }
        .distinct()
    return all.map { "https://www.youtube.com/watch?v=$it" }
}

private fun JSONArray?.bestLogoPath(): String? {
    if (this == null) return null
    val candidates = (0 until length()).mapNotNull { index -> optJSONObject(index) }
    val preferred = listOf("id", "", "en")
    for (language in preferred) {
        val item = candidates.firstOrNull {
            val lang = it.optString(_q9("qx3QMzJjWRgd")).takeIf { value -> value != _q9("rBvTAA==") }.orEmpty()
            lang == language && it.optString(_q9("pAfTCVsgATNE")).isNotBlank()
        }
        if (item != null) return item.optString(_q9("pAfTCVsgATNE"))
    }
    return candidates.firstOrNull { it.optString(_q9("pAfTCVsgATNE")).isNotBlank() }?.optString(_q9("pAfTCVsgATNE"))
}

private fun JSONArray?.tvContentRatingId(): String? {
    if (this == null) return null
    return (0 until length())
        .mapNotNull { index -> optJSONObject(index) }
        .firstOrNull { it.optString(_q9("qx3QMzdhVnFzCw==")) == "ID" }
        ?.optString(_q9("sA/LBWo3"))
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

private fun JSONArray?.movieCertificationId(): String? {
    if (this == null) return null
    val indonesia = (0 until length())
        .mapNotNull { index -> optJSONObject(index) }
        .firstOrNull { it.optString(_q9("qx3QMzdhVnFzCw==")) == "ID" }
        ?: return null
    val releases = indonesia.optJSONArray(_q9("sAvTCWUjBRhIW7O6rw==")) ?: return null
    return (0 until releases.length())
        .mapNotNull { index -> releases.optJSONObject(index) }
        .mapNotNull { it.optString(_q9("oQvNGG02CSRNTq6wsg==")).trim().takeIf(String::isNotBlank) }
        .firstOrNull()
}
