package com.pencurimovie

import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamWishExtractor

/** Alias Dood untuk domain yang digunakan PencuriMovie. */
class Dsvplay : DoodLaExtractor() {
    override var mainUrl = "https://dsvplay.com"
}

/** Alias StreamWish untuk domain aktif pada halaman contoh. */
class Hgcloud : StreamWishExtractor() {
    override val name = "Hgcloud"
    override val mainUrl = "https://hgcloud.to"
}

/** Alias lama agar link hglink.to tetap dapat diproses bila muncul lagi. */
class Hglink : StreamWishExtractor() {
    override val name = "Hglink"
    override val mainUrl = "https://hglink.to"
}

/** Alias MixDrop untuk mirror aktual yang ditemukan pada HTML episode. */
class MixdropTop : MixDrop() {
    override var mainUrl = "https://mixdrop.top"
}
