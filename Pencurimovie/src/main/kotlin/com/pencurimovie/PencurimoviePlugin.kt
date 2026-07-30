package com.pencurimovie

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class PencurimoviePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PencurimovieProvider())
        registerExtractorAPI(Dsvplay())
        registerExtractorAPI(Hgcloud())
        registerExtractorAPI(Hglink())
        registerExtractorAPI(MixdropTop())
    }
}
