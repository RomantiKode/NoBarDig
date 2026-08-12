package com.agooseangsa.MidasXXI

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.addDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private const val TMDB_BASE = "https://api.themoviedb.org/3"
private const val _a3 = "id-ID"
private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"

private val tmdbReadAccessToken: String
    get() = BuildConfig.TMDB_READ_ACCESS_TOKEN
private val tmdbApiKey: String
    get() = BuildConfig.TMDB_API_KEY

private val _a0 = Mutex()
private val _a1 = mutableMapOf<String, AgooseTmdbMetadata?>()

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
    val imdbId: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val year: Int?,
    val runtimeMinutes: Int?,
    val voteAverage: Double?,
    val genres: List<String>,
    val actors: List<Pair<Actor, String?>>,
    val trailers: List<String>,
    val contentRating: String?,
)

internal suspend fun fetchAgooseTmdbMetadata(identity: AgooseTmdbIdentity): AgooseTmdbMetadata? {
    if (!hasTmdbCredential()) return null

    val cacheKey = listOf(
        identity.isTv.toString(),
        identity.tmdbId?.toString().orEmpty(),
        identity.imdbId.orEmpty(),
        identity.originalTitle.orEmpty(),
        identity.displayTitle,
        identity.year?.toString().orEmpty(),
    ).joinToString("|")

    _a0.withLock {
        if (_a1.containsKey(cacheKey)) return _a1[cacheKey]
    }

    val metadata = runCatching { fetchMetadataUncached(identity) }.getOrNull()
    _a0.withLock { _a1[cacheKey] = metadata }
    return metadata
}

internal suspend fun enrichAgooseTmdbEpisodes(tmdbId: Int?, episodes: List<Episode>) {
    if (tmdbId == null || !hasTmdbCredential()) return

    val bySeason = episodes.groupBy { it.season }.filterKeys { it != null }
    for ((seasonNumberNullable, seasonEpisodes) in bySeason) {
        val seasonNumber = seasonNumberNullable ?: continue
        val json = runCatching {
            JSONObject(
                app.get(
                    "$TMDB_BASE/tv/$tmdbId/season/$seasonNumber",
                    headers = tmdbHeaders(),
                    params = tmdbParams(_q9("ckbTOfUf/dw=") to _a3),
                ).text,
            )
        }.getOrNull() ?: continue

        val metadataByEpisode = json.optJSONArray(_q9("e1fULe8a/8o="))
            .objects()
            .associateBy { it.optInt(_q9("e1fULe8a/+b+1QrYu28=")) }

        seasonEpisodes.forEach { episode ->
            val number = episode.episode ?: return@forEach
            val item = metadataByEpisode[number] ?: return@forEach

            item.optStringOrNull(_q9("cEbQOw=="))?.let { episode.name = it }
            item.optStringOrNull(_q9("cVHYLPYX/84="))?.let { episode.description = it }
            item.optStringOrNull(_q9("bVPUMuwh6tjkyA=="))?.let { episode.posterUrl = imageUrl(_q9("aRKNbg=="), it) }
            item.optStringOrNull(_q9("f07PAeQf7tw="))?.let { episode.addDate(it) }
            item.optInt(_q9("bFLTKukT/w==")).takeIf { it > 0 }?.let { episode.runTime = it * 60 }
            item.optDouble(_q9("aEjJO98f7NziwQDf")).takeIf { !it.isNaN() && it > 0.0 }?.let {
                episode.score = Score.from(it, 10)
            }
        }
    }
}

private suspend fun fetchMetadataUncached(identity: AgooseTmdbIdentity): AgooseTmdbMetadata? {
    val tmdbId = identity.tmdbId
        ?: identity.imdbId?.let { findTmdbIdByImdb(it, identity.isTv) }
        ?: searchTmdbId(identity)
        ?: return null

    val typePath = if (identity.isTv) "tv" else _q9("c0jLN+U=")
    val json = JSONObject(
        app.get(
            "$TMDB_BASE/$typePath/$tmdbId",
            headers = tmdbHeaders(),
            params = tmdbParams(
                _q9("ckbTOfUf/dw=") to _a3,
                _q9("f1fNO+4axc3//xXfrW2AveK7") to _q9("e1/JO/IQ+9XPyQPJ8n6dtvW3M6ctJYq8DA4AbdpdeXVxVJEs5RL/2OPFON6/aYqgvb0ounUpiak0GRI1xVp6Yw=="),
            ),
        ).text,
    )

    val release = if (identity.isTv) json.optString(_q9("eE7PLfQh+9Di/wPbqng=")) else json.optString(_q9("bELRO+EN/+b0wRPf"))
    val imdbId = if (identity.isTv) {
        json.optJSONObject(_q9("e1/JO/IQ+9XPyQPJ"))?.optStringOrNull(_q9("d0rZPN8X/g=="))
    } else {
        json.optStringOrNull(_q9("d0rZPN8X/g=="))
    }

    val actors = json.optJSONObject(_q9("fVXYOukK6Q=="))
        ?.optJSONArray(_q9("fUbOKg=="))
        .objects()
        .take(20)
        .mapNotNull { item ->
            val name = item.optStringOrNull(_q9("cEbQOw==")) ?: return@mapNotNull null
            val image = item.optStringOrNull(_q9("blXSOOkS/+bgwRPS"))?.let { imageUrl(_q9("aRaFaw=="), it) }
            Actor(name, image) to item.optStringOrNull(_q9("fU/cLOEd7tzi"))
        }

    val trailers = json.optJSONObject(_q9("aE7ZO+8N"))
        ?.optJSONArray(_q9("bELOK+wK6Q=="))
        .objects()
        .filter { item ->
            item.optString(_q9("bU7JOw==")).equals(_q9("R0jICvUc/w=="), ignoreCase = true) &&
                item.optString(_q9("al7NOw==")).equals(_q9("SlXcN+wb6A=="), ignoreCase = true)
        }
        .mapNotNull { it.optStringOrNull(_q9("dULE")) }
        .distinct()
        .map { "https://www.youtube.com/watch?v=$it" }

    val logo = json.optJSONObject(_q9("d0rcOeUN"))
        ?.optJSONArray(_q9("ckjaMfM="))
        .objects()
        .sortedBy { item ->
            when (item.optStringOrNull(_q9("d1TSAbZNo+ah"))) {
                "id" -> 0
                "en" -> 1
                else -> 2
            }
        }
        .firstNotNullOfOrNull { it.optStringOrNull(_q9("eE7RO98O+834")) }
        ?.let { imageUrl(_q9("aRKNbg=="), it) }

    val runtime = if (identity.isTv) {
        json.optJSONArray(_q9("e1fULe8a/+bi1QnlqnSCtg=="))?.optInt(0)?.takeIf { it > 0 }
    } else {
        json.optInt(_q9("bFLTKukT/w==")).takeIf { it > 0 }
    }

    return AgooseTmdbMetadata(
        tmdbId = tmdbId,
        imdbId = imdbId,
        overview = json.optStringOrNull(_q9("cVHYLPYX/84=")),
        posterUrl = json.optStringOrNull(_q9("bkjOKuUMxcnx1A8="))?.let { imageUrl(_q9("aRKNbg=="), it) },
        backdropUrl = json.optStringOrNull(_q9("fEbeNeQM9cnP0AbOtg=="))?.let { imageUrl(_q9("aRaPZrA="), it) },
        logoUrl = logo,
        year = release.take(4).toIntOrNull(),
        runtimeMinutes = runtime,
        voteAverage = json.optDouble(_q9("aEjJO98f7NziwQDf")).takeIf { !it.isNaN() && it > 0.0 },
        genres = json.optJSONArray(_q9("eULTLOUN")).stringValues(_q9("cEbQOw==")),
        actors = actors.orEmpty(),
        trailers = trailers.orEmpty(),
        contentRating = readContentRating(json, identity.isTv),
    )
}

private suspend fun findTmdbIdByImdb(imdbId: String, isTv: Boolean): Int? {
    if (!Regex(_q9("QFPJAuRVvg==")).matches(imdbId)) return null

    val json = JSONObject(
        app.get(
            "$TMDB_BASE/find/$imdbId",
            headers = tmdbHeaders(),
            params = tmdbParams(
                _q9("e1/JO/IQ+9XP0wjPrH6K") to _q9("d0rZPN8X/g=="),
                _q9("ckbTOfUf/dw=") to _a3,
            ),
        ).text,
    )
    val key = if (isTv) _q9("alHiLOUN79Xk0w==") else _q9("c0jLN+Uh6Nzj1QvOrQ==")
    return json.optJSONArray(key)?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
}

private suspend fun searchTmdbId(identity: AgooseTmdbIdentity): Int? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val typePath = if (identity.isTv) "tv" else _q9("c0jLN+U=")
    val yearParam = if (identity.isTv) _q9("eE7PLfQh+9Di/wPbqniwqvS/NQ==") else _q9("Z0LcLA==")

    for (query in queries) {
        val params = tmdbParams(
            _q9("ckbTOfUf/dw=") to _a3,
            _q9("b1LYLPk=") to query,
        ).toMutableMap()
        identity.year?.let { params[yearParam] = it.toString() }

        val results = JSONObject(
            app.get(
                "$TMDB_BASE/search/$typePath",
                headers = tmdbHeaders(),
                params = params,
            ).text,
        ).optJSONArray(_q9("bELOK+wK6Q==")) ?: continue

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
    val titleKey = if (identity.isTv) _q9("cEbQOw==") else _q9("ak7JMuU=")
    val originalKey = if (identity.isTv) _q9("cVXUOekQ+9XPzgbXuw==") else _q9("cVXUOekQ+9XP1A7Osng=")
    val dateKey = if (identity.isTv) _q9("eE7PLfQh+9Di/wPbqng=") else _q9("bELRO+EN/+b0wRPf")

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

private fun readContentRating(json: JSONObject, isTv: Boolean): String? {
    if (isTv) {
        val ratings = json.optJSONObject(_q9("fUjTKuUQ7ubiwRPTsHqc"))?.optJSONArray(_q9("bELOK+wK6Q==")).objects()
        return ratings.firstOrNull { it.optString(_q9("d1TSAbNPrI/PkQ==")) == "ID" }
            ?.optStringOrNull(_q9("bEbJN+4Z"))
            ?: ratings.firstOrNull { it.optString(_q9("d1TSAbNPrI/PkQ==")) == "US" }
                ?.optStringOrNull(_q9("bEbJN+4Z"))
    }

    val releases = json.optJSONObject(_q9("bELRO+EN/+b0wRPfrQ=="))?.optJSONArray(_q9("bELOK+wK6Q==")).objects()
    val country = releases.firstOrNull { it.optString(_q9("d1TSAbNPrI/PkQ==")) == "ID" }
        ?: releases.firstOrNull { it.optString(_q9("d1TSAbNPrI/PkQ==")) == "US" }
        ?: return null
    return country.optJSONArray(_q9("bELRO+EN/+b0wRPfrQ=="))
        .objects()
        .firstNotNullOfOrNull { it.optStringOrNull(_q9("fULPKukY89rx1A7VsA==")) }
}

private fun hasTmdbCredential(): Boolean =
    tmdbReadAccessToken.isNotBlank() || tmdbApiKey.isNotBlank()

private fun tmdbHeaders(): Map<String, String> = mutableMapOf(
    _q9("f0TeO/AK") to _q9("f1fNMukd+835zwmVtG6AvQ=="),
).apply {
    if (tmdbReadAccessToken.isNotBlank()) this[_q9("X1LJNu8M88Px1A7VsA==")] = "Bearer $tmdbReadAccessToken"
}

private fun tmdbParams(vararg values: Pair<String, String>): Map<String, String> =
    mutableMapOf(*values).apply {
        if (tmdbReadAccessToken.isBlank() && tmdbApiKey.isNotBlank()) this[_q9("f1fUAesb4w==")] = tmdbApiKey
    }

private fun imageUrl(size: String, path: String): String = "$TMDB_IMAGE_BASE/$size$path"

private fun normalizeTitleForTmdbMatch(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex(_q9("RXnhLvsy5+Xg2ynHgzY=")), " ")
    .trim()

private fun JSONObject.optStringOrNull(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != _q9("cFLRMg==") }

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull(::optJSONObject)
}

private fun JSONArray?.stringValues(key: String): List<String> =
    objects().mapNotNull { it.optStringOrNull(key) }
