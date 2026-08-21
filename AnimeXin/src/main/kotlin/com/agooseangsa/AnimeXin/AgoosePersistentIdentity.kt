package com.agooseangsa.AnimeXin

import java.net.URI
import java.util.Locale

internal object AgoosePersistentIdentity {

    fun resolveUniqueUrl(
        providerNamespace: String,
        currentProviderUrl: String,
        existingStableUniqueUrl: String? = null,
        providerStableId: String? = null,
        hostIndependentPathProven: Boolean = false,
        queryIsIdentity: Boolean = false,
    ): String {
        existingStableUniqueUrl.cleanPart()?.let { return it }

        val namespace = providerNamespace.cleanPart()?.lowercase(Locale.ROOT) ?: return currentProviderUrl
        providerStableId.cleanPart()?.let { stableId ->
            return "$namespace|site-id|$stableId"
        }

        if (hostIndependentPathProven) {
            hostIndependentPathKey(namespace, currentProviderUrl, queryIsIdentity)?.let { return it }
        }

        return currentProviderUrl
    }

    private fun hostIndependentPathKey(
        namespace: String,
        rawUrl: String,
        queryIsIdentity: Boolean,
    ): String? = runCatching {
        val uri = URI(rawUrl)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if ((scheme != _q9("ysIIXg==") && scheme != _q9("ysIIXlU=")) || uri.host.isNullOrBlank()) return null

        val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
        val query = uri.rawQuery?.takeIf { it.isNotBlank() }

        if (path == "/" && (!queryIsIdentity || query == null)) return null

        buildString {
            append(namespace)
            append(_q9("3sUVWkMSC3RX3oY="))
            append(path)
            if (queryIsIdentity && query != null) {
                append('?')
                append(query)
            }
        }
    }.getOrNull()

    private fun String?.cleanPart(): String? = this?.trim()?.takeIf { it.isNotBlank() }
}
