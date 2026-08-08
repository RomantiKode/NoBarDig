package com.agooseangsa.Donghub

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DonghubPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Donghub())
        registerExtractorAPI(_f0())
        registerExtractorAPI(_f1())
        registerExtractorAPI(_f2())
    }
}
