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

val providerProfileFile = project.file("config/ProviderProfile.json")
require(providerProfileFile.isFile) {
    "Missing config/ProviderProfile.json (Agoose ProviderProfile Standard v1)"
}
val providerProfileJson = providerProfileFile.readText(Charsets.UTF_8).trim()
require(providerProfileJson.startsWith("{") && providerProfileJson.endsWith("}")) {
    "config/ProviderProfile.json must contain a JSON object"
}

fun String.asBuildConfigString(): String =
    "\"" + this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t") + "\""

android {
    namespace = "com.agooseangsa.AnimeXin"

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", tmdbReadAccessToken.asBuildConfigString())
        buildConfigField("String", "TMDB_API_KEY", tmdbApiKey.asBuildConfigString())
        buildConfigField("String", "AGOOSE_PROVIDER_PROFILE_JSON", providerProfileJson.asBuildConfigString())
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
