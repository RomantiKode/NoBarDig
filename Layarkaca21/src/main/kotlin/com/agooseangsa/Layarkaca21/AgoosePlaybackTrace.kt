package com.agooseangsa.Layarkaca21

import android.util.Log
import java.net.URI

internal enum class _l2 {
    DETAIL_READABLE,
    SOURCE_MODE_EFFECTIVE,
    PLAYER_DISCOVERED,
    EXTRACTOR_MATCHED,
    WRAPPER_RESOLVED,
    MEDIA_RESOLVED,
    PLAYBACK_RUNTIME_PASS,
}

internal enum class _l3 { PASS, FAIL, NOT_TESTED, NOT_APPLICABLE }

internal data class _l4(
    val provider: String,
    val stage: _l2,
    val result: _l3,
    val requestSummary: String,
    val refererSummary: String? = null,
    val resolver: String? = null,
    val next: String? = null,
    val reason: String? = null,
)

internal class _l5(
    moduleName: String,
    private val enabled: Boolean,
) {
    private val tag = "${moduleName}Playback"

    fun record(event: _l4) {
        if (!enabled) return
        Log.d(
            tag,
            buildString {
                append("stage=${event.stage} result=${event.result}")
                append(" provider=${event.provider}")
                append(" requestHost=${event.requestSummary}")
                event.refererSummary?.let { append(" refererHost=$it") }
                event.resolver?.take(120)?.let { append(" resolver=$it") }
                event.next?.take(120)?.let { append(" next=$it") }
                event.reason?.take(160)?.let { append(" reason=$it") }
            },
        )
    }
}

internal fun _l6(raw: String?): String = runCatching {
    URI(raw ?: return@runCatching _q9("XaFqVJoDSA==")).host ?: _q9("XaFqVJoDSA==")
}.getOrDefault(_q9("XaFqVJoDSA=="))
