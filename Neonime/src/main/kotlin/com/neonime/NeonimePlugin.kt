package com.neonime

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NeonimePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Neonime())
    }
}
