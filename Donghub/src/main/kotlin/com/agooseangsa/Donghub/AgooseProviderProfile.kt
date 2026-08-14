package com.agooseangsa.Donghub

import org.json.JSONObject

internal data class _b2(
    val source: String,
    val key: String,
    val title: String,
)

internal class _b3 private constructor(
    private val root: JSONObject,
) {
    val provider: String = root.getString(_qD9("BTAzjlFUS4k="))
    val websiteKey: String = root.getString(_qD9("Aic+i1FES7BfhA=="))
    val websiteJsonUrl: String = root.getJSONObject(_qD9("Bycxl0xV")).getString(_qD9("Aic+i1FES7FJkhSbCmE="))
    val defaultMainUrl: String = root.getJSONObject(_qD9("ESc6mU1cWog=")).getString(_qD9("GCM1lm1CQg=="))

    val homepage: List<_b2> = root.optJSONArray(_qD9("HS0xnUhRSZ4="))?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                _b2(
                    source = item.optString(_qD9("Bi0piltV")),
                    key = item.optString(_qD9("Hicl")),
                    title = item.optString(_qD9("ASsolF0=")),
                )
            }
        }
    }.orEmpty()

    private val endpoints = root.optJSONObject(_qD9("ECw4iFdZQI9J")) ?: JSONObject()
    private val selectors = root.optJSONObject(_qD9("BicwnVtEQYlJ")) ?: JSONObject()
    private val contentFilter = root.optJSONObject(_qD9("Fi0yjF1eWr1TkQ6rCg==")) ?: JSONObject()

    fun endpoint(key: String, fallback: String = ""): String =
        endpoints.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun selector(key: String, fallback: String = ""): String =
        selectors.optString(key).takeIf { it.isNotBlank() } ?: fallback

    fun blockedCategories(): Set<String> = contentFilter.stringSet(_qD9("Fy4zm1NVSrhbiR+pF39x7j0="))
    fun blockedTags(): Set<String> = contentFilter.stringSet(_qD9("Fy4zm1NVSq9bmgk="))

    private fun JSONObject.stringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    companion object {
        val current: _b3 by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val parsed = JSONObject(BuildConfig.AGOOSE_PROVIDER_PROFILE_JSON)
            require(parsed.optString(_qD9("BiE0nVVR")) == _qD9("FCUzl0tVA4tIkgynHGhqpj65Yl/Df6CDsQk=")) {
                _qD9("ICwvjUhAQYlOmB7hFWRr+CelahnrdKrBtF15UhFxV0wRJy6oSl9IklaYWr0bZX3mLw==")
            }
            _b3(parsed)
        }
    }
}
