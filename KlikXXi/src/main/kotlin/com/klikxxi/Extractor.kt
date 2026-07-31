package com.klikxxi

import com.lagradost.cloudstream3.extractors.StreamWishExtractor

/**
 * Alias lokal untuk host yang terlihat pada HTML player KlikXXi.
 * Cloudstream versi baru sudah memiliki alias serupa, tetapi registrasi lokal
 * menjaga kompatibilitas pada instalasi yang extractornya belum diperbarui.
 */
class HgCloud : StreamWishExtractor() {
    override val name = "HgCloud"
    override val mainUrl = "https://hgcloud.to"
}
