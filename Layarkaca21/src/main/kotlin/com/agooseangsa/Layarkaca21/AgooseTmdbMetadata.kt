package com.agooseangsa.Layarkaca21

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.LinkedHashMap

private const val TMDB_BASE = "https://api.themoviedb.org/3"
private const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w500"
private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/original"
private const val TMDB_ACTOR_IMAGE_BASE = "https://image.tmdb.org/t/p/w342"
private const val TMDB_LOGO_BASE = "https://image.tmdb.org/t/p/original"
private const val TMDB_EPISODE_STILL_BASE = "https://image.tmdb.org/t/p/w500"
internal const val _d13 = 20
internal const val _p5 = 3
internal const val _p6 = 5
internal const val _p7 = 20
internal const val _p8 = 8
internal const val _p9 = 8
internal const val _p10 = 12

internal const val _p11 = 128
internal const val _p12 = 64
internal const val _p13 = 24L * 60L * 60L * 1000L
internal const val _p14 = 12L * 60L * 60L * 1000L
internal const val _p15 = 12L * 60L * 60L * 1000L
internal const val _p16 = 12L * 60L * 60L * 1000L
internal const val _p17 = 30L * 60L * 1000L

internal enum class _p18 {
    TMDB_LOOKUP,
    TMDB_DETAIL,
    TMDB_FALLBACK,
    TMDB_SEASON,
    PROVIDER_RECOMMENDATION_SEARCH,
}

internal const val _p19 = 64
internal const val _p20 = 80
internal const val _p21 = 60

internal enum class _p22 {
    WEB,
    TMDB,
    TVMAZE,
    THETVDB,
    OMDB,
    MULTI_PROVIDER,
    MERGE,
    WEB_FALLBACK,
    TMDB_FALLBACK,
    EMPTY,
}

internal enum class _p23 {
    DIRECT_TMDB_ID,
    IMDB_FIND,
    ORIGINAL_TITLE_SEARCH,
    DISPLAY_TITLE_SEARCH,
    AMBIGUOUS_SEARCH,
    DISABLED,
    NO_CREDENTIAL,
    NO_MATCH,
}

internal enum class _p24 {
    HIGH,
    MEDIUM,
    LOW,
    NONE,
}

internal enum class _p25 {
    CACHE_HIT,
    CACHE_MISS,
    CACHE_BYPASS,
    BUDGET_BLOCKED,
    NETWORK_ATTEMPT,
    NETWORK_SUCCESS,
    NETWORK_FAILURE,
    MATCH,
    FIELD,
}

internal data class _p26(
    val field: String,
    val origin: _p22,
)

internal data class _p27(
    val type: _p25,
    val key: String,
)

internal data class _p28(
    val matchMethod: _p23? = null,
    val matchConfidence: _p24 = _p24.NONE,
    val matchScore: Int = 0,
    val fields: Map<String, _p26> = emptyMap(),
    val cacheHitsByKind: Map<_p18, Int> = emptyMap(),
    val cacheMissesByKind: Map<_p18, Int> = emptyMap(),
    val cacheBypassesByKind: Map<_p18, Int> = emptyMap(),
    val budgetBlocksByKind: Map<_p18, Int> = emptyMap(),
    val networkAttemptsByKind: Map<_p18, Int> = emptyMap(),
    val networkSuccessesByKind: Map<_p18, Int> = emptyMap(),
    val networkFailuresByKind: Map<_p18, Int> = emptyMap(),
    val metrics: Map<String, Int> = emptyMap(),
    val events: List<_p27> = emptyList(),
)

internal class _p29(
    private val maxEvents: Int = _p19,
) {
    private val lock = Any()
    private val fields = linkedMapOf<String, _p26>()
    private val cacheHits = mutableMapOf<_p18, Int>()
    private val cacheMisses = mutableMapOf<_p18, Int>()
    private val cacheBypasses = mutableMapOf<_p18, Int>()
    private val budgetBlocks = mutableMapOf<_p18, Int>()
    private val networkAttempts = mutableMapOf<_p18, Int>()
    private val networkSuccesses = mutableMapOf<_p18, Int>()
    private val networkFailures = mutableMapOf<_p18, Int>()
    private val metrics = linkedMapOf<String, Int>()
    private val events = mutableListOf<_p27>()
    private var matchMethod: _p23? = null
    private var matchConfidence: _p24 = _p24.NONE
    private var matchScore: Int = 0

    private fun addEvent(type: _p25, key: String) {
        if (maxEvents <= 0) return
        events += _p27(type, key)
        while (events.size > maxEvents) events.removeAt(0)
    }

    private fun increment(target: MutableMap<_p18, Int>, kind: _p18) {
        target[kind] = (target[kind] ?: 0) + 1
    }

    internal fun recordField(field: String, origin: _p22) = synchronized(lock) {
        val current = fields[field]?.origin
        val combined = when {
            current == null || current == _p22.EMPTY -> origin
            origin == _p22.EMPTY -> current
            current == origin -> current
            else -> _p22.MERGE
        }
        fields[field] = _p26(field, combined)
        addEvent(_p25.FIELD, field)
    }

    internal fun recordMatch(
        method: _p23,
        confidence: _p24 = _p24.NONE,
        score: Int = 0,
    ) = synchronized(lock) {
        matchMethod = method
        matchConfidence = confidence
        matchScore = score.coerceAtLeast(0)
        addEvent(_p25.MATCH, method.name)
    }

    internal fun recordCacheHit(kind: _p18) = synchronized(lock) {
        increment(cacheHits, kind)
        addEvent(_p25.CACHE_HIT, kind.name)
    }

    internal fun recordCacheMiss(kind: _p18) = synchronized(lock) {
        increment(cacheMisses, kind)
        addEvent(_p25.CACHE_MISS, kind.name)
    }

    internal fun recordCacheBypass(kind: _p18) = synchronized(lock) {
        increment(cacheBypasses, kind)
        addEvent(_p25.CACHE_BYPASS, kind.name)
    }

    internal fun recordBudgetBlocked(kind: _p18) = synchronized(lock) {
        increment(budgetBlocks, kind)
        addEvent(_p25.BUDGET_BLOCKED, kind.name)
    }

    internal fun recordNetworkAttempt(kind: _p18) = synchronized(lock) {
        increment(networkAttempts, kind)
        addEvent(_p25.NETWORK_ATTEMPT, kind.name)
    }

    internal fun recordNetworkSuccess(kind: _p18) = synchronized(lock) {
        increment(networkSuccesses, kind)
        addEvent(_p25.NETWORK_SUCCESS, kind.name)
    }

    internal fun recordNetworkFailure(kind: _p18) = synchronized(lock) {
        increment(networkFailures, kind)
        addEvent(_p25.NETWORK_FAILURE, kind.name)
    }

    internal fun setMetric(name: String, value: Int) = synchronized(lock) {
        metrics[name] = value.coerceAtLeast(0)
    }

    internal fun incrementMetric(name: String, delta: Int = 1) = synchronized(lock) {
        metrics[name] = ((metrics[name] ?: 0) + delta).coerceAtLeast(0)
    }

    internal fun snapshot(): _p28 = synchronized(lock) {
        _p28(
            matchMethod = matchMethod,
            matchConfidence = matchConfidence,
            matchScore = matchScore,
            fields = fields.toMap(),
            cacheHitsByKind = cacheHits.toMap(),
            cacheMissesByKind = cacheMisses.toMap(),
            cacheBypassesByKind = cacheBypasses.toMap(),
            budgetBlocksByKind = budgetBlocks.toMap(),
            networkAttemptsByKind = networkAttempts.toMap(),
            networkSuccessesByKind = networkSuccesses.toMap(),
            networkFailuresByKind = networkFailures.toMap(),
            metrics = metrics.toMap(),
            events = events.toList(),
        )
    }
}

internal data class _p30(
    val maxTotalNetworkRequests: Int = 16,
    val maxTmdbLookupRequests: Int = 2,
    val maxTmdbDetailRequests: Int = 1,
    val maxTmdbFallbackRequests: Int = 1,
    val maxTmdbSeasonRequests: Int = 8,
    val maxProviderRecommendationSearches: Int = 8,
)

internal data class _p31(
    val usedTotalNetworkRequests: Int,
    val usedByKind: Map<_p18, Int>,
    val remainingTotalNetworkRequests: Int,
)

internal class _p32(
    private val limits: _p30 = _p30(),
) {
    private val lock = Any()
    private val usedByKind = mutableMapOf<_p18, Int>()
    private var usedTotal = 0

    internal fun tryConsume(kind: _p18): Boolean = synchronized(lock) {
        val totalLimit = limits.maxTotalNetworkRequests.coerceAtLeast(0)
        val kindLimit = when (kind) {
            _p18.TMDB_LOOKUP -> limits.maxTmdbLookupRequests
            _p18.TMDB_DETAIL -> limits.maxTmdbDetailRequests
            _p18.TMDB_FALLBACK -> limits.maxTmdbFallbackRequests
            _p18.TMDB_SEASON -> limits.maxTmdbSeasonRequests
            _p18.PROVIDER_RECOMMENDATION_SEARCH -> limits.maxProviderRecommendationSearches
        }.coerceAtLeast(0)
        val usedKind = usedByKind[kind] ?: 0
        if (usedTotal >= totalLimit || usedKind >= kindLimit) return@synchronized false
        usedTotal += 1
        usedByKind[kind] = usedKind + 1
        true
    }

    internal fun snapshot(): _p31 = synchronized(lock) {
        _p31(
            usedTotalNetworkRequests = usedTotal,
            usedByKind = usedByKind.toMap(),
            remainingTotalNetworkRequests = (limits.maxTotalNetworkRequests.coerceAtLeast(0) - usedTotal).coerceAtLeast(0),
        )
    }
}

internal data class _p33(
    val diagnostics: _p28,
    val budget: _p31,
)

internal class _p34(
    internal val budget: _p32 = _p32(),
    internal val diagnostics: _p29 = _p29(),
    internal val features: _p118 = _j0.metadata.features,
    internal val providers: _p122 = _j0.metadata.providers,
) {
    init {
        diagnostics.setMetric(_q9("TqpgToAGQwKbr66UOff4mOlmyNw="), if (features.core) 1 else 0)
        diagnostics.setMetric(_q9("TqpgToAGQwKOqa+Edv64nOVrz9TPkA=="), if (features.visual) 1 else 0)
        diagnostics.setMetric(_q9("TqpgToAGQwKZo6ieZeG4nOVrz9TPkA=="), if (features.actors) 1 else 0)
        diagnostics.setMetric(_q9("TqpgToAGQwKMtvKUefP0le5u"), if (features.tv) 1 else 0)
        diagnostics.setMetric(_q9("TqpgToAGQwKdsLWCePbziqVvw9nImFCh"), if (features.episodes) 1 else 0)
        diagnostics.setMetric(_q9("TqpgToAGQwKKpb+eev/zl+9r2dHFmkbrpO46kPe/PA=="), if (features.recommendations) 1 else 0)
        _p119.values().forEach { id ->
            diagnostics.setMetric(
                "provider.${id.name.lowercase(Locale.ROOT)}.configured_enabled",
                if (providers.enabled.isEnabled(id)) 1 else 0,
            )
            diagnostics.setMetric(
                "provider.${id.name.lowercase(Locale.ROOT)}.implemented",
                if (_p45.descriptor(id).implemented) 1 else 0,
            )
        }
    }

    internal fun providerPlan(
        isTv: Boolean,
        capability: _p42? = null,
    ): _p44 {
        val plan = _p45.plan(
            profile = providers,
            mediaKind = if (isTv) _p41.TV else _p41.MOVIE,
            capability = capability,
        )
        diagnostics.setMetric(_q9("WL1uTJwQQ17WsLCQebz1luVsxN/fhlCh"), plan.configured.size)
        diagnostics.setMetric(_q9("WL1uTJwQQ17WsLCQebzzge5p2MzLllmg"), plan.executable.size)
        diagnostics.setMetric(_q9("WL1uTJwQQ17WsLCQebzynO1v38rPkA=="), plan.deferred.size)
        return plan
    }

    internal fun snapshot(): _p33 = _p33(
        diagnostics = diagnostics.snapshot(),
        budget = budget.snapshot(),
    )

    internal fun safeDiagnosticLines(): List<String> {
        val snap = snapshot()
        val lines = mutableListOf<String>()
        snap.diagnostics.matchMethod?.let { lines += "MATCH=${it.name}" }
        lines += "MATCH_CONFIDENCE=${snap.diagnostics.matchConfidence.name}"
        lines += "MATCH_SCORE=${snap.diagnostics.matchScore}"
        snap.diagnostics.fields.toSortedMap().forEach { (field, provenance) ->
            lines += "FIELD $field=${provenance.origin.name}"
        }
        fun appendCounts(label: String, values: Map<_p18, Int>) {
            values.toSortedMap(compareBy { it.name }).forEach { (kind, count) ->
                lines += "$label ${kind.name}=$count"
            }
        }
        appendCounts(_q9("a45CcrArbmWs"), snap.diagnostics.cacheHitsByKind)
        appendCounts(_q9("a45CcrAra2Wrkw=="), snap.diagnostics.cacheMissesByKind)
        appendCounts(_q9("a45CcrArZHWogY+i"), snap.diagnostics.cacheBypassesByKind)
        appendCounts(_q9("appFfbAgeW60j5+6UtY="), snap.diagnostics.budgetBlocksByKind)
        appendCounts(_q9("ZopVbbombXO5lIi0WsLC"), snap.diagnostics.networkAttemptsByKind)
        appendCounts(_q9("ZopVbbombXOrlZ+yUsHF"), snap.diagnostics.networkSuccessesByKind)
        appendCounts(_q9("ZopVbbombXO+gZW9QsDT"), snap.diagnostics.networkFailuresByKind)
        snap.diagnostics.metrics.toSortedMap().forEach { (name, value) -> lines += "METRIC $name=$value" }
        lines += "BUDGET_USED_TOTAL=${snap.budget.usedTotalNetworkRequests}"
        lines += "BUDGET_REMAINING_TOTAL=${snap.budget.remainingTotalNetworkRequests}"
        return lines
    }
}

private data class _p35<V : Any>(
    val value: V,
    val expiresAtMs: Long,
)

internal class _p36<K, V : Any>(
    private val maxEntries: Int,
) {
    private val lock = Any()
    private val values = LinkedHashMap<K, _p35<V>>(16, 0.75f, true)

    internal fun get(key: K, nowMs: Long = System.currentTimeMillis()): V? = synchronized(lock) {
        val entry = values[key] ?: return@synchronized null
        if (entry.expiresAtMs <= nowMs) {
            values.remove(key)
            return@synchronized null
        }
        entry.value
    }

    internal fun put(key: K, value: V, ttlMs: Long, nowMs: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (maxEntries <= 0 || ttlMs <= 0L) return@synchronized
        values[key] = _p35(value, nowMs + ttlMs)
        while (values.size > maxEntries) {
            val eldest = values.entries.firstOrNull()?.key ?: break
            values.remove(eldest)
        }
    }

    internal fun clear() = synchronized(lock) { values.clear() }
    internal fun size(): Int = synchronized(lock) { values.size }
}

private object _p37 {
    val tmdbResponses = _p36<String, String>(_p11)
    val providerSearches = _p36<String, List<SearchResponse>>(_p12)
}

internal fun _p38() {
    _p37.tmdbResponses.clear()
    _p37.providerSearches.clear()
}

private suspend fun <T : Any> agooseCachedBudgetedLoad(
    cache: _p36<String, T>,
    key: String,
    ttlMs: Long,
    context: _p34,
    kind: _p18,
    loader: suspend () -> T?,
): T? {
    cache.get(key)?.let {
        context.diagnostics.recordCacheHit(kind)
        return it
    }
    context.diagnostics.recordCacheMiss(kind)
    if (!context.budget.tryConsume(kind)) {
        context.diagnostics.recordBudgetBlocked(kind)
        return null
    }
    context.diagnostics.recordNetworkAttempt(kind)
    val loaded = try { loader() } catch (_: Throwable) { null }
    if (loaded == null) {
        context.diagnostics.recordNetworkFailure(kind)
        return null
    }
    context.diagnostics.recordNetworkSuccess(kind)
    cache.put(key, loaded, ttlMs)
    return loaded
}

private fun _p39(
    provider: _p119,
    url: String,
    params: Map<String, String>,
): String = buildString {
    append(provider.name).append('|').append(url)
    params.toSortedMap().forEach { (key, value) ->
        append('|').append(key).append('=').append(value)
    }
}

private fun _p40(url: String, params: Map<String, String>): String =
    _p39(_p119.TMDB, url, params)

internal enum class _p41 { MOVIE, TV }

internal enum class _p42 {
    IDENTITY,
    CORE,
    VISUAL,
    ACTORS,
    TV,
    EPISODES,
    RECOMMENDATIONS,
}

internal data class _p43(
    val id: _p119,
    val mediaKinds: Set<_p41>,
    val capabilities: Set<_p42>,

    val implemented: Boolean,
) {
    fun supports(mediaKind: _p41, capability: _p42? = null): Boolean =
        mediaKind in mediaKinds && (capability == null || capability in capabilities)
}

internal data class _p44(
    val configured: List<_p43>,
    val executable: List<_p43>,
    val deferred: List<_p43>,
)

internal object _p45 {
    private val descriptors = mapOf(
        _p119.TMDB to _p43(
            id = _p119.TMDB,
            mediaKinds = setOf(_p41.MOVIE, _p41.TV),
            capabilities = _p42.values().toSet(),
            implemented = true,
        ),
        _p119.TVMAZE to _p43(
            id = _p119.TVMAZE,
            mediaKinds = setOf(_p41.TV),
            capabilities = setOf(
                _p42.IDENTITY, _p42.CORE, _p42.VISUAL,
                _p42.ACTORS, _p42.TV, _p42.EPISODES,
            ),
            implemented = false,
        ),
        _p119.THETVDB to _p43(
            id = _p119.THETVDB,
            mediaKinds = setOf(_p41.MOVIE, _p41.TV),
            capabilities = setOf(
                _p42.IDENTITY, _p42.CORE, _p42.VISUAL,
                _p42.ACTORS, _p42.TV, _p42.EPISODES,
            ),
            implemented = false,
        ),
        _p119.OMDB to _p43(
            id = _p119.OMDB,
            mediaKinds = setOf(_p41.MOVIE, _p41.TV),
            capabilities = setOf(
                _p42.IDENTITY, _p42.CORE, _p42.EPISODES,
            ),
            implemented = false,
        ),
    )

    internal fun descriptor(id: _p119): _p43 =
        descriptors.getValue(id)

    internal fun plan(
        profile: _p122,
        mediaKind: _p41,
        capability: _p42? = null,
    ): _p44 {
        val configured = profile.effectiveOrder
            .map(::descriptor)
            .filter { it.supports(mediaKind, capability) }
        return _p44(
            configured = configured,
            executable = configured.filter { it.implemented },
            deferred = configured.filterNot { it.implemented },
        )
    }
}

internal data class _p46(
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    val tvmazeId: Int? = null,
    val originalTitle: String? = null,
    val displayTitle: String,
    val year: Int? = null,
    val isTv: Boolean,
)

internal data class _p47(
    val provider: _p119,
    val providerMediaId: String,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    val tvmazeId: Int? = null,
    val confidence: _p24 = _p24.NONE,
    val score: Int = 0,
)

internal interface _r0 {
    val providerId: _p119
    suspend fun resolveIdentity(
        identity: _p46,
        context: _p34,
    ): _p47?
}

private suspend fun _p48(
    url: String,
    params: Map<String, String>,
    context: _p34,
    kind: _p18,
    ttlMs: Long,
): String? = agooseCachedBudgetedLoad(
    cache = _p37.tmdbResponses,
    key = _p40(url, params),
    ttlMs = ttlMs,
    context = context,
    kind = kind,
) {
    app.get(url, headers = _d11(), params = params).text.takeIf { it.isNotBlank() }
}

private suspend fun _p49(
    query: String,
    providerApiName: String?,
    context: _p34,
    providerSearch: suspend (String) -> List<SearchResponse>?,
): List<SearchResponse> {
    val providerKey = providerApiName?.trim()?.takeIf { it.isNotBlank() }
    if (providerKey == null) {
        val kind = _p18.PROVIDER_RECOMMENDATION_SEARCH
        context.diagnostics.recordCacheBypass(kind)
        if (!context.budget.tryConsume(kind)) {
            context.diagnostics.recordBudgetBlocked(kind)
            return emptyList()
        }
        context.diagnostics.recordNetworkAttempt(kind)
        return try {
            providerSearch(query).orEmpty().also { context.diagnostics.recordNetworkSuccess(kind) }
        } catch (_: Throwable) {
            context.diagnostics.recordNetworkFailure(kind)
            emptyList()
        }
    }
    val key = providerKey.lowercase(Locale.ROOT) + "|" + _e7(query)
    return agooseCachedBudgetedLoad(
        cache = _p37.providerSearches,
        key = key,
        ttlMs = _p17,
        context = context,
        kind = _p18.PROVIDER_RECOMMENDATION_SEARCH,
    ) {
        try { providerSearch(query).orEmpty() } catch (_: Throwable) { null }
    }.orEmpty()
}

private val TMDB_READ_ACCESS_TOKEN: String
    get() = BuildConfig.TMDB_READ_ACCESS_TOKEN
private val TMDB_API_KEY: String
    get() = BuildConfig.TMDB_API_KEY

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

data class _d0(
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val originalTitle: String? = null,
    val displayTitle: String,
    val year: Int? = null,
    val isTv: Boolean,
)

internal data class _p50(
    val tmdbId: Int,
    val method: _p23,
    val confidence: _p24,
    val score: Int,
)

private data class _p51(
    val tmdbId: Int,
    val method: _p23,
    val confidence: _p24,
    val score: Int,
)

data class _d1(
    val name: String,
    val profilePath: String? = null,
    val character: String? = null,
    val order: Int = Int.MAX_VALUE,
)

data class _p52(
    val season: Int,
    val episode: Int,
    val airDate: String,
)

data class _p53(
    val season: Int,
    val name: String? = null,
)

internal data class _p54(
    val season: Int,
    val episode: Int,
)

internal data class _p55(
    val season: Int,
    val episode: Int,
    val name: String? = null,
    val overview: String? = null,
    val stillPath: String? = null,
    val airDate: String? = null,
    val voteAverage: Double? = null,
    val runtimeMinutes: Int? = null,
)

internal data class _p56(
    val tmdbId: Int,
    val localizedTitle: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val isTv: Boolean,
)

internal data class _d2(
    val tmdbId: Int,
    val imdbId: String? = null,
    val localizedTitle: String? = null,
    val originalTitle: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val logoPath: String? = null,
    val year: Int? = null,
    val runtimeMinutes: Int? = null,
    val voteAverage: Double? = null,
    val genres: List<String> = emptyList(),
    val actors: List<_d1> = emptyList(),
    val trailerUrls: List<String> = emptyList(),
    val contentRating: String? = null,
    val showStatus: ShowStatus? = null,
    val nextEpisode: _p52? = null,
    val seasons: List<_p53> = emptyList(),
    val recommendations: List<_p56> = emptyList(),
)

enum class _m0 {
    VALID,
    SUSPECT,
    INVALID,
}

enum class _m1 {
    ID,
    NON_ID,
    UNKNOWN,
}

data class _m2(
    val original: String?,
    val cleaned: String?,
    val quality: _m0,
    val language: _m1,
    val sanitized: Boolean,
    val reasons: List<String> = emptyList(),
)

data class _m3(
    val value: String?,
    val source: String,
    val reason: String,
    val assessment: _m2,
)

internal suspend fun _d5(
    identity: _d0,
    profile: _k8 = _j0.metadata.tmdb,
    context: _p34 = _p34(),
): _d2? {
    if (!profile.enabled || !context.providers.enabled.isEnabled(_p119.TMDB)) {
        context.diagnostics.recordMatch(_p23.DISABLED)
        context.diagnostics.setMetric(_q9("WL1uTJwQQ17WtLGVdbzykPhrz9TPkA=="), 1)
        return null
    }
    context.providerPlan(identity.isTv, _p42.IDENTITY)
    if (!_d10()) {
        context.diagnostics.recordMatch(_p23.NO_CREDENTIAL)
        return null
    }

    return runCatching {
        val match = _p98(identity, profile.language, context)
            ?: run {
                if (context.diagnostics.snapshot().matchMethod != _p23.AMBIGUOUS_SEARCH) {
                    context.diagnostics.recordMatch(_p23.NO_MATCH)
                }
                return@runCatching null
            }
        context.diagnostics.recordMatch(match.method, match.confidence, match.score)
        context.diagnostics.setMetric(_q9("Ra51WZ1aVU+Xsrk="), match.score)
        context.diagnostics.setMetric(_q9("Ra51WZ1aTkWfqA=="), if (match.confidence == _p24.HIGH) 1 else 0)
        context.diagnostics.setMetric(_q9("Ra51WZ1aS0mcqamc"), if (match.confidence == _p24.MEDIUM) 1 else 0)
        if (match.confidence != _p24.HIGH) {

            context.diagnostics.incrementMetric(_q9("Ra51WZ1aQ0KKqb+Zevf4jdRowdfJn1Ch"))
            return@runCatching null
        }
        val tmdbId = match.tmdbId

        val typePath = if (identity.isTv) "tv" else _q9("RaB3U5A=")
        val imageLanguage = profile.language.substringBefore('-').lowercase(Locale.ROOT).takeIf { it.isNotBlank() } ?: "id"
        val features = context.features
        val appendParts = mutableListOf<String>()
        if (features.core && identity.isTv) appendParts += _q9("Tbd1X4caR0CnqbiC")
        if (features.actors) appendParts += if (identity.isTv) _q9("SahmSJATR1idn7+Dcvb/jfg=") else _q9("S71kXpwAVQ==")
        if (features.visual) {
            appendParts += _q9("XqZlX5oH")
            appendParts += _q9("QaJgXZAH")
            appendParts += if (identity.isTv) _q9("S6BvTpAaUnOKoaiYefXl") else _q9("WqptX5QHQ3OcoaiUZA==")
        }
        if (features.recommendations) appendParts += _q9("WqpiVZgZQ0KcoaiYePzl")

        val detailParams = _d12(
            _q9("RK5vXYAVQUk=") to profile.language,
        ).toMutableMap().apply {
            if (features.visual) {
                this[_q9("QaFiVoAQQ3ORrb2Wcs36mOVt2NnNkQ==")] = listOf(imageLanguage, "en", _q9("RrptVg==")).distinct().joinToString(",")
                if (identity.isTv) {
                    this[_q9("QaFiVoAQQ3OOqbiUeM36mOVt2NnNkQ==")] = listOf(imageLanguage, "en", _q9("RrptVg==")).distinct().joinToString(",")
                }
            }
            if (appendParts.isNotEmpty()) this[_q9("Sb9xX5sQeViXn66UZOL5l/hv")] = appendParts.distinct().joinToString(",")
        }
        val detailText = _p48(
            url = "$TMDB_BASE/$typePath/$tmdbId",
            params = detailParams,
            context = context,
            kind = _p18.TMDB_DETAIL,
            ttlMs = _p14,
        ) ?: return@runCatching null
        val json = JSONObject(detailText)

        val release = if (identity.isTv) json.optString(_q9("TqZzSYErR0WKn7iQY/c=")) else json.optString(_q9("WqptX5QHQ3OcoaiU"))
        val externalIds = json.optJSONObject(_q9("Tbd1X4caR0CnqbiC"))
        val imdbId = if (identity.isTv) externalIds?._p105(_q9("QaJlWKodQg==")) else json._p105(_q9("QaJlWKodQg=="))

        val appendedTrailerUrls = if (features.visual) {
            _p59(json.optJSONObject(_q9("XqZlX5oH")), imageLanguage)
        } else emptyList()
        val trailerUrls = if (
            features.visual &&
            !identity.isTv &&
            appendedTrailerUrls.isEmpty() &&
            !profile.language.equals(_q9("TaEsb6Y="), ignoreCase = true)
        ) {
            _p61(tmdbId, context).ifEmpty { appendedTrailerUrls }
        } else {
            appendedTrailerUrls
        }

        _d2(
            tmdbId = tmdbId,
            imdbId = if (features.core) imdbId else null,
            localizedTitle = json._p105(if (identity.isTv) _q9("Rq5sXw==") else _q9("XKZ1VpA=")),
            originalTitle = json._p105(if (identity.isTv) _q9("R71oXZwaR0Cnrr2ccg==") else _q9("R71oXZwaR0CntLWFe/c=")),
            overview = json._p105(_q9("R7lkSIMdQ1s=")),
            posterPath = if (features.core) json._p105(_q9("WKByTpAGeVyZtLQ=")) else null,
            backdropPath = if (features.core) json._p105(_q9("Sq5iUZEGSVynsL2Ffw==")) else null,
            logoPath = if (features.visual) _p57(json.optJSONObject(_q9("QaJgXZAH")), imageLanguage) else null,
            year = if (features.core) release.take(4).toIntOrNull() else null,
            runtimeMinutes = when {
                identity.isTv && features.tv -> _p66(json)
                !identity.isTv && features.core -> json.optInt(_q9("WrpvTpwZQw==")).takeIf { it > 0 }
                else -> null
            },
            voteAverage = if (features.core) json.optDouble(_q9("XqB1X6oVUEmKobuU")).takeIf { !it.isNaN() && it > 0.0 } else null,
            genres = if (features.core) json.optJSONArray(_q9("T6pvSJAH"))._p106(_q9("Rq5sXw==")) else emptyList(),
            actors = if (features.actors) _d15(
                credits = json.optJSONObject(if (identity.isTv) _q9("SahmSJATR1idn7+Dcvb/jfg=") else _q9("S71kXpwAVQ==")),
                aggregateTv = identity.isTv,
            ) else emptyList(),
            trailerUrls = trailerUrls,
            contentRating = if (features.visual) _p62(json, identity.isTv) else null,
            showStatus = if (identity.isTv && features.tv) _p63(json) else null,
            nextEpisode = if (identity.isTv && features.tv) _p64(json.optJSONObject(_q9("Rqp5TqoRVkWLr7iUSOb5pupj3w=="))) else null,
            seasons = if (identity.isTv && features.tv) _p65(json.optJSONArray(_q9("W6pgSZoaVQ=="))) else emptyList(),
            recommendations = if (features.recommendations) _p89(
                json.optJSONObject(_q9("WqpiVZgZQ0KcoaiYePzl"))?.optJSONArray(_q9("WqpyT5kAVQ==")),
                isTv = identity.isTv,
            ) else emptyList(),
        )
    }.getOrNull()
}

private fun _p57(images: JSONObject?, preferredLanguage: String): String? {
    val logos = images?.optJSONArray(_q9("RKBmVYY=")) ?: return null
    return (0 until logos.length())
        .mapNotNull { index -> logos.optJSONObject(index) }
        .mapNotNull { item ->
            val path = item._p105(_q9("TqZtX6oER1iQ")) ?: return@mapNotNull null
            val language = item._p105(_q9("QbxuZcNHH3PJ"))?.lowercase(Locale.ROOT)
            val languageRank = when (language) {
                preferredLanguage.lowercase(Locale.ROOT) -> 0
                "en" -> 1
                null -> 2
                else -> 3
            }
            val vote = item.optDouble(_q9("XqB1X6oVUEmKobuU")).takeIf { !it.isNaN() } ?: 0.0
            val width = item.optInt(_q9("X6ZlTp0="), 0)
            _p58(path, languageRank, vote, width)
        }
        .sortedWith(
            compareBy<_p58> { it.languageRank }
                .thenByDescending { it.voteAverage }
                .thenByDescending { it.width },
        )
        .firstOrNull()
        ?.path
}

private data class _p58(
    val path: String,
    val languageRank: Int,
    val voteAverage: Double,
    val width: Int,
)

private fun _p59(videos: JSONObject?, preferredLanguage: String): List<String> {
    val results = videos?.optJSONArray(_q9("WqpyT5kAVQ==")) ?: return emptyList()
    return (0 until results.length())
        .mapNotNull { index -> results.optJSONObject(index) }
        .mapNotNull { item ->
            val site = item._p105(_q9("W6Z1Xw==")) ?: return@mapNotNull null
            val key = item._p105(_q9("Q6p4")) ?: return@mapNotNull null
            if (!site.equals(_q9("caB0boAWQw=="), ignoreCase = true)) return@mapNotNull null
            val type = item._p105(_q9("XLZxXw==")).orEmpty()
            if (!type.equals(_q9("fL1gU5kRVA=="), ignoreCase = true) && !type.equals(_q9("fKpgSZAG"), ignoreCase = true)) {
                return@mapNotNull null
            }
            val language = item._p105(_q9("QbxuZcNHH3PJ"))?.lowercase(Locale.ROOT)
            val languageRank = when (language) {
                preferredLanguage.lowercase(Locale.ROOT) -> 0
                "en" -> 1
                null -> 2
                else -> 3
            }
            _p60(
                url = "https://www.youtube.com/watch?v=$key",
                officialRank = if (item.optBoolean(_q9("R6lnU5YdR0A="), false)) 0 else 1,
                typeRank = if (type.equals(_q9("fL1gU5kRVA=="), ignoreCase = true)) 0 else 1,
                languageRank = languageRank,
            )
        }
        .sortedWith(
            compareBy<_p60> { it.officialRank }
                .thenBy { it.typeRank }
                .thenBy { it.languageRank },
        )
        .map { it.url }
        .distinctBy(::_p77)
        .take(_p5)
}

private data class _p60(
    val url: String,
    val officialRank: Int,
    val typeRank: Int,
    val languageRank: Int,
)

private suspend fun _p61(
    tmdbId: Int,
    context: _p34,
): List<String> = runCatching {
    val params = _d12(_q9("RK5vXYAVQUk=") to _q9("TaEsb6Y="))
    val text = _p48(
        url = "$TMDB_BASE/movie/$tmdbId/videos",
        params = params,
        context = context,
        kind = _p18.TMDB_FALLBACK,
        ttlMs = _p16,
    ) ?: return@runCatching emptyList()
    _p59(JSONObject(text), "en")
}.getOrDefault(emptyList())

private fun _p62(json: JSONObject, isTv: Boolean): String? {
    return if (isTv) {
        val results = json.optJSONObject(_q9("S6BvTpAaUnOKoaiYefXl"))?.optJSONArray(_q9("WqpyT5kAVQ==")) ?: return null
        (0 until results.length())
            .mapNotNull { results.optJSONObject(it) }
            .firstOrNull { it._p105(_q9("QbxuZcZFEBqn8Q=="))?.equals("ID", ignoreCase = true) == true }
            ?._p105(_q9("Wq51U5sT"))
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    } else {
        val results = json.optJSONObject(_q9("WqptX5QHQ3OcoaiUZA=="))?.optJSONArray(_q9("WqpyT5kAVQ==")) ?: return null
        val indonesia = (0 until results.length())
            .mapNotNull { results.optJSONObject(it) }
            .firstOrNull { it._p105(_q9("QbxuZcZFEBqn8Q=="))?.equals("ID", ignoreCase = true) == true }
            ?: return null
        val releaseDates = indonesia.optJSONArray(_q9("WqptX5QHQ3OcoaiUZA==")) ?: return null
        val typePriority = mapOf(3 to 0, 2 to 1, 4 to 2, 6 to 3, 5 to 4, 1 to 5)
        (0 until releaseDates.length())
            .mapNotNull { releaseDates.optJSONObject(it) }
            .mapNotNull { item ->
                val certification = item._p105(_q9("S6pzTpwST0+ZtLWeeQ=="))?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                certification to (typePriority[item.optInt(_q9("XLZxXw=="))] ?: Int.MAX_VALUE)
            }
            .sortedBy { it.second }
            .firstOrNull()
            ?.first
    }
}

private fun _p63(json: JSONObject): ShowStatus? {
    val normalized = json._p105(_q9("W7tgToAH"))
        ?.trim()
        ?.lowercase(Locale.ROOT)
    return when (normalized) {
        _q9("TaFlX5E="), _q9("S65vWZAYQ0g="), _q9("S65vWZAYSkmc") -> ShowStatus.Completed
        _q9("Wqp1T4caT0Kf4K+UZfvzig=="), _q9("QaEhSocbQlmbtLWeeQ=="), _q9("WKNgVJsRQg=="), _q9("WKZtVYE=") -> ShowStatus.Ongoing
        else -> if (json.optBoolean(_q9("QaFeSocbQlmbtLWeeQ=="), false)) ShowStatus.Ongoing else null
    }
}

private fun _p64(item: JSONObject?): _p52? {
    item ?: return null
    val season = item.optInt(_q9("W6pgSZoaeUKNrb6UZQ=="), -1)
    val episode = item.optInt(_q9("Tb9oSZoQQ3OWtbGTcuA="), -1)
    val airDate = item._p105(_q9("SaZzZZEVUkk=")) ?: return null
    if (season < 0 || episode <= 0 || !Regex(_q9("dpNlQcEJC3Ccu+6MOs7ygrl3iQ==")).matches(airDate)) return null
    return _p52(season = season, episode = episode, airDate = airDate)
}

private fun _p65(items: JSONArray?): List<_p53> {
    items ?: return emptyList()
    return (0 until items.length())
        .mapNotNull { index -> items.optJSONObject(index) }
        .mapNotNull { item ->
            val season = item.optInt(_q9("W6pgSZoaeUKNrb6UZQ=="), -1)
            if (season < 0) return@mapNotNull null
            _p53(
                season = season,
                name = item._p105(_q9("Rq5sXw==")),
            )
        }
        .distinctBy { it.season }
}

private fun _p66(json: JSONObject): Int? {
    val values = json.optJSONArray(_q9("Tb9oSZoQQ3OKtbKuY/v7nA=="))
        ?.let { array -> (0 until array.length()).map { array.optInt(it, 0) } }
        .orEmpty()
        .filter { it > 0 }
        .sorted()
    if (values.isEmpty()) return null
    return values[(values.size - 1) / 2]
}

private fun _d15(
    credits: JSONObject?,
    aggregateTv: Boolean,
): List<_d1> {
    val cast = credits?.optJSONArray(_q9("S65yTg==")) ?: return emptyList()
    return (0 until cast.length())
        .mapNotNull { index ->
            val item = cast.optJSONObject(index) ?: return@mapNotNull null
            val name = item._p105(_q9("Rq5sXw==")) ?: return@mapNotNull null
            val character = if (aggregateTv) {
                item.optJSONArray(_q9("WqBtX4Y="))
                    ?.let { roles ->
                        (0 until roles.length())
                            .mapNotNull { roleIndex -> roles.optJSONObject(roleIndex)?._p105(_q9("S6dgSJQXUkmK")) }
                            .distinct()
                            .take(3)
                            .joinToString(_q9("COAh"))
                            .takeIf { it.isNotBlank() }
                    }
            } else {
                item._p105(_q9("S6dgSJQXUkmK"))
            }

            _d1(
                name = name,
                profilePath = item._p105(_q9("WL1uXJwYQ3OIoaiZ")),
                character = character,
                order = item.optInt(_q9("R71lX4c="), Int.MAX_VALUE),
            )
        }
        .sortedBy { it.order }
        .distinctBy { _d18(it.name) }
        .take(_d13)
}

internal fun _d1._d16(): ActorData = ActorData(
    actor = Actor(
        name = name,
        image = profilePath?.takeIf { it.startsWith("/") }?.let { "$TMDB_ACTOR_IMAGE_BASE$it" },
    ),
    roleString = character?.takeIf { it.isNotBlank() },
)

internal fun _d17(
    webActors: List<ActorData>?,
    tmdbActors: List<_d1>?,
    context: _p34? = null,
): List<ActorData>? {
    if (context?.features?.actors == false) {
        val result = webActors?.take(_d13)?.takeIf { it.isNotEmpty() }
        context.diagnostics.recordField(_q9("Sax1VYcH"), if (result.isNullOrEmpty()) _p22.EMPTY else _p22.WEB)
        context.diagnostics.setMetric(_q9("Sax1VYdaQEmZtKmDcs3ykPhrz9TPkA=="), 1)
        context.diagnostics.setMetric(_q9("Sax1VYdaQEWWobCudP3jl/8="), result.orEmpty().size)
        return result
    }
    val tmdbByName = tmdbActors.orEmpty()
        .filter { it.name.isNotBlank() }
        .distinctBy { _d18(it.name) }
        .associateBy { _d18(it.name) }

    val usedTmdbNames = mutableSetOf<String>()
    val merged = mutableListOf<ActorData>()
    val seenNames = mutableSetOf<String>()

    webActors.orEmpty().forEach { webActor ->
        val normalizedName = _d18(webActor.actor.name)
        if (normalizedName.isBlank() || !seenNames.add(normalizedName)) return@forEach

        val tmdbActor = tmdbByName[normalizedName]
        if (tmdbActor != null) usedTmdbNames += normalizedName
        merged += if (tmdbActor == null) webActor else webActor._p67(tmdbActor)
    }

    tmdbActors.orEmpty()
        .sortedBy { it.order }
        .forEach { tmdbActor ->
            val normalizedName = _d18(tmdbActor.name)
            if (normalizedName.isBlank() || normalizedName in usedTmdbNames || !seenNames.add(normalizedName)) {
                return@forEach
            }
            merged += tmdbActor._d16()
        }

    val result = merged
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<ActorData>> { !it.value.actor.image.isNullOrBlank() }
                .thenBy { it.index },
        )
        .map { it.value }
        .take(_d13)
        .takeIf { it.isNotEmpty() }

    context?.diagnostics?.apply {
        val webCount = webActors.orEmpty().count { _d18(it.actor.name).isNotBlank() }
        val tmdbCount = tmdbActors.orEmpty().count { _d18(it.name).isNotBlank() }
        recordField(
            _q9("Sax1VYcH"),
            when {
                webCount > 0 && tmdbCount > 0 -> _p22.MERGE
                webCount > 0 -> _p22.WEB
                tmdbCount > 0 -> _p22.TMDB
                else -> _p22.EMPTY
            },
        )
        setMetric(_q9("Sax1VYdaUUman7+eYvzi"), webCount)
        setMetric(_q9("Sax1VYdaUkGcooOSeOf4jQ=="), tmdbCount)
        setMetric(_q9("Sax1VYdaQEWWobCudP3jl/8="), result.orEmpty().size)
        setMetric(_q9("Sax1VYdaUUWMqIOYevPxnA=="), result.orEmpty().count { !it.actor.image.isNullOrBlank() })
    }
    return result
}

private fun ActorData._p67(tmdbActor: _d1): ActorData {
    val tmdbImage = tmdbActor.profilePath
        ?.takeIf { it.startsWith("/") }
        ?.let { "$TMDB_ACTOR_IMAGE_BASE$it" }
    return copy(
        actor = actor.copy(
            image = actor.image?.takeIf { it.isNotBlank() } ?: tmdbImage,
        ),
        roleString = roleString?.takeIf { it.isNotBlank() }
            ?: tmdbActor.character?.takeIf { it.isNotBlank() },
    )
}

internal data class _p68(
    val posterUrl: String? = null,
    val backgroundPosterUrl: String? = null,
    val year: Int? = null,
    val displayGenres: List<String> = emptyList(),
    val score10: Double? = null,
    val durationMinutes: Int? = null,
    val verifiedTmdbId: Int? = null,
    val verifiedImdbId: String? = null,
)

internal data class _p69(
    val posterUrl: String? = null,
    val backgroundPosterUrl: String? = null,
    val year: Int? = null,
    val displayGenres: List<String> = emptyList(),
    val score10: Double? = null,
    val durationMinutes: Int? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
)

internal fun _p70(
    web: _p68,
    tmdb: _d2?,
    isTv: Boolean,
    context: _p34? = null,
): _p69 {
    val coreEnabled = context?.features?.core ?: true
    val effectiveTmdb = tmdb.takeIf { coreEnabled }
    val webPoster = web.posterUrl?.trim()?.takeIf { it.isNotBlank() }
    val webBackdrop = web.backgroundPosterUrl?.trim()?.takeIf { it.isNotBlank() }
    val tmdbPoster = effectiveTmdb?.posterPath?.takeIf { it.startsWith("/") }?.let { "$TMDB_POSTER_BASE$it" }
    val tmdbBackdrop = effectiveTmdb?.backdropPath?.takeIf { it.startsWith("/") }?.let { "$TMDB_BACKDROP_BASE$it" }

    val result = _p69(

        posterUrl = webPoster ?: tmdbPoster,
        backgroundPosterUrl = webBackdrop ?: tmdbBackdrop,
        year = web.year?.takeIf { it > 1800 } ?: effectiveTmdb?.year?.takeIf { it > 1800 },

        displayGenres = _p95(web.displayGenres, effectiveTmdb?.genres.orEmpty()),

        score10 = effectiveTmdb?.voteAverage?.takeIf(::_p96)
            ?: web.score10?.takeIf(::_p96),

        durationMinutes = if (isTv) {
            web.durationMinutes?.takeIf { it > 0 }
        } else {
            web.durationMinutes?.takeIf { it > 0 }
                ?: effectiveTmdb?.runtimeMinutes?.takeIf { it > 0 }
        },
        tmdbId = effectiveTmdb?.tmdbId ?: web.verifiedTmdbId?.takeIf { it > 0 },
        imdbId = effectiveTmdb?.imdbId?.takeIf(::_p97)
            ?: web.verifiedImdbId?.takeIf(::_p97),
    )

    context?.diagnostics?.apply {
        fun field(name: String, webPresent: Boolean, tmdbPresent: Boolean, tmdbIsPrimary: Boolean = false) {
            recordField(name, when {
                webPresent && tmdbPresent && name == _q9("T6pvSJAH") -> _p22.MERGE
                tmdbIsPrimary && tmdbPresent -> _p22.TMDB
                tmdbIsPrimary && webPresent -> _p22.WEB_FALLBACK
                webPresent -> _p22.WEB
                tmdbPresent -> _p22.TMDB_FALLBACK
                else -> _p22.EMPTY
            })
        }
        field(_q9("WKByTpAG"), webPoster != null, tmdbPoster != null)
        field(_q9("Sq5iUZEGSVw="), webBackdrop != null, tmdbBackdrop != null)
        field(_q9("UapgSA=="), web.year?.takeIf { it > 1800 } != null, effectiveTmdb?.year?.takeIf { it > 1800 } != null)
        field(_q9("T6pvSJAH"), web.displayGenres.any { it.isNotBlank() }, effectiveTmdb?.genres.orEmpty().any { it.isNotBlank() })
        field(_q9("W6xuSJA="), web.score10?.takeIf(::_p96) != null, effectiveTmdb?.voteAverage?.takeIf(::_p96) != null, tmdbIsPrimary = true)
        field(_q9("TLpzW4EdSUI="), web.durationMinutes?.takeIf { it > 0 } != null, (!isTv && effectiveTmdb?.runtimeMinutes?.takeIf { it > 0 } != null))
        field(_q9("W7ZvWdsAS0ian7WV"), web.verifiedTmdbId?.takeIf { it > 0 } != null, effectiveTmdb?.tmdbId?.takeIf { it > 0 } != null, tmdbIsPrimary = true)
        field(_q9("W7ZvWdsdS0ian7WV"), web.verifiedImdbId?.takeIf(::_p97) != null, effectiveTmdb?.imdbId?.takeIf(::_p97) != null, tmdbIsPrimary = true)
    }
    return result
}

internal fun LoadResponse._p71(metadata: _p69) {
    posterUrl = metadata.posterUrl
    backgroundPosterUrl = metadata.backgroundPosterUrl
    year = metadata.year
    tags = metadata.displayGenres.takeIf { it.isNotEmpty() }
    score = metadata.score10?.let { Score.from10(it) }
    duration = metadata.durationMinutes
    metadata.tmdbId?.let { addTMDbId(it.toString()) }
    metadata.imdbId?.let { addImdbId(it) }
}

internal data class _p72(
    val trailerUrls: List<String> = emptyList(),
    val logoUrl: String? = null,
    val contentRating: String? = null,
)

internal data class _p73(
    val trailerUrls: List<String> = emptyList(),
    val logoUrl: String? = null,
    val contentRating: String? = null,
)

internal fun _p74(
    web: _p72,
    tmdb: _d2?,
    context: _p34? = null,
): _p73 {
    val visualEnabled = context?.features?.visual ?: true
    val effectiveTmdb = tmdb.takeIf { visualEnabled }
    val webLogo = web.logoUrl?.trim()?.takeIf { it.isNotBlank() }
    val tmdbLogo = effectiveTmdb?.logoPath?.takeIf { it.startsWith("/") }?.let { "$TMDB_LOGO_BASE$it" }
    val webRating = web.contentRating?.trim()?.takeIf { it.isNotBlank() }
    val tmdbIndonesiaRating = effectiveTmdb?.contentRating?.trim()?.takeIf { it.isNotBlank() }

    val result = _p73(
        trailerUrls = _p76(web.trailerUrls, effectiveTmdb?.trailerUrls.orEmpty()),
        logoUrl = webLogo ?: tmdbLogo,
        contentRating = tmdbIndonesiaRating ?: webRating,
    )
    context?.diagnostics?.apply {
        val webTrailers = web.trailerUrls.any { it.isNotBlank() }
        val tmdbTrailers = effectiveTmdb?.trailerUrls.orEmpty().any { it.isNotBlank() }
        recordField(_q9("XL1gU5kRVF8="), when {
            webTrailers && tmdbTrailers -> _p22.MERGE
            webTrailers -> _p22.WEB
            tmdbTrailers -> _p22.TMDB
            else -> _p22.EMPTY
        })
        recordField(_q9("RKBmVQ=="), when {
            webLogo != null -> _p22.WEB
            tmdbLogo != null -> _p22.TMDB_FALLBACK
            else -> _p22.EMPTY
        })
        recordField(_q9("S6BvTpAaUnOKoaiYefU="), when {
            tmdbIndonesiaRating != null -> _p22.TMDB
            webRating != null -> _p22.WEB_FALLBACK
            else -> _p22.EMPTY
        })
    }
    return result
}

internal suspend fun LoadResponse._p75(metadata: _p73) {
    logoUrl = metadata.logoUrl
    contentRating = metadata.contentRating
    if (metadata.trailerUrls.isNotEmpty()) {
        addTrailer(metadata.trailerUrls)
    }
}

private fun _p76(
    website: List<String>,
    tmdb: List<String>,
): List<String> {
    val seen = linkedSetOf<String>()
    return (website + tmdb).mapNotNull { raw ->
        val value = raw.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val key = _p77(value)
        if (seen.add(key)) value else null
    }.take(_p6)
}

private fun _p77(url: String): String {
    val clean = url.trim()
    val youtubeKey = Regex(_q9("APBoE91LHFWXtaiES7z0nKR21NffgECnpNx1kfS3d8YX9XZbgRdOcMfo48s5uLDQtHyQxM+ZV6ClryeB87UqmlvgKBPdL2cBoqHxiye/r6amV9aOhokc"))
        .find(clean)
        ?.groupValues
        ?.getOrNull(1)
    return youtubeKey?.let { "youtube:${it.lowercase(Locale.ROOT)}" }
        ?: clean.substringBefore('#').trimEnd('/').lowercase(Locale.ROOT)
}

internal data class _p78(
    val showStatus: ShowStatus? = null,
    val nextAiring: NextAiring? = null,
    val seasonNames: List<SeasonData> = emptyList(),
    val episodeSeasonNumbers: List<Int> = emptyList(),
    val durationMinutes: Int? = null,
)

internal data class _p79(
    val showStatus: ShowStatus? = null,
    val nextAiring: NextAiring? = null,
    val seasonNames: List<SeasonData> = emptyList(),
    val durationMinutes: Int? = null,
)

internal fun _p80(
    web: _p78,
    tmdb: _d2?,
    context: _p34? = null,
): _p79 {
    val tvEnabled = context?.features?.tv ?: true
    val effectiveTmdb = tmdb.takeIf { tvEnabled }
    val availableSeasons = web.episodeSeasonNumbers
        .filter { it >= 0 }
        .distinct()
        .sorted()

    val websiteSeasonByNumber = web.seasonNames
        .filter { it.season >= 0 }
        .associateBy { it.season }
    val tmdbSeasonByNumber = effectiveTmdb?.seasons.orEmpty().associateBy { it.season }

    val resolvedSeasonNames = if (availableSeasons.isEmpty()) {
        web.seasonNames.distinctBy { it.season }
    } else {
        availableSeasons.mapNotNull { season ->
            websiteSeasonByNumber[season] ?: tmdbSeasonByNumber[season]
                ?.name
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { SeasonData(season = season, name = it) }
        }
    }

    val tmdbNextAiring = effectiveTmdb?.nextEpisode?._p82()
    val result = _p79(
        showStatus = effectiveTmdb?.showStatus ?: web.showStatus,
        nextAiring = web.nextAiring ?: tmdbNextAiring,
        seasonNames = resolvedSeasonNames,
        durationMinutes = web.durationMinutes?.takeIf { it > 0 }
            ?: effectiveTmdb?.runtimeMinutes?.takeIf { it > 0 },
    )
    context?.diagnostics?.apply {
        recordField(_q9("XLkvSYEVUlmL"), when {
            effectiveTmdb?.showStatus != null -> _p22.TMDB
            web.showStatus != null -> _p22.WEB_FALLBACK
            else -> _p22.EMPTY
        })
        recordField(_q9("XLkvVJAMUnOZqa6YefU="), when {
            web.nextAiring != null -> _p22.WEB
            tmdbNextAiring != null -> _p22.TMDB_FALLBACK
            else -> _p22.EMPTY
        })
        val webSeasonCount = websiteSeasonByNumber.size
        val tmdbSeasonFillCount = resolvedSeasonNames.count { it.season !in websiteSeasonByNumber && it.season in tmdbSeasonByNumber }
        recordField(_q9("XLkvSZAVVUOWn7KQevfl"), when {
            webSeasonCount > 0 && tmdbSeasonFillCount > 0 -> _p22.MERGE
            webSeasonCount > 0 -> _p22.WEB
            tmdbSeasonFillCount > 0 -> _p22.TMDB_FALLBACK
            else -> _p22.EMPTY
        })
        recordField(_q9("XLkvXoAGR1iRr7I="), when {
            web.durationMinutes?.takeIf { it > 0 } != null -> _p22.WEB
            effectiveTmdb?.runtimeMinutes?.takeIf { it > 0 } != null -> _p22.TMDB_FALLBACK
            else -> _p22.EMPTY
        })
    }
    return result
}

internal fun LoadResponse._p81(metadata: _p79) {
    if (this !is EpisodeResponse) return
    metadata.showStatus?.let { showStatus = it }
    metadata.nextAiring?.let { nextAiring = it }
    if (metadata.seasonNames.isNotEmpty()) seasonNames = metadata.seasonNames
    metadata.durationMinutes?.let { duration = it }
}

private fun _p52._p82(): NextAiring? {
    val unixTime = _p83(airDate) ?: return null
    return NextAiring(
        episode = episode,
        unixTime = unixTime,
        season = season,
    )
}

private fun _p83(value: String): Long? = runCatching {
    val parser = SimpleDateFormat(_q9("UbZ4Q9g5awGcpA=="), Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone(_q9("fZtC"))
    }
    val midnightUtcMillis = parser.parse(value)?.time ?: return@runCatching null
    (midnightUtcMillis / 1000L) + 12L * 60L * 60L
}.getOrNull()

internal suspend fun _p84(
    tmdb: _d2?,
    providerEpisodes: List<Episode>,
    profile: _k8 = _j0.metadata.tmdb,
    context: _p34 = _p34(),
): Map<_p54, _p55> {
    if (!context.features.episodes) {
        context.diagnostics.setMetric(_q9("Tb9oSZoQQwKepb2FYuDzpu9j3tnImFCh"), 1)
        return emptyMap()
    }
    val tmdbId = tmdb?.tmdbId?.takeIf { it > 0 } ?: return emptyMap()
    if (!profile.enabled || !_d10()) return emptyMap()

    val seasonNumbers = providerEpisodes
        .mapNotNull { item ->
            val season = item.season ?: return@mapNotNull null
            val episode = item.episode ?: return@mapNotNull null
            season.takeIf { it >= 0 && episode > 0 }
        }
        .distinct()
        .sorted()
        .take(_p7)

    if (seasonNumbers.isEmpty()) return emptyMap()

    return seasonNumbers
        .flatMap { season -> _p85(tmdbId, season, profile.language, context) }
        .associateBy { _p54(it.season, it.episode) }
}

private suspend fun _p85(
    tmdbId: Int,
    season: Int,
    language: String,
    context: _p34,
): List<_p55> = runCatching {
    val params = _d12(_q9("RK5vXYAVQUk=") to language)
    val text = _p48(
        url = "$TMDB_BASE/tv/$tmdbId/season/$season",
        params = params,
        context = context,
        kind = _p18.TMDB_SEASON,
        ttlMs = _p15,
    ) ?: return@runCatching emptyList()
    _p86(JSONObject(text).optJSONArray(_q9("Tb9oSZoQQ18=")), season)
}.getOrDefault(emptyList())

private fun _p86(
    items: JSONArray?,
    expectedSeason: Int,
): List<_p55> {
    items ?: return emptyList()
    return (0 until items.length())
        .mapNotNull { index -> items.optJSONObject(index) }
        .mapNotNull { item ->
            val season = item.optInt(_q9("W6pgSZoaeUKNrb6UZQ=="), expectedSeason)
            val episode = item.optInt(_q9("Tb9oSZoQQ3OWtbGTcuA="), -1)
            if (season != expectedSeason || season < 0 || episode <= 0) return@mapNotNull null

            val vote = item.optDouble(_q9("XqB1X6oVUEmKobuU")).takeIf { !it.isNaN() && it in 0.0..10.0 && it > 0.0 }
            val runtime = item.optInt(_q9("WrpvTpwZQw=="), 0).takeIf { it > 0 }
            val airDate = item._p105(_q9("SaZzZZEVUkk="))
                ?.takeIf { Regex(_q9("dpNlQcEJC3Ccu+6MOs7ygrl3iQ==")).matches(it) }

            _p55(
                season = season,
                episode = episode,
                name = item._p105(_q9("Rq5sXw==")),
                overview = item._p105(_q9("R7lkSIMdQ1s=")),
                stillPath = item._p105(_q9("W7toVpkrVk2MqA==")),
                airDate = airDate,
                voteAverage = vote,
                runtimeMinutes = runtime,
            )
        }
        .distinctBy { _p54(it.season, it.episode) }
}

internal fun _p87(
    providerEpisodes: List<Episode>,
    tmdbEpisodes: Map<_p54, _p55>,
    context: _p34? = null,
): List<Episode> {
    if (context?.features?.episodes == false) {
        context.diagnostics.setMetric(_q9("Tb9oSZoQQwKepb2FYuDzpu9j3tnImFCh"), 1)
        listOf(_q9("Tb9oSZoQQwKWobGU"), _q9("Tb9oSZoQQwKcpa+SZfvmjeJlww=="), _q9("Tb9oSZoQQwKIr6+FcuA="), _q9("Tb9oSZoQQwKLo7ODcg=="), _q9("Tb9oSZoQQwKcoaiU"), _q9("Tb9oSZoQQwKKtbKFfv/z")).forEach {
            context.diagnostics.recordField(it, _p22.WEB)
        }
        return providerEpisodes
    }
    if (providerEpisodes.isEmpty()) {
        context?.diagnostics?.setMetric(_q9("Tb9oSZoQQwKIsrOHfvbzi9Rpws3EgA=="), 0)
        return providerEpisodes
    }
    context?.diagnostics?.apply {
        setMetric(_q9("Tb9oSZoQQwKIsrOHfvbzi9Rpws3EgA=="), providerEpisodes.size)
        setMetric(_q9("Tb9oSZoQQwKMrbiTSP/zjepuzMzLq1aqtO4v"), tmdbEpisodes.size)
    }
    if (tmdbEpisodes.isEmpty()) {
        listOf(_q9("Tb9oSZoQQwKWobGU"), _q9("Tb9oSZoQQwKcpa+SZfvmjeJlww=="), _q9("Tb9oSZoQQwKIr6+FcuA="), _q9("Tb9oSZoQQwKLo7ODcg=="), _q9("Tb9oSZoQQwKcoaiU"), _q9("Tb9oSZoQQwKKtbKFfv/z")).forEach {
            context?.diagnostics?.recordField(it, _p22.WEB)
        }
        return providerEpisodes
    }

    val webCounts = mutableMapOf<String, Int>()
    val tmdbCounts = mutableMapOf<String, Int>()
    var enrichedEpisodeCount = 0
    fun webUsed(field: String) { webCounts[field] = (webCounts[field] ?: 0) + 1 }
    fun tmdbUsed(field: String) { tmdbCounts[field] = (tmdbCounts[field] ?: 0) + 1 }

    val resultEpisodes = providerEpisodes.map { web ->
        val season = web.season ?: return@map web
        val episodeNumber = web.episode ?: return@map web
        if (season < 0 || episodeNumber <= 0) return@map web

        val tmdb = tmdbEpisodes[_p54(season, episodeNumber)] ?: return@map web
        val result = web.copy()

        if (result.name.isNullOrBlank() || _p88(result.name, episodeNumber)) {
            val replacement = tmdb.name?.trim()?.takeIf { it.isNotBlank() && !_p88(it, episodeNumber) }
            if (replacement != null) { result.name = replacement; tmdbUsed(_q9("Tb9oSZoQQwKWobGU")); enrichedEpisodeCount += 1 }
            else if (!web.name.isNullOrBlank()) webUsed(_q9("Tb9oSZoQQwKWobGU"))
        } else webUsed(_q9("Tb9oSZoQQwKWobGU"))
        if (result.description.isNullOrBlank()) {
            val replacement = tmdb.overview?.trim()?.takeIf { it.isNotBlank() }
            if (replacement != null) { result.description = replacement; tmdbUsed(_q9("Tb9oSZoQQwKcpa+SZfvmjeJlww==")) }
        } else webUsed(_q9("Tb9oSZoQQwKcpa+SZfvmjeJlww=="))
        if (result.posterUrl.isNullOrBlank()) {
            val replacement = tmdb.stillPath?.takeIf { it.startsWith("/") }?.let { "$TMDB_EPISODE_STILL_BASE$it" }
            if (replacement != null) { result.posterUrl = replacement; tmdbUsed(_q9("Tb9oSZoQQwKIr6+FcuA=")) }
        } else webUsed(_q9("Tb9oSZoQQwKIr6+FcuA="))
        if (result.score == null) {
            val replacement = tmdb.voteAverage?.takeIf { it in 0.0..10.0 && it > 0.0 }
            if (replacement != null) { result.score = Score.from10(replacement); tmdbUsed(_q9("Tb9oSZoQQwKLo7ODcg==")) }
        } else webUsed(_q9("Tb9oSZoQQwKLo7ODcg=="))
        if (result.date == null) {
            if (tmdb.airDate != null) { result.addDate(tmdb.airDate); tmdbUsed(_q9("Tb9oSZoQQwKcoaiU")) }
        } else webUsed(_q9("Tb9oSZoQQwKcoaiU"))
        if (result.runTime == null) {
            val replacement = tmdb.runtimeMinutes?.takeIf { it > 0 }
            if (replacement != null) { result.runTime = replacement * 60; tmdbUsed(_q9("Tb9oSZoQQwKKtbKFfv/z")) }
        } else webUsed(_q9("Tb9oSZoQQwKKtbKFfv/z"))
        result
    }

    context?.diagnostics?.apply {
        val fields = listOf(_q9("Tb9oSZoQQwKWobGU"), _q9("Tb9oSZoQQwKcpa+SZfvmjeJlww=="), _q9("Tb9oSZoQQwKIr6+FcuA="), _q9("Tb9oSZoQQwKLo7ODcg=="), _q9("Tb9oSZoQQwKcoaiU"), _q9("Tb9oSZoQQwKKtbKFfv/z"))
        fields.forEach { field ->
            val webCount = webCounts[field] ?: 0
            val tmdbCount = tmdbCounts[field] ?: 0
            recordField(field, when {
                webCount > 0 && tmdbCount > 0 -> _p22.MERGE
                webCount > 0 -> _p22.WEB
                tmdbCount > 0 -> _p22.TMDB_FALLBACK
                else -> _p22.EMPTY
            })
            setMetric("$field.web_count", webCount)
            setMetric("$field.tmdb_count", tmdbCount)
        }
        setMetric(_q9("Tb9oSZoQQwKdrq6YdPrzndRpws3EgA=="), enrichedEpisodeCount)
    }
    return resultEpisodes
}

private fun _p88(value: String?, episodeNumber: Int): Boolean {
    val normalized = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace(Regex(_q9("c+FeF6hf")), " ")
        ?.replace(Regex(_q9("dLwq")), " ")
        ?: return true
    if (normalized.isBlank()) return true

    val number = episodeNumber.toString()
    val padded = episodeNumber.toString().padStart(2, '0')
    val generic = setOf(
        "episode $number", "episode $padded", "ep $number", "ep $padded",
        "eps $number", "eps $padded", "e$number", "e$padded",
        "episode ke $number", "episode ke $padded",
    )
    return normalized in generic
}

private fun _p89(
    items: JSONArray?,
    isTv: Boolean,
): List<_p56> {
    items ?: return emptyList()
    val titleKey = if (isTv) _q9("Rq5sXw==") else _q9("XKZ1VpA=")
    val originalKey = if (isTv) _q9("R71oXZwaR0Cnrr2ccg==") else _q9("R71oXZwaR0CntLWFe/c=")
    val dateKey = if (isTv) _q9("TqZzSYErR0WKn7iQY/c=") else _q9("WqptX5QHQ3OcoaiU")

    return (0 until items.length())
        .mapNotNull { index -> items.optJSONObject(index) }
        .mapNotNull { item ->
            val tmdbId = item.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
            val localized = item._p105(titleKey)?.trim()?.takeIf { it.isNotBlank() }
            val original = item._p105(originalKey)?.trim()?.takeIf { it.isNotBlank() }
            val title = localized ?: original ?: return@mapNotNull null
            _p56(
                tmdbId = tmdbId,
                localizedTitle = title,
                originalTitle = original?.takeUnless { it.equals(title, ignoreCase = true) },
                year = item._p105(dateKey)?.take(4)?.toIntOrNull(),
                isTv = isTv,
            )
        }
        .distinctBy { it.tmdbId }
        .take(_p8)
}

internal suspend fun _p90(
    websiteRecommendations: List<SearchResponse>,
    tmdb: _d2?,
    currentProviderUrl: String? = null,
    providerApiName: String? = null,
    context: _p34 = _p34(),
    providerSearch: suspend (String) -> List<SearchResponse>?,
): List<SearchResponse> {
    val output = mutableListOf<SearchResponse>()
    val seenUrls = linkedSetOf<String>()
    val currentKey = currentProviderUrl?.let(::_p94)
    var websiteAccepted = 0
    var tmdbMapped = 0

    fun finishRecommendations(): List<SearchResponse> {
        context.diagnostics.recordField(_q9("WqpiVZgZQ0KcoaiYePzl"), when {
            websiteAccepted > 0 && tmdbMapped > 0 -> _p22.MERGE
            websiteAccepted > 0 -> _p22.WEB
            tmdbMapped > 0 -> _p22.TMDB
            else -> _p22.EMPTY
        })
        context.diagnostics.setMetric(_q9("WqpiVZgZQ0KcoaiYePy4ju5o3tHekWqmrvU1hg=="), websiteAccepted)
        context.diagnostics.setMetric(_q9("WqpiVZgZQ0KcoaiYePy4jeZuz+fHlUW1pOQEkfSvNpo="), tmdbMapped)
        context.diagnostics.setMetric(_q9("WqpiVZgZQ0KcoaiYePy4n+JkzNT1l1qwr/Q="), output.size)
        return output
    }

    fun addProviderResult(item: SearchResponse): Boolean {
        if (output.size >= _p10) return false
        if (providerApiName != null && item.apiName != providerApiName) return false
        val url = item.url.trim().takeIf { it.isNotBlank() } ?: return false
        if (url.contains(_q9("XKdkV5oCT0mcovKeZfU="), ignoreCase = true)) return false
        val key = _p94(url)
        if (currentKey != null && key == currentKey) return false
        if (!seenUrls.add(key)) return false
        output += item
        return true
    }

    websiteRecommendations.forEach(::addProviderResult)
    websiteAccepted = output.size
    if (!context.features.recommendations) {
        context.diagnostics.setMetric(_q9("WqpiVZgZQ0KcoaiYePy4n+5r2c3YkWqhqPM6kPe/PA=="), 1)
        return finishRecommendations()
    }
    if (output.size >= _p10) return finishRecommendations()

    val candidates = tmdb?.recommendations.orEmpty()
        .filter { it.tmdbId != tmdb?.tmdbId }
        .take(_p8)
    context.diagnostics.setMetric(_q9("WqpiVZgZQ0KcoaiYePy4jeZuz+fJlVuhqOQ6hv6FO4FdoXU="), candidates.size)
    if (candidates.isEmpty()) return finishRecommendations()

    var searchCalls = 0
    candidateLoop@ for (candidate in candidates) {
        if (output.size >= _p10) break
        val queries = listOfNotNull(candidate.localizedTitle, candidate.originalTitle)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy(::_e7)

        for (query in queries) {
            if (searchCalls >= _p9) break@candidateLoop
            searchCalls += 1
            val results = _p49(
                query = query,
                providerApiName = providerApiName,
                context = context,
                providerSearch = providerSearch,
            )
            val mapped = _p91(
                candidate = candidate,
                results = results,
                providerApiName = providerApiName,
            ) ?: continue
            if (addProviderResult(mapped)) tmdbMapped += 1
            continue@candidateLoop
        }
    }

    return finishRecommendations()
}

private fun _p91(
    candidate: _p56,
    results: List<SearchResponse>,
    providerApiName: String?,
): SearchResponse? {
    val expectedTitles = listOfNotNull(candidate.localizedTitle, candidate.originalTitle)
        .map(::_e7)
        .filter { it.isNotBlank() }
        .toSet()
    if (expectedTitles.isEmpty()) return null

    val matches = results
        .asSequence()
        .filter { providerApiName == null || it.apiName == providerApiName }
        .filter { it.url.isNotBlank() && !it.url.contains(_q9("XKdkV5oCT0mcovKeZfU="), ignoreCase = true) }
        .filter { result -> _e7(result.name) in expectedTitles }
        .filter { result -> _p93(candidate.isTv, result.type) }
        .distinctBy { _p94(it.url) }
        .toList()

    if (matches.isEmpty()) return null
    val candidateYear = candidate.year
    if (candidateYear != null) {
        val exactYear = matches.filter { _p92(it) == candidateYear }
        if (exactYear.size == 1) return exactYear.single()
        if (exactYear.size > 1) return null

        val withoutYear = matches.filter { _p92(it) == null }
        return withoutYear.singleOrNull().takeIf { withoutYear.size == matches.size }
    }

    return matches.singleOrNull()
}

private fun _p92(item: SearchResponse): Int? = when (item) {
    is MovieSearchResponse -> item.year
    is TvSeriesSearchResponse -> item.year
    is AnimeSearchResponse -> item.year
    else -> null
}

private fun _p93(candidateIsTv: Boolean, type: TvType?): Boolean {
    type ?: return false
    return if (candidateIsTv) {
        type in setOf(TvType.TvSeries, TvType.Anime, TvType.Cartoon, TvType.OVA, TvType.AsianDrama, TvType.Documentary)
    } else {
        type in setOf(TvType.Movie, TvType.AnimeMovie, TvType.Documentary)
    }
}

private fun _p94(value: String): String = value
    .trim()
    .substringBefore('#')
    .trimEnd('/')
    .lowercase(Locale.ROOT)

private fun _p95(
    website: List<String>,
    tmdb: List<String>,
): List<String> {
    val seen = linkedSetOf<String>()
    return (website + tmdb).mapNotNull { raw ->
        val value = raw.trim().replace(Regex(_q9("dLwq")), " ").takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val key = value.lowercase(Locale.ROOT)
        if (seen.add(key)) value else null
    }
}

private fun _p96(value: Double): Boolean =
    !value.isNaN() && value in 0.0..10.0

private fun _p97(value: String): Boolean =
    Regex(_q9("drt1ZpFfAg==")).matches(value.trim())

private suspend fun _p98(
    identity: _d0,
    language: String,
    context: _p34,
): _p50? {
    identity.tmdbId?.takeIf { it > 0 }?.let {
        return _p50(
            tmdbId = it,
            method = _p23.DIRECT_TMDB_ID,
            confidence = _p24.HIGH,
            score = 100,
        )
    }

    identity.imdbId?.let { imdbId ->
        _d6(imdbId, identity.isTv, language, context)?.let { tmdbId ->
            return _p50(
                tmdbId = tmdbId,
                method = _p23.IMDB_FIND,
                confidence = _p24.HIGH,
                score = 100,
            )
        }
    }

    return _p99(identity, language, context)
}

private suspend fun _d6(imdbId: String, isTv: Boolean, language: String, context: _p34): Int? {
    if (!Regex(_q9("drt1ZpFfAg==")).matches(imdbId)) return null

    val params = _d12(
        _q9("Tbd1X4caR0Cns7OEZfHz") to _q9("QaJlWKodQg=="),
        _q9("RK5vXYAVQUk=") to language,
    )
    val text = _p48(
        url = "$TMDB_BASE/find/$imdbId",
        params = params,
        context = context,
        kind = _p18.TMDB_LOOKUP,
        ttlMs = _p13,
    ) ?: return null
    val json = JSONObject(text)

    val key = if (isTv) _q9("XLleSJAHU0CMsw==") else _q9("RaB3U5ArVEmLtbCFZA==")
    return json.optJSONArray(key)?.optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
}

private suspend fun _p99(
    identity: _d0,
    language: String,
    context: _p34,
): _p50? {
    val queries = listOfNotNull(identity.originalTitle, identity.displayTitle)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy(::_e7)

    val typePath = if (identity.isTv) "tv" else _q9("RaB3U5A=")
    val yearParam = if (identity.isTv) _q9("TqZzSYErR0WKn7iQY/fJgO5r3w==") else _q9("UapgSA==")
    val candidatesById = linkedMapOf<Int, _p51>()

    for (query in queries) {
        val params = _d12(
            _q9("RK5vXYAVQUk=") to language,
            _q9("WbpkSIw=") to query,
        ).toMutableMap()
        identity.year?.let { params[yearParam] = it.toString() }

        val text = _p48(
            url = "$TMDB_BASE/search/$typePath",
            params = params,
            context = context,
            kind = _p18.TMDB_LOOKUP,
            ttlMs = _p13,
        ) ?: continue
        val results = JSONObject(text).optJSONArray(_q9("WqpyT5kAVQ==")) ?: continue
        val method = if (
            identity.originalTitle?.let(::_e7) == _e7(query)
        ) {
            _p23.ORIGINAL_TITLE_SEARCH
        } else {
            _p23.DISPLAY_TITLE_SEARCH
        }

        for (index in 0 until minOf(results.length(), 5)) {
            val candidate = results.optJSONObject(index) ?: continue
            val scored = _p100(candidate, identity, method) ?: continue
            val previous = candidatesById[scored.tmdbId]
            if (previous == null || scored.score > previous.score) candidatesById[scored.tmdbId] = scored
        }
    }

    val ranked = candidatesById.values
        .filter { it.confidence != _p24.LOW }
        .sortedByDescending { it.score }
    val top = ranked.firstOrNull() ?: return null
    val second = ranked.drop(1).firstOrNull()

    if (second != null && second.tmdbId != top.tmdbId && second.score == top.score) {
        context.diagnostics.recordMatch(
            _p23.AMBIGUOUS_SEARCH,
            _p24.MEDIUM,
            top.score,
        )
        context.diagnostics.incrementMetric(_q9("Ra51WZ1aR0GaqbuEeOfl"))
        return null
    }

    return _p50(
        tmdbId = top.tmdbId,
        method = top.method,
        confidence = top.confidence,
        score = top.score,
    )
}

private fun _p100(
    candidate: JSONObject,
    identity: _d0,
    method: _p23,
): _p51? {
    val tmdbId = candidate.optInt("id").takeIf { it > 0 } ?: return null
    val titleKey = if (identity.isTv) _q9("Rq5sXw==") else _q9("XKZ1VpA=")
    val originalKey = if (identity.isTv) _q9("R71oXZwaR0Cnrr2ccg==") else _q9("R71oXZwaR0CntLWFe/c=")
    val dateKey = if (identity.isTv) _q9("TqZzSYErR0WKn7iQY/c=") else _q9("WqptX5QHQ3OcoaiU")

    val candidateDisplay = candidate._p105(titleKey)?.let(::_e7).orEmpty()
    val candidateOriginal = candidate._p105(originalKey)?.let(::_e7).orEmpty()
    val expectedDisplay = _e7(identity.displayTitle)
    val expectedOriginal = identity.originalTitle?.let(::_e7).orEmpty()

    var score = 20
    val displayExact = expectedDisplay.isNotBlank() && (expectedDisplay == candidateDisplay || expectedDisplay == candidateOriginal)
    val originalExact = expectedOriginal.isNotBlank() && (expectedOriginal == candidateOriginal || expectedOriginal == candidateDisplay)
    if (!displayExact && !originalExact) return null
    if (displayExact) score += 50
    if (originalExact) score += 40

    val candidateYear = candidate._p105(dateKey)?.take(4)?.toIntOrNull()
    if (identity.year != null && candidateYear != null) {
        if (identity.year != candidateYear) return null
        score += 25
    }

    val confidence = when {
        score >= _p20 -> _p24.HIGH
        score >= _p21 -> _p24.MEDIUM
        else -> _p24.LOW
    }
    return _p51(
        tmdbId = tmdbId,
        method = method,
        confidence = confidence,
        score = score,
    )
}

internal fun _m4(
    webDescription: String?,
    tmdbOverview: String?,
    profile: _k8 = _j0.metadata.tmdb,
    filterProfile: _k7 = _j0.metadata.descriptionFilter,
): _m3 {
    val assessment = _m5(
        webDescription,
        qualityEnabled = profile.descriptionQuality.enabled,
        filterProfile = filterProfile,
    )
    val web = assessment.cleaned?.takeIf { it.isNotBlank() }
    val originalWeb = assessment.original?.takeIf { it.isNotBlank() }
    val tmdb = tmdbOverview?._p104()?.takeIf { it.isNotBlank() }

    fun webDecision(reason: String): _m3 = _m3(
        value = web ?: originalWeb,
        source = if (assessment.sanitized && !web.isNullOrBlank()) _q9("X6pjZZYYQ02W") else _q9("X6pj"),
        reason = reason,
        assessment = assessment,
    )

    fun invalidFallback(reason: String): _m3 = when (profile.invalidWebDescriptionFallback) {
        _k5.WEB -> _m3(
            value = originalWeb,
            source = if (originalWeb.isNullOrBlank()) _q9("TaJxTow=") else _q9("X6pj"),
            reason = reason,
            assessment = assessment,
        )
        _k5.EMPTY -> _m3(
            value = null,
            source = _q9("TaJxTow="),
            reason = reason,
            assessment = assessment,
        )
    }

    if (!profile.enabled) return webDecision(_q9("XKJlWKoQT1+ZorCUcw=="))

    return when (profile.descriptionPolicy) {
        _k4.LEGACY_TMDB_PREFERRED -> {
            if (!tmdb.isNullOrBlank()) _m3(tmdb, _q9("XKJlWKodQg=="), _q9("RKpmW5YNeViVpL6uZ+Dzn+54393O"), assessment)
            else _m3(originalWeb, if (originalWeb == null) _q9("TaJxTow=") else _q9("X6pj"), _q9("RKpmW5YNeVudooOXdv76m+ppxg=="), assessment)
        }

        _k4.WEB_ONLY -> {
            if (assessment.quality == _m0.INVALID) invalidFallback(_q9("X6pjZZwaUE2Uqbg="))
            else webDecision(_q9("X6pjZZoaSlU="))
        }

        _k4.TMDB_IF_MISSING -> {
            if (assessment.quality != _m0.INVALID && !web.isNullOrBlank()) {
                webDecision(_q9("X6pjZZQCR0WUob6dcg=="))
            } else if (!tmdb.isNullOrBlank()) {
                _m3(tmdb, _q9("XKJlWKodQg=="), _q9("X6pjZZgdVV+RrruueODJkOV8zNTDkA=="), assessment)
            } else invalidFallback(_q9("X6pjZZgdVV+RrruueODJkOV8zNTDkGqxrOQ5re60OZhJpm1blxhD"))
        }

        _k4.PREFER_INDONESIAN -> when (assessment.quality) {
            _m0.VALID -> when (assessment.language) {
                _m1.ID -> webDecision(_q9("Xq5tU5ErT0Kcr7KUZPv3l9R9yNo="))
                _m1.NON_ID -> {
                    if (!tmdb.isNullOrBlank()) _m3(tmdb, _q9("XKJlWKodQg=="), _q9("Xq5tU5ErSEOWn7WVSOXzm9R+wNzIq1SzoOk3k/m2PQ=="), assessment)
                    else webDecision(_q9("Xq5tU5ErSEOWn7WVSOXzm9R+wNzIq0CroPY6m/e7OoJN"))
                }
                _m1.UNKNOWN -> webDecision(_q9("Xq5tU5ErUUman7CQefXjmOxv8s3En1uqtu4="))
            }

            _m0.SUSPECT -> {
                if (!tmdb.isNullOrBlank()) _m3(tmdb, _q9("XKJlWKodQg=="), _q9("W7pySpAXUnOPpb6uY//ym9Rr29nDmFSnreU="), assessment)
                else if (!web.isNullOrBlank()) webDecision(_q9("W7pySpAXUnOPpb6uY//ym9R/w9nclVypoOI3lw=="))
                else invalidFallback(_q9("W7pySpAXUnOPpb6uef3JjPhrz9TPq0GgufQ="))
            }

            _m0.INVALID -> {
                if (!tmdb.isNullOrBlank()) _m3(tmdb, _q9("XKJlWKodQg=="), _q9("QaF3W5kdQnOPpb6uY//ym9Rr29nDmFSnreU="), assessment)
                else invalidFallback(_q9("QaF3W5kdQnOPpb6uY//ym9R/w9nclVypoOI3lw=="))
            }
        }
    }
}

internal fun _m5(
    raw: String?,
    qualityEnabled: Boolean = true,
    filterProfile: _k7 = _j0.metadata.descriptionFilter,
): _m2 {
    val original = raw?._p104()?.takeIf { it.isNotBlank() }
        ?: return _m2(
            original = null,
            cleaned = null,
            quality = _m0.INVALID,
            language = _m1.UNKNOWN,
            sanitized = false,
            reasons = listOf(_q9("RaZySZwaQQ==")),
        )

    if (!qualityEnabled) {
        return _m2(
            original = original,
            cleaned = original,
            quality = _m0.VALID,
            language = _m6(original),
            sanitized = false,
            reasons = listOf(_q9("WbpgVpwAX3ObqLmSfM3ykPhrz9TPkA==")),
        )
    }

    val reasons = mutableListOf<String>()
    var cleaned = original
    var sanitized = false

    val customRulesEnabled = filterProfile.enabled
    val genericRulesEnabled = customRulesEnabled && filterProfile.genericRules
    val customAllowHit = customRulesEnabled && filterProfile.allowPatterns.any { cleaned._p102(it) }
    if (customAllowHit) reasons += _q9("S7pyTpoZeU2UrLOGSP/3jehi")

    if (customRulesEnabled && filterProfile.stripPatterns.isNotEmpty()) {
        var removed = 0
        filterProfile.stripPatterns.forEach { phrase ->
            val next = cleaned._p103(phrase)
            if (next != cleaned) {
                cleaned = next._p104()
                removed += 1
            }
        }
        if (removed > 0) {
            sanitized = true
            reasons += _q9("S7pyTpoZeV+MsrWBSODzlOR8yNw=")
        }
    }

    val lowerBeforeBoundary = cleaned.lowercase(Locale.ROOT)
    val boundaryRules = buildList {
        if (genericRulesEnabled) {
            addAll(_p111)
            addAll(_p113)
        }
        if (customRulesEnabled) addAll(filterProfile.boundaryMarkers)
    }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.ROOT) }

    val boundary = boundaryRules
        .mapNotNull { phrase -> lowerBeforeBoundary.indexOf(phrase.lowercase(Locale.ROOT)).takeIf { it >= 0 }?.let { it to phrase } }
        .minByOrNull { it.first }

    if (boundary != null) {
        val (index, phrase) = boundary
        if (index >= _p108) {
            val prefix = cleaned.substring(0, index).trim(' ', '-', '—', '|', ':', ';')
            if (prefix.length >= _p107) {
                cleaned = prefix
                sanitized = true
                reasons += if (filterProfile.boundaryMarkers.any { it.equals(phrase, ignoreCase = true) }) {
                    _q9("S7pyTpoZeU6XtbKVduDvpv9rxNT1hlCorvY+lg==")
                } else if (_p111.any { it.equals(phrase, ignoreCase = true) }) {
                    _q9("Rap1W5EVUk2norOEefb3i/JV2dnDmGq3pO00hP6+")
                } else {
                    _q9("SqBoVpAGVkCZtLmuY/P/ldR4yNXFglCh")
                }
            } else {
                reasons += _q9("SqBoVpAGVkCZtLmuc/37kOVrw8w=")
            }
        } else {
            reasons += _q9("SqBoVpAGVkCZtLmueff3i9R52dnYgA==")
        }
    }

    cleaned = cleaned._p104()
    val cleanLower = cleaned.lowercase(Locale.ROOT)
    val strongHits = if (genericRulesEnabled) _p113.count { it in cleanLower } else 0
    val ctaHits = if (genericRulesEnabled) _p114.count { it in cleanLower } else 0
    val promoHits = if (genericRulesEnabled) _p115.count { it in cleanLower } else 0
    val urlCount = if (genericRulesEnabled) _p112.findAll(cleaned).count() else 0
    val customInvalidHits = if (customRulesEnabled && !customAllowHit) {
        filterProfile.invalidPatterns.count { cleaned._p102(it) }
    } else 0

    if (customInvalidHits > 0) reasons += _q9("S7pyTpoZeUWWtr2dfvbJlOp+ztA=")
    if (urlCount >= 2) reasons += _q9("RbptTpwESkmnta6dZA==")
    if (ctaHits >= 2) reasons += _q9("RbptTpwESkmno6iQ")
    if (promoHits >= 3) reasons += _q9("WL1uV5orQkOVqbKQeeY=")
    if (cleaned.length < _p107) reasons += _q9("XKBuZYYcSV6M")

    val minimumCleanLength = filterProfile.minimumCleanLength.coerceIn(_p109, _p110)
    val quality = when {
        customInvalidHits > 0 -> _m0.INVALID
        _q9("SqBoVpAGVkCZtLmueff3i9R52dnYgA==") in reasons -> _m0.INVALID
        _q9("SqBoVpAGVkCZtLmuc/37kOVrw8w=") in reasons && cleaned.length < _p108 -> _m0.INVALID
        strongHits >= 2 -> _m0.INVALID
        promoHits >= 4 -> _m0.INVALID
        ctaHits >= 3 -> _m0.INVALID
        urlCount >= 3 -> _m0.INVALID
        cleaned.length < minimumCleanLength -> _m0.INVALID
        cleaned.length < _p107 -> _m0.SUSPECT
        promoHits >= 2 || ctaHits >= 1 || urlCount >= 1 -> _m0.SUSPECT
        else -> _m0.VALID
    }

    return _m2(
        original = original,
        cleaned = cleaned.takeIf { quality != _m0.INVALID || sanitized },
        quality = quality,
        language = _m6(cleaned),
        sanitized = sanitized,
        reasons = reasons.distinct(),
    )
}

internal fun _p34._p101(decision: _m3) {
    diagnostics.recordField(
        _q9("TKpyWYcdVliRr7I="),
        when (decision.source.lowercase(Locale.ROOT)) {
            _q9("X6pj"), _q9("X6pjZZYYQ02W") -> _p22.WEB
            _q9("XKJlWKodQg==") -> _p22.TMDB_FALLBACK
            _q9("TaJxTow=") -> _p22.EMPTY
            else -> _p22.EMPTY
        },
    )
}

private fun String._p102(value: String): Boolean =
    value.isNotBlank() && indexOf(value, ignoreCase = true) >= 0

private fun String._p103(value: String): String {
    if (value.isBlank()) return this
    return Regex(Regex.escape(value), RegexOption.IGNORE_CASE).replace(this, " ")
}

fun _m6(text: String?): _m1 {
    val normalized = text?.lowercase(Locale.ROOT)?.replace(Regex(_q9("c5FdSo44W3HT")), " ")?.trim().orEmpty()
    if (normalized.isBlank()) return _m1.UNKNOWN
    val tokens = normalized.split(Regex(_q9("dLwq"))).filter { it.length > 1 }
    if (tokens.size < 5) return _m1.UNKNOWN

    val idScore = tokens.sumOf { token -> _p116[token] ?: 0 }
    val enScore = tokens.sumOf { token -> _p117[token] ?: 0 }

    return when {
        idScore >= 4 && idScore >= enScore + 2 -> _m1.ID
        enScore >= 4 && enScore >= idScore + 2 -> _m1.NON_ID
        else -> _m1.UNKNOWN
    }
}

private fun String._p104(): String =
    replace('\u00a0', ' ')
        .replace(Regex(_q9("dLwq")), " ")
        .trim()

private fun _e7(value: String): String = value
    .trim()
    .lowercase(Locale.ROOT)
    .replace(Regex(_q9("c5FdSo44W3CIu5KMSrk=")), " ")
    .trim()

private fun _d18(value: String): String = value
    .trim()
    .lowercase(Locale.ROOT)
    .replace(Regex(_q9("c5FdSo44W3CIu5KMSrk=")), " ")
    .trim()

private fun JSONObject._p105(key: String): String? =
    optString(key).trim().takeIf { it.isNotBlank() && it != _q9("RrptVg==") }

private fun JSONArray?._p106(key: String): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.optString(key)?.trim()?.takeIf { it.isNotBlank() }
    }
}

private const val _p107 = 60
private const val _p108 = 70
private const val _p109 = 20
private const val _p110 = 500

private val _p111 = listOf(
    _q9("R6NkUs8="), _q9("TKZxVYYAT0Kf4KyQc/Os"), _q9("XK5mVpwaQxY="), _q9("T6pvSJBO"), _q9("Q7pgVpwAR1/C"), _q9("XK5pT5tO"), _q9("TLpzW4YdHA=="),
    _q9("RqpmW4cVHA=="), _q9("WqZtU4ZO"), _q9("Sq5pW4YVHA=="), _q9("SaFmXZQGR0LC"), _q9("WKpvXpQER1iZruY="), _q9("TKZzX54HTxY="), _q9("WKpsW5waHA=="),
    _q9("WKByTpAQBk6B+g=="), _q9("WKByTpAQBkOW+g=="), _q9("TKZzX5YASV7C"), _q9("S65yTs8="), _q9("W7tgSIZO"), _q9("S6B0VIEGXxY="), _q9("WqptX5QHQxY="), _q9("WrpvTpwZQxY="),
)

private val _p112 = Regex(_q9("APBoE50AUlyL/+beOO7hjvxWg8T2lm6k7Ppr36L3BcV04SkFzxdJQYSuuYVr/eSe92PJxN6CSb24+ieB8q49kkehbVObEQ9wmg=="))

private val _p113 = listOf(
    _q9("WKpzVoBUQkWTpaiQf+f/"),
    _q9("Q65sU9UAT0iZq/yccvzvkOZ6zNaKklyppA=="),
    _q9("Q65sU9UcR0KBofyccvzzlPtvwdPLmhWpqO4w"),
    _q9("QK5vQ5RUU0KMtbfRZffgkO59"),
    _q9("XKZlW55UREmKtL2fcPXjl+wqx9ndlVc="),
    _q9("XKZlW55UREmKqKmTYvzxmOUqyd3Ek1Sr4fMyhu6p"),
    _q9("W6Z1T4ZUUUma4KmfY+f92eVlw8zFmhW2tfI+k/azNok="),
    _q9("RqBvTpoaBl+MsrmQevv4nqtsxNTH1Fqrrek1lw=="),
    _q9("TKB2VJkbR0jYprWderLinPlozMrf"),
    _q9("SaFlW9UeU0uZ4LiQZ/Pi2eZvw9fEgFqr4eYynvb6NI9BoW9DlA=="),
    _q9("QqZqW9UVSEiZ4LWfcPv42eZvw9fEgFqr4eYynvb6PItGqGBU1R9TTZSpqJBk"),
    _q9("W6ZtW54VSAyNrqiEfLL7nOZoyNTD1FGzpQ=="),
    _q9("SqBuUZgVVEfYs7WFYuE="),
    _q9("RKZvUdUVSlidsrKQY/vw"),
    _q9("SaNgV5QABl+RtKmCN+bzi+lr380="),
)

private val _p114 = listOf(
    _q9("Q6NoUdUQTwyLqbKY"),
    _q9("Q6NoUdUYT0KT"),
    _q9("TKB2VJkbR0jYs7maduD3l+w="),
    _q9("Q7pvUIAaQUXYs7WFYuE="),
    _q9("SqBuUZgVVEc="),
    _q9("SqptU9UQUEg="),
    _q9("RapsWJAYTwyctrg="),
    _q9("RqBvTpoaBkuKoaiYZA=="),
)

private val _p115 = listOf(
    _q9("W7tzX5QZT0Kf4LuDdub/ig=="),
    _q9("RqBvTpoaBkqRrLE="),
    _q9("TKB2VJkbR0jYprWdeg=="),
    _q9("Q7pgVpwAR1/YqLg="),
    _q9("SqN0SJQN"),
    _q9("X6pjF5EY"),
    _q9("W6pzTJAGBkmKsrOD"),
    _q9("RKZvUdUVSlidsrKQY/vw"),
    _q9("W7pjTpwASknYqbKVePzziuJr"),
    _q9("W7pjGpwaQkM="),
    _q9("W6Z1T4ZUTU2VqQ=="),
    _q9("X6pjSZwAQwyTobGY"),
)

private val _p116 = mapOf(
    _q9("Ua5vXQ==") to 2, _q9("TK5v") to 1, _q9("TKpvXZQa") to 2, _q9("XaF1T54=") to 2, _q9("TK5zUw==") to 1, _q9("WK5lWw==") to 1,
    _q9("W6puSJQaQQ==") to 2, _q9("W6p1X5kVTg==") to 2, _q9("Q6p1U54V") to 2, _q9("RapzX54V") to 2, _q9("Q65zX5sV") to 2,
    _q9("RapvUJQQTw==") to 2, _q9("TK5tW5g=") to 1, _q9("W6pjW5IVTw==") to 2, _q9("Rq5sT5s=") to 2, _q9("QKZvXZIV") to 1,
    _q9("XKpzSZAWU1g=") to 2, _q9("QK5zT4Y=") to 1, _q9("SaRgVA==") to 1, _q9("R6NkUg==") to 1, _q9("W65gTg==") to 1,
    _q9("Q6ppU5EBVk2W") to 2, _q9("Q6ZyW50=") to 2, _q9("TKZzU5sNRw==") to 2, _q9("Q6ptT5QGQU0=") to 1,
)

private val _p117 = mapOf(
    _q9("XKdk") to 1, _q9("SaFl") to 1, _q9("X6Z1Ug==") to 2, _q9("Tr1uVw==") to 1, _q9("XKdoSQ==") to 1, _q9("XKdgTg==") to 1,
    _q9("X6dkVA==") to 2, _q9("Sal1X4c=") to 2, _q9("QaF1VQ==") to 1, _q9("Sa1uT4E=") to 1, _q9("X6doVpA=") to 2, _q9("XKdkU4c=") to 2,
    _q9("QKZy") to 1, _q9("QKpz") to 1, _q9("SqpiVZgRVQ==") to 2, _q9("SqpiVZgR") to 2, _q9("RbpyTg==") to 1, _q9("X6du") to 1,
    _q9("Sqp1TZARSA==") to 2, _q9("SahgU5sHUg==") to 2, _q9("XKdzVYATTg==") to 2, _q9("RKZnXw==") to 1, _q9("Tq5sU5kN") to 1,
    _q9("RbZyTpAGT0ONsw==") to 2, _q9("W7tuSIw=") to 1, _q9("W6pzU5AH") to 1,
)
