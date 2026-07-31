package com.donghub

import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.StreamWishExtractor

class DailymotionCom : Dailymotion() {
    override var name = "Dailymotion"
    override var mainUrl = "https://www.dailymotion.com"
}

class DailymotionNoWww : Dailymotion() {
    override var name = "Dailymotion"
    override var mainUrl = "https://dailymotion.com"
}

class GeoDailymotionCom : Dailymotion() {
    override var name = "Dailymotion Geo"
    override var mainUrl = "https://geo.dailymotion.com"
}

class KiRooserlyxoseShop : StreamWishExtractor() {
    override var name = "StreamWish"
    override var mainUrl = "https://ki.rooserlyxose.shop"
}

class MorenciusCom : StreamWishExtractor() {
    override var name = "StreamWish"
    override var mainUrl = "https://morencius.com"
}
