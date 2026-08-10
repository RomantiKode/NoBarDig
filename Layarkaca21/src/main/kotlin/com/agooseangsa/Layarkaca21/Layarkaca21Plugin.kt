package com.agooseangsa.Layarkaca21

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Layarkaca21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Layarkaca21())
    }
}
