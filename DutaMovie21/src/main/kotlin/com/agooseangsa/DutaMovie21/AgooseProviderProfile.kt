package com.agooseangsa.DutaMovie21

import org.json.JSONObject

internal data class AgooseHomepageProfile(
    val source: String,
    val key: String,
    val title: String,
)

internal data class AgooseFailoverProfile(
    val enabled: Boolean,
    val mode: String,
    val serverResolveTimeoutMs: Int,
)

internal data class AgooseOfflineIndicatorProfile(
    val enabled: Boolean,
    val mediaSource: String,
    val label: String,
)

internal class AgooseProviderProfile private constructor(
    private val root: JSONObject,
) {
    val provider: String = root.getString(_q9("VRRbXtuy7uM="))
    val websiteKey: String = root.getString(_q9("UgNWW9ui7tqtOw=="))
    val websiteJsonUrl: String = root.getJSONObject(_q9("VwNZR8az")).getString(_q9("UgNWW9ui7tu7LTrdsuA="))
    val defaultMainUrl: String = root.getJSONObject(_q9("QQNSSce6/+I=")).getString(_q9("SAddRuek5w=="))

    val homepage: List<AgooseHomepageProfile> = root.optJSONArray(_q9("TQlZTcK37PQ="))?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                AgooseHomepageProfile(
                    source = item.optString(_q9("VglBWtGz")),
                    key = item.optString(_q9("TgNN")),
                    title = item.optString(_q9("UQ9ARNc=")),
                )
            }
        }
    }.orEmpty()

    private val endpoints = root.optJSONObject(_q9("QAhQWN2/5eW7")) ?: JSONObject()
    private val selectors = root.optJSONObject(_q9("VgNYTdGi5OO7")) ?: JSONObject()
    private val playback = root.optJSONObject(_q9("VQpVUdC36Po=")) ?: JSONObject()
    private val failoverObject = playback.optJSONObject(_q9("QwddRN2g7uM=")) ?: JSONObject()
    private val offlineIndicatorObject = playback.optJSONObject(_q9("SgBSRNu47timJj3rofgZuw==")) ?: JSONObject()
    private val contentFilter = root.optJSONObject(_q9("RglaXNe4/9ehLiDtsg==")) ?: JSONObject()

    val failover: AgooseFailoverProfile = AgooseFailoverProfile(
        enabled = failoverObject.optBoolean(_q9("QAhVSt6z7w=="), false),
        mode = failoverObject.optString(_q9("SAlQTQ=="), "first_success")
            .takeIf { it == "first_success" } ?: "first_success",
        serverResolveTimeoutMs = failoverObject.optInt(_q9("VgNGXtek2fS7LTj+pdgfpEZcVtmvuA=="), 10_000)
            .coerceIn(1_000, 60_000),
    )

    val offlineIndicator: AgooseOfflineIndicatorProfile = AgooseOfflineIndicatorProfile(
        enabled = offlineIndicatorObject.optBoolean(_q9("QAhVSt6z7w=="), false),
        mediaSource = offlineIndicatorObject.optString(_q9("SANQQdOF5OS6ITE=")).trim(),
        label = offlineIndicatorObject.optString(_q9("SQdWTd4="), _q9("dilhevGTq8eBBhHH4MMwj296beg="))
            .trim()
            .ifBlank { _q9("dilhevGTq8eBBhHH4MMwj296beg=") },
    )

    fun endpoint(key: String, fallback: String = ""): String =
        endpoints.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun selector(key: String, fallback: String = ""): String =
        selectors.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun playbackInt(key: String, fallback: Int): Int =
        playback.optInt(key, fallback).takeIf { it > 0 } ?: fallback

    fun playbackString(key: String, fallback: String = ""): String =
        playback.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun blockedCategories(): Set<String> = contentFilter.stringSet(_q9("RwpbS9mz79KpNjHvr/4frFA="))
    fun blockedTags(): Set<String> = contentFilter.stringSet(_q9("RwpbS9mz78WpJSc="))

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
            require(parsed.optString(_q9("VgVcTd+3")) == _q9("RAFbR8GzpuG6LSLhpOkE5FNBTMuLp+GBUNs=")) {
                _q9("cAhHXcKm5OO8JzCnreUFukpdRI2jrOvDVY+aKPFQGXZBA0Z4wLnt+KQndPuj5BOkQg==")
            }
            AgooseProviderProfile(parsed)
        }
    }
}
