import java.util.Properties

version = 2

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
    namespace = "com.agooseangsa.MidasXXI"

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
    description = "MIDASXXI Tempat seru buat nonton film dan drama Korea! Mulai dari LK21, IDLIX, hingga Bioskopkeren, Rebahin Sub Indo. dan Nonton Film semi bikin mager makin asyik!"
    authors = listOf("Agoose")
    iconUrl = "https://unairi.ac.id/wp-content/uploads/2024/01/cropped-favicon.png"

    // Public provider metadata; runtime playback remains separately gated in docs.
    status = 1

    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )
}
