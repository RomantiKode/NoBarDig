import java.util.Properties

version = 1

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
    namespace = "com.agooseangsa.AnimeXin"

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
    description = "AnimeXin - Streaming Download Donghua Subtitle Indonesia English"
    authors = listOf("Agoose")
    iconUrl = "https://animexin.dev/wp-content/uploads/2026/01/cropped-New-Logo-e1768365053967-192x192.png"
    status = 3
    tvTypes = listOf(
        "Movie",
        "Anime",
    )
}
