// CANONICAL/READABLE SOURCE BUILD FILE. Protected public source is generated from this module.
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
    namespace = "com.agooseangsa.Sokuja"

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
    description = "SOKUJA menyediakan koleksi anime subtitle Indonesia terlengkap. Nonton streaming dan download anime sub Indo HD gratis, update episode baru setiap hari."
    authors = listOf("Agoose")
    iconUrl = "https://x6.sokuja.uk/favicon.png"

    // New provider delivery; kept beta-only until real Cloudstream runtime validation is performed.
    status = 3

    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )
}
