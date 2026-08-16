package com.agooseangsa.DutaMovie21.shared

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

data class AgooseFailoverPolicy(
    val enabled: Boolean = false,
    val mode: String = "first_success",
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
        require(policy.mode == "first_success") { "Unsupported failover mode: ${policy.mode}" }

        val _f0 = if (policy.enabled) candidates else candidates.take(1)
        val _f1 = mutableListOf<AgooseFailoverAttempt>()
        val _f2 = policy.serverResolveTimeoutMs.coerceIn(1_000L, 60_000L)

        for (candidate in _f0) {
            val label = labelOf(candidate)
            val resolved = try {
                withTimeoutOrNull(_f2) { resolver(candidate) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                false
            }

            when (resolved) {
                true -> {
                    _f1 += AgooseFailoverAttempt(label, "SUCCESS")
                    return AgooseFailoverResult(true, _f1)
                }
                null -> _f1 += AgooseFailoverAttempt(label, "TIMEOUT")
                false -> _f1 += AgooseFailoverAttempt(label, "FAILED")
            }
        }

        return AgooseFailoverResult(false, _f1)
    }
}
