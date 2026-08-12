package com.agooseangsa.Sokuja

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

class Sokuja : MainAPI() {
    override var mainUrl = DEFAULT_MAIN_URL
    override var name = "Sokuja"
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val hasMainPage = true
    override val mainPage = mainPageOf(
        SECTION_ONGOING to "Ongoing Series",
        SECTION_COMPLETED to "Anime Completed",
        SECTION_POPULAR_WEEKLY to "Anime Populer Mingguan",
        SECTION_POPULAR_MONTHLY to "Anime Populer Bulanan",
        SECTION_POPULAR_ALL_TIME to "Anime Populer Sepanjang Masa",
    )

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false

    private val blockedCategoryKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_CATEGORIES.mapNotNull(::normalizeTaxonomyName).toSet()
    }
    private val blockedTagKeys by lazy(LazyThreadSafetyMode.NONE) {
        BLOCKED_TAGS.mapNotNull(::normalizeTaxonomyName).toSet()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        _a0()
        if (page > 1) return newHomePageResponse(request.name, emptyList(), false)

        val response = app.get(mainUrl)
        _a1(response.url)
        val items = _a2(response.document, request.data)
            .mapNotNull(::_a3)
            .distinctBy { it.url }
        return newHomePageResponse(request.name, items, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        _a0()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val encoded = URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        val response = app.get("$mainUrl/?s=$encoded")
        _a1(response.url)

        return response.document
            .select(_q9("rw/WAiQxOy9eX6GB4cfVrastEdxPlwXm8axB/xEoJrqjQNgeayUQaU5WqLy3s9yxpyYqzj3MA+7v4QfPTGlr6eoH0gst"))
            .mapNotNull(::_a3)
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        _a0()
        val initialUrl = rewriteProviderUrlToCurrentOrigin(url)
        val initialResponse = app.get(initialUrl)
        _a1(initialResponse.url)

        val seriesUrl = findSeriesUrlFromEpisode(initialResponse.document)
        val detailResponse = if (seriesUrl != null) app.get(seriesUrl) else initialResponse
        _a1(detailResponse.url)

        val detail = _a4(detailResponse.document, detailResponse.url)
        enforceContentAllowed(detail.genres, detail.tags)

        val tmdb = _b0(
            AgooseTmdbIdentity(
                tmdbId = detail.tmdbId,
                imdbId = detail.imdbId,
                originalTitle = detail.originalTitle,
                displayTitle = detail.title,
                year = detail.year,
                isTv = detail.type == TvType.TvSeries,
            ),
        )

        val finalPlot = tmdb?.overview?.takeIf { it.isNotBlank() } ?: detail.plot
        val finalTags = tmdb?.genres?.takeIf { it.isNotEmpty() } ?: detail.genres
        val finalScore = tmdb?.voteAverage?.let(Score::from10) ?: detail.score?.let(Score::from10)
        val finalDuration = tmdb?.runtimeMinutes ?: detail.durationMinutes
        val finalActors = tmdb?.actors
            ?.takeIf { it.isNotEmpty() }
            ?.map { ActorData(Actor(it.name, it.profileUrl)) }
        val trailers = (tmdb?.trailerUrls.orEmpty() + detail.trailerUrls).distinct()
        val finalStatus = mapTmdbShowStatus(tmdb?.status) ?: detail.showStatus

        return if (detail.type == TvType.Movie) {
            val movieData = detail.episodes.firstOrNull()?.url
                ?: throw ErrorLoadingException(_q9("hx7WH2s0BWdBVbG2ucjnrKk1HpIy2QTj4+8I9h91b/e3Bd4CJCABI00ao7qoid2v4jQVgXXIGQ=="))

            newMovieLoadResponse(detail.title, detail.canonicalUrl, TvType.Movie, movieData) {
                posterUrl = detail.posterUrl ?: tmdb?.posterUrl
                backgroundPosterUrl = detail.backgroundUrl ?: tmdb?.backdropUrl
                logoUrl = tmdb?.logoUrl
                year = detail.year ?: tmdb?.year
                plot = finalPlot
                tags = finalTags
                score = finalScore
                duration = finalDuration
                actors = finalActors
                contentRating = tmdb?.contentRating
                recommendations = detail.recommendations
                tmdb?.tmdbId?.let { addTMDbId(it.toString()) }
                (tmdb?.imdbId ?: detail.imdbId)?.let { addImdbId(it) }
                addTrailer(trailers)
            }
        } else {
            val episodes = detail.episodes.map { item ->
                newEpisode(item.url) {
                    name = item.name
                    episode = item.number
                }
            }

            newTvSeriesLoadResponse(detail.title, detail.canonicalUrl, TvType.TvSeries, episodes) {
                posterUrl = detail.posterUrl ?: tmdb?.posterUrl
                backgroundPosterUrl = detail.backgroundUrl ?: tmdb?.backdropUrl
                logoUrl = tmdb?.logoUrl
                year = detail.year ?: tmdb?.year
                plot = finalPlot
                tags = finalTags
                score = finalScore
                duration = finalDuration
                actors = finalActors
                showStatus = finalStatus
                contentRating = tmdb?.contentRating
                recommendations = detail.recommendations
                tmdb?.tmdbId?.let { addTMDbId(it.toString()) }
                (tmdb?.imdbId ?: detail.imdbId)?.let { addImdbId(it) }
                addTrailer(trailers)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        _a0()
        val episodeUrl = rewriteProviderUrlToCurrentOrigin(data)
        val response = app.get(episodeUrl)
        _a1(response.url)

        var emitted = false
        val seen = linkedSetOf<String>()

        response.document.select(_q9("tAfbCWsLEzVPZ+v/r4fBsaElL4BgzjA=")).forEach { element ->
            val mediaUrl = element.attr(_q9("owzMVnciAw==")).ifBlank { fixUrl(element.attr(_q9("sRzc"))) }
            if (isDirectMediaUrl(mediaUrl) && seen.add(mediaUrl)) {
                _a7(mediaUrl, episodeUrl, element.attr(_q9("pg/LDSkhFSZAU7Om")), callback)
                emitted = true
            }
        }
        if (emitted) return true

        val wrappers = response.document
            .select(_q9("ozXXHmE2PnpETrOvr9Kb7LEvH4Z4zEPu5qtQvAZpeqW7U+I="))
            .mapNotNull { anchor ->
                val href = anchor.attr(_q9("qhzaCg==")).trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                QualityWrapper(href, parseQualityLabel(anchor.text()))
            }
            .distinctBy { it.url }

        for (wrapper in wrappers) {
            val builtInMatched = runCatching {
                loadExtractor(wrapper.url, episodeUrl, subtitleCallback, callback)
            }.getOrDefault(false)
            if (builtInMatched) emitted = true

            val resolved = _a6(wrapper, episodeUrl)
            for (mediaUrl in resolved) {
                if (seen.add(mediaUrl)) {
                    _a7(mediaUrl, episodeUrl, wrapper.qualityLabel, callback)
                    emitted = true
                }
            }
        }

        return emitted
    }

    private suspend fun _a0() {
        if (mainUrlResolved) return
        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock

            val remoteCandidates = runCatching {
                JSONObject(app.get(MAIN_URL_JSON).text).readMainUrlCandidates()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidates + DEFAULT_MAIN_URL)
                .mapNotNull(::normalizeHttpBaseUrl)
                .distinct()

            for (candidate in candidates) {
                val response = runCatching { app.get(candidate) }.getOrNull() ?: continue
                if (!response.isSuccessful) continue
                val resolved = normalizeHttpBaseUrl(response.url) ?: continue
                mainUrl = resolved
                mainUrlResolved = true
                return@withLock
            }
            mainUrl = DEFAULT_MAIN_URL
        }
    }

    private fun _a1(responseUrl: String?) {
        normalizeHttpBaseUrl(responseUrl)?.let { mainUrl = it }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(REMOTE_CONFIG_KEY) ?: return emptyList()
        return (0 until array.length())
            .map { index -> array.optString(index) }
            .mapNotNull(::normalizeHttpBaseUrl)
            .distinct()
    }

    private fun normalizeHttpBaseUrl(url: String?): String? {
        val value = url?.trim()?.removeSuffix("/")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if ((scheme == _q9("qhrLHA==") || scheme == _q9("qhrLHHc=")) && !uri.host.isNullOrBlank()) {
                "$scheme://${uri.authority}"
            } else null
        }.getOrNull()
    }

    private fun rewriteProviderUrlToCurrentOrigin(url: String): String {
        return runCatching {
            val uri = URI(url)
            if (!uri.isAbsolute || uri.host.isNullOrBlank()) return@runCatching fixUrl(url)
            val defaultHost = URI(DEFAULT_MAIN_URL).host
            if (!uri.host.equals(defaultHost, ignoreCase = true) && !uri.host.endsWith(_q9("7B3QB3E6AWlZUQ=="))) {
                return@runCatching url
            }
            buildString {
                append(mainUrl)
                append(uri.rawPath ?: "/")
                uri.rawQuery?.let { append('?').append(it) }
            }
        }.getOrDefault(url)
    }

    private fun _a2(document: Document, heading: String): List<Element> {
        val h2 = document.select("h2").firstOrNull { it.text().trim().equals(heading, ignoreCase = true) }
            ?: return emptyList()
        var node: Element? = h2
        repeat(4) {
            node = node?.parent()
            val cards = node?.select(_q9("o0DYHmslEGlOVqi8t7PcsacmKs49zAPu7+EHz0xpa+nqB9ILLQ==")).orEmpty()
            if (cards.isNotEmpty()) return cards
        }
        return emptyList()
    }

    private fun _a3(element: Element): SearchResponse? {
        val href = element.attr(_q9("qhzaCg==")).trim().takeIf { it.isNotBlank() } ?: return null
        if (!href.startsWith(_q9("7Q/RBWk1Tw==")) && !href.contains(_q9("7Q/RBWk1Tw=="))) return null

        val title = element.selectFirst("h3")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: element.selectFirst(_q9("qwPYN2U8FBo="))?.attr(_q9("owLL"))?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = element.selectFirst(_q9("qwPYN3ciAxo="))?.attr(_q9("sRzc"))?.trim()?.takeIf { it.isNotBlank() }?.let { fixUrl(it) }
        val year = element.select("p").asSequence()
            .map { it.text().trim() }
            .mapNotNull { YEAR_REGEX.find(it)?.value?.toIntOrNull() }
            .firstOrNull()
        val score = element.select(_q9("sR7eAg==")).asSequence()
            .map { it.text() }
            .mapNotNull { SCORE_REGEX.find(it)?.groupValues?.getOrNull(1)?.toDoubleOrNull() }
            .firstOrNull()
        val badge = element.select(_q9("sR7eAg==")).asSequence().map { it.text().trim() }
            .firstOrNull { it.equals(_q9("jwHJBWE="), true) || it.equals("TV", true) }
        val cardUrl = fixUrl(href)

        return if (badge.equals(_q9("jwHJBWE="), ignoreCase = true)) {
            newMovieSearchResponse(title, cardUrl, TvType.Movie) {
                posterUrl = poster
                this.year = year
                this.score = Score.from10(score)
            }
        } else {
            newTvSeriesSearchResponse(title, cardUrl, TvType.TvSeries) {
                posterUrl = poster
                this.year = year
                this.score = Score.from10(score)
            }
        }
    }

    private fun findSeriesUrlFromEpisode(document: Document): String? {
        val anchor = document.select(_q9("ozXXHmE2PnoDW6m2sY2bng==")).firstOrNull {
            it.text().trim().equals(_q9("kQvSGWVwJTdFSai7uQ=="), ignoreCase = true)
        } ?: return null
        return fixUrl(anchor.attr(_q9("qhzaCg==")))
    }

    private fun _a4(document: Document, responseUrl: String): DetailData {
        val mediaJson = _a8(document)
        val canonicalUrl = document.selectFirst(_q9("rgfRB18iBSsRWaaxs4bdoKMsKah63wjh3w=="))?.attr(_q9("qhzaCg=="))
            ?.takeIf { it.isNotBlank() }
            ?: responseUrl
        val info = document.select("dt").associate { dt ->
            dt.text().trim().lowercase(Locale.ROOT) to (dt.nextElementSibling()?.text()?.trim().orEmpty())
        }

        val typeText = mediaJson?.optString(_q9("ghrGHGE=")).orEmpty().ifBlank { info[_q9("tgfPCQ==")].orEmpty() }
        val type = if (typeText.contains(_q9("rwHJBWE="), ignoreCase = true)) TvType.Movie else TvType.TvSeries
        val title = mediaJson?.optString(_q9("rA/SCQ=="))?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("h1")?.text()?.replace(SUBTITLE_SUFFIX, "")?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_q9("iBvbGWhwBCJYW66z/LvbqLcqFdNmxAnm6aRM+wJkZ++pD9E="))
        val year = mediaJson?.optString(_q9("pg/LCVQlAitFSa+6uA=="))?.take(4)?.toIntOrNull()
            ?: info[_q9("tg/XGWo=")]?.let { YEAR_REGEX.find(it)?.value?.toIntOrNull() }
        val poster = mediaJson?.optString(_q9("qwPeC2E="))?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(_q9("rwvLDV8gEihcX7WrpdXbpPgpGZJ1yDDc4etG5hNvfsc="))?.attr(_q9("oQHRGGE+FA=="))?.trim()?.takeIf { it.isNotBlank() }
        val synopsis = mediaJson?.optString(_q9("pgvMD3Y5EDNFVak="))?.trim()?.takeIf { it.isNotBlank() }
            ?: parseSynopsis(document)
        val genres = parseJsonLdStringList(mediaJson?.opt(_q9("pQvRHmE=")))
            .ifEmpty { document.select(_q9("ozXXHmE2PnoDXaKxro2bng==")).map { it.text().trim() }.filter(String::isNotBlank).distinct() }
        val score = mediaJson?.optJSONObject(_q9("ownYHmE3ATNJaKartYbT"))?.optDouble(_q9("sA/LBWo3NiZAT6I="))
            ?.takeIf { !it.isNaN() && it > 0.0 }
        val duration = parseDurationMinutes(info[_q9("phvNDXc5")])
        val showStatus = when {
            info[_q9("sRreGHEj")].equals(_q9("gQHSHGg1FCJI"), true) -> ShowStatus.Completed
            info[_q9("sRreGHEj")].equals(_q9("jQDYA20+Bw=="), true) -> ShowStatus.Ongoing
            else -> null
        }
        val episodes = _a5(document, type)
        val recommendations = _a9(document)
        val trailerUrls = parseDirectTrailerUrls(document)
        val tmdbId = parseTmdbId(document)
        val imdbId = parseImdbId(document)

        return DetailData(
            title = title,
            canonicalUrl = canonicalUrl,
            type = type,
            year = year,
            posterUrl = poster,
            backgroundUrl = null,
            plot = synopsis,
            genres = genres,
            tags = emptyList(),
            score = score,
            durationMinutes = duration,
            showStatus = showStatus,
            episodes = episodes,
            recommendations = recommendations,
            trailerUrls = trailerUrls,
            tmdbId = tmdbId,
            imdbId = imdbId,
            originalTitle = null,
        )
    }

    private fun _a8(document: Document): JSONObject? {
        return document.select(_q9("sQ3NBXQkOzNVSqLivZjEr6sjFYd7wgOo7uAD+AVuZMc=")).asSequence().mapNotNull { script ->
            val raw = script.data().ifBlank { script.html() }.trim()
            runCatching { JSONObject(raw) }.getOrNull()
        }.firstOrNull { json ->
            val type = json.optString(_q9("ghrGHGE="))
            type.equals(_q9("jwHJBWE="), true) || type.equals(_q9("ljjsCXY5BTQ="), true)
        }
    }

    private fun parseJsonLdStringList(value: Any?): List<String> {
        return when (value) {
            is JSONArray -> (0 until value.length()).mapNotNull { index ->
                value.optString(index).trim().takeIf { it.isNotBlank() }
            }
            is String -> value.split(',').map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }.distinct()
    }

    private fun parseSynopsis(document: Document): String? {
        val heading = document.select("h2").firstOrNull { it.text().trim().startsWith(_q9("kQfRA3QjCTQM")) }
            ?: return null
        return heading.parent()?.select("p")?.joinToString("\n\n") { it.text().trim() }
            ?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun _a5(document: Document, type: TvType): List<EpisodeItem> {
        val heading = document.select("h2").firstOrNull { it.text().trim().startsWith(_q9("hg/ZGGUiQAJcU7SwuI0=")) }
            ?: return emptyList()
        var node: Element? = heading
        var anchors: List<Element> = emptyList()
        for (depth in 0 until 4) {
            node = node?.parent()
            val candidates = node?.select(_q9("ozXXHmE2PQ==")).orEmpty().filter { anchor ->
                val href = anchor.attr(_q9("qhzaCg=="))
                !href.startsWith(_q9("7Q/RBWk1Tw==")) && href.contains(_q9("sRvdGG0kDCIBU6m7s4bRsKsh"))
            }
            if (candidates.isNotEmpty()) {
                anchors = candidates
                break
            }
        }

        return anchors.mapNotNull { anchor ->
            val href = anchor.attr(_q9("qhzaCg==")).trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val text = anchor.text().trim()
            val number = EPISODE_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: EPISODE_SLUG_REGEX.find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: if (type == TvType.Movie) 1 else null
                ?: return@mapNotNull null
            val name = if (type == TvType.Movie) {
                anchor.selectFirst("h3")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: _q9("jwHJBWE=")
            } else {
                "Episode $number"
            }
            EpisodeItem(name, number, fixUrl(href))
        }.distinctBy { it.url }.sortedBy { it.number }
    }

    private fun _a9(document: Document): List<SearchResponse> {
        val heading = document.select("h2").firstOrNull { it.text().trim().equals(_q9("gwDWAWFwNCJeUaa2qA=="), true) }
            ?: return emptyList()
        val container = heading.parent() ?: return emptyList()
        return container.select(_q9("o0DYHmslEGlOVqi8t7PcsacmKs49zAPu7+EHz0xpa+nqB9ILLQ=="))
            .mapNotNull(::_a3)
            .distinctBy { it.url }
    }

    private fun parseDirectTrailerUrls(document: Document): List<String> {
        return document.select(_q9("ozXXHmE2PQ==")).mapNotNull { anchor ->
            val href = anchor.attr(_q9("qhzaCg==")).trim()
            href.takeIf { YOUTUBE_URL_REGEX.containsMatchIn(it) }
        }.distinct()
    }

    private fun parseTmdbId(document: Document): Int? {
        val href = document.select(_q9("ozXXHmE2SnpYUqKys57dpqYiWpxgykLq7fJB91lcJrqjNdceYTZKelhSorKznt2mpiJanGDKQvP0q3U="))
            .firstOrNull()?.attr(_q9("qhzaCg==")) ?: return null
        return TMDB_ID_REGEX.find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun parseImdbId(document: Document): String? {
        val href = document.select(_q9("ozXXHmE2SnpFV6O98ovbru00HYd+yELz9tk=")).firstOrNull()?.attr(_q9("qhzaCg==")) ?: return null
        return IMDB_ID_REGEX.find(href)?.value
    }

    private fun parseDurationMinutes(value: String?): Int? {
        val text = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val hours = HOUR_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = MINUTE_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val total = hours * 60 + minutes
        return total.takeIf { it > 0 }
    }

    private fun mapTmdbShowStatus(value: String?): ShowStatus? {
        return when (value?.trim()?.lowercase(Locale.ROOT)) {
            _q9("pwDbCWA="), _q9("oQ/RD2E8BSM=") -> ShowStatus.Completed
            _q9("sAvLGXY+CSlLGrS6roHRsA=="), _q9("qwCfHHY/BDJPTq6wsg=="), _q9("sgLeAmo1BA=="), _q9("sgfTA3A=") -> ShowStatus.Ongoing
            else -> null
        }
    }

    private suspend fun _a6(wrapper: QualityWrapper, episodeUrl: String): List<String> {
        val response = runCatching {
            app.get(wrapper.url, referer = episodeUrl)
        }.getOrNull() ?: return emptyList()

        val media = linkedSetOf<String>()
        if (isDirectMediaUrl(response.url)) media += response.url

        response.document.select(_q9("tAfbCWsLEzVPZ+v/r4fBsaElL4BgzjCrouVz+gRkbMc=")).forEach { element ->
            val attr = if (element.hasAttr(_q9("sRzc"))) _q9("sRzc") else _q9("qhzaCg==")
            val absolute = element.attr("abs:$attr").ifBlank { element.attr(attr) }
            if (isDirectMediaUrl(absolute)) media += absolute
        }
        DIRECT_MEDIA_REGEX.findAll(response.text).forEach { match ->
            val candidate = match.value.replace("\\/", "/")
            if (isDirectMediaUrl(candidate)) media += candidate
        }
        return media.toList()
    }

    private suspend fun _a7(
        mediaUrl: String,
        mediaReferer: String,
        qualityLabel: String?,
        callback: (ExtractorLink) -> Unit,
    ) {
        val label = qualityLabel?.takeIf { it.isNotBlank() } ?: parseQualityLabel(mediaUrl)
        val quality = label?.let(::getQualityFromName) ?: Qualities.Unknown.value
        callback(
            newExtractorLink(
                name,
                listOfNotNull(name, label).joinToString(" "),
                mediaUrl,
            ) {
                referer = mediaReferer
                this.quality = quality
            },
        )
    }

    private fun parseQualityLabel(value: String): String? =
        QUALITY_REGEX.find(value)?.groupValues?.getOrNull(1)?.let { "${it}p" }

    private fun isDirectMediaUrl(url: String): Boolean =
        url.startsWith(_q9("qhrLHA=="), ignoreCase = true) && MEDIA_EXTENSION_REGEX.containsMatchIn(url)

    private fun shouldBlockContent(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ): Boolean {
        val categoryBlocked = categories.asSequence().mapNotNull(::normalizeTaxonomyName)
            .any { it in blockedCategoryKeys }
        if (categoryBlocked) return true
        return tags.asSequence().mapNotNull(::normalizeTaxonomyName).any { it in blockedTagKeys }
    }

    private fun enforceContentAllowed(
        categories: Iterable<String> = emptyList(),
        tags: Iterable<String> = emptyList(),
    ) {
        if (shouldBlockContent(categories, tags)) {
            throw ErrorLoadingException(_q9("iQHRGGE+QCNFWKuwt4HG460sEZsyxgLp5O1P5wRgefPiHs0DcjkEIl4="))
        }
    }

    private fun normalizeTaxonomyName(value: String?): String? = value
        ?.trim()
        ?.replace(WHITESPACE, " ")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase(Locale.ROOT)

    private data class EpisodeItem(val name: String, val number: Int, val url: String)
    private data class QualityWrapper(val url: String, val qualityLabel: String?)
    private data class DetailData(
        val title: String,
        val canonicalUrl: String,
        val type: TvType,
        val year: Int?,
        val posterUrl: String?,
        val backgroundUrl: String?,
        val plot: String?,
        val genres: List<String>,
        val tags: List<String>,
        val score: Double?,
        val durationMinutes: Int?,
        val showStatus: ShowStatus?,
        val episodes: List<EpisodeItem>,
        val recommendations: List<SearchResponse>,
        val trailerUrls: List<String>,
        val tmdbId: Int?,
        val imdbId: String?,
        val originalTitle: String?,
    )

    companion object {
        private val DEFAULT_MAIN_URL = _q9("qhrLHHdqT2hUDOmss4PBqaNuAZg=")
        private const val REMOTE_CONFIG_KEY = "Sokuja"
        private const val MAIN_URL_JSON =
            "https://raw.githubusercontent.com/mj1Per127/agoosecloudstream/main/Website.json"

        private const val SECTION_ONGOING = "Ongoing Series"
        private const val SECTION_COMPLETED = "Anime Completed"
        private const val SECTION_POPULAR_WEEKLY = "Anime Populer Mingguan"
        private const val SECTION_POPULAR_MONTHLY = "Anime Populer Bulanan"
        private const val SECTION_POPULAR_ALL_TIME = "Anime Populer Sepanjang Masa"

        private val BLOCKED_CATEGORIES = emptySet<String>()
        private val BLOCKED_TAGS = emptySet<String>()

        private val YEAR_REGEX = Regex(_q9("6lGFXT0sUncFZqOk7pU="))
        private val SCORE_REGEX = Regex(_q9("IPY6MHd6SBwcF/6C98CL+Z5uL8M/lDCsq7sB"))
        private val EPISODE_REGEX = Regex(_q9("hx7WH2s0BRtfEe+DuMOd"), RegexOption.IGNORE_CASE)
        private val EPISODE_SLUG_REGEX = Regex(_q9("px7WH2s0BWoEZqP09cU="), RegexOption.IGNORE_CASE)
        private val QUALITY_REGEX = Regex(_q9("6l2JXHhkWHdQD/PvoN+G875xRMsi0VyztrRUoEc3OrOy"), RegexOption.IGNORE_CASE)
        private val HOUR_REGEX = Regex(_q9("6jLbRy0ME20EBf23rpTcrLcyCJlzwEQ="), RegexOption.IGNORE_CASE)
        private val MINUTE_REGEX = Regex(_q9("6jLbRy0ME20EBf2ytYbIrqsuAYd30QDi7O1cuw=="), RegexOption.IGNORE_CASE)
        private val MEDIA_EXTENSION_REGEX = Regex(_q9("nkCXUz49EHNQV/Sq5MGc/PgbS9BP0Umu"), RegexOption.IGNORE_CASE)
        private val DIRECT_MEDIA_REGEX = Regex(_q9("qhrLHHdvWmgDYZmDr7SW5P5+KdhOg0W4uOlYpgpsOe/6R5dTPgxfHHJmtIP+z4j9n2pdzA=="), RegexOption.IGNORE_CASE)
        private val YOUTUBE_URL_REGEX = Regex(_q9("qhrLHHdvWmgDEvjlq5/Dn+xpS9stlxTo9/Bd8BNdJPmtA5AbZSQDL3AFseKgkdu2tjUo3XDIQq4="), RegexOption.IGNORE_CASE)
        private val TMDB_ID_REGEX = Regex(_q9("tgbaAWsmCSJIWJvxs5rT7Op/Tp592wTi/vBeu1kpVv7pRw=="), RegexOption.IGNORE_CASE)
        private val IMDB_ID_REGEX = Regex(_q9("thrjCC8="))
        private val SUBTITLE_SUFFIX = Regex(_q9("nh2UP3EyFC5YVqL/lYbQrKwlB5pziQ=="), RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex(_q9("nh2U"))
    }
}
