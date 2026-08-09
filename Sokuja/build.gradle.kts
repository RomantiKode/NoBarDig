// CANONICAL/READABLE SOURCE BUILD FILE. Debug/maintenance memakai canonical source.
// Protection Standard ACTIVE: public release dibuat dari canonical source melalui Agoose-SourceProtect-v1.
version = 2

cloudstream {
    language = "id"
    description = "SOKUJA menyediakan koleksi anime subtitle Indonesia terlengkap. Nonton streaming dan download anime sub Indo HD gratis, update episode baru setiap hari."
    authors = listOf("Agoose")

    // 0: Down, 1: Ok, 2: Slow, 3: Beta-only
    // v2 memperbaiki compile-time suspend boundary; runtime Cloudstream tetap belum diuji, jadi Beta-only.
    status = 3

    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )
}
