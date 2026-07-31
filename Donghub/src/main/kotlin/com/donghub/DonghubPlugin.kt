package com.donghub

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class DonghubPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DonghubProvider())
        registerExtractorAPI(DailymotionCom())
        registerExtractorAPI(DailymotionNoWww())
        registerExtractorAPI(GeoDailymotionCom())
        registerExtractorAPI(KiRooserlyxoseShop())
        registerExtractorAPI(MorenciusCom())
    }
}
