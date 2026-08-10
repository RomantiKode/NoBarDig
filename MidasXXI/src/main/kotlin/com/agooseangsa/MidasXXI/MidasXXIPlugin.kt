package com.agooseangsa.MidasXXI

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MidasXXIPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(MidasXXI())
    }
}
