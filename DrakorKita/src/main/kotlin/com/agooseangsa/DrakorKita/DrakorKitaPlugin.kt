package com.agooseangsa.DrakorKita

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DrakorKitaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DrakorKita())
    }
}
