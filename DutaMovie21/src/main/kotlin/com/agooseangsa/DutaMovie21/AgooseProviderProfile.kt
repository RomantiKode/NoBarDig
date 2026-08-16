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
    val provider: String = root.getString(_q9("J4qtPyLm3no="))
    val websiteKey: String = root.getString(_q9("IJ2gOiL23kOzTQ=="))
    val websiteJsonUrl: String = root.getJSONObject(_q9("JZ2vJj/n")).getString(_q9("IJ2gOiL23kKlWz3tGx4="))
    val defaultMainUrl: String = root.getJSONObject(_q9("M52kKD7uz3s=")).getString(_q9("OpmrJx7w1w=="))

    val homepage: List<AgooseHomepageProfile> = root.optJSONArray(_q9("P5evLDvj3G0="))?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                AgooseHomepageProfile(
                    source = item.optString(_q9("JJe3Oyjn")),
                    key = item.optString(_q9("PJ27")),
                    title = item.optString(_q9("I5G2JS4=")),
                )
            }
        }
    }.orEmpty()

    private val endpoints = root.optJSONObject(_q9("MpamOSTr1Xyl")) ?: JSONObject()
    private val selectors = root.optJSONObject(_q9("JJ2uLCj21Hql")) ?: JSONObject()
    private val playback = root.optJSONObject(_q9("J5SjMCnj2GM=")) ?: JSONObject()
    private val failoverObject = playback.optJSONObject(_q9("MZmrJST03no=")) ?: JSONObject()
    private val offlineIndicatorObject = playback.optJSONObject(_q9("OJ6kJSLs3kG4UDrbCAbIlA==")) ?: JSONObject()
    private val contentFilter = root.optJSONObject(_q9("NJesPS7sz06/WCfdGw==")) ?: JSONObject()

    val failover: AgooseFailoverProfile = AgooseFailoverProfile(
        enabled = failoverObject.optBoolean(_q9("MpajKyfn3w=="), false),
        mode = failoverObject.optString(_q9("OpemLA=="), "first_success")
            .takeIf { it == "first_success" } ?: "first_success",
        serverResolveTimeoutMs = failoverObject.optInt(_q9("JJ2wPy7w6W2lWz/ODCbOi5InAtMYeQ=="), 10_000)
            .coerceIn(1_000, 60_000),
    )

    val offlineIndicator: AgooseOfflineIndicatorProfile = AgooseOfflineIndicatorProfile(
        enabled = offlineIndicatorObject.optBoolean(_q9("MpajKyfn3w=="), false),
        mediaSource = offlineIndicatorObject.optString(_q9("Op2mICrR1H2kVzY=")).trim(),
        label = offlineIndicatorObject.optString(_q9("O5mgLCc="), _q9("BLeXGwjHm16fcBb3ST3hoLsBOeI="))
            .trim()
            .ifBlank { _q9("BLeXGwjHm16fcBb3ST3hoLsBOeI=") },
    )

    fun endpoint(key: String, fallback: String = ""): String =
        endpoints.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun selector(key: String, fallback: String = ""): String =
        selectors.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun playbackInt(key: String, fallback: Int): Int =
        playback.optInt(key, fallback).takeIf { it > 0 } ?: fallback

    fun playbackString(key: String, fallback: String = ""): String =
        playback.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun blockedCategories(): Set<String> = contentFilter.stringSet(_q9("NZStKiDn30u3QDbfBgDOg4Q="))
    fun blockedTags(): Set<String> = contentFilter.stringSet(_q9("NZStKiDn31y3UyA="))

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
            require(parsed.optString(_q9("JJuqLCbj")) == _q9("Np+tJjjnlnikWyXRDRfVy4c6GME8ZuWx4s4=")) {
                _q9("ApaxPDvy1HqiUTeXBBvUlZ4mEIcUbe/z55oVvCSXIvEznbAZOe3dYbpRc8sKGsKLlg==")
            }
            AgooseProviderProfile(parsed)
        }
    }
}
