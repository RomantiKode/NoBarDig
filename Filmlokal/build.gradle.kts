// CANONICAL/READABLE SOURCE BUILD FILE. Debug/maintenance uses canonical source only.
// Protected Public is generated from this source with embedded Agoose-SourceProtect-v1.
import java.util.Properties

version = 3

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

val tmdbReadAccessToken = System.getenv("TMDB_READ_ACCESS_TOKEN")
    ?: localProperties.getProperty("tmdb.readToken")
    ?: ""
val tmdbApiKey = System.getenv("TMDB_API_KEY")
    ?: localProperties.getProperty("tmdb.apiKey")
    ?: localProperties.getProperty("tmdb.key")
    ?: ""

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.agooseangsa.Filmlokal"

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", tmdbReadAccessToken.asBuildConfigString())
        buildConfigField("String", "TMDB_API_KEY", tmdbApiKey.asBuildConfigString())
    }
}

cloudstream {
    language = "id"
    description = "Filmlokal merupakan Situs Nonton Layarkaca21 Online Terlengkap yang menyediakan film-film berkualitas dan tentunya sangat cocok untuk Anda yang ingin menghabiskan waktu luang di antara kesibukan Anda. Biasanya, weekend seseorang akan diisi dengan liburan dan nonton, di situs ini anda akan mendapatkan banyak sekali film bioskop 21 terbaru di semua genre dan negara, bahkan sangat memuaskan bagi Anda yang movie-holic dan suka menghabiskan waktu di rumah sambil menonton film. Situs ini sangat rekomendasi sekali karena selalu update film setiap hari dan Anda dapat mendownload film dari situs tersebut layaknya situs penyedia bioskop lainnya."
    authors = listOf("Agoose")
    iconUrl = "https://tv1.filmlokal.me/wp-content/uploads/2022/01/cropped-02B03F76-64AE-4D9C-8DE7-C4EE4DC115B2.jpeg"

    // Runtime playback has not been validated inside Cloudstream yet.
    status = 3

    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )
}
