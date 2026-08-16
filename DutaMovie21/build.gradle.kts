// CANONICAL/READABLE SOURCE BUILD FILE.
version = 3

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
    namespace = "com.agooseangsa.DutaMovie21"

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "AGOOSE_PROVIDER_PROFILE_JSON", providerProfileJson.asBuildConfigString())
    }
}

cloudstream {
    language = "id"
    description = "DUTAMOVIE21 tempat Nonton Movie Film Online Bioskop Online Sub Indo. Kamu harus mencoba nonton film disini. 204.3.234.75, Bioskop Online Terbaik Indonesia."
    authors = listOf("Agoose")
    iconUrl = "https://backupdata.b-cdn.net/image/dutamovie21-icon.png"
    status = 3
    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )
}
