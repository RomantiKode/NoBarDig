package com.agooseangsa.Layarkaca21

import android.util.Log

internal class _l7(
    moduleName: String,
    private val enabled: Boolean,
) {
    private val tag = "${moduleName}Metadata"

    fun description(decision: _m3) {
        if (!enabled) return
        Log.d(
            tag,
            "DESCRIPTION_SELECTED source=${decision.source} reason=${decision.reason} " +
                "quality=${decision.assessment.quality} language=${decision.assessment.language} " +
                "sanitized=${decision.assessment.sanitized}",
        )
    }

    fun context(context: _p34) {
        if (!enabled) return
        context.safeDiagnosticLines().forEach { line -> Log.d(tag, line) }
    }
}
