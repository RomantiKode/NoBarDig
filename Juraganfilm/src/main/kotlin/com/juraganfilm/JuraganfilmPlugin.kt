package com.juraganfilm

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class JuraganfilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(JuraganfilmProvider())
    }
}
