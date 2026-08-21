// CANONICAL/READABLE SOURCE BUILD FILE. Debug/maintenance memakai canonical source.
// Jika Protection Standard aktif, jangan publish canonical source ke repo public; generate release/protected output lebih dulu.
// Metadata AnimeXin ditetapkan dari Info.txt dan audit target Stage34R5.
// cloudstream.description: sumber utama = meta[name="description"] homepage/Home.txt;
// fallback = og:description homepage, lalu deskripsi singkat hasil audit target.
import groovy.json.JsonSlurper
import java.util.Properties

version = 1

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

// Agoose ProviderProfile Standard v1.
// The JSON is canonical provider-local configuration and must not contain secrets.
val providerProfileFile = project.file("config/ProviderProfile.json")
require(providerProfileFile.isFile) {
    "Missing config/ProviderProfile.json (Agoose ProviderProfile Standard v1)"
}
val providerProfileJson = providerProfileFile.readText(Charsets.UTF_8).trim()
require(providerProfileJson.startsWith("{") && providerProfileJson.endsWith("}")) {
    "config/ProviderProfile.json must contain a JSON object"
}

// Agoose Standalone Metadata Credential Registry v2 (Stage 12), consumed by Metadata Standard v3.5 / Stage 22 (credential contract itself unchanged).
// Secrets are supplied by CI environment or private root local.properties; never ProviderProfile/source.
// TheTVDB is executable only when ProviderProfile enables it and valid credential input is present.
// TVmaze/OMDb remain deferred. Disabled providers are deliberately embedded as empty strings even when repository secrets exist.
val providerProfileRoot = runCatching {
    JsonSlurper().parseText(providerProfileJson) as? Map<*, *>
}.getOrNull() ?: emptyMap<Any?, Any?>()

fun profileObject(parent: Map<*, *>, key: String): Map<*, *>? = parent[key] as? Map<*, *>

fun metadataProviderEnabled(provider: String, defaultValue: Boolean): Boolean {
    val metadata = profileObject(providerProfileRoot, "metadata") ?: return defaultValue
    val providers = profileObject(metadata, "providers") ?: return defaultValue
    val enabled = profileObject(providers, "enabled") ?: return defaultValue
    return (enabled[provider] as? Boolean) ?: defaultValue
}

fun legacyTmdbEnabled(): Boolean {
    val metadata = profileObject(providerProfileRoot, "metadata") ?: return true
    val tmdb = profileObject(metadata, "tmdb") ?: return true
    return (tmdb["enabled"] as? Boolean) ?: true
}

fun envOrLocal(envName: String, vararg localKeys: String): String {
    System.getenv(envName)?.let { return it }
    for (key in localKeys) {
        localProperties.getProperty(key)?.let { return it }
    }
    return ""
}

val tmdbCredentialEnabled = legacyTmdbEnabled() && metadataProviderEnabled("tmdb", true)
val theTvdbCredentialEnabled = metadataProviderEnabled("thetvdb", false)
val omdbCredentialEnabled = metadataProviderEnabled("omdb", false)

val tmdbReadAccessToken = if (tmdbCredentialEnabled) {
    envOrLocal("TMDB_READ_ACCESS_TOKEN", "tmdb.readToken")
} else ""
val tmdbReadAccessTokens = if (tmdbCredentialEnabled) {
    envOrLocal("TMDB_READ_ACCESS_TOKENS", "tmdb.readTokens")
} else ""
val tmdbApiKey = if (tmdbCredentialEnabled) {
    envOrLocal("TMDB_API_KEY", "tmdb.apiKey", "tmdb.key")
} else ""
val tmdbApiKeys = if (tmdbCredentialEnabled) {
    envOrLocal("TMDB_API_KEYS", "tmdb.apiKeys")
} else ""
val theTvdbApiKey = if (theTvdbCredentialEnabled) {
    envOrLocal("THETVDB_API_KEY", "thetvdb.apiKey")
} else ""
val theTvdbApiKeys = if (theTvdbCredentialEnabled) {
    envOrLocal("THETVDB_API_KEYS", "thetvdb.apiKeys")
} else ""
val theTvdbPin = if (theTvdbCredentialEnabled) {
    envOrLocal("THETVDB_PIN", "thetvdb.pin")
} else ""
val omdbApiKey = if (omdbCredentialEnabled) {
    envOrLocal("OMDB_API_KEY", "omdb.apiKey")
} else ""
val omdbApiKeys = if (omdbCredentialEnabled) {
    envOrLocal("OMDB_API_KEYS", "omdb.apiKeys")
} else ""
// TVmaze public metadata API requires no credential and therefore has no secret field here.

fun String.asBuildConfigString(): String =
    "\"" + this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t") + "\""

android {
    // BuildConfig is generated in the Android namespace; keep it aligned with the Kotlin helper package.
    namespace = "com.agooseangsa.AnimeXin"

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", tmdbReadAccessToken.asBuildConfigString())
        buildConfigField("String", "TMDB_READ_ACCESS_TOKENS", tmdbReadAccessTokens.asBuildConfigString())
        buildConfigField("String", "TMDB_API_KEY", tmdbApiKey.asBuildConfigString())
        buildConfigField("String", "TMDB_API_KEYS", tmdbApiKeys.asBuildConfigString())
        buildConfigField("String", "THETVDB_API_KEY", theTvdbApiKey.asBuildConfigString())
        buildConfigField("String", "THETVDB_API_KEYS", theTvdbApiKeys.asBuildConfigString())
        buildConfigField("String", "THETVDB_PIN", theTvdbPin.asBuildConfigString())
        buildConfigField("String", "OMDB_API_KEY", omdbApiKey.asBuildConfigString())
        buildConfigField("String", "OMDB_API_KEYS", omdbApiKeys.asBuildConfigString())
        buildConfigField("String", "AGOOSE_PROVIDER_PROFILE_JSON", providerProfileJson.asBuildConfigString())
    }
}

cloudstream {
    language = "id"
    description = "AnimeXin - Streaming Download Donghua Subtitle Indonesia English"
    authors = listOf("Agoose")

    // Icon provider memakai logo/favicon resmi yang ditemukan pada snapshot target.
    iconUrl = "https://animexin.dev/wp-content/uploads/2020/06/cropped-index.jpg"

    // 0: Down, 1: Ok, 2: Slow, 3: Beta-only
    status = 3 // Beta-only until canonical Cloudstream build/runtime playback is verified

    // Tipe konten diselaraskan dengan evidence target: serial anime dan anime movie.
    tvTypes = listOf(
        "Anime",
        "AnimeMovie",
    )
}
