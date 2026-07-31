package com.layarkaca21

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Layarkaca21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Layarkaca21())
        registerExtractorAPI(Lk21Hownetwork())
        registerExtractorAPI(Lk21CloudHownetwork())
        registerExtractorAPI(Lk21Co4nxtrl())
        registerExtractorAPI(Lk21Furher())
        registerExtractorAPI(Lk21FurherAlt())
        registerExtractorAPI(Lk21Turbovidhls())
        registerExtractorAPI(Lk21Abyss())
        registerExtractorAPI(Lk21ShortInk())
        registerExtractorAPI(Lk21VideoNode())
    }
}
