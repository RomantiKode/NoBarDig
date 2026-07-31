package com.neonime

import android.util.Base64
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.addSub
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

class Neonime : MainAPI() {
    override var mainUrl = "https://otakupoi.org/neonime"
    override var name = "Neonime"
    override var lang = "id"

    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
    )

    override val mainPage = mainPageOf(
        mainUrl to "Terbaru",
        "$mainUrl/ongoing/" to "On-Going",
        "$mainUrl/movies/" to "Movie",
        "$mainUrl/batchs/" to "Batch",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page <= 1) {
            request.data
        } else {
            "${request.data.trimEnd('/')}/page/$page/"
        }

        val results = app.get(pageUrl).document
            .select(CARD_SELECTOR)
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(request.name, results)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        return app.get("$mainUrl/search/?q=$encodedQuery").document
            .select(CARD_SELECTOR)
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = firstNonBlank(
            document.selectFirst("h1.xptitle")?.text(),
            document.selectFirst("h1.title-post")?.text(),
            document.selectFirst("meta[property=og:title]")?.attr("content"),
            document.title(),
        )?.let(::cleanTitle) ?: throw IllegalStateException("Judul Neonime tidak ditemukan")

        val poster = absoluteUrl(
            firstNonBlank(
                document.selectFirst("img.postcover")?.attr("src"),
                document.selectFirst("img.cover")?.attr("src"),
                document.selectFirst("meta[property=og:image]")?.attr("content"),
            )
        )
        val background = absoluteUrl(document.selectFirst(".vignette img")?.attr("src"))
        val plot = document.select(".boltab div[itemprop=description] p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .ifBlank {
                document.selectFirst("meta[name=description]")?.attr("content")?.trim().orEmpty()
            }
            .takeIf { it.isNotBlank() }

        val year = document.infoValue("Release Year")
            ?.text()
            ?.let { YEAR_REGEX.find(it)?.value?.toIntOrNull() }
        val tags = document.infoValue("Genre")
            ?.select("a")
            ?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
        val status = parseStatus(document.infoValue("TV Status")?.text())
        val trailer = document.selectFirst("#traimbed[data-embed]")
            ?.attr("data-embed")
            ?.takeIf { it.isNotBlank() }
        val japaneseTitle = document.infoValue("Original name")?.text()?.trim()
        val englishTitle = document.selectFirst(".js-alternative-titles .spacetab")
            ?.text()
            ?.substringAfter("English:", missingDelimiterValue = "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val episodeEntries = document.select("a.othereps[href]")
            .mapNotNull { anchor ->
                val href = absoluteUrl(anchor.attr("href")) ?: return@mapNotNull null
                val episodeTitle = anchor.text().trim()
                val episodeNumber = EPISODE_REGEX.find(episodeTitle)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                EpisodeEntry(href, episodeTitle, episodeNumber)
            }
            .distinctBy { it.url }
            .sortedWith(compareBy<EpisodeEntry> { it.number ?: Int.MAX_VALUE }.thenBy { it.title })

        val pageType = inferType(
            listOfNotNull(
                url,
                document.infoValue("Type")?.text(),
                title,
            ).joinToString(" ")
        )

        if (episodeEntries.isEmpty() && pageType != TvType.Anime) {
            return newMovieLoadResponse(title, url, pageType, url) {
                posterUrl = poster
                backgroundPosterUrl = background
                this.year = year
                this.plot = plot
                this.tags = tags
                addTrailer(trailer)
            }
        }

        val episodes = episodeEntries.map { entry ->
            newEpisode(entry.url) {
                name = entry.title
                episode = entry.number
            }
        }

        return newAnimeLoadResponse(title, url, pageType) {
            engName = englishTitle ?: title
            japName = japaneseTitle
            posterUrl = poster
            backgroundPosterUrl = background
            this.year = year
            this.plot = plot
            this.tags = tags
            showStatus = status
            addEpisodes(DubStatus.Subbed, episodes)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val document = app.get(data).document

        val iframeUrls = document
            .select("#lightsVideo iframe, .contstream iframe, iframe#istream")
            .flatMap { iframe ->
                VIDEO_ATTRIBUTES.mapNotNull { attribute ->
                    absoluteUrl(iframe.attr(attribute))
                }
            }

        val encodedPlayerUrls = document.select("script")
            .asSequence()
            .map { it.html() }
            .filter { it.contains("JSON.parse") && it.contains("player") }
            .flatMap { script ->
                PLAYER_VALUE_REGEX.findAll(script).mapNotNull { match ->
                    decodePlayerUrl(match.groupValues[1])
                }
            }
            .toList()

        val sourceUrls = (iframeUrls + encodedPlayerUrls)
            .filter(::isSafeVideoUrl)
            .distinct()

        for (sourceUrl in sourceUrls) {
            loadExtractor(
                sourceUrl,
                data,
                subtitleCallback,
                callback,
            )
        }

        return sourceUrls.isNotEmpty()
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val anchor = selectFirst("a[href]") ?: return null
        val href = absoluteUrl(anchor.attr("href")) ?: return null
        val title = firstNonBlank(
            selectFirst(".titlelist")?.text(),
            selectFirst("h2")?.text(),
            selectFirst("img[alt]")?.attr("alt"),
        )?.let(::cleanTitle) ?: return null
        val poster = absoluteUrl(
            firstNonBlank(
                selectFirst("img")?.attr("data-src"),
                selectFirst("img")?.attr("data-lazy-src"),
                selectFirst("img")?.attr("src"),
            )
        )
        val episodeText = selectFirst(".eplist")?.text().orEmpty()
        val episodeCount = parseEpisodeCount(episodeText)
        val type = inferType("$href $title $episodeText")

        return newAnimeSearchResponse(title, href, type) {
            posterUrl = poster
            if (type == TvType.Anime) addSub(episodeCount)
        }
    }

    private fun Element.infoValue(label: String): Element? {
        return select(".tablist").firstOrNull { block ->
            block.selectFirst("b")
                ?.text()
                ?.trim()
                ?.equals(label, ignoreCase = true) == true
        }?.selectFirst("span")
    }

    private fun absoluteUrl(value: String?): String? {
        val rawUrl = value
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.startsWith("data:", ignoreCase = true) }
            ?: return null

        return runCatching {
            when {
                rawUrl.startsWith("https://", ignoreCase = true) ||
                    rawUrl.startsWith("http://", ignoreCase = true) -> rawUrl
                rawUrl.startsWith("//") -> "https:$rawUrl"
                else -> URI("${mainUrl.trimEnd('/')}/").resolve(rawUrl).toString()
            }
        }.getOrNull()
    }

    private fun decodePlayerUrl(value: String): String? = runCatching {
        val firstUrlDecode = URLDecoder.decode(value, Charsets.UTF_8.name())
        val firstBase64Decode = String(
            Base64.decode(rotateRight(firstUrlDecode, ROTATION), Base64.DEFAULT),
            Charsets.UTF_8,
        )
        val secondUrlDecode = URLDecoder.decode(firstBase64Decode, Charsets.UTF_8.name())
        String(
            Base64.decode(rotateRight(secondUrlDecode, ROTATION), Base64.DEFAULT),
            Charsets.UTF_8,
        ).trim()
    }.getOrNull()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun rotateRight(value: String, amount: Int): String {
        if (value.isEmpty()) return value
        val shift = amount % value.length
        return value.takeLast(shift) + value.dropLast(shift)
    }

    private fun isSafeVideoUrl(url: String): Boolean {
        val normalized = url.lowercase()
        return (normalized.startsWith("https://") || normalized.startsWith("http://")) &&
            BLOCKED_AD_HOSTS.none(normalized::contains)
    }

    private fun parseEpisodeCount(text: String): Int? {
        if (text.contains("movie", ignoreCase = true) || text.contains("ova", ignoreCase = true)) {
            return null
        }
        return NUMBER_REGEX.findAll(text)
            .mapNotNull { it.value.toIntOrNull() }
            .lastOrNull()
    }

    private fun cleanTitle(rawTitle: String): String {
        return rawTitle
            .replace(NEONIME_SUFFIX_REGEX, "")
            .replace(OTAKUPOI_SUFFIX_REGEX, "")
            .trim()
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun inferType(value: String): TvType {
        return when {
            value.contains("movie", ignoreCase = true) -> TvType.AnimeMovie
            value.contains("ova", ignoreCase = true) ||
                value.contains("special", ignoreCase = true) -> TvType.OVA
            else -> TvType.Anime
        }
    }

    private fun parseStatus(value: String?): ShowStatus {
        return when (value?.trim()?.lowercase()) {
            "ongoing", "on-going", "in production", "returning series" -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }

    private data class EpisodeEntry(
        val url: String,
        val title: String,
        val number: Int?,
    )

    private companion object {
        const val CARD_SELECTOR = "div.bg-white.shadow.xrelated.relative, div.xrelated"
        const val ROTATION = 13

        val VIDEO_ATTRIBUTES = listOf(
            "src",
            "data-src",
            "data-lazy-src",
            "data-wpfc-original-src",
        )
        val BLOCKED_AD_HOSTS = setOf(
            "dtscout.com",
            "purpleads.io",
            "prplads.com",
            "histats.com",
            "rivalgaums.com",
            "shopee.co.id",
        )

        val EPISODE_REGEX = Regex("""Episode\s*([0-9]+)""", RegexOption.IGNORE_CASE)
        val NUMBER_REGEX = Regex("""\d+""")
        val YEAR_REGEX = Regex("""(?:19|20)\d{2}""")
        val PLAYER_VALUE_REGEX = Regex("""[\"']player\d+[\"']\s*:\s*[\"']([^\"']+)[\"']""")
        val NEONIME_SUFFIX_REGEX = Regex("""\s*-\s*Neonime.*$""", RegexOption.IGNORE_CASE)
        val OTAKUPOI_SUFFIX_REGEX = Regex("""\s*\|\s*OtakuPoi.*$""", RegexOption.IGNORE_CASE)
    }
}
