package com.agooseangsa.AnimeXin

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AnimeXinPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeXin())
    }
}
