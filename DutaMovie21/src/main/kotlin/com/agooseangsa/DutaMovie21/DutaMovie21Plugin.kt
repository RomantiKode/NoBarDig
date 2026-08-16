package com.agooseangsa.DutaMovie21

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DutaMovie21Plugin : Plugin() {
    override fun load(_d0: Context) {
        registerMainAPI(DutaMovie21())
    }
}
