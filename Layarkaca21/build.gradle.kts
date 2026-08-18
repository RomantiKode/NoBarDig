// CANONICAL/READABLE SOURCE BUILD FILE — Agoose Modular v3r4r13
import java.util.Properties

version = 7
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

// Agoose ProviderProfile Standard v1: bundled non-secret provider configuration.
val providerProfileFile = project.file("config/ProviderProfile.json")
require(providerProfileFile.isFile) { "Missing config/ProviderProfile.json" }
val providerProfileJson = providerProfileFile.readText(Charsets.UTF_8).trim()
require(providerProfileJson.startsWith("{") && providerProfileJson.endsWith("}")) {
    "config/ProviderProfile.json must contain a JSON object"
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t") + "\""

android {
    // Keep generated BuildConfig in the same package as the provider/TMDB helper.
    namespace = "com.agooseangsa.Layarkaca21"

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
    description = "Nonton dan download film & series terbaru di LK21. Streaming sub Indo gratis, kualitas HD. Tersedia drama Korea, anime, film barat, dan Asia lengkap!"
    authors = listOf("Agoose")
    iconUrl = "https://assets.showcdnx.com/favicon.ico"
    status = 3
    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )
}
