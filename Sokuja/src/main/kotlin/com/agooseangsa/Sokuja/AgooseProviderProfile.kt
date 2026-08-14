package com.agooseangsa.Sokuja

import org.json.JSONObject

internal data class AgooseHomepageProfile(
    val source: String,
    val key: String,
    val title: String,
)

internal class AgooseProviderProfile private constructor(
    private val root: JSONObject,
) {
    val provider: String = root.getString(_q9("shzQGm00BTU="))
    val websiteKey: String = root.getString(_q9("tQvdH20kBQxJQw=="))
    val websiteJsonUrl: String = root.getJSONObject(_q9("sAvSA3A1")).getString(_q9("tQvdH20kBQ1fVamKroQ="))
    val defaultMainUrl: String = root.getJSONObject(_q9("pgvZDXE8FDQ=")).getString(_q9("rw/WAlEiDA=="))

    val homepage: List<AgooseHomepageProfile> = root.optJSONArray(_q9("qgHSCXQxByI="))?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                AgooseHomepageProfile(
                    source = item.optString(_q9("sQHKHmc1")),
                    key = item.optString(_q9("qQvG")),
                    title = item.optString(_q9("tgfLAGE=")),
                )
            }
        }
    }.orEmpty()

    private val endpoints = root.getJSONObject(_q9("pwDbHGs5DjNf"))
    private val selectors = root.getJSONObject(_q9("sQvTCWckDzVf"))
    private val playback = root.getJSONObject(_q9("sgLeFWYxAyw="))
    private val contentFilter = root.getJSONObject(_q9("oQHRGGE+FAFFVrO6rg=="))

    fun homepage(key: String): AgooseHomepageProfile =
        homepage.firstOrNull { it.key == key }
            ?: error("Missing ProviderProfile homepage key: $key")

    fun endpoint(key: String): String =
        endpoints.optString(key).takeIf { it.isNotBlank() }
            ?: error("Missing ProviderProfile endpoint: $key")

    fun selector(key: String): String =
        selectors.optString(key).takeIf { it.isNotBlank() }
            ?: error("Missing ProviderProfile selector/marker: $key")

    fun playbackInt(key: String): Int =
        playback.optInt(key, 0).takeIf { it > 0 }
            ?: error("Missing/invalid ProviderProfile playback integer: $key")

    fun playbackString(key: String): String =
        playback.optString(key).takeIf { it.isNotBlank() }
            ?: error("Missing ProviderProfile playback string: $key")

    fun blockedCategories(): Set<String> = contentFilter.stringSet(_q9("oALQD281BARNTqK4s5rdprE="))
    fun blockedTags(): Set<String> = contentFilter.stringSet(_q9("oALQD281BBNNXbQ="))

    private fun JSONObject.stringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    companion object {
        val current: AgooseProviderProfile by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val parsed = JSONObject(BuildConfig.AGOOSE_PROVIDER_PROFILE_JSON)
            require(parsed.optString(_q9("sQ3XCWkx")) == _q9("ownQA3c1TTdeVbG2uI3G7rIyG5V7wQiq9LU=")) {
                _q9("lwDMGXQgDzVYX6PwsYHHsKsuE9NTygLo8eEIwgRufPOmC808dj8GLkBf56y/gNGuow==")
            }
            AgooseProviderProfile(parsed)
        }
    }
}
