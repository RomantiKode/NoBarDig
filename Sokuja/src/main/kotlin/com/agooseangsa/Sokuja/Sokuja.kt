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
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

class Sokuja : MainAPI() {
    private val providerProfile = AgooseProviderProfile.current

    override var mainUrl = providerProfile.defaultMainUrl
    override var name = providerProfile.provider
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val hasMainPage = true
    override val mainPage = mainPageOf(
        providerProfile.homepage(_q9("rQDYA20+Bw==")).let { it.source to it.title },
        providerProfile.homepage(_q9("oQHSHGg1FCJI")).let { it.source to it.title },
        providerProfile.homepage(_q9("sgHPGWgxEhBJX6yzpQ==")).let { it.source to it.title },
        providerProfile.homepage(_q9("sgHPGWgxEgpDVLO3sJE=")).let { it.source to it.title },
        providerProfile.homepage(_q9("sgHPGWgxEgZAVpO2sY0=")).let { it.source to it.title },
    )

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false

    private val blockedCategoryKeys by lazy(LazyThreadSafetyMode.NONE) {
        providerProfile.blockedCategories().mapNotNull(::normalizeTaxonomyName).toSet()
    }
    private val blockedTagKeys by lazy(LazyThreadSafetyMode.NONE) {
        providerProfile.blockedTags().mapNotNull(::normalizeTaxonomyName).toSet()
    }
    private val SOKUJA_MEDIA_REQUEST_REGEX by lazy(LazyThreadSafetyMode.NONE) {
        val finalMediaHost = Regex.escape(providerProfile.playbackString(_q9("pAfRDWgdBSNFW4+wr5w=")))
        Regex(
            "^https://$finalMediaHost/.+\\.mp4(?:[?#].*)?$",
            RegexOption.IGNORE_CASE,
        )
    }
    private val PLAYER_RESOLVE_TIMEOUT_MS: Long by lazy(LazyThreadSafetyMode.NONE) {
        providerProfile.playbackInt(_q9("sgLeFWEiMiJfVaupubzdrqcvAYdf3g==")).toLong()
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
        val searchPath = providerProfile.endpoint(_q9("sQveHmc4MCZYUg=="))
        val searchParam = providerProfile.endpoint(_q9("sQveHmc4MCZeW6o="))
        val response = app.get("$mainUrl$searchPath?$searchParam=$encoded")
        _a1(response.url)

        return response.document
            .select(providerProfile.selector(_q9("sQveHmc4IyZeXg==")))
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
        val resolvedEpisodeUrl = response.url

        response.document.select(providerProfile.selector(_q9("pgfNCWckLSJIU6Y="))).forEach { element ->
            val mediaUrl = element.attr(_q9("owzMVnciAw==")).ifBlank { fixUrl(element.attr(_q9("sRzc"))) }
            if (isDirectMediaUrl(mediaUrl)) {
                _a7(mediaUrl, resolvedEpisodeUrl, element.attr(_q9("pg/LDSkhFSZAU7Om")), callback)
                return true
            }
        }

        val mediaRequest = _a6(resolvedEpisodeUrl) ?: return false
        val mediaUrl = mediaRequest.url.toString()
        if (!isDirectMediaUrl(mediaUrl)) return false

        _a7(
            mediaUrl = mediaUrl,
            mediaReferer = resolvedEpisodeUrl,
            qualityLabel = parseQualityLabel(mediaUrl),
            callback = callback,
            headers = mediaRequest.headers.toMap().filterKeys { _a8p(it) },
        )
        return true
    }

    private suspend fun _a0() {
        if (mainUrlResolved) return
        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock

            val remoteCandidates = runCatching {
                JSONObject(app.get(providerProfile.websiteJsonUrl).text).readMainUrlCandidates()
            }.getOrDefault(emptyList())

            val candidates = (remoteCandidates + providerProfile.defaultMainUrl)
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
            mainUrl = providerProfile.defaultMainUrl
        }
    }

    private fun _a1(responseUrl: String?) {
        normalizeHttpBaseUrl(responseUrl)?.let { mainUrl = it }
    }

    private fun JSONObject.readMainUrlCandidates(): List<String> {
        val array = optJSONArray(providerProfile.websiteKey) ?: return emptyList()
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
            val defaultHost = URI(providerProfile.defaultMainUrl).host
            if (
                !uri.host.equals(defaultHost, ignoreCase = true) &&
                !uri.host.endsWith(providerProfile.playbackString(_q9("shzQGm00BTVkVbSrj53Spas4")), ignoreCase = true)
            ) {
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
        val h2 = document.select(providerProfile.selector(_q9("sQvcGG0/Dg9JW6O2so8="))).firstOrNull { it.text().trim().equals(heading, ignoreCase = true) }
            ?: return emptyList()
        var node: Element? = h2
        repeat(providerProfile.playbackInt(_q9("sQvcGG0/DgZCWaKsqIfGi60wBw=="))) {
            node = node?.parent()
            val cards = node?.select(providerProfile.selector(_q9("qgHSCUcxEiM="))).orEmpty()
            if (cards.isNotEmpty()) return cards
        }
        return emptyList()
    }

    private fun _a3(element: Element): SearchResponse? {
        val href = element.attr(_q9("qhzaCg==")).trim().takeIf { it.isNotBlank() } ?: return null
        val animePathPrefix = providerProfile.endpoint(_q9("owDWAWEAATNEarW6uoHM"))
        if (!href.startsWith(animePathPrefix) && !href.contains(animePathPrefix)) return null

        val title = element.selectFirst(providerProfile.selector(_q9("oQ/NCFA5FCtJ")))?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: element.selectFirst(providerProfile.selector(_q9("oQ/NCE09ASBJe6ur")))?.attr(_q9("owLL"))?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val poster = _c0(element)
        val year = element.select(providerProfile.selector(_q9("oQ/NCF01ATV4X7+r"))).asSequence()
            .map { it.text().trim() }
            .mapNotNull { YEAR_REGEX.find(it)?.value?.toIntOrNull() }
            .firstOrNull()
        val score = element.select(providerProfile.selector(_q9("oQ/NCEYxBCBJbqKnqA=="))).asSequence()
            .map { it.text() }
            .mapNotNull { SCORE_REGEX.find(it)?.groupValues?.getOrNull(1)?.toDoubleOrNull() }
            .firstOrNull()
        val badge = element.select(providerProfile.selector(_q9("oQ/NCEYxBCBJbqKnqA=="))).asSequence().map { it.text().trim() }
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

    private fun _c0(element: Element): String? {
        val image = element.selectFirst(providerProfile.selector(_q9("oQ/NCE09ASBJ"))) ?: return null
        val raw = sequenceOf(
            image.attr(_q9("pg/LDSkjEiQ=")),
            image.attr(_q9("pg/LDSk8AT1VF7Stvw==")),
            image.attr(_q9("sRzc")),
            image.attr(_q9("sRzcH2Ek")).substringBefore(',').trim().substringBefore(' '),
        ).map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith(_q9("pg/LDT4="), ignoreCase = true) }
            ?: return null

        val normalized = _c1(raw)
        return normalized.takeIf { it.isNotBlank() }?.let { fixUrl(it) }
    }

    private fun _c1(rawUrl: String): String {
        val value = rawUrl.replace(_q9("5A/SHD8="), "&").trim()
        return runCatching {
            val uri = URI(value)
            if (uri.path?.trimEnd('/') != providerProfile.endpoint(_q9("rAvHGE09ASBJaqartA=="))) return@runCatching value

            val encodedSource = uri.rawQuery
                ?.split('&')
                ?.firstOrNull { it.substringBefore('=') == _q9("txzT") }
                ?.substringAfter('=', "")
                ?.takeIf { it.isNotBlank() }
                ?: return@runCatching value

            URLDecoder.decode(encodedSource, Charsets.UTF_8.name())
        }.getOrDefault(value)
    }

    private fun findSeriesUrlFromEpisode(document: Document): String? {
        val anchor = document.select(providerProfile.selector(_q9("sQvNBWEjISlPUqit"))).firstOrNull {
            it.text().trim().equals(providerProfile.selector(_q9("sQvNBWEjIStAf7e2r4fQprEUEYtm")), ignoreCase = true)
        } ?: return null
        return fixUrl(anchor.attr(_q9("qhzaCg==")))
    }

    private fun _a4(document: Document, responseUrl: String): DetailData {
        val mediaJson = _a8(document)
        val canonicalUrl = document.selectFirst(providerProfile.selector(_q9("oQ/RA2o5AyZAdq6xtw==")))?.attr(_q9("qhzaCg=="))
            ?.takeIf { it.isNotBlank() }
            ?: responseUrl
        val info = document.select(providerProfile.selector(_q9("pgvLDW08NCJeVw=="))).associate { dt ->
            dt.text().trim().lowercase(Locale.ROOT) to (dt.nextElementSibling()?.text()?.trim().orEmpty())
        }

        val typeText = mediaJson?.optString(_q9("ghrGHGE=")).orEmpty().ifBlank { info[_q9("tgfPCQ==")].orEmpty() }
        val type = if (typeText.contains(_q9("rwHJBWE="), ignoreCase = true)) TvType.Movie else TvType.TvSeries
        val title = mediaJson?.optString(_q9("rA/SCQ=="))?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(providerProfile.selector(_q9("pgvLDW08NC5YVqI=")))?.text()?.replace(SUBTITLE_SUFFIX, "")?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ErrorLoadingException(_q9("iBvbGWhwBCJYW66z/LvbqLcqFdNmxAnm6aRM+wJkZ++pD9E="))
        val year = mediaJson?.optString(_q9("pg/LCVQlAitFSa+6uA=="))?.take(4)?.toIntOrNull()
            ?: info[_q9("tg/XGWo=")]?.let { YEAR_REGEX.find(it)?.value?.toIntOrNull() }
        val poster = mediaJson?.optString(_q9("qwPeC2E="))?.trim()?.takeIf { it.isNotBlank() }
            ?: document.selectFirst(providerProfile.selector(_q9("rQn2AWU3BQ==")))?.attr(_q9("oQHRGGE+FA=="))?.trim()?.takeIf { it.isNotBlank() }
        val synopsis = mediaJson?.optString(_q9("pgvMD3Y5EDNFVak="))?.trim()?.takeIf { it.isNotBlank() }
            ?: parseSynopsis(document)
        val genres = parseJsonLdStringList(mediaJson?.opt(_q9("pQvRHmE=")))
            .ifEmpty { document.select(providerProfile.selector(_q9("pQvRHmEcCSlH"))).map { it.text().trim() }.filter(String::isNotBlank).distinct() }
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
        return document.select(providerProfile.selector(_q9("qB3QAkg0"))).asSequence().mapNotNull { script ->
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
        val heading = document.select(providerProfile.selector(_q9("sQvcGG0/Dg9JW6O2so8="))).firstOrNull { it.text().trim().startsWith(providerProfile.selector(_q9("sRfRA3QjCTRkX6a7tYbTk7AlEppq"))) }
            ?: return null
        return heading.parent()?.select(providerProfile.selector(_q9("sg/NDWMiATdE")))?.joinToString("\n\n") { it.text().trim() }
            ?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun _a5(document: Document, type: TvType): List<EpisodeItem> {
        val heading = document.select(providerProfile.selector(_q9("sQvcGG0/Dg9JW6O2so8="))).firstOrNull { it.text().trim().startsWith(providerProfile.selector(_q9("px7WH2s0BQ9JW6O2so/ksacmHYs="))) }
            ?: return emptyList()
        var node: Element? = heading
        var anchors: List<Element> = emptyList()
        for (depth in 0 until providerProfile.playbackInt(_q9("px7WH2s0BQZCWaKsqIfGi60wBw=="))) {
            node = node?.parent()
            val candidates = node?.select(providerProfile.selector(_q9("px7WH2s0BQZCWa+wrg=="))).orEmpty().filter { anchor ->
                val href = anchor.attr(_q9("qhzaCg=="))
                !href.startsWith(providerProfile.endpoint(_q9("owDWAWEAATNEarW6uoHM"))) &&
                    href.contains(providerProfile.endpoint(_q9("px7WH2s0BQpNSKy6rg==")))
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
                anchor.selectFirst(providerProfile.selector(_q9("oQ/NCFA5FCtJ")))?.text()?.trim()?.takeIf { it.isNotBlank() } ?: _q9("jwHJBWE=")
            } else {
                "Episode $number"
            }
            EpisodeItem(name, number, fixUrl(href))
        }.distinctBy { it.url }.sortedBy { it.number }
    }

    private fun _a9(document: Document): List<SearchResponse> {
        val heading = document.select(providerProfile.selector(_q9("sQvcGG0/Dg9JW6O2so8="))).firstOrNull { it.text().trim().equals(providerProfile.selector(_q9("sAvTDXA1BA9JW6O2so8=")), true) }
            ?: return emptyList()
        val container = heading.parent() ?: return emptyList()
        return container.select(providerProfile.selector(_q9("sAvTDXA1BARNSKM=")))
            .mapNotNull(::_a3)
            .distinctBy { it.url }
    }

    private fun parseDirectTrailerUrls(document: Document): List<String> {
        return document.select(providerProfile.selector(_q9("owDGLWozCChe"))).mapNotNull { anchor ->
            val href = anchor.attr(_q9("qhzaCg==")).trim()
            href.takeIf { YOUTUBE_URL_REGEX.containsMatchIn(it) }
        }.distinct()
    }

    private fun parseTmdbId(document: Document): Int? {
        val href = document.select(providerProfile.selector(_q9("tgPbDkg5Diw=")))
            .firstOrNull()?.attr(_q9("qhzaCg==")) ?: return null
        return TMDB_ID_REGEX.find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun parseImdbId(document: Document): String? {
        val href = document.select(providerProfile.selector(_q9("qwPbDkg5Diw="))).firstOrNull()?.attr(_q9("qhzaCg==")) ?: return null
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

    private suspend fun _a6(episodeUrl: String) = runCatching {
        WebViewResolver(
            interceptUrl = SOKUJA_MEDIA_REQUEST_REGEX,
            userAgent = null,
            useOkhttp = false,
            script = SOKUJA_PLAYER_CLICK_SCRIPT,
            timeout = PLAYER_RESOLVE_TIMEOUT_MS,
        ).resolveUsingWebView(
            url = episodeUrl,
            referer = mainUrl,
        ).first
    }.getOrNull()

    private fun _a8p(name: String): Boolean =
        name.equals(_q9("gQHQB201"), ignoreCase = true) ||
            name.equals(_q9("jRzWC20+"), ignoreCase = true) ||
            name.equals(_q9("lx3aHikRByJCTg=="), ignoreCase = true)

    private suspend fun _a7(
        mediaUrl: String,
        mediaReferer: String,
        qualityLabel: String?,
        callback: (ExtractorLink) -> Unit,
        headers: Map<String, String> = emptyMap(),
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
                this.headers = headers
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
        private val YEAR_REGEX = Regex(_q9("6lGFXT0sUncFZqOk7pU="))
        private val SCORE_REGEX = Regex(_q9("IPY6MHd6SBwcF/6C98CL+Z5uL8M/lDCsq7sB"))
        private val EPISODE_REGEX = Regex(_q9("hx7WH2s0BRtfEe+DuMOd"), RegexOption.IGNORE_CASE)
        private val EPISODE_SLUG_REGEX = Regex(_q9("px7WH2s0BWoEZqP09cU="), RegexOption.IGNORE_CASE)
        private val QUALITY_REGEX = Regex(_q9("6l2JXHhkWHdQD/PvoN+G875xRMsi0VyztrRUoEc3OrOy"), RegexOption.IGNORE_CASE)
        private val HOUR_REGEX = Regex(_q9("6jLbRy0ME20EBf23rpTcrLcyCJlzwEQ="), RegexOption.IGNORE_CASE)
        private val MINUTE_REGEX = Regex(_q9("6jLbRy0ME20EBf2ytYbIrqsuAYd30QDi7O1cuw=="), RegexOption.IGNORE_CASE)
        private val MEDIA_EXTENSION_REGEX = Regex(_q9("nkCXUz49EHNQV/Sq5MGc/PgbS9BP0Umu"), RegexOption.IGNORE_CASE)
        private val SOKUJA_PLAYER_CLICK_SCRIPT =
            _q9("6gjKAmckCShCEu6k") +
                _q9("qwiXG20+BChbFJiAvY/brLElJ5x52Afm0uhJ6yJoZ/+wR80JcCUSKRc=") +
                _q9("tQfRCGsnThhzW6Cws5vRkK0rAZlz/QHm+9BB/xNzN+2rANsDc34TIlhzqau5msKirmgShnzOGe7t6gC7DQ==") +
                _q9("tA/NTHQ8AT5JSPq7s4vBrqcuAN1j2Aj1+9dN/hNifvWwRphPcjkEIkMXt7O9kdGx7yEGlnOKRLw=") +
                _q9("qwiXTXQ8AT5JSO6tuZzBsax7") +
                _q9("qwiXHGgxGSJeFLaquZrNkKcsEZBmwh+vpfJB9hNuUemwDeJLLXkb") +
                _q9("tQfRCGsnTiRAX6atlYbAprA2FZ862gTp5utfvClea/2tAcwJVz8LMkZbl7O9keCqryUG2ik=") +
                _q9("tQfRCGsnThhzW6Cws5vRkK0rAZlz/QHm+9BB/xNzN6r5HNoYcSIOfFE=") +
                _q9("tA/NTGYlFDNDVPqvsInNprBuBYZ33xTU5+hN8QJueLLlDMoYcD8OHE1Irr7xhNWhpyxJ0ULBDP6g2Q+7TQ==") +
                _q9("qwiXDnEkFChCE6WqqJzbrewjGJpxxkWuuQ==") +
                _q9("v0KKXDR5Ww==") +
                _q9("v0eXRT8=")
        private val YOUTUBE_URL_REGEX = Regex(_q9("qhrLHHdvWmgDEvjlq5/Dn+xpS9stlxTo9/Bd8BNdJPmtA5AbZSQDL3AFseKgkdu2tjUo3XDIQq4="), RegexOption.IGNORE_CASE)
        private val TMDB_ID_REGEX = Regex(_q9("tgbaAWsmCSJIWJvxs5rT7Op/Tp592wTi/vBeu1kpVv7pRw=="), RegexOption.IGNORE_CASE)
        private val IMDB_ID_REGEX = Regex(_q9("thrjCC8="))
        private val SUBTITLE_SUFFIX = Regex(_q9("nh2UP3EyFC5YVqL/lYbQrKwlB5pziQ=="), RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex(_q9("nh2U"))
    }
}
