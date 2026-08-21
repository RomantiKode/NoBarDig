package com.agooseangsa.AnimeXin

import org.json.JSONObject

internal data class AgooseHomepageProfile(
    val source: String,
    val key: String,
    val title: String,
)

internal enum class AgooseSourceMode {
    AUTO,
    ALL_AVAILABLE,
    FIRST_SUCCESS,
}

internal data class AgooseRuntimeDiscoveryProfile(
    val enabled: Boolean,
    val timeoutMs: Int,
)

internal data class AgooseOfflineProfile(
    val enabled: Boolean,
    val mediaSource: String,
    val label: String,
) {
    val available: Boolean
        get() = enabled && mediaSource.isNotBlank() && !mediaSource.contains(_q9("4fkyeml3JA=="), ignoreCase = true)
}

internal data class AgooseDiagnosticsProfile(
    val enabled: Boolean,
)

internal enum class AgooseDescriptionPolicy {
    LEGACY_TMDB_PREFERRED,
    PREFER_INDONESIAN,
    WEB_ONLY,
    TMDB_IF_MISSING,
}

internal enum class AgooseInvalidWebDescriptionFallback {
    EMPTY,
    WEB,
}

internal data class AgooseDescriptionQualityProfile(
    val enabled: Boolean,
)

internal data class AgooseDescriptionFilterProfile(
    val enabled: Boolean,
    val genericRules: Boolean,
    val boundaryMarkers: List<String>,
    val stripPatterns: List<String>,
    val invalidPatterns: List<String>,
    val allowPatterns: List<String>,
    val minimumCleanLength: Int,
    val explicitlyConfigured: Boolean,
)

internal data class AgooseTmdbMetadataProfile(
    val enabled: Boolean,
    val language: String,
    val descriptionPolicy: AgooseDescriptionPolicy,
    val invalidWebDescriptionFallback: AgooseInvalidWebDescriptionFallback,
    val descriptionQuality: AgooseDescriptionQualityProfile,
    val explicitlyConfigured: Boolean,
)

internal data class AgooseMetadataFeatureProfile(
    val core: Boolean = true,
    val visual: Boolean = true,
    val actors: Boolean = true,
    val tv: Boolean = true,
    val episodes: Boolean = true,
    val recommendations: Boolean = true,
) {
    val anyEnabled: Boolean
        get() = core || visual || actors || tv || episodes || recommendations
}

internal data class AgooseTagEnrichmentProfile(
    val enabled: Boolean = true,
    val maxTags: Int = 15,
    val maxEnrichedTags: Int = 8,
    val anilistMinimumRank: Int = 60,
    val include: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
    val mapping: Map<String, String> = emptyMap(),
)

internal data class AgooseFieldEnrichmentProfile(
    val enabled: Boolean = true,
    val nativeOutputPolicy: String = _q9("z9cMcVJQJHZP2Y93YIwy58XXu6RkR8M9e0PFrmsO5w=="),
    val identityFields: List<String> = emptyList(),
    val evidenceFields: List<String> = emptyList(),
    val playbackCapabilityFields: List<String> = emptyList(),
)

internal data class AgooseFieldRegistryProfile(
    val version: String = "",
    val registry: String = "",
    val enrichment: String = "",
    val mapping: String = "",
    val dependencyMap: String = "",
    val audit: String = "",
    val strictNativeMapping: Boolean = true,
)

internal enum class AgooseMetadataProviderId {
    TMDB,
    TVMAZE,
    THETVDB,
    WIKIMEDIA,
    OMDB,
    ANILIST,
    BANGUMI,
    KITSU,
    MYANIMELIST,
}

internal enum class AgooseMetadataContentLane {
    GENERAL,
    ANIME_ASIAN,
}

internal enum class AgooseMetadataFallbackPolicy {
    NO_MATCH_ONLY,
    NO_MATCH_OR_MISSING_FIELDS,
}

internal data class AgooseMetadataProviderEnabledProfile(
    val tmdb: Boolean = true,
    val tvmaze: Boolean = false,
    val thetvdb: Boolean = false,
    val wikimedia: Boolean = false,
    val omdb: Boolean = false,
    val anilist: Boolean = false,
    val bangumi: Boolean = false,
    val kitsu: Boolean = false,
    val myanimelist: Boolean = false,
) {
    fun isEnabled(id: AgooseMetadataProviderId): Boolean = when (id) {
        AgooseMetadataProviderId.TMDB -> tmdb
        AgooseMetadataProviderId.TVMAZE -> tvmaze
        AgooseMetadataProviderId.THETVDB -> thetvdb
        AgooseMetadataProviderId.WIKIMEDIA -> wikimedia
        AgooseMetadataProviderId.OMDB -> omdb
        AgooseMetadataProviderId.ANILIST -> anilist
        AgooseMetadataProviderId.BANGUMI -> bangumi
        AgooseMetadataProviderId.KITSU -> kitsu
        AgooseMetadataProviderId.MYANIMELIST -> myanimelist
    }
}

internal data class AgooseMetadataProvidersProfile(

    val order: List<AgooseMetadataProviderId> = listOf(
        AgooseMetadataProviderId.TMDB,
        AgooseMetadataProviderId.TVMAZE,
        AgooseMetadataProviderId.THETVDB,
        AgooseMetadataProviderId.WIKIMEDIA,
        AgooseMetadataProviderId.OMDB,
    ),

    val animeAsianOrder: List<AgooseMetadataProviderId> = listOf(
        AgooseMetadataProviderId.ANILIST,
        AgooseMetadataProviderId.TMDB,
        AgooseMetadataProviderId.BANGUMI,
        AgooseMetadataProviderId.KITSU,
        AgooseMetadataProviderId.MYANIMELIST,
        AgooseMetadataProviderId.THETVDB,
        AgooseMetadataProviderId.WIKIMEDIA,
        AgooseMetadataProviderId.TVMAZE,
        AgooseMetadataProviderId.OMDB,
    ),
    val enabled: AgooseMetadataProviderEnabledProfile = AgooseMetadataProviderEnabledProfile(),
    val fallbackPolicy: AgooseMetadataFallbackPolicy = AgooseMetadataFallbackPolicy.NO_MATCH_ONLY,
) {

    val effectiveOrder: List<AgooseMetadataProviderId>
        get() = effectiveOrder(AgooseMetadataContentLane.GENERAL)

    fun effectiveOrder(lane: AgooseMetadataContentLane): List<AgooseMetadataProviderId> {
        val configured = when (lane) {
            AgooseMetadataContentLane.GENERAL -> order
            AgooseMetadataContentLane.ANIME_ASIAN -> animeAsianOrder
        }
        return (configured + AgooseMetadataProviderId.values().toList())
            .distinct()
            .filter { enabled.isEnabled(it) }
    }
}

internal data class AgooseMetadataProfile(
    val tmdb: AgooseTmdbMetadataProfile,
    val descriptionFilter: AgooseDescriptionFilterProfile,
    val features: AgooseMetadataFeatureProfile,
    val providers: AgooseMetadataProvidersProfile,
    val tagEnrichment: AgooseTagEnrichmentProfile,
    val fieldEnrichment: AgooseFieldEnrichmentProfile,
)

internal class AgooseProviderProfile private constructor(
    private val root: JSONObject,
) {
    val provider: String = root.getString(_q9("0sQTWE9bHmc="))
    val websiteKey: String = root.getString(_q9("1dMeXU9LHl5Gzw=="))
    val websiteJsonUrl: String = root.optJSONObject(_q9("0NMRQVJa"))
        ?.optString(_q9("1dMeXU9LHl9Q2ZRGYZQ="))
        ?.trim()
        .orEmpty()
    val defaultMainUrl: String = root.optJSONObject(_q9("xtMaT1NTD2Y="))
        ?.optString(_q9("z9cVQHNNFw=="))
        ?.trim()
        .orEmpty()

    val homepage: List<AgooseHomepageProfile> = root.optJSONArray(_q9("ytkRS1ZeHHA="))?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                AgooseHomepageProfile(
                    source = item.optString(_q9("0dkJXEVa")),
                    key = item.optString(_q9("ydMF")),
                    title = item.optString(_q9("1t8IQkM=")),
                )
            }
        }
    }.orEmpty()

    private val endpoints = root.optJSONObject(_q9("x9gYXklWFWFQ")) ?: JSONObject()
    private val selectors = root.optJSONObject(_q9("0dMQS0VLFGdQ")) ?: JSONObject()
    private val playback = root.optJSONObject(_q9("0todV0ReGH4=")) ?: JSONObject()
    private val legacyFailover = playback.optJSONObject(_q9("xNcVQklJHmc="))
    private val runtimeDiscoveryObject = playback.optJSONObject(_q9("0MMSWk9SHlFKxZl8ZZ0y+w==")) ?: JSONObject()
    private val offlineObject = playback.optJSONObject(_q9("zdAaQk9RHg=="))
        ?: playback.optJSONObject(_q9("zdAaQk9RHlxN0pNwcowv8A=="))
        ?: JSONObject()
    private val diagnosticsObject = root.optJSONObject(_q9("xt8dSUhQCGFK1Yk=")) ?: JSONObject()
    private val contentFilter = root.optJSONObject(_q9("wdkSWkNRD1NK2o52YQ==")) ?: JSONObject()
    private val metadataObject = root.optJSONObject(_q9("z9MIT0JeD3Q="))
    private val tmdbMetadataObject = metadataObject?.optJSONObject(_q9("1tsYTA=="))
    private val descriptionFilterObject = metadataObject?.optJSONObject(_q9("xtMPTVRWC2FK2ZRVepQ059Y="))
    private val metadataFeaturesObject = metadataObject?.optJSONObject(_q9("xNMdWlNNHmY="))
    private val metadataProvidersObject = metadataObject?.optJSONObject(_q9("0sQTWE9bHmdQ"))
    private val tagEnrichmentObject = metadataObject?.optJSONObject(_q9("1tcba0hNEnZL2599Zw=="))
    private val fieldEnrichmentObject = metadataObject?.optJSONObject(_q9("xN8ZQkJ6FWdK1ZJ+dpY0"))
    private val fieldRegistryObject = root.optJSONObject(_q9("xN8ZQkJtHnJKxY5hag=="))

    val sourceMode: AgooseSourceMode = parseSourceMode()

    val serverResolveTimeoutMs: Int = safeTimeout(
        preferred = playback.optIntOrNull(_q9("0dMOWENNKXBQ2ZZldqwp78HVkb5IQA==")),
        legacy = legacyFailover?.optIntOrNull(_q9("0dMOWENNKXBQ2ZZldqwp78HVkb5IQA==")),
        fallback = DEFAULT_SERVER_TIMEOUT_MS,
        min = MIN_SERVER_TIMEOUT_MS,
        max = MAX_SERVER_TIMEOUT_MS,
    )

    val runtimeDiscovery: AgooseRuntimeDiscoveryProfile = AgooseRuntimeDiscoveryProfile(
        enabled = runtimeDiscoveryObject.optBoolean(_q9("x9gdTEpaHw=="), false),
        timeoutMs = safeTimeout(
            preferred = runtimeDiscoveryObject.optIntOrNull(_q9("1t8RS0lKD1hQ")),
            legacy = null,
            fallback = DEFAULT_RUNTIME_TIMEOUT_MS,
            min = MIN_RUNTIME_TIMEOUT_MS,
            max = MAX_RUNTIME_TIMEOUT_MS,
        ),
    )

    val offline: AgooseOfflineProfile = AgooseOfflineProfile(
        enabled = offlineObject.optBoolean(_q9("x9gdTEpaHw=="), false),
        mediaSource = offlineObject.optString(_q9("z9MYR0dsFGBR1Z8=")).trim(),
        label = offlineObject.optString(_q9("ztceS0o="), DEFAULT_OFFLINE_LABEL)
            .trim()
            .ifBlank { DEFAULT_OFFLINE_LABEL },
    )

    val diagnostics: AgooseDiagnosticsProfile = AgooseDiagnosticsProfile(
        enabled = when {
            diagnosticsObject.has(_q9("x9gdTEpaHw==")) -> diagnosticsObject.optBoolean(_q9("x9gdTEpaHw=="), false)
            diagnosticsObject.has(_q9("0todV0ReGH53xJtwdr0u48bWga4=")) -> diagnosticsObject.optBoolean(_q9("0todV0ReGH53xJtwdr0u48bWga4="), false)
            else -> false
        },
    )

    val metadata: AgooseMetadataProfile = AgooseMetadataProfile(
        tmdb = parseTmdbMetadataProfile(),
        descriptionFilter = parseDescriptionFilterProfile(),
        features = parseMetadataFeatureProfile(),
        providers = parseMetadataProvidersProfile(),
        tagEnrichment = parseTagEnrichmentProfile(),
        fieldEnrichment = parseFieldEnrichmentProfile(),
    )

    val fieldRegistry: AgooseFieldRegistryProfile = parseFieldRegistryProfile()

    fun endpoint(key: String, fallback: String = ""): String =
        endpoints.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun selector(key: String, fallback: String = ""): String =
        selectors.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun playbackInt(key: String, fallback: Int): Int =
        playback.optInt(key, fallback).takeIf { it > 0 } ?: fallback

    fun playbackString(key: String, fallback: String = ""): String =
        playback.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun blockedCategories(): Set<String> = contentFilter.stringSet(_q9("wNoTTU1aH1ZCwp90fIop59c="))
    fun blockedTags(): Set<String> = contentFilter.stringSet(_q9("wNoTTU1aH0FC0Yk="))

    private fun parseSourceMode(): AgooseSourceMode {
        val raw = playback.optString(_q9("0dkJXEVaNnpH0w==")).trim().lowercase()
        return when (raw) {
            _q9("w8MIQQ==") -> AgooseSourceMode.AUTO
            _q9("w9oQcUdJGnxP15h/dg==") -> AgooseSourceMode.ALL_AVAILABLE
            _q9("xN8OXVJgCGBA1Z9gYA==") -> AgooseSourceMode.FIRST_SUCCESS
            "" -> if (legacyFailover != null) AgooseSourceMode.FIRST_SUCCESS else AgooseSourceMode.AUTO
            else -> AgooseSourceMode.AUTO
        }
    }

    private fun parseTmdbMetadataProfile(): AgooseTmdbMetadataProfile {
        val configured = tmdbMetadataObject != null
        if (!configured) {
            return AgooseTmdbMetadataProfile(
                enabled = true,
                language = DEFAULT_TMDB_LANGUAGE,
                descriptionPolicy = AgooseDescriptionPolicy.LEGACY_TMDB_PREFERRED,
                invalidWebDescriptionFallback = AgooseInvalidWebDescriptionFallback.WEB,
                descriptionQuality = AgooseDescriptionQualityProfile(enabled = false),
                explicitlyConfigured = false,
            )
        }

        val tmdb = tmdbMetadataObject ?: JSONObject()
        val quality = tmdb.optJSONObject(_q9("xtMPTVRWC2FK2ZRCZpks69DD")) ?: JSONObject()
        return AgooseTmdbMetadataProfile(
            enabled = tmdb.optBoolean(_q9("x9gdTEpaHw=="), true),
            language = tmdb.optString(_q9("ztcSSVNeHHA="), DEFAULT_TMDB_LANGUAGE)
                .trim()
                .ifBlank { DEFAULT_TMDB_LANGUAGE },
            descriptionPolicy = when (tmdb.optString(_q9("xtMPTVRWC2FK2ZRDfJQp4d0=")).trim().lowercase()) {
                _q9("ztMbT0VGJGFO0phMY4ol5MHIlq9h") -> AgooseDescriptionPolicy.LEGACY_TMDB_PREFERRED
                _q9("1dMecUlRF2w=") -> AgooseDescriptionPolicy.WEB_ONLY
                _q9("1tsYTHlWHUpO34lgepYn") -> AgooseDescriptionPolicy.TMDB_IF_MISSING
                _q9("0sQZSENNJHxN0pV9dosp48o="), "" -> AgooseDescriptionPolicy.PREFER_INDONESIAN
                else -> AgooseDescriptionPolicy.PREFER_INDONESIAN
            },
            invalidWebDescriptionFallback = when (tmdb.optString(_q9("y9gKT0pWH0JG1L52YJsy69TOjaVrdcsncn7CpHI=")).trim().lowercase()) {
                _q9("1dMe") -> AgooseInvalidWebDescriptionFallback.WEB
                _q9("x9sMWl8="), "" -> AgooseInvalidWebDescriptionFallback.EMPTY
                else -> AgooseInvalidWebDescriptionFallback.EMPTY
            },
            descriptionQuality = AgooseDescriptionQualityProfile(
                enabled = quality.optBoolean(_q9("x9gdTEpaHw=="), true),
            ),
            explicitlyConfigured = true,
        )
    }

    private fun parseMetadataFeatureProfile(): AgooseMetadataFeatureProfile {
        val features = metadataFeaturesObject ?: return AgooseMetadataFeatureProfile()
        fun enabled(key: String): Boolean = when (val raw = features.opt(key)) {
            null -> true
            is Boolean -> raw
            else -> true
        }
        return AgooseMetadataFeatureProfile(
            core = enabled(_q9("wdkOSw==")),
            visual = enabled(_q9("1N8PW0dT")),
            actors = enabled(_q9("w9UIQVRM")),
            tv = enabled("tv"),
            episodes = enabled(_q9("x8YVXUlbHmY=")),
            recommendations = enabled(_q9("0NMfQUtSHntH1456fJYz")),
        )
    }

    private fun parseMetadataProvidersProfile(): AgooseMetadataProvidersProfile {
        val providers = metadataProvidersObject ?: return AgooseMetadataProvidersProfile()
        val enabledObject = providers.optJSONObject(_q9("x9gdTEpaHw==")) ?: JSONObject()

        fun safeEnabled(key: String, fallback: Boolean): Boolean = when (val raw = enabledObject.opt(key)) {
            null -> fallback
            is Boolean -> raw
            else -> fallback
        }

        fun parseProviderId(raw: String): AgooseMetadataProviderId? = when (raw.trim().lowercase()) {
            _q9("1tsYTA==") -> AgooseMetadataProviderId.TMDB
            _q9("1sART1xa") -> AgooseMetadataProviderId.TVMAZE
            _q9("1t4ZWlBbGQ=="), _q9("1sAYTA==") -> AgooseMetadataProviderId.THETVDB
            _q9("1d8XR0taH3xC"), _q9("1d8XR0JeD3Q="), _q9("1d8XR1ZaH3xC") -> AgooseMetadataProviderId.WIKIMEDIA
            _q9("zdsYTA==") -> AgooseMetadataProviderId.OMDB
            _q9("w9gVQk9MDw==") -> AgooseMetadataProviderId.ANILIST
            _q9("wNcSSVNSEg=="), _q9("wNER") -> AgooseMetadataProviderId.BANGUMI
            _q9("yd8IXVM=") -> AgooseMetadataProviderId.KITSU
            _q9("z88dQE9SHnlKxY4="), _q9("z88jT0hWFnB82pNgZw=="), _q9("z9cQ") -> AgooseMetadataProviderId.MYANIMELIST
            else -> null
        }

        fun parseOrder(key: String, fallback: List<AgooseMetadataProviderId>): List<AgooseMetadataProviderId> =
            providers.optJSONArray(key)?.let { array ->
                (0 until array.length())
                    .mapNotNull { parseProviderId(array.optString(it)) }
                    .distinct()
            }.orEmpty().ifEmpty { fallback }

        val defaults = AgooseMetadataProvidersProfile()
        val configuredOrder = parseOrder(_q9("zcQYS1Q="), defaults.order)
        val configuredAnimeAsianOrder = parseOrder(_q9("w9gVQ0N+CHxC2LVhd50y"), defaults.animeAsianOrder)

        val fallbackPolicy = when (providers.optString(_q9("xNcQQkReGH5z2ZZ6cIE=")).trim().lowercase()) {
            _q9("zNkjQ0dLGH182YhMfpEz8c3Ug5VjWs8nem8=") -> AgooseMetadataFallbackPolicy.NO_MATCH_OR_MISSING_FIELDS
            _q9("zNkjQ0dLGH182ZR/ag=="), "" -> AgooseMetadataFallbackPolicy.NO_MATCH_ONLY
            else -> AgooseMetadataFallbackPolicy.NO_MATCH_ONLY
        }

        return AgooseMetadataProvidersProfile(
            order = configuredOrder,
            animeAsianOrder = configuredAnimeAsianOrder,
            enabled = AgooseMetadataProviderEnabledProfile(
                tmdb = safeEnabled(_q9("1tsYTA=="), true),
                tvmaze = safeEnabled(_q9("1sART1xa"), false),
                thetvdb = safeEnabled(_q9("1t4ZWlBbGQ=="), false),
                wikimedia = safeEnabled(_q9("1d8XR0taH3xC"), false),
                omdb = safeEnabled(_q9("zdsYTA=="), false),
                anilist = safeEnabled(_q9("w9gVQk9MDw=="), false),
                bangumi = safeEnabled(_q9("wNcSSVNSEg=="), false),
                kitsu = safeEnabled(_q9("yd8IXVM="), false),
                myanimelist = safeEnabled(_q9("z88dQE9SHnlKxY4="), false),
            ),
            fallbackPolicy = fallbackPolicy,
        )
    }

    private fun parseFieldEnrichmentProfile(): AgooseFieldEnrichmentProfile {
        val cfg = fieldEnrichmentObject ?: return AgooseFieldEnrichmentProfile()
        val policy = cfg.optString(_q9("zNcIR1BaNGBXxo9nQ5cs68fD")).trim().ifBlank { _q9("z9cMcVJQJHZP2Y93YIwy58XXu6RkR8M9e0PFrmsO5w==") }
        return AgooseFieldEnrichmentProfile(
            enabled = cfg.optBoolean(_q9("x9gdTEpaHw=="), true),
            nativeOutputPolicy = if (policy == _q9("z9cMcVJQJHZP2Y93YIwy58XXu6RkR8M9e0PFrmsO5w==")) policy else _q9("z9cMcVJQJHZP2Y93YIwy58XXu6RkR8M9e0PFrmsO5w=="),
            identityFields = cfg.safeFieldNameList(_q9("y9IZQFJWD2xl359/d4s=")),
            evidenceFields = cfg.safeFieldNameList(_q9("x8AVSkNRGHBl359/d4s=")),
            playbackCapabilityFields = cfg.safeFieldNameList(_q9("0todV0ReGH5g14pycZEs69DDoqNgX844")),
        )
    }

    private fun parseFieldRegistryProfile(): AgooseFieldRegistryProfile {
        val cfg = fieldRegistryObject ?: return AgooseFieldRegistryProfile()
        fun safeDocPath(key: String): String {
            val value = cfg.optString(key).trim()
            return value.takeIf { it.startsWith(_q9("0cIdQEJeCXFQmQ==")) && it.endsWith(_q9("jNsY")) && it.length <= MAX_FIELD_DOC_PATH_LENGTH }.orEmpty()
        }
        return AgooseFieldRegistryProfile(
            version = cfg.optString(_q9("1NMOXU9QFQ==")).trim().take(MAX_FIELD_REGISTRY_VERSION_LENGTH),
            registry = safeDocPath(_q9("0NMbR1VLCWw=")),
            enrichment = safeDocPath(_q9("x9gOR0VXFnBNwg==")),
            mapping = safeDocPath(_q9("z9cMXk9RHA==")),
            dependencyMap = safeDocPath(_q9("xtMMS0hbHntAz7dyYw==")),
            audit = safeDocPath(_q9("w8MYR1I=")),
            strictNativeMapping = cfg.optBoolean(_q9("0cIOR0VLNXRX34x2Xpkw8s3Ugw=="), true),
        )
    }

    private fun parseTagEnrichmentProfile(): AgooseTagEnrichmentProfile {
        val cfg = tagEnrichmentObject ?: return AgooseTagEnrichmentProfile()
        fun safeInt(key: String, fallback: Int, min: Int, max: Int): Int {
            val value = cfg.optIntOrNull(key) ?: fallback
            return if (value in min..max) value else fallback
        }
        return AgooseTagEnrichmentProfile(
            enabled = cfg.optBoolean(_q9("x9gdTEpaHw=="), true),
            maxTags = safeInt(_q9("z9cEekdYCA=="), DEFAULT_TAG_MAX_TAGS, MIN_TAG_MAX_TAGS, MAX_TAG_MAX_TAGS),
            maxEnrichedTags = safeInt(_q9("z9cEa0hNEnZL055Hcp8z"), DEFAULT_TAG_MAX_ENRICHED, 0, MAX_TAG_MAX_TAGS),
            anilistMinimumRank = safeInt(_q9("w9gVQk9MD1hK2JN+ZpUS48rR"), DEFAULT_TAG_ANILIST_MIN_RANK, 1, 100),
            include = cfg.safeTagStringList(_q9("y9gfQlNbHg==")),
            exclude = cfg.safeTagStringList(_q9("x84fQlNbHg==")),
            mapping = cfg.safeTagStringMap(_q9("z9cMXk9RHA==")),
        )
    }

    private fun parseDescriptionFilterProfile(): AgooseDescriptionFilterProfile {
        val configured = descriptionFilterObject != null
        if (!configured) {

            return AgooseDescriptionFilterProfile(
                enabled = true,
                genericRules = true,
                boundaryMarkers = emptyList(),
                stripPatterns = emptyList(),
                invalidPatterns = emptyList(),
                allowPatterns = emptyList(),
                minimumCleanLength = LEGACY_MIN_DESCRIPTION_LENGTH,
                explicitlyConfigured = false,
            )
        }

        val rules = descriptionFilterObject ?: JSONObject()
        return AgooseDescriptionFilterProfile(
            enabled = rules.optBoolean(_q9("x9gdTEpaHw=="), true),
            genericRules = rules.optBoolean(_q9("xdMSS1RWGEdW2p9g"), true),
            boundaryMarkers = rules.safeStringList(_q9("wNkJQEJeCWxu14h4dooz")),
            stripPatterns = rules.safeStringList(_q9("0cIOR1ZvGmFX04h9YA==")),
            invalidPatterns = rules.safeStringList(_q9("y9gKT0pWH0VCwo52YZYz")),
            allowPatterns = rules.safeStringList(_q9("w9oQQVFvGmFX04h9YA==")),
            minimumCleanLength = safeDescriptionLength(rules.optIntOrNull(_q9("z98SR0tKFlZP05t9X50u5dDS"))),
            explicitlyConfigured = true,
        )
    }

    private fun JSONObject.stringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun JSONObject.safeStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.length in 2..MAX_DESCRIPTION_RULE_LENGTH }
            .distinctBy { it.lowercase() }
            .take(MAX_DESCRIPTION_RULE_COUNT)
    }

    private fun JSONObject.safeFieldNameList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.length in 2..MAX_FIELD_NAME_LENGTH && it.matches(Regex(_q9("+fdRdEcSAUh499dJctU6somDu+QoboA="))) }
            .distinctBy { it.lowercase() }
            .take(MAX_FIELD_NAME_COUNT)
    }

    private fun JSONObject.safeTagStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length())
            .map { array.optString(it).trim().replace(Regex(_q9("/sVX")), " ") }
            .filter { it.length in 2..MAX_TAG_RULE_LENGTH }
            .distinctBy { it.lowercase() }
            .take(MAX_TAG_RULE_COUNT)
    }

    private fun JSONObject.safeTagStringMap(key: String): Map<String, String> {
        val obj = optJSONObject(key) ?: return emptyMap()
        val out = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext() && out.size < MAX_TAG_RULE_COUNT) {
            val originalKey = keys.next()
            val rawKey = originalKey.trim().replace(Regex(_q9("/sVX")), " ")
            val value = obj.optString(originalKey).trim().replace(Regex(_q9("/sVX")), " ")
            if (rawKey.length in 2..MAX_TAG_RULE_LENGTH && value.length in 2..MAX_TAG_RULE_LENGTH) out[rawKey] = value
        }
        return out
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key)) return null
        val raw = opt(key)
        return when (raw) {
            is Int -> raw
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    companion object {
        private const val DEFAULT_SERVER_TIMEOUT_MS = 10_000
        private const val MIN_SERVER_TIMEOUT_MS = 1_000
        private const val MAX_SERVER_TIMEOUT_MS = 60_000
        private const val DEFAULT_RUNTIME_TIMEOUT_MS = 7_000
        private const val MIN_RUNTIME_TIMEOUT_MS = 1_000
        private const val MAX_RUNTIME_TIMEOUT_MS = 30_000
        private const val DEFAULT_OFFLINE_LABEL = "SOURCE VIDEO OFFLINE"
        private const val DEFAULT_TMDB_LANGUAGE = "id-ID"
        private const val LEGACY_MIN_DESCRIPTION_LENGTH = 24
        private const val DEFAULT_MIN_DESCRIPTION_LENGTH = 40
        private const val MIN_DESCRIPTION_LENGTH = 20
        private const val MAX_DESCRIPTION_LENGTH = 500
        private const val MAX_DESCRIPTION_RULE_COUNT = 64
        private const val MAX_DESCRIPTION_RULE_LENGTH = 240
        private const val DEFAULT_TAG_MAX_TAGS = 15
        private const val DEFAULT_TAG_MAX_ENRICHED = 8
        private const val DEFAULT_TAG_ANILIST_MIN_RANK = 60
        private const val MIN_TAG_MAX_TAGS = 5
        private const val MAX_TAG_MAX_TAGS = 30
        private const val MAX_TAG_RULE_COUNT = 64
        private const val MAX_TAG_RULE_LENGTH = 64
        private const val MAX_FIELD_NAME_COUNT = 64
        private const val MAX_FIELD_NAME_LENGTH = 64
        private const val MAX_FIELD_DOC_PATH_LENGTH = 240
        private const val MAX_FIELD_REGISTRY_VERSION_LENGTH = 32

        private fun safeDescriptionLength(value: Int?): Int {
            val candidate = value ?: DEFAULT_MIN_DESCRIPTION_LENGTH
            return if (candidate in MIN_DESCRIPTION_LENGTH..MAX_DESCRIPTION_LENGTH) candidate else DEFAULT_MIN_DESCRIPTION_LENGTH
        }

        private fun safeTimeout(
            preferred: Int?,
            legacy: Int?,
            fallback: Int,
            min: Int,
            max: Int,
        ): Int {
            val value = preferred ?: legacy ?: fallback
            return if (value in min..max) value else fallback
        }

        val current: AgooseProviderProfile by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val parsed = JSONObject(BuildConfig.AGOOSE_PROVIDER_PROFILE_JSON)
            require(parsed.optString(_q9("0dUUS0te")) == _q9("w9ETQVVaVmVR2Yx6d50yr9TIi6xsX89maC0=")) {
                _q9("99gPW1ZPFGdX0548fpEz8c3Ug+pEVMUkbXmDl2sS5d/G0w5+VFAdfE/T2mBwkCXvxQ==")
            }
            AgooseProviderProfile(parsed)
        }
    }
}
