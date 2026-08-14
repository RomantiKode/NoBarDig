package com.agooseangsa.Layarkaca21

import java.net.URI

internal enum class AgoosePlaybackStage {
    PLAYER_DISCOVERED,
    EXTRACTOR_MATCHED,
    WRAPPER_RESOLVED,
    MEDIA_RESOLVED,
    PLAYBACK_RUNTIME_PASS,
}

internal enum class AgooseTraceResult { PASS, FAIL, NOT_TESTED, NOT_APPLICABLE }

internal data class AgoosePlaybackEvent(
    val provider: String,
    val stage: AgoosePlaybackStage,
    val result: AgooseTraceResult,
    val requestSummary: String,
    val refererSummary: String? = null,
    val resolver: String? = null,
    val next: String? = null,
    val reason: String? = null,
)

internal class AgoosePlaybackTrace(
    private val enabled: Boolean,
    private val sink: (String) -> Unit = ::println,
) {
    fun record(event: AgoosePlaybackEvent) {
        if (!enabled) return
        sink(
            buildString {
                append("[AgoosePlayback] provider=${event.provider}")
                append(" stage=${event.stage}")
                append(" result=${event.result}")
                append(" request=${event.requestSummary}")
                event.refererSummary?.let { append(" referer=$it") }
                event.resolver?.let { append(" resolver=$it") }
                event.next?.let { append(" next=$it") }
                event.reason?.let { append(" reason=$it") }
            },
        )
    }
}

internal fun agooseHostPathSummary(raw: String?): String = runCatching {
    val uri = URI(raw ?: return@runCatching _q9("XaFqVJoDSA=="))
    val host = uri.host ?: _q9("XaFqVJoDSA==")
    val path = uri.path?.takeIf { it.isNotBlank() } ?: "/"
    "$host$path"
}.getOrDefault(_q9("XaFqVJoDSA=="))
