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
    val provider: String = root.getString(_q9("3Sx9r+c7SpA="))
    val websiteKey: String = root.getString(_q9("2jtwqucrSqlG7Q=="))
    val websiteJsonUrl: String = root.getJSONObject(_q9("3zt/tvo6")).getString(_q9("2jtwqucrSqhQ+23Lr3U="))
    val defaultMainUrl: String = root.getJSONObject(_q9("yTt0uPszW5E=")).getString(_q9("wD97t9stQw=="))

    val homepage: List<AgooseHomepageProfile> = root.optJSONArray(_q9("xTF/vP4+SIc="))?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                AgooseHomepageProfile(
                    source = item.optString(_q9("3jFnq+06")),
                    key = item.optString(_q9("xjtr")),
                    title = item.optString(_q9("2Tdmtes=")),
                )
            }
        }
    }.orEmpty()

    private val endpoints = root.optJSONObject(_q9("yDB2qeE2QZZQ")) ?: JSONObject()
    private val selectors = root.optJSONObject(_q9("3jt+vO0rQJBQ")) ?: JSONObject()
    private val playback = root.optJSONObject(_q9("3TJzoOw+TIk=")) ?: JSONObject()
    private val failoverObject = playback.optJSONObject(_q9("yz97teEpSpA=")) ?: JSONObject()
    private val offlineIndicatorObject = playback.optJSONObject(_q9("wjh0tecxSqtN8Gr9vG2QzA==")) ?: JSONObject()
    private val contentFilter = root.optJSONObject(_q9("zjF8resxW6RK+Hf7rw==")) ?: JSONObject()

    val failover: AgooseFailoverProfile = AgooseFailoverProfile(
        enabled = failoverObject.optBoolean(_q9("yDBzu+I6Sw=="), false),
        mode = failoverObject.optString(_q9("wDF2vA=="), _q9("yzdgqvoAXJdA92btrg=="))
            .takeIf { it == _q9("yzdgqvoAXJdA92btrg==") } ?: _q9("yzdgqvoAXJdA92btrg=="),
        serverResolveTimeoutMs = failoverObject.optInt(_q9("3jtgr+stfYdQ+2/ouE2W05bRwR6NiQ=="), 10_000)
            .coerceIn(1_000, 60_000),
    )

    val offlineIndicator: AgooseOfflineIndicatorProfile = AgooseOfflineIndicatorProfile(
        enabled = offlineIndicatorObject.optBoolean(_q9("yDBzu+I6Sw=="), false),
        mediaSource = offlineIndicatorObject.optString(_q9("wDt2sO8MQJdR92Y=")).trim(),
        label = offlineIndicatorObject.optString(_q9("wT9wvOI="), _q9("/hFHi80aD7Rq0EbR/Va5+L/3+i8="))
            .trim()
            .ifBlank { _q9("/hFHi80aD7Rq0EbR/Va5+L/3+i8=") },
    )

    fun endpoint(key: String, fallback: String = ""): String =
        endpoints.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun selector(key: String, fallback: String = ""): String =
        selectors.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun playbackInt(key: String, fallback: Int): Int =
        playback.optInt(key, fallback).takeIf { it > 0 } ?: fallback

    fun playbackString(key: String, fallback: String = ""): String =
        playback.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun blockedCategories(): Set<String> = contentFilter.stringSet(_q9("zzJ9uuU6S6FC4Gb5smuW24A="))
    fun blockedTags(): Set<String> = contentFilter.stringSet(_q9("zzJ9uuU6S7ZC83A="))

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
            require(parsed.optString(_q9("3j16vOM+")) == _q9("zDl9tv06ApJR+3X3uXyNk4PM2wyplhtHHaw=")) {
                _q9("+DBhrP4vQJBX8WexsHCMzZrQ00qBnREFGPgOfjKqC4bJO2CJ/DBJi0/xI+2+cZrTkg==")
            }
            AgooseProviderProfile(parsed)
        }
    }
}
