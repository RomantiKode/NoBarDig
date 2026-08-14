package com.agooseangsa.Layarkaca21

import org.json.JSONObject

internal data class _j1(
    val source: String,
    val key: String,
    val title: String,
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

    private val defaults get() = root.getJSONObject(_q9("TKpnW4AYUl8="))
    private val remote get() = root.getJSONObject(_q9("WqpsVYER"))
    private val endpoints get() = root.getJSONObject(_q9("TaFlSpodSFiL"))
    private val selectors get() = root.getJSONObject(_q9("W6ptX5YASV6L"))
    private val playback get() = root.getJSONObject(_q9("WKNgQ5cVRUc="))
    private val labels get() = root.optJSONObject(_q9("RK5jX5kH")) ?: JSONObject()
    private val diagnostics get() = root.optJSONObject(_q9("TKZgXZsbVViRo68=")) ?: JSONObject()
    private val contentFilter get() = root.optJSONObject(_q9("S6BvTpAaUmqRrKiUZQ==")) ?: JSONObject()

    val _j7: String get() = root.getString(_q9("X6pjSZwAQ2eduQ=="))
    val _j6: String get() = remote.getString(_q9("X6pjSZwAQ2aLr7KkZf4="))
    val _j4: String get() = defaults.getString(_q9("W6pzU5AHc16U"))
    val _j5: String get() = defaults.getString(_q9("RaB3U5AhVEA="))

    val homepage: List<_j1> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val array = root.getJSONArray(_q9("QKBsX4UVQUk="))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            _j1(
                source = item.getString(_q9("W6B0SJYR")),
                key = item.getString(_q9("Q6p4")),
                title = item.getString(_q9("XKZ1VpA=")),
            )
        }
    }

    val _j8: String get() = endpoints.getString(_q9("W6pgSJYcdk2MqA=="))
    val _j9: String get() = endpoints.getString(_q9("W6pgSJYcdk2KobE="))
    val _j10: String get() = endpoints.getString(_q9("W6pzU5AHdEmcqa6UdObGmP9i"))
    val _j11: String get() = endpoints.getString(_q9("W6pzU5AHdEmcqa6UdObHjO541OjLhlSo"))

    fun selector(key: String): String = selectors.getString(key)
    fun _j3(key: String): String =
        selector(_q9("QKBsX4UVQUmvqbiWcubCnOZ6wdnekQ==")).replace(_q9("U6RkQ4g="), key)

    val _j12: String get() = labels.optString(_q9("Sax1VYckVEmeqaQ="), _q9("aqZvTpQaQQy+qbCc"))
    val _j13: String get() = playback.getString(_q9("XqZlX5o6SUidiLOCYw=="))
    val _j14: Long get() = playback.getLong(_q9("XqZlX5o6SUidkrmCeP7gnN9jwN3FgUGIsg=="))
    val _j15: Int get() = playback.getInt(_q9("Ra55f4UdVUOcpYyQcPfelvt5"))
    val _j16: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Regex(playback.getString(_q9("RaplU5QmQ12Npa+FRffxnPM=")))
    }
    val _j17: Boolean get() = diagnostics.optBoolean(_q9("WKNgQ5cVRUessr2Sctf4mOlmyNw="), false)

    val _j18: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        contentFilter.stringSet(_q9("SqNuWZ4RQm+ZtLmWeOD/nPg="))
    }
    val _j19: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        contentFilter.stringSet(_q9("SqNuWZ4RQniZp68="))
    }

    private fun JSONObject.stringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }
}
