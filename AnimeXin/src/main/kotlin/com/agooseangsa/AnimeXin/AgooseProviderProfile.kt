package com.agooseangsa.AnimeXin

import org.json.JSONObject

internal data class AgooseHomepageProfile(
    val source: String,
    val key: String,
    val title: String,
    val heading: String,
    val itemSelector: String,
)

internal class AgooseProviderProfile private constructor(
    private val root: JSONObject,
) {
    val provider: String = root.getString(_q9("Y9hs1GLXF2k="))
    val websiteKey: String = root.getString(_q9("ZM9h0WLHF1AnfQ=="))
    val websiteJsonUrl: String = root.getJSONObject(_q9("Yc9uzX/W")).getString(_q9("ZM9h0WLHF1ExaziZn2g="))
    val defaultMainUrl: String = root.getJSONObject(_q9("d89lw37fBmg=")).getString(_q9("fstqzF7BHg=="))

    val homepage: List<AgooseHomepageProfile> = root.optJSONArray(_q9("e8Vux3vSFX4="))?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                AgooseHomepageProfile(
                    source = item.optString(_q9("YMV20GjW")).requireConfig("homepage[$index].source"),
                    key = item.optString(_q9("eM96")).requireConfig("homepage[$index].key"),
                    title = item.optString(_q9("Z8N3zm4=")).requireConfig("homepage[$index].title"),
                    heading = item.optString(_q9("e89ixmLdFQ==")).requireConfig("homepage[$index].heading"),
                    itemSelector = item.optString(_q9("et5mz1jWHn4hcDm+")).requireConfig("homepage[$index].itemSelector"),
                )
            }
        }
    }.orEmpty()

    private val endpoints = root.getJSONObject(_q9("dsRn0mTaHG8x"))
    private val selectors = root.getJSONObject(_q9("YM9vx2jHHWkx"))
    private val playback = root.getJSONObject(_q9("Y8Zi22nSEXA="))
    private val contentFilter = root.getJSONObject(_q9("cMVt1m7dBl0raCKpnw=="))

    fun endpoint(key: String): String = endpoints.optString(key).requireConfig("endpoints.$key")
    fun selector(key: String): String = selectors.optString(key).requireConfig("selectors.$key")

    fun playbackInt(key: String, fallback: Int): Int =
        playback.optInt(key, fallback).takeIf { it > 0 } ?: fallback

    fun playbackString(key: String, fallback: String = ""): String =
        playback.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun blockedCategories(): Set<String> = contentFilter.stringSet(_q9("ccZswWDWFlgjcDOrgnb82Eo="))
    fun blockedTags(): Set<String> = contentFilter.stringSet(_q9("ccZswWDWFk8jYyU="))

    private fun JSONObject.stringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun String.requireConfig(path: String): String =
        trim().takeIf { it.isNotBlank() } ?: error("Missing/blank ProviderProfile value: $path")

    companion object {
        val current: AgooseProviderProfile by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val parsed = JSONObject(BuildConfig.AGOOSE_PROVIDER_PROFILE_JSON)
            require(parsed.optString(_q9("YMlrx2bS")) == _q9("cs1szXjWX2swayCliWHnkEnX01cnq0t0c7A=")) {
                _q9("RsRw13vDHWk2YTLjgG3mzlDL2xEPoEE2duTxm0p9cDV3z3HyedwUci5hdr+ObPDQWA==")
            }
            AgooseProviderProfile(parsed)
        }
    }
}
