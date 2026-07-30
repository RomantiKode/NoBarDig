package com.nomat

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NomatPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Nomat())

        // Domain aliases observed by the original module. They reuse the
        // maintained Cloudstream VidHidePro implementation instead of
        // duplicating the complete extractor logic locally.
        registerExtractorAPI(Dingtezuni())
        registerExtractorAPI(Movearnpre())
        registerExtractorAPI(Mivalyo())
        registerExtractorAPI(Bingezove())
        registerExtractorAPI(PlayHydrax())
    }
}
