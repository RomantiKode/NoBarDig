package com.rebahin21

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Rebahin21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Rebahin21Provider())
    }
}
