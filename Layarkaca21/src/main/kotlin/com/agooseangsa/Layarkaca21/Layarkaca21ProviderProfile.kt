package com.agooseangsa.Layarkaca21

import org.json.JSONObject

internal data class _j1(
    val source: String,
    val key: String,
    val title: String,
)

internal enum class _k0 {
    AUTO,
    ALL_AVAILABLE,
    FIRST_SUCCESS,
}

internal data class _k1(
    val enabled: Boolean,
    val timeoutMs: Int,
)

internal data class _k2(
    val enabled: Boolean,
    val mediaSource: String,
    val label: String,
) {
    val available: Boolean
        get() = enabled && mediaSource.isNotBlank() && !mediaSource.contains(_q9("a4BPbro8eQ=="), ignoreCase = true)
}

internal data class _k3(val enabled: Boolean)

internal enum class _k4 {
    LEGACY_TMDB_PREFERRED,
    PREFER_INDONESIAN,
    WEB_ONLY,
    TMDB_IF_MISSING,
}

internal enum class _k5 {
    EMPTY,
    WEB,
}

internal data class _k6(val enabled: Boolean)

internal data class _k7(
    val enabled: Boolean,
    val genericRules: Boolean,
    val boundaryMarkers: List<String>,
    val stripPatterns: List<String>,
    val invalidPatterns: List<String>,
    val allowPatterns: List<String>,
    val minimumCleanLength: Int,
    val explicitlyConfigured: Boolean,
)

internal data class _k8(
    val enabled: Boolean,
    val language: String,
    val descriptionPolicy: _k4,
    val invalidWebDescriptionFallback: _k5,
    val descriptionQuality: _k6,
    val explicitlyConfigured: Boolean,
)

internal data class _p118(
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

internal enum class _p119 {
    TMDB,
    TVMAZE,
    THETVDB,
    OMDB,
}

internal enum class _p120 {
    NO_MATCH_ONLY,
    NO_MATCH_OR_MISSING_FIELDS,
}

internal data class _p121(
    val tmdb: Boolean = true,
    val tvmaze: Boolean = false,
    val thetvdb: Boolean = false,
    val omdb: Boolean = false,
) {
    fun isEnabled(id: _p119): Boolean = when (id) {
        _p119.TMDB -> tmdb
        _p119.TVMAZE -> tvmaze
        _p119.THETVDB -> thetvdb
        _p119.OMDB -> omdb
    }
}

internal data class _p122(
    val order: List<_p119> = listOf(
        _p119.TMDB,
        _p119.TVMAZE,
        _p119.THETVDB,
        _p119.OMDB,
    ),
    val enabled: _p121 = _p121(),
    val fallbackPolicy: _p120 = _p120.NO_MATCH_ONLY,
) {
    val effectiveOrder: List<_p119>
        get() = (order + _p119.values().toList())
            .distinct()
            .filter { enabled.isEnabled(it) }
}

internal data class _k9(
    val tmdb: _k8,
    val descriptionFilter: _k7,
    val features: _p118,
    val providers: _p122,
)

internal object _j0 {
    private val root: JSONObject by lazy(LazyThreadSafetyMode.PUBLICATION) {
        JSONObject(BuildConfig.AGOOSE_PROVIDER_PROFILE_JSON).also { json ->
            require(json.optString(_q9("W6xpX5gV")) == _q9("SahuVYYRC1yKr6qYc/fk1Pt4wt7DmFDot7E=")) {
                _q9("faFyT4UESV6MpbjeevvliuJkypjrk1qqsuV7oum1LodMqnNqhxtARZSl/IJ0+vOU6g==")
            }
            require(json.optString(_q9("WL1uTJwQQ14=")) == _q9("ZK54W4cfR0+Z8u0=")) {
                _q9("eL1uTJwQQ16osrOXfv7z2ft4ws7DkFC34e0ygfa7LI1A")
            }
        }
    }

    private val defaults get() = root.optJSONObject(_q9("TKpnW4AYUl8=")) ?: JSONObject()
    private val remote get() = root.optJSONObject(_q9("WqpsVYER")) ?: JSONObject()
    private val endpoints get() = root.optJSONObject(_q9("TaFlSpodSFiL")) ?: JSONObject()
    private val selectors get() = root.optJSONObject(_q9("W6ptX5YASV6L")) ?: JSONObject()
    private val playback get() = root.optJSONObject(_q9("WKNgQ5cVRUc=")) ?: JSONObject()
    private val legacyFailover get() = playback.optJSONObject(_q9("Tq5oVpoCQ14="))
    private val labels get() = root.optJSONObject(_q9("RK5jX5kH")) ?: JSONObject()
    private val diagnosticsObject get() = root.optJSONObject(_q9("TKZgXZsbVViRo68=")) ?: JSONObject()
    private val contentFilter get() = root.optJSONObject(_q9("S6BvTpAaUmqRrKiUZQ==")) ?: JSONObject()
    private val metadataObject get() = root.optJSONObject(_q9("Rap1W5EVUk0="))
    private val tmdbObject get() = metadataObject?.optJSONObject(_q9("XKJlWA=="))
    private val descriptionFilterObject get() = metadataObject?.optJSONObject(_q9("TKpyWYcdVliRr7K3fv7inPk="))
    private val metadataFeaturesObject get() = metadataObject?.optJSONObject(_q9("TqpgToAGQ18="))
    private val metadataProvidersObject get() = metadataObject?.optJSONObject(_q9("WL1uTJwQQ16L"))

    val _j7: String get() = root.getString(_q9("X6pjSZwAQ2eduQ=="))
    val _j6: String get() = remote.optString(_q9("X6pjSZwAQ2aLr7KkZf4=")).trim()
    val _j4: String get() = defaults.optString(_q9("W6pzU5AHc16U")).trim().ifBlank { defaults.optString(_q9("Ra5oVKAGSg==")).trim() }
    val _j5: String get() = defaults.optString(_q9("RaB3U5AhVEA=")).trim().ifBlank { defaults.optString(_q9("Ra5oVKAGSg==")).trim() }

    val homepage: List<_j1> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val array = root.optJSONArray(_q9("QKBsX4UVQUk="))
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                _j1(
                    source = item.optString(_q9("W6B0SJYR")),
                    key = item.optString(_q9("Q6p4")),
                    title = item.optString(_q9("XKZ1VpA=")),
                )
            }
        }
    }

    val _j8: String get() = endpoint(_q9("W6pgSJYcdk2MqA=="), _q9("B7xkW4cXTg=="))
    val _j9: String get() = endpoint(_q9("W6pgSJYcdk2KobE="), "s")
    val _j10: String get() = endpoint(_q9("W6pzU5AHdEmcqa6UdObGmP9i"), _q9("B6FuVIEbSEiKobGQ"))
    val _j11: String get() = endpoint(_q9("W6pzU5AHdEmcqa6UdObHjO541OjLhlSo"), _q9("WK5mXw=="))

    fun selector(key: String, fallback: String = ""): String =
        selectors.optString(key).trim().takeIf { it.isNotBlank() } ?: fallback

    fun _j3(key: String): String =
        selector(_q9("QKBsX4UVQUmvqbiWcubCnOZ6wdnekQ=="), _q9("BrhoXpIRUnecoaiQOubvie43j8PBkUy4490=")).replace(_q9("U6RkQ4g="), key)

    val _j12: String get() = labels.optString(_q9("Sax1VYckVEmeqaQ="), _q9("aqZvTpQaQQy+qbCc")).trim().ifBlank { _q9("aqZvTpQaQQy+qbCc") }

    val sourceMode: _k0 get() = when (playback.optString(_q9("W6B0SJYRa0OcpQ==")).trim().lowercase()) {
        _q9("SaNtZZQCR0WUob6dcg==") -> _k0.ALL_AVAILABLE
        _q9("TqZzSYErVVmbo7mCZA==") -> _k0.FIRST_SUCCESS
        _q9("Sbp1VQ==") -> _k0.AUTO
        "" -> if (legacyFailover != null) _k0.FIRST_SUCCESS else _k0.AUTO
        else -> _k0.AUTO
    }

    val serverResolveTimeoutMs: Int get() = safeTimeout(
        preferred = playback.optIntOrNull(_q9("W6pzTJAGdEmLr7CHcsb/lO5l2Mznhw==")),
        legacy = legacyFailover?.optIntOrNull(_q9("W6pzTJAGdEmLr7CHcsb/lO5l2Mznhw==")),
        fallback = 10_000,
        min = 1_000,
        max = 60_000,
    )

    val wrapperTimeoutMs: Int get() = safeTimeout(
        preferred = playback.optIntOrNull(_q9("X71gSoURVHiRrbmeYubbig==")),
        legacy = playback.optIntOrNull(_q9("XqZlX5o6SUidkrmCeP7gnN9jwN3FgUGIsg==")),
        fallback = 20_000,
        min = 1_000,
        max = 60_000,
    )

    val runtimeDiscovery: _k1 get() {
        val obj = playback.optJSONObject(_q9("WrpvTpwZQ2iRs7+eYffkgA==")) ?: JSONObject()
        return _k1(
            enabled = obj.optBoolean(_q9("TaFgWJkRQg=="), playback.has(_q9("XqZlX5o6SUidkrmCeP7gnN9jwN3FgUGIsg=="))),
            timeoutMs = safeTimeout(
                preferred = obj.optIntOrNull(_q9("XKZsX5oBUmGL")),
                legacy = playback.optIntOrNull(_q9("XqZlX5o6SUidkrmCeP7gnN9jwN3FgUGIsg==")),
                fallback = 7_000,
                min = 1_000,
                max = 30_000,
            ),
        )
    }

    val offline: _k2 get() {
        val obj = playback.optJSONObject(_q9("R6lnVpwaQw=="))
            ?: playback.optJSONObject(_q9("R6lnVpwaQ2WWpLWSdub5iw=="))
            ?: JSONObject()
        return _k2(
            enabled = obj.optBoolean(_q9("TaFgWJkRQg=="), false),
            mediaSource = obj.optString(_q9("RaplU5QnSVmKo7k=")).trim(),
            label = obj.optString(_q9("RK5jX5k="), _q9("e4BUaLYxBnqxhJm+N93Qv8dD4/0=")).trim().ifBlank { _q9("e4BUaLYxBnqxhJm+N93Qv8dD4/0=") },
        )
    }

    val diagnostics: _k3 get() = _k3(
        enabled = when {
            diagnosticsObject.has(_q9("TaFgWJkRQg==")) -> diagnosticsObject.optBoolean(_q9("TaFgWJkRQg=="), false)
            diagnosticsObject.has(_q9("WKNgQ5cVRUessr2Sctf4mOlmyNw=")) -> diagnosticsObject.optBoolean(_q9("WKNgQ5cVRUessr2Sctf4mOlmyNw="), false)
            else -> false
        },
    )

    val _j13: String get() = playback.optString(_q9("WL1oV5QGX3uKoayBcuDelvh+")).trim()
        .ifBlank { playback.optString(_q9("XqZlX5o6SUidiLOCYw=="), _q9("XqZlX5oaSUid7riU")).trim().ifBlank { _q9("XqZlX5oaSUid7riU") } }
    val _j14: Long get() = wrapperTimeoutMs.toLong()
    val _j15: Int get() = playback.optIntOrNull(_q9("Ra55apQTQ2SXsK8="))?.takeIf { it in 1..12 }
        ?: playback.optIntOrNull(_q9("Ra55f4UdVUOcpYyQcPfelvt5"))?.takeIf { it in 1..12 }
        ?: 3
    val _j16: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Regex(playback.optString(_q9("RaplU5QmQ12Npa+FRffxnPM="), _q9("APBoE91LHHDWre+EL+7K1+Z6mZGCyw+e/qMGjr/z")))
    }

    val metadata: _k9 get() = _k9(
        tmdb = parseTmdbMetadataProfile(),
        descriptionFilter = parseDescriptionFilterProfile(),
        features = parseMetadataFeatureProfile(),
        providers = parseMetadataProvidersProfile(),
    )

    val _j18: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        contentFilter.stringSet(_q9("SqNuWZ4RQm+ZtLmWeOD/nPg="))
    }
    val _j19: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        contentFilter.stringSet(_q9("SqNuWZ4RQniZp68="))
    }

    private fun endpoint(key: String, fallback: String): String =
        endpoints.optString(key).trim().takeIf { it.isNotBlank() } ?: fallback

    private fun parseTmdbMetadataProfile(): _k8 {
        val configured = tmdbObject != null
        if (!configured) {
            return _k8(
                enabled = true,
                language = _q9("Qassc7E="),
                descriptionPolicy = _k4.LEGACY_TMDB_PREFERRED,
                invalidWebDescriptionFallback = _k5.WEB,
                descriptionQuality = _k6(enabled = false),
                explicitlyConfigured = false,
            )
        }
        val tmdb = tmdbObject ?: JSONObject()
        val quality = tmdb.optJSONObject(_q9("TKpyWYcdVliRr7KgYvP6kP9z")) ?: JSONObject()
        return _k8(
            enabled = tmdb.optBoolean(_q9("TaFgWJkRQg=="), true),
            language = tmdb.optString(_q9("RK5vXYAVQUk="), _q9("Qassc7E=")).trim().ifBlank { _q9("Qassc7E=") },
            descriptionPolicy = when (tmdb.optString(_q9("TKpyWYcdVliRr7KheP7/mvI=")).trim().lowercase()) {
                _q9("RKpmW5YNeViVpL6uZ+Dzn+54393O") -> _k4.LEGACY_TMDB_PREFERRED
                _q9("X6pjZZoaSlU=") -> _k4.WEB_ONLY
                _q9("XKJlWKodQHOVqa+Cfvzx") -> _k4.TMDB_IF_MISSING
                else -> _k4.PREFER_INDONESIAN
            },
            invalidWebDescriptionFallback = when (tmdb.optString(_q9("QaF3W5kdQnudopiUZPHkkPt+xNfEslSpreI6kfA=")).trim().lowercase()) {
                _q9("X6pj") -> _k5.WEB
                else -> _k5.EMPTY
            },
            descriptionQuality = _k6(quality.optBoolean(_q9("TaFgWJkRQg=="), true)),
            explicitlyConfigured = true,
        )
    }

    private fun parseMetadataFeatureProfile(): _p118 {
        val features = metadataFeaturesObject ?: return _p118()
        fun enabled(key: String): Boolean = when (val raw = features.opt(key)) {
            null -> true
            is Boolean -> raw
            else -> true
        }
        return _p118(
            core = enabled(_q9("S6BzXw==")),
            visual = enabled(_q9("XqZyT5QY")),
            actors = enabled(_q9("Sax1VYcH")),
            tv = enabled("tv"),
            episodes = enabled(_q9("Tb9oSZoQQ18=")),
            recommendations = enabled(_q9("WqpiVZgZQ0KcoaiYePzl")),
        )
    }

    private fun parseMetadataProvidersProfile(): _p122 {
        val providers = metadataProvidersObject ?: return _p122()
        val enabledObject = providers.optJSONObject(_q9("TaFgWJkRQg==")) ?: JSONObject()

        fun safeEnabled(key: String, fallback: Boolean): Boolean = when (val raw = enabledObject.opt(key)) {
            null -> fallback
            is Boolean -> raw
            else -> fallback
        }

        fun parseProviderId(raw: String): _p119? = when (raw.trim().lowercase()) {
            _q9("XKJlWA==") -> _p119.TMDB
            _q9("XLlsW48R") -> _p119.TVMAZE
            _q9("XKdkToMQRA=="), _q9("XLllWA==") -> _p119.THETVDB
            _q9("R6JlWA==") -> _p119.OMDB
            else -> null
        }

        val defaultOrder = _p122().order
        val configuredOrder = providers.optJSONArray(_q9("R71lX4c="))?.let { array ->
            (0 until array.length())
                .mapNotNull { parseProviderId(array.optString(it)) }
                .distinct()
        }.orEmpty().ifEmpty { defaultOrder }

        val fallbackPolicy = when (providers.optString(_q9("Tq5tVpcVRUeor7CYdOs=")).trim().lowercase()) {
            _q9("RqBeV5QARUSnr66uevvliuJkyufMnVCppfM=") -> _p120.NO_MATCH_OR_MISSING_FIELDS
            else -> _p120.NO_MATCH_ONLY
        }

        return _p122(
            order = configuredOrder,
            enabled = _p121(
                tmdb = safeEnabled(_q9("XKJlWA=="), true),
                tvmaze = safeEnabled(_q9("XLlsW48R"), false),
                thetvdb = safeEnabled(_q9("XKdkToMQRA=="), false),
                omdb = safeEnabled(_q9("R6JlWA=="), false),
            ),
            fallbackPolicy = fallbackPolicy,
        )
    }

    private fun parseDescriptionFilterProfile(): _k7 {
        val configured = descriptionFilterObject != null
        if (!configured) {
            return _k7(
                enabled = true,
                genericRules = true,
                boundaryMarkers = emptyList(),
                stripPatterns = emptyList(),
                invalidPatterns = emptyList(),
                allowPatterns = emptyList(),
                minimumCleanLength = 24,
                explicitlyConfigured = false,
            )
        }
        val rules = descriptionFilterObject ?: JSONObject()
        return _k7(
            enabled = rules.optBoolean(_q9("TaFgWJkRQg=="), true),
            genericRules = rules.optBoolean(_q9("T6pvX4cdRX6NrLmC"), true),
            boundaryMarkers = rules.safeStringList(_q9("SqB0VJEVVFW1oa6acuDl")),
            stripPatterns = rules.safeStringList(_q9("W7tzU4UkR1iMpa6fZA==")),
            invalidPatterns = rules.safeStringList(_q9("QaF3W5kdQnyZtKiUZfzl")),
            allowPatterns = rules.safeStringList(_q9("SaNtVYIkR1iMpa6fZA==")),
            minimumCleanLength = rules.optIntOrNull(_q9("RaZvU5gBS2+Upb2fW/f4nv9i"))?.takeIf { it in 20..500 } ?: 40,
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
            .filter { it.length in 2..240 }
            .distinctBy { it.lowercase() }
            .take(64)
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key)) return null
        return when (val raw = opt(key)) {
            is Int -> raw
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    private fun safeTimeout(preferred: Int?, legacy: Int?, fallback: Int, min: Int, max: Int): Int {
        val value = preferred ?: legacy ?: fallback
        return if (value in min..max) value else fallback
    }
}
