package com.agooseangsa.MidasXXI

import org.json.JSONObject

internal data class AgooseHomepageProfile(
    val source: String,
    val key: String,
    val title: String,
)

internal class AgooseProviderProfile private constructor(
    private val root: JSONObject,
) {
    val provider: String = root.getString(_q9("eP0/ulMbaDI="))
    val websiteKey: String = root.getString(_q9("f+oyv1MLaAs9Tw=="))
    val websiteJsonUrl: String = root.getJSONObject(_q9("euo9o04a")).getString(_q9("f+oyv1MLaAorWTw4IW8="))
    val defaultMainUrl: String = root.getJSONObject(_q9("bOo2rU8TeTM=")).getString(_q9("Ze45om8NYQ=="))

    val homepage: List<AgooseHomepageProfile> = root.optJSONArray(_q9("YOA9qUoeaiU="))?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                AgooseHomepageProfile(
                    source = item.optString(_q9("e+Alvlka")),
                    key = item.optString(_q9("Y+op")),
                    title = item.optString(_q9("fOYkoF8=")),
                )
            }
        }
    }.orEmpty()

    private val endpoints = root.getJSONObject(_q9("beE0vFUWYzQr"))
    private val selectors = root.getJSONObject(_q9("e+o8qVkLYjIr"))
    private val contentFilter = root.getJSONObject(_q9("a+A+uF8ReQYxWiYIIQ=="))

    fun endpoint(key: String): String = endpoints.getString(key).also {
        require(it.isNotBlank()) { "ProviderProfile endpoint is blank: $key" }
    }

    fun selector(key: String): String = selectors.getString(key).also {
        require(it.isNotBlank()) { "ProviderProfile selector/marker is blank: $key" }
    }

    fun blockedCategories(): Set<String> = contentFilter.stringSet(_q9("auM/r1EaaQM5QjcKPHFnOS0="))
    fun blockedTags(): Set<String> = contentFilter.stringSet(_q9("auM/r1EaaRQ5USE="))

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
            require(parsed.optString(_q9("e+w4qVce")) == _q9("aeg/o0kaIDAqWSQEN2Z8cS6YsB6HnSbmDxE=")) {
                _q9("XeEjuUoPYjIsUzZCPmp9LzeEuFivliykCkXEET63mSRs6iKcSBBrKTRTch4wa2sxPw==")
            }
            AgooseProviderProfile(parsed)
        }
    }
}
