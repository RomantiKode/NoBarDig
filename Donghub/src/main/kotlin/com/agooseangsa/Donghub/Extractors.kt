package com.agooseangsa.Donghub

import com.lagradost.cloudstream3.extractors.Dailymotion
import com.lagradost.cloudstream3.extractors.OkRuSSL

/** Target uses both www.dailymotion.com and geo.dailymotion.com embeds. */
class DonghubDailymotion : Dailymotion() {
    override var name = "Dailymotion"
    override var mainUrl = "https://www.dailymotion.com"
}

class DonghubGeoDailymotion : Dailymotion() {
    override var name = "Dailymotion Geo"
    override var mainUrl = "https://geo.dailymotion.com"
}

/** Verified target mirror uses https://ok.ru/videoembed/... */
class DonghubOkRu : OkRuSSL() {
    override var name = "OK.ru"
    override var mainUrl = "https://ok.ru"
}
