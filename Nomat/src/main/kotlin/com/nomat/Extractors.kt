package com.nomat

import com.lagradost.cloudstream3.extractors.VidHidePro

/**
 * Thin aliases for VidHide/EarnVids mirrors used by Nomat's player gateway.
 * Keeping these classes declarative makes future domain changes easy and
 * delegates stream parsing to Cloudstream's maintained VidHidePro extractor.
 */
class Dingtezuni : VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://dingtezuni.com"
}

class Movearnpre : VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://movearnpre.com"
}

class Mivalyo : VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://mivalyo.com"
}

class Bingezove : VidHidePro() {
    override var name = "EarnVids"
    override var mainUrl = "https://bingezove.com"
}

class PlayHydrax : VidHidePro() {
    override var name = "Hydrax"
    override var mainUrl = "https://playhydrax.com"
}
