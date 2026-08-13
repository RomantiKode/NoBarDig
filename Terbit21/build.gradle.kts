// CANONICAL/READABLE SOURCE BUILD FILE. Debug/maintenance memakai canonical source.
// Protection Standard ACTIVE: publish generated Protected Public, bukan canonical Kotlin.
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

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.agooseangsa.Terbit21"

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
    description = "Terbit21 Nonton Movie 21 Bioskop Online Keren XX1 INDOXXI Ganool Dunia21 Layarkaca21 iLk21 Dunia21 Bioskop IDLIX CGVINDO INDOFILM BIOSKOPKEREN"
    authors = listOf("Agoose")
    status = 3
    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )
}
