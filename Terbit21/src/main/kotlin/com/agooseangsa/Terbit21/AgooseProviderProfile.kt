package com.agooseangsa.Terbit21

import org.json.JSONObject
import java.net.URI

internal data class AgooseHomepageProfile(
    val source: String,
    val key: String,
    val title: String,
)

internal class AgooseProviderProfile private constructor(
    private val root: JSONObject,
) {
    val provider: String = root.getString(_q9("F0jCADMNOcQ="))
    val websiteKey: String = root.getString(_q9("EF/PBTMdOf2lRQ=="))
    val websiteJsonUrl: String = root.getJSONObject(_q9("FV/AGS4M")).getString(_q9("EF/PBTMdOfyzU/eC7cM="))

    private val defaults = root.getJSONObject(_q9("A1/LFy8FKMU="))
    val defaultMainUrl: String = defaults.getString(_q9("ClvEGA8bMA=="))
    val defaultOrigins: List<String> = defaults.stringList(_q9("CEjEETMHLw==")).ifEmpty { listOf(defaultMainUrl) }
    val historicalOrigin: String = defaults.optString(_q9("D1PeAjUbNdWhUNal9sjFlQ==")).trim()
    val historicalHost: String? = runCatching { URI(historicalOrigin).host }.getOrNull()

    val homepage: List<AgooseHomepageProfile> = root.optJSONArray(_q9("D1XAEyoIO9M="))?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                AgooseHomepageProfile(
                    source = item.optString(_q9("FFXYBDkM")),
                    key = item.optString(_q9("DF/U")),
                    title = item.optString(_q9("E1PZGj8=")),
                )
            }
        }.filter { it.source.isNotBlank() && it.title.isNotBlank() }
    }.orEmpty()

    private val endpoints = root.getJSONObject(_q9("AlTJBjUAMsKz"))
    private val selectors = root.getJSONObject(_q9("FF/BEzkdM8Sz"))
    private val playback = root.getJSONObject(_q9("F1bMDzgIP90="))
    private val classification = root.optJSONObject(_q9("BFbMBSkAOt+jXe2+8ME=")) ?: JSONObject()
    private val contentFilter = root.getJSONObject(_q9("BFXDAj8HKPCpUO2y7Q=="))

    fun endpoint(key: String): String = endpoints.getString(key)
    fun selector(key: String): String = selectors.getString(key)
    fun playbackInt(key: String): Int = playback.getInt(key).also { require(it > 0) }
    fun classificationStrings(key: String): List<String> = classification.stringList(key)
    fun blockedCategories(): Set<String> = contentFilter.stringList(_q9("BVbCFTEMOPWhSPyw8N3Fnic=")).toSet()
    fun blockedTags(): Set<String> = contentFilter.stringList(_q9("BVbCFTEMOOKhW+o=")).toSet()

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotBlank() }
    }

    companion object {
        val current: AgooseProviderProfile by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val parsed = JSONObject(BuildConfig.AGOOSE_PROVIDER_PROFILE_JSON)
            require(parsed.optString(_q9("FFnFEzcI")) == _q9("Bl3CGSkMccayU++++8re1iTqXY7pBIKGUTg=")) {
                _q9("MlTeAyoZM8S0Wf348sbfiD32VcjBD4jEVGzHdDt9EK4DX98mKAY636xZuaT8x8mWNQ==")
            }
            AgooseProviderProfile(parsed)
        }
    }
}
