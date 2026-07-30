package com.pencurimovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

class PencurimovieProvider : MainAPI() {
    override var mainUrl = "https://ww21.pencurimovie.sbs"
    override var name = "PencuriMovie"
    override var lang = "ms"

    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Satu request halaman utama menghasilkan dua baris katalog.
    // Ini lebih ringan daripada meminta puluhan halaman genre secara bersamaan.
    override val mainPage = mainPageOf("/" to "PencuriMovie")

    private val trailingYearRegex = Regex("""\s*\((\d{4})\)\s*$""")
    private val seasonNumberRegex = Regex("""(?i)\bseason\s*(\d+)\b""")
    private val episodeNumberRegex = Regex("""(?i)\bepisode\s*(\d+)\b""")
    private val episodePathRegex = Regex("""(?i)season[-_\s]*(\d+)[-_\s]*episode[-_\s]*(\d+)""")
    private val durationHourRegex = Regex("""(?i)(\d+)\s*(?:h|hour|hours|jam)\b""")
    private val durationMinuteRegex = Regex("""(?i)(\d+)\s*(?:m|min|mins|minute|minutes|menit)\b""")

    // Pertahanan tambahan bila tema suatu saat menyisipkan iframe iklan ke area player.
    // Script popup di <head> tidak dijalankan oleh app.get()/Jsoup, tetapi URL iframe
    // tetap disaring agar tracker/iklan tidak dikirim ke registry extractor.
    private val blockedPlayerHosts = setOf(
        "push-sdk.net",
        "bvtpk.com",
        "googletagmanager.com",
        "google-analytics.com",
        "doubleclick.net",
        "hcaptcha.com",
        "gstatic.com",
        "ay267.com"
    )

    private fun parseTitle(raw: String): Pair<String, Int?> {
        val clean = raw.trim()
        val match = trailingYearRegex.find(clean)
        val year = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        val title = clean.replace(trailingYearRegex, "").trim()
        return title to year
    }

    private fun normalizeImageUrl(raw: String?): String? {
        val clean = raw?.trim().orEmpty()
        if (
            clean.isBlank() ||
            clean.startsWith("data:", ignoreCase = true) ||
            clean.startsWith("javascript:", ignoreCase = true) ||
            clean.startsWith("blob:", ignoreCase = true)
        ) return null

        return when {
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            else -> fixUrlNull(clean)
        }
    }

    private fun Element.httpImageUrl(): String? {
        return listOf(
            attr("data-src"),
            attr("data-original"),
            attr("data-lazy-src"),
            attr("src"),
            attr("content")
        ).asSequence().mapNotNull(::normalizeImageUrl).firstOrNull()
    }

    private fun parseDurationMinutes(raw: String?): Int? {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return null

        val hours = durationHourRegex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val minutes = durationMinuteRegex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        if (hours != null || minutes != null) {
            return (hours ?: 0) * 60 + (minutes ?: 0)
        }

        return Regex("""\d+""").find(text)?.value?.toIntOrNull()
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val anchor = selectFirst("a.ml-mask[href], a[href]") ?: return null
        val hrefRaw = anchor.attr("href").trim()
        if (hrefRaw.isBlank() || hrefRaw.startsWith("javascript", ignoreCase = true)) return null

        val href = fixUrl(hrefRaw)
        // Kartu episode langsung tidak dipakai sebagai detail serial karena dapat
        // menyebabkan daftar episode terpecah dan duplikat di halaman utama.
        if (href.contains("/episode/", ignoreCase = true)) return null

        val rawTitle = anchor.attr("oldtitle").ifBlank {
            selectFirst(".mli-info h2, h2, h3")?.text().orEmpty()
        }.ifBlank {
            selectFirst("img[alt]")?.attr("alt").orEmpty()
        }.ifBlank {
            anchor.attr("title")
        }

        val (title, year) = parseTitle(rawTitle)
        if (title.isBlank()) return null

        val poster = selectFirst("img.mli-thumb, img")?.httpImageUrl()
        val resolutionText = select(".mli-resolution").joinToString(" ") { it.text() }
        val qualityText = resolutionText.ifBlank {
            selectFirst(".mli-quality-text, .mli-quality, .quality")?.text().orEmpty()
        }
        val quality = getQualityFromString(qualityText)
        val isSeries = href.contains("/series/", ignoreCase = true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.year = year
                this.quality = quality
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.year = year
                this.quality = quality
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page <= 1) mainUrl else "$mainUrl/page/$page/"
        val document = app.get(pageUrl).document

        val lists = document.select(".movies-list-wrap").mapNotNull { section ->
            val sectionName = section.selectFirst(".ml-title .pull-left, .ml-title span")
                ?.text()
                ?.substringBefore("View more")
                ?.trim()
                .orEmpty()

            // Latest Episodes sengaja tidak dimasukkan karena URL-nya menunjuk
            // halaman episode, bukan halaman induk serial.
            val displayName = when {
                sectionName.equals("Latest Movies", ignoreCase = true) -> "Latest Movies"
                sectionName.equals("Latest TV Series", ignoreCase = true) -> "Latest TV Series"
                else -> return@mapNotNull null
            }

            val items = section.select(".ml-item").mapNotNull { it.toSearchResult() }
            if (items.isEmpty()) null else HomePageList(displayName, items)
        }

        if (lists.isEmpty()) {
            throw ErrorLoadingException("Katalog PencuriMovie tidak ditemukan")
        }

        return newHomePageResponse(lists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val document = app.get("$mainUrl/?s=$encodedQuery").document
        return document.select(".movies-list .ml-item, .ml-item")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

    private fun Document.infoRow(label: String): Element? {
        return select(".mvic-info p").firstOrNull { row ->
            row.selectFirst("strong")
                ?.text()
                ?.trim()
                ?.removeSuffix(":")
                ?.equals(label, ignoreCase = true) == true
        }
    }

    private fun cleanEpisodeName(raw: String, episodeNumber: Int?): String {
        val clean = raw.replace(Regex("""\s+"""), " ").trim()
        val withoutPrefix = clean.replace(
            Regex("""(?i)^episode\s*\d+\s*[-:–—]?\s*"""),
            ""
        ).trim()

        return withoutPrefix.ifBlank {
            episodeNumber?.let { "Episode $it" } ?: "Episode"
        }
    }

    private fun episodeNumbers(
        text: String,
        href: String,
        fallbackSeason: Int? = null,
        fallbackEpisode: Int? = null
    ): Pair<Int?, Int?> {
        val pathMatch = episodePathRegex.find(href)
        val season = seasonNumberRegex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: pathMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: fallbackSeason
        val episode = episodeNumberRegex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: pathMatch?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: fallbackEpisode

        return season to episode
    }

    private fun parseEpisodes(document: Document): List<Episode> {
        val episodes = linkedMapOf<String, Episode>()

        // Layout MovieMo yang dipakai halaman serial saat ini:
        // #seasons > .tvseason > .les-content > a[href*='/episode/']
        document.select("#seasons .tvseason, div.tvseason").forEach { seasonBlock ->
            val seasonText = seasonBlock
                .selectFirst(".les-title strong, .les-title")
                ?.text()
                .orEmpty()
            val seasonFromHeader = seasonNumberRegex.find(seasonText)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

            seasonBlock.select(".les-content a[href*='/episode/']").forEachIndexed { index, anchor ->
                val rawHref = anchor.attr("href").trim()
                if (rawHref.isBlank()) return@forEachIndexed

                val href = fixUrl(rawHref)
                val linkText = anchor.text().trim()
                val (season, episodeNumber) = episodeNumbers(
                    text = linkText,
                    href = href,
                    fallbackSeason = seasonFromHeader ?: 1,
                    fallbackEpisode = index + 1
                )

                episodes[href] = newEpisode(href) {
                    this.name = cleanEpisodeName(linkText, episodeNumber)
                    this.season = season ?: 1
                    this.episode = episodeNumber
                }
            }
        }

        // Layout alternatif MovieMo pada beberapa versi tema.
        if (episodes.isEmpty()) {
            document.select("ul.episodes-list").forEach { list ->
                val seasonFromList = seasonNumberRegex.find(list.id())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 1

                list.select("li").forEachIndexed { index, item ->
                    val anchor = item.selectFirst("a[href*='/episode/']") ?: return@forEachIndexed
                    val rawHref = anchor.attr("href").trim()
                    if (rawHref.isBlank()) return@forEachIndexed

                    val href = fixUrl(rawHref)
                    val rawName = item.selectFirst(".ep-title")?.text()?.trim()
                        .orEmpty()
                        .ifBlank { anchor.text().trim() }
                    val explicitNumber = item.selectFirst(".ep-num")
                        ?.text()
                        ?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }
                    val (season, episodeNumber) = episodeNumbers(
                        text = rawName,
                        href = href,
                        fallbackSeason = seasonFromList,
                        fallbackEpisode = explicitNumber ?: index + 1
                    )

                    episodes[href] = newEpisode(href) {
                        this.name = cleanEpisodeName(rawName, episodeNumber)
                        this.season = season ?: seasonFromList
                        this.episode = episodeNumber
                        this.posterUrl = item.selectFirst("img")?.httpImageUrl()
                    }
                }
            }
        }

        // Fallback tetap dibatasi ke area daftar episode supaya link komentar,
        // laporan, tutorial, dan iklan tidak ikut dianggap episode.
        if (episodes.isEmpty()) {
            document.select(
                "#seasons a[href*='/episode/'], " +
                    ".episodes-list a[href*='/episode/'], " +
                    ".episodios a[href*='/episode/'], " +
                    "#episodes a[href*='/episode/'], " +
                    ".tvshows-list a[href*='/episode/']"
            ).forEachIndexed { index, anchor ->
                val rawHref = anchor.attr("href").trim()
                if (rawHref.isBlank()) return@forEachIndexed

                val href = fixUrl(rawHref)
                val text = anchor.text().trim()
                val (season, episodeNumber) = episodeNumbers(
                    text = text,
                    href = href,
                    fallbackSeason = 1,
                    fallbackEpisode = index + 1
                )

                episodes[href] = newEpisode(href) {
                    this.name = cleanEpisodeName(text, episodeNumber)
                    this.season = season ?: 1
                    this.episode = episodeNumber
                }
            }
        }

        return episodes.values.sortedWith(
            compareBy<Episode> { it.season ?: 1 }
                .thenBy { it.episode ?: 0 }
        )
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val rawTitle = document.selectFirst(".mvic-desc h3[itemprop=name], .mvic-desc h3")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank {
                document.selectFirst("meta[property=og:title]")
                    ?.attr("content")
                    ?.substringBefore(" - Pencuri")
                    ?.trim()
                    .orEmpty()
            }
        val (title, titleYear) = parseTitle(rawTitle)
        if (title.isBlank()) throw ErrorLoadingException("Judul tidak ditemukan")

        val poster = document.selectFirst(".mvic-thumb img")?.httpImageUrl()
            ?: document.selectFirst("meta[property=og:image]")?.httpImageUrl()
            ?: document.selectFirst("meta[name=twitter:image]")?.httpImageUrl()

        val description = document.selectFirst(
            ".mvic-desc .desc .f-desc, " +
                ".mvic-desc .desc[itemprop=description], " +
                ".mvic-desc .desc"
        )?.text()?.trim().orEmpty().ifBlank {
            document.selectFirst("meta[property=og:description]")
                ?.attr("content")
                ?.trim()
                .orEmpty()
        }.ifBlank {
            document.selectFirst("meta[name=description]")
                ?.attr("content")
                ?.trim()
                .orEmpty()
        }

        val genreRow = document.infoRow("Genre")
        val actorRow = document.infoRow("Actors")
        val directorRow = document.infoRow("Director")
        val countryRow = document.infoRow("Country")
        val studioRow = document.infoRow("Studio")
        val statusRow = document.infoRow("TV Status") ?: document.infoRow("Status")
        val networkRow = document.infoRow("Networks") ?: document.infoRow("Network")
        val durationRow = document.infoRow("Duration")
        val releaseRow = document.infoRow("Release")

        val genres = genreRow?.select("a")?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val actors = actorRow?.select("a")?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val directors = directorRow?.select("a")?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val countries = countryRow?.select("a")?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val studios = studioRow?.select("a")?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val networks = networkRow?.select("a")?.map { it.text().trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val showStatus = statusRow?.selectFirst("span")?.text()?.trim()
            .orEmpty()
            .ifBlank {
                statusRow?.text()
                    ?.substringAfter(":", "")
                    ?.trim()
                    .orEmpty()
            }

        val year = releaseRow?.selectFirst("a[href*='/release-year/']")
            ?.text()
            ?.trim()
            ?.toIntOrNull()
            ?: titleYear
        val duration = parseDurationMinutes(durationRow?.text())
        val rating = document.selectFirst(".imdb-r[itemprop=ratingValue], .imdb-r")
            ?.text()
            ?.trim()
            ?.toDoubleOrNull()
        val trailer = document.selectFirst("meta[itemprop=embedUrl]")
            ?.attr("content")
            ?.takeIf { it.startsWith("http") }

        val extras = buildList {
            if (directors.isNotEmpty()) add("Director: ${directors.joinToString()}")
            if (countries.isNotEmpty()) add("Country: ${countries.joinToString()}")
            if (studios.isNotEmpty()) add("Studio: ${studios.joinToString()}")
            if (networks.isNotEmpty()) add("Networks: ${networks.joinToString()}")
            if (showStatus.isNotBlank()) add("Status: $showStatus")
        }
        val plot = listOf(description, extras.joinToString("\n"))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

        val recommendations = document.select(".mlw-related .ml-item")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        val episodes = parseEpisodes(document)
        val isSeries = url.contains("/series/", ignoreCase = true) ||
            document.selectFirst(
                "[itemtype='http://schema.org/TVSeries'], " +
                    "[itemtype='https://schema.org/TVSeries']"
            ) != null ||
            episodes.isNotEmpty()

        return if (isSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = genres
                this.year = year
                this.duration = duration ?: 0
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
                if (rating != null) addScore(rating.toString(), 10)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = genres
                this.year = year
                this.duration = duration ?: 0
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
                if (rating != null) addScore(rating.toString(), 10)
            }
        }
    }

    private fun playerHost(url: String): String? {
        return runCatching { URI(url).host?.lowercase() }.getOrNull()
    }

    private fun isBlockedPlayerHost(host: String): Boolean {
        return blockedPlayerHosts.any { blocked ->
            host == blocked || host.endsWith(".$blocked")
        }
    }

    private fun normalizePlayerUrl(raw: String): String? {
        val clean = raw.trim()
        if (
            clean.isBlank() ||
            clean.startsWith("javascript:", ignoreCase = true) ||
            clean.startsWith("data:", ignoreCase = true) ||
            clean.startsWith("blob:", ignoreCase = true) ||
            clean.startsWith("about:", ignoreCase = true)
        ) return null

        val normalized = when {
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("/") -> fixUrl(clean)
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            else -> return null
        }

        val host = playerHost(normalized) ?: return null
        return normalized.takeUnless { isBlockedPlayerHost(host) }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, referer = mainUrl).document

        // Halaman film dan episode MovieMo memakai container yang sama. Player
        // disimpan pada data-src dan baru dimasukkan ke src setelah tombol server
        // diklik di browser. Jsoup dapat membacanya tanpa menjalankan iklan/popup.
        val embeds = document.select(
            "#player2 iframe[data-src], #player2 iframe[src], " +
                "#player2 iframe[data-litespeed-src], " +
                "#content-embed .movieplay iframe[data-src], " +
                "#content-embed .movieplay iframe[src], " +
                "#content-embed .movieplay iframe[data-litespeed-src]"
        ).mapNotNull { iframe ->
            iframe.attr("data-src")
                .ifBlank { iframe.attr("data-litespeed-src") }
                .ifBlank { iframe.attr("src") }
                .let(::normalizePlayerUrl)
        }.distinct()

        // Deduplikasi penting karena host yang sama dapat diproses oleh alias
        // plugin dan extractor bawaan CloudStream sekaligus.
        val emittedUrls = linkedSetOf<String>()
        val uniqueCallback: (ExtractorLink) -> Unit = { link ->
            if (emittedUrls.add(link.url)) callback(link)
        }

        document.select(
            "#player2 source[src], #player2 video[src], " +
                "#content-embed .movieplay source[src], " +
                "#content-embed .movieplay video[src]"
        ).mapNotNull { it.attr("src").let(::normalizePlayerUrl) }
            .distinct()
            .forEach { directUrl ->
                uniqueCallback(
                    newExtractorLink(
                        source = name,
                        name = "$name Direct",
                        url = directUrl,
                        type = if (directUrl.contains(".m3u8", ignoreCase = true)) {
                            ExtractorLinkType.M3U8
                        } else {
                            ExtractorLinkType.VIDEO
                        }
                    ) {
                        this.referer = data
                        this.quality = Qualities.Unknown.value
                    }
                )
            }

        // Diproses berurutan agar ringan. Empat host yang pasti muncul pada HTML
        // contoh diberi dispatcher eksplisit. MixDrop/Morencius tetap diserahkan
        // ke registry loadExtractor agar kompatibel dengan extractor aplikasi.
        embeds.forEach { embedUrl ->
            val before = emittedUrls.size
            val host = playerHost(embedUrl).orEmpty()
            var usedExplicitExtractor = false

            try {
                when {
                    host == "dsvplay.com" || host.endsWith(".dsvplay.com") -> {
                        usedExplicitExtractor = true
                        Dsvplay().getUrl(embedUrl, data, subtitleCallback, uniqueCallback)
                    }
                    host == "hgcloud.to" || host.endsWith(".hgcloud.to") -> {
                        usedExplicitExtractor = true
                        Hgcloud().getUrl(embedUrl, data, subtitleCallback, uniqueCallback)
                    }
                    host == "hglink.to" || host.endsWith(".hglink.to") -> {
                        usedExplicitExtractor = true
                        Hglink().getUrl(embedUrl, data, subtitleCallback, uniqueCallback)
                    }
                    host == "mixdrop.top" || host.endsWith(".mixdrop.top") -> {
                        usedExplicitExtractor = true
                        MixdropTop().getUrl(embedUrl, data, subtitleCallback, uniqueCallback)
                    }
                    host == "voe.sx" || host.endsWith(".voe.sx") -> {
                        usedExplicitExtractor = true
                        com.lagradost.cloudstream3.extractors.Voe()
                            .getUrl(embedUrl, data, subtitleCallback, uniqueCallback)
                    }
                    host == "streamtape.com" || host.endsWith(".streamtape.com") -> {
                        usedExplicitExtractor = true
                        com.lagradost.cloudstream3.extractors.StreamTape()
                            .getUrl(embedUrl, data, subtitleCallback, uniqueCallback)
                    }
                    else -> loadExtractor(
                        embedUrl,
                        data,
                        subtitleCallback,
                        uniqueCallback
                    )
                }
            } catch (_: Exception) {
                // Satu server yang mati tidak boleh menghentikan server lainnya.
            }

            // Alias/host dapat berubah. Bila dispatcher eksplisit tidak memberi
            // hasil, registry umum CloudStream tetap mendapat kesempatan kedua.
            if (usedExplicitExtractor && emittedUrls.size == before) {
                try {
                    loadExtractor(embedUrl, data, subtitleCallback, uniqueCallback)
                } catch (_: Exception) {
                    // Lanjutkan ke server berikutnya.
                }
            }
        }

        // Jangan mengembalikan true hanya karena iframe ditemukan. True berarti
        // sekurangnya satu link media benar-benar berhasil dikirim ke CloudStream.
        return emittedUrls.isNotEmpty()
    }
}
