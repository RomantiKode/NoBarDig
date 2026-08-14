package com.agooseangsa.DrakorKita

import org.json.JSONObject

internal data class AgooseHomepageProfile(
    val source: String,
    val key: String,
    val title: String,
)

internal class AgooseProviderProfile private constructor(
    private val root: JSONObject,
) {
    val provider: String = root.getString(_q9("Kf3aW5VRPUM="))
    val websiteKey: String = root.getString(_q9("LurXXpVBPXqfNw=="))
    val websiteJsonUrl: String = root.getJSONObject(_q9("K+rYQohQ")).getString(_q9("LurXXpVBPXuJIe67QMA="))
    val defaultMainUrl: String = root.getJSONObject(_q9("PerTTIlZLEI=")).getString(_q9("NO7cQ6lHNA=="))

    val homepage: List<AgooseHomepageProfile> = root.optJSONArray(_q9("MeDYSIxUP1Q="))?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                AgooseHomepageProfile(
                    source = item.optString(_q9("KuDAX59Q")),
                    key = item.optString(_q9("MurM")),
                    title = item.optString(_q9("LebBQZk=")),
                )
            }
        }
    }.orEmpty()

    private val endpoints = root.optJSONObject(_q9("POHRXZNcNkWJ")) ?: JSONObject()
    private val selectors = root.optJSONObject(_q9("KurZSJ9BN0OJ")) ?: JSONObject()
    private val playback = root.optJSONObject(_q9("KePUVJ5UO1o=")) ?: JSONObject()
    private val classification = root.optJSONObject(_q9("OuPUXo9cPliZL/SHXcI=")) ?: JSONObject()
    private val contentFilter = root.optJSONObject(_q9("OuDbWZlbLHeTIvSLQA==")) ?: JSONObject()

    fun endpoint(key: String, fallback: String = ""): String =
        endpoints.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun selector(key: String, fallback: String = ""): String =
        selectors.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun playbackInt(key: String, fallback: Int): Int =
        playback.optInt(key, fallback).takeIf { it > 0 } ?: fallback

    fun classificationStrings(key: String): List<String> =
        classification.stringList(key)

    fun blockedCategories(): Set<String> = contentFilter.stringSet(_q9("O+PaTpdQPHKbOuWJXd7xlUg="))
    fun blockedTags(): Set<String> = contentFilter.stringSet(_q9("O+PaTpdQPGWbKfM="))

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotBlank() }
    }

    private fun JSONObject.stringSet(key: String): Set<String> =
        stringList(key).toSet()

    companion object {
        val current: AgooseProviderProfile by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val parsed = JSONObject(BuildConfig.AGOOSE_PROVIDER_PROFILE_JSON)
            require(parsed.optString(_q9("KuzdSJFU")) == _q9("OOjaQo9QdUGIIfaHVsnq3UvV2VG7ubM/+wY=")) {
                _q9("DOHGWIxFN0OOK+TBX8Xrg1LJ0ReTsrl9/lKdoj7zuLM96sd9jlo+WJYroJ1RxP2dWg==")
            }
            AgooseProviderProfile(parsed)
        }
    }
}
