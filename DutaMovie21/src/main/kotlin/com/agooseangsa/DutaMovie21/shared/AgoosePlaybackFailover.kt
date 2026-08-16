package com.agooseangsa.DutaMovie21.shared

import com.agooseangsa.DutaMovie21._q9
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

data class AgooseFailoverPolicy(
    val enabled: Boolean = false,
    val mode: String = _q9("yzdgqvoAXJdA92btrg=="),
    val serverResolveTimeoutMs: Long = 10_000L,
)

data class AgooseFailoverAttempt(
    val label: String,
    val result: String,
)

data class AgooseFailoverResult(
    val success: Boolean,
    val attempted: List<AgooseFailoverAttempt>,
)

object AgoosePlaybackFailover {
    suspend fun <T> resolve(
        candidates: List<T>,
        labelOf: (T) -> String,
        policy: AgooseFailoverPolicy,
        resolver: suspend (T) -> Boolean,
    ): AgooseFailoverResult {
        if (candidates.isEmpty()) return AgooseFailoverResult(false, emptyList())
        require(policy.mode == _q9("yzdgqvoAXJdA92btrg==")) { "Unsupported failover mode: ${policy.mode}" }

        val selected = if (policy.enabled) candidates else candidates.take(1)
        val attempts = mutableListOf<AgooseFailoverAttempt>()
        val timeoutMs = policy.serverResolveTimeoutMs.coerceIn(1_000L, 60_000L)

        for (candidate in selected) {
            val label = labelOf(candidate)
            val resolved = try {
                withTimeoutOrNull(timeoutMs) { resolver(candidate) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                false
            }

            when (resolved) {
                true -> {
                    attempts += AgooseFailoverAttempt(label, _q9("/gtRmssMfA=="))
                    return AgooseFailoverResult(true, attempts)
                }
                null -> attempts += AgooseFailoverAttempt(label, _q9("+RdfnMEKew=="))
                false -> attempts += AgooseFailoverAttempt(label, _q9("6x9blcsb"))
            }
        }

        return AgooseFailoverResult(false, attempts)
    }
}
