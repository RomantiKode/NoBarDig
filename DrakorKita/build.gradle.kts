// CANONICAL/READABLE SOURCE BUILD FILE. Debug/maintenance uses canonical source.
// Protection Standard ACTIVE: publish generated protected output, not canonical Kotlin.
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
    namespace = "com.agooseangsa.DrakorKita"

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
    description = "DrakorKita, DrakorIndo, DramaQu, Streaming online drama korea terbaru, Gratis download film drakor subtitle indonesia, kumpulan film serial drama korea terbaik"
    authors = listOf("Agoose")

    // iconUrl intentionally omitted: the discovered favicon is tied to a rotating random host.
    // 0: Down, 1: Ok, 2: Slow, 3: Beta-only
    status = 3

    tvTypes = listOf(
        "Movie",
        "TvSeries",
    )
}
