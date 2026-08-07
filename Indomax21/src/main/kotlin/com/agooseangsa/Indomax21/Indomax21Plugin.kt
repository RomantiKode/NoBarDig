package com.agooseangsa.Indomax21

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Indomax21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Indomax21())
        registerExtractorAPI(ImaxStreams())
        registerExtractorAPI(ImaxStreamsCom())
    }
}
