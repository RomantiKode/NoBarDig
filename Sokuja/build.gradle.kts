// CANONICAL/READABLE SOURCE BUILD FILE. Protected public source is generated from this module.
import java.util.Properties

version = 4

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

// Agoose ProviderProfile Standard v1: canonical non-secret provider configuration.
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
    namespace = "com.agooseangsa.Sokuja"

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
    description = "SOKUJA menyediakan koleksi anime subtitle Indonesia terlengkap. Nonton streaming dan download anime sub Indo HD gratis, update episode baru setiap hari."
    authors = listOf("Agoose")
    iconUrl = "https://x6.sokuja.uk/favicon.png"

    // First Agoose Modular replacement; runtime playback gate remains beta until device validation.
    status = 3

    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )
}
