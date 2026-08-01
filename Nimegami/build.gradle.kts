// use an integer for version numbers
version = 3

cloudstream {
    language = "id"
    description = "Tempat Download dan Nonton Anime Subtitle Indonesia, dengan Format Mp4 dan MKV dan dalam Ukuran 480p, 720p, 360p, 240p dan BATCH (Paket)"
    authors = listOf("Agoose")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1
    tvTypes = listOf(
        "AnimeMovie",
        "Anime",
        "OVA",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=nimegami.id&sz=%size%"
}
