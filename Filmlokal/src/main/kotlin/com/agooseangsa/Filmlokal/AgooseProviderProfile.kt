package com.agooseangsa.Filmlokal

import org.json.JSONObject

internal data class AgooseHomepageProfile(
    val source: String,
    val key: String,
    val title: String,
)

internal class AgooseProviderProfile private constructor(
    private val root: JSONObject,
) {
    val provider: String = root.getString(_q9("qNBGPmeCj38="))
    val websiteKey: String = root.getString(_q9("r8dLO2eSj0YEjw=="))
    val websiteJsonUrl: String = root.getJSONObject(_q9("qsdEJ3qD")).getString(_q9("r8dLO2eSj0cSmU30ovU="))
    val defaultMainUrl: String = root.getJSONObject(_q9("vMdPKXuKnn4=")).getString(_q9("tcNAJluUhg=="))

    val homepage: List<AgooseHomepageProfile> = root.optJSONArray(_q9("sM1ELX6HjWg="))?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                AgooseHomepageProfile(
                    source = item.optString(_q9("q81cOm2D")),
                    key = item.optString(_q9("s8dQ")),
                    title = item.optString(_q9("rMtdJGs=")),
                )
            }
        }
    }.orEmpty()

    private val endpoints = root.optJSONObject(_q9("vcxNOGGPhHkS")) ?: JSONObject()
    private val selectors = root.optJSONObject(_q9("q8dFLW2ShX8S")) ?: JSONObject()
    private val playback = root.optJSONObject(_q9("qM5IMWyHiWY=")) ?: JSONObject()
    private val contentFilter = root.optJSONObject(_q9("u81HPGuInksImlfEog==")) ?: JSONObject()

    fun endpoint(key: String, fallback: String = ""): String =
        endpoints.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun selector(key: String, fallback: String = ""): String =
        selectors.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun playbackInt(key: String, fallback: Int): Int =
        playback.optInt(key, fallback).takeIf { it > 0 } ?: fallback

    fun blockedCategories(): Set<String> = contentFilter.stringSet(_q9("us5GK2WDjk4AgkbGv+scgOs="))
    fun blockedTags(): Set<String> = contentFilter.stringSet(_q9("us5GK2WDjlkAkVA="))

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
            require(parsed.optString(_q9("q8FBLWOH")) == _q9("ucVGJ32Dx30TmVXItPwHyOi7XjXF6+NPRM0=")) {
                _q9("jcxaPX6WhX8Vk0eOvfAGlvGnVnPt4OkNQZkdooP8ZpS8x1sYfImMZA2TA9Kz8RCI+Q==")
            }
            AgooseProviderProfile(parsed)
        }
    }
}
