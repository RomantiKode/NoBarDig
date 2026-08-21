package com.agooseangsa.AnimeXin

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

class AnimeXin : MainAPI() {
    private val providerProfile = AgooseProviderProfile.current

    override var mainUrl = providerProfile.defaultMainUrl
    override var name = _q9("49gVQ0MfI3xN")
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override val hasMainPage = true
    override val mainPage = providerProfile.homepage.map { MainPageData(it.title, it.key, false) }

    private val mainUrlMutex = Mutex()
    private var mainUrlResolved = false

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        ensureMainUrl()
        if (page > 1 && request.data != _q9("ztcIS1VL")) return newHomePageResponse(request, emptyList(), false)

        val path = if (page <= 1) "/" else "/page/$page/"
        val response = app.get("$mainUrl$path")
        syncMainUrl(response.url)
        val selector = when (request.data) {
            _q9("0tkMW0peCQ==") -> providerProfile.selector(_q9("ytkRS3ZQC2BP14g="), _q9("jNoVXVJKC3ENxpVjZpQh8NfWja5gQYplfG/b"))
            _q9("ztcIS1VL") -> providerProfile.selector(_q9("ytkRS2peD3BQwg=="), _q9("jNoVXVJKC3EN2JVhfpksoorYl7I="))
            _q9("0NMfQUtSHntH1456fJY=") -> providerProfile.selector(_q9("ytkRS3RaGHpO2599d5k068vU"), _q9("jMUZXE9aCDhE05QzPZQp8dDPlK4lHcg4Zg=="))
            else -> return newHomePageResponse(request, emptyList(), false)
        }
        val cards = response.document.select(selector).mapNotNull(::toSearchResponse).distinctBy { it.url }
        val hasNext = request.data == _q9("ztcIS1VL") && response.document.selectFirst(_q9("w5gSS15LVWVC0Z8+fY0t4MHIl+YlHdoqeXXNpm0U/NiC11JAQ0cP")) != null
        return newHomePageResponse(request, cards, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureMainUrl()
        val param = providerProfile.endpoint(_q9("0dMdXEVXK3RR15c="), "s")
        val encoded = URLEncoder.encode(query.trim(), _q9("9+I6Ax4="))
        val response = app.get("$mainUrl/?$param=$encoded")
        syncMainUrl(response.url)
        return response.document
            .select(providerProfile.selector(_q9("wdcOSg=="), _q9("jNQPVg==")))
            .mapNotNull(::toSearchResponse)
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureMainUrl()
        val response = app.get(migrateDetailUrl(url))
        syncMainUrl(response.url)
        val document = response.document
        val localMeta = readDetail(document)
        if (localMeta.title.isBlank()) throw ErrorLoadingException(_q9("49gVQ0NnEnsZlpBmd40sosDfkKtsX4o/d3jCrDkZ+sLH2wlFR1E="))

        if (localMeta.type.equals(_q9("79kKR0M="), ignoreCase = true)) {
            return newMovieLoadResponse(localMeta.title, response.url, TvType.AnimeMovie, response.url) {
                posterUrl = localMeta.poster
                year = localMeta.year
                plot = localMeta.plot
                tags = localMeta.genres
                duration = localMeta.durationMinutes
                addScore(localMeta.rating)
                localMeta.trailer?.let { addTrailer(it, response.url) }
                uniqueUrl = persistentIdentity(response.url)
            }
        }

        val seriesResponse = if (document.select(providerProfile.selector(_q9("0dMOR0NMPmVKxZV3dos="), _q9("jNMMQk9MD3BRlo9/M5Qp"))).isNotEmpty()) {
            response
        } else {
            val allEpisodesUrl = findAllEpisodesUrl(document)
                ?: throw ErrorLoadingException(_q9("49gVQ0NnEnsZlo5yZowh7IT7iKYldtoibXPHompd59/G1xcOQlYPcE7DkXJ9"))
            app.get(allEpisodesUrl).also { syncMainUrl(it.url) }
        }
        val seriesDoc = seriesResponse.document
        val seriesMeta = readDetail(seriesDoc).mergeMissing(localMeta)
        val episodes = parseEpisodes(seriesDoc)
        if (episodes.isEmpty()) throw ErrorLoadingException(_q9("49gVQ0NnEnsZlp5ydYwh8ITflKN2XM4uPmjKo3gWs9LLwhlDU1Qaew=="))

        return newAnimeLoadResponse(seriesMeta.title, seriesResponse.url, TvType.Anime) {
            engName = seriesMeta.title
            japName = seriesMeta.altTitle
            posterUrl = seriesMeta.poster
            year = seriesMeta.year
            plot = seriesMeta.plot
            tags = seriesMeta.genres
            duration = seriesMeta.durationMinutes
            showStatus = when (seriesMeta.status.lowercase()) {
                _q9("wdkRXkpaD3BH") -> ShowStatus.Completed
                _q9("zdgbQU9RHA==") -> ShowStatus.Ongoing
                else -> null
            }
            addScore(seriesMeta.rating)
            addEpisodes(DubStatus.Subbed, episodes)
            seriesMeta.trailer?.let { addTrailer(it, seriesResponse.url) }
            uniqueUrl = persistentIdentity(seriesResponse.url)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        ensureMainUrl()
        val response = app.get(migrateDetailUrl(data))
        syncMainUrl(response.url)
        val candidates = _d7s(response.document)
        if (candidates.isEmpty()) return false

        var emitted = false
        for (candidate in candidates) {
            var candidateEmitted = false
            withTimeoutOrNull(providerProfile.serverResolveTimeoutMs.toLong()) {
                loadExtractor(
                    candidate.url,
                    response.url,
                    subtitleCallback,
                ) { link ->
                    candidateEmitted = true
                    emitted = true
                    callback(link)
                }
            }
            if (providerProfile.sourceMode == AgooseSourceMode.FIRST_SUCCESS && candidateEmitted) break
        }
        return emitted
    }

    private fun toSearchResponse(card: Element): SearchResponse? {
        val anchor = card.selectFirst(providerProfile.selector(_q9("wdcOSmpWFX4="), _q9("w+0UXENZJg=="))) ?: return null
        val href = anchor.absUrl(_q9("ysQZSA==")).ifBlank { anchor.attr(_q9("ysQZSA==")) }.takeIf { it.isNotBlank() } ?: return null
        val title = card.selectFirst(_q9("jNMbSVJWD3lG"))?.text()?.trim().takeUnless { it.isNullOrBlank() }
            ?: card.selectFirst(_q9("jMII"))?.ownText()?.trim().takeUnless { it.isNullOrBlank() }
            ?: card.selectFirst(_q9("jMIIDk4N"))?.text()?.trim().takeUnless { it.isNullOrBlank() }
            ?: anchor.attr(_q9("1t8IQkM=")).trim().takeIf { it.isNotBlank() }
            ?: return null
        val rawType = card.selectFirst(_q9("jMIFXkNFVzUN0510Z4Ew5w=="))?.text().orEmpty()
        val type = if (rawType.contains(_q9("z9kKR0M="), true) || MOVIE_WORD.containsMatchIn(title)) TvType.AnimeMovie else TvType.Anime
        val poster = card.selectFirst(providerProfile.selector(_q9("wdcOSnZQCGFGxA=="), _q9("y9sb")))?.let(::imageUrl)
        val episode = card.selectFirst(_q9("jNMMVgofVXBE0Z9jeosv5sE="))?.text()?.let(::firstInt)
        return newAnimeSearchResponse(title, href, type) {
            posterUrl = poster
            addDubStatus(DubStatus.Subbed, episode)
        }
    }

    private fun parseEpisodes(document: Document): List<Episode> = document
        .select(providerProfile.selector(_q9("0dMOR0NMPmVKxZV3dos="), _q9("jNMMQk9MD3BRlo9/M5Qp")))
        .mapNotNull { item ->
            val anchor = item.selectFirst(providerProfile.selector(_q9("x8YVXUlbHllK2JE="), _q9("w+0UXENZJg=="))) ?: return@mapNotNull null
            val href = anchor.absUrl(_q9("ysQZSA==")).ifBlank { anchor.attr(_q9("ysQZSA==")) }
            if (href.isBlank()) return@mapNotNull null
            val number = firstInt(item.selectFirst(providerProfile.selector(_q9("x8YVXUlbHltW25h2YQ=="), _q9("jNMMQgtRDng=")))?.text())
            val title = item.selectFirst(providerProfile.selector(_q9("x8YVXUlbHkFKwpZ2"), _q9("jNMMQgtLEmFP0w==")))?.text()?.trim()
            val date = item.selectFirst(providerProfile.selector(_q9("x8YVXUlbHlFCwp8="), _q9("jNMMQgtbGmFG")))?.text()?.trim()
            newEpisode(href) {
                name = title
                episode = number
                season = 1
                if (!date.isNullOrBlank()) addDate(date, _q9("7/sxYwZbVzVaz4Nq"))
            }
        }
        .sortedBy { it.episode ?: Int.MAX_VALUE }

    private fun readDetail(document: Document): DetailMeta {
        val info = document.selectFirst(providerProfile.selector(_q9("xtMIT09TKHBR359gWpYm7Q=="), _q9("jNQVSUVQFWFG2I4zPZEu5MvC")))
            ?: document.selectFirst(providerProfile.selector(_q9("xtMIT09TPmVKxZV3drEu5Ms="), _q9("jMUVQEFTHjhK2Jx8M9Yp7MLVnA==")))
        val title = info?.selectFirst(_q9("yodQDghWFXNM2pN+eoxg6pY="))?.text()?.trim().orEmpty()
        val alt = info?.selectFirst(providerProfile.selector(_q9("xtMIT09TOnlX4pNnf50="), _q9("jNcQWkNN")))?.text()?.trim()
        val meta = info?.select(providerProfile.selector(_q9("xtMIT09TNnBX155yZ5k="), _q9("jMUMSwZMC3RN")))
            ?.associate { span ->
                val key = span.selectFirst("b")?.text()?.removeSuffix(":")?.trim().orEmpty()
                key.lowercase() to span.text().substringAfter(":", span.text()).trim()
            }.orEmpty()
        val rating = info?.selectFirst(_q9("jMQdWk9RHDVQwoh8fZ8="))?.text()?.substringAfter(_q9("8NcIR0hY"))?.trim()
        val genres = info?.select(providerProfile.selector(_q9("xtMIT09TPHBNxJ9g"), _q9("jNEZQF5aHzVC")))?.map { it.text().trim() }?.filter { it.isNotBlank() }.orEmpty()
        val poster = document.selectFirst(providerProfile.selector(_q9("xtMIT09TK3pQwp9h"), _q9("jNQVSUVQFWFG2I4zPYwo98nYxKNoVIZrMG/KqX4R9pvL2BpBBhEPfVbbmDN6lSc=")))?.let(::imageUrl)
        val rawDescription = info?.selectFirst(_q9("jNIZXUURFnxN0p9g"))?.wholeText()
            ?: document.selectFirst(_q9("jNMSWlRGVnZM2I52fYwb69Dfibp3XNp2ennQpGsU48LL2RJz"))?.wholeText()
        val plot = _i8p(rawDescription, title)
        val released = meta[_q9("0NMQS0dMHnE=")].orEmpty()
        val duration = meta[_q9("xsMOT1JWFHs=")].orEmpty()
        return DetailMeta(
            title = title,
            altTitle = alt,
            type = meta[_q9("1s8MSw==")].orEmpty(),
            status = meta[_q9("0cIdWlNM")].orEmpty(),
            year = YEAR.find(released)?.value?.toIntOrNull(),
            durationMinutes = parseDurationMinutes(duration),
            rating = rating,
            genres = genres,
            poster = poster,
            plot = plot,
            trailer = findTrailer(document),
        )
    }

    private fun DetailMeta.mergeMissing(other: DetailMeta): DetailMeta = copy(
        title = title.ifBlank { other.title },
        altTitle = altTitle ?: other.altTitle,
        type = type.ifBlank { other.type },
        status = status.ifBlank { other.status },
        year = year ?: other.year,
        durationMinutes = durationMinutes ?: other.durationMinutes,
        rating = rating ?: other.rating,
        genres = genres.ifEmpty { other.genres },
        poster = poster ?: other.poster,
        plot = plot ?: other.plot,
        trailer = trailer ?: other.trailer,
    )

    private fun findAllEpisodesUrl(document: Document): String? = document
        .select(_q9("w+0UXENZJg=="))
        .firstOrNull { it.text().contains(_q9("49oQDmNPEmZM0p9g"), ignoreCase = true) }
        ?.let { it.absUrl(_q9("ysQZSA==")).ifBlank { it.attr(_q9("ysQZSA==")) } }
        ?.takeIf { it.isNotBlank() }

    private fun _d7s(document: Document): List<ServerCandidate> = document
        .select(providerProfile.selector(_q9("0todV0NNNGVX35V9YA=="), _q9("0dMQS0VLVXhKxIh8Ydgv8tDTi6ReRcsna3n+")))
        .mapNotNull { option ->
            val label = option.text().trim()
            if (!isEligibleServerLabel(label)) return@mapNotNull null
            val value = option.attr(_q9("1NcQW0M=")).trim()
            if (value.isBlank()) return@mapNotNull null
            val decoded = runCatching { base64Decode(value) }.getOrDefault(value)
            val parsed = Jsoup.parse(decoded)
            val rawUrl = parsed.selectFirst(_q9("y9AOT0taIGZR1ac/M44p5sHVv7l3UPdnPm/Msmse9u3RxB9z"))?.attr(_q9("0cQf"))
                ?.takeIf { it.isNotBlank() }
                ?: HTTP_URL.find(decoded)?.value
                ?: value.takeIf { it.startsWith(_q9("ysIIXhwQVA==")) || it.startsWith(_q9("ysIIXlUFVDo=")) || it.startsWith("//") }
                ?: return@mapNotNull null
            ServerCandidate(label, normalizePlayerUrl(rawUrl))
        }
        .filter { it.url.isNotBlank() }
        .distinctBy { it.label.lowercase() to it.url }

    private fun isEligibleServerLabel(label: String): Boolean {
        val normalized = label.lowercase()
        return normalized.contains(_q9("y9gYQUhaCHxC")) || normalized.contains(_q9("y9gYQQ==")) || normalized.contains(_q9("w9oQDlVKGQ=="))
    }

    private fun normalizePlayerUrl(raw: String): String {
        val value = raw.trim().replace(_q9("hNcRXh0="), "&")
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith(_q9("ysIIXhwQVA==")) || value.startsWith(_q9("ysIIXlUFVDo=")) -> value
            value.startsWith("/") -> mainUrl.trimEnd('/') + value
            else -> value
        }
    }

    private fun _i8p(raw: String?, title: String): String? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.replace("\r", "").replace(Regex(_q9("+ZYgWnsU")), " ").trim()
        val after = clean.substringAfter(_q9("69gYQUhaCHxC"), "").trim()
        if (after.isBlank()) return null
        val lines = after.lines().map { it.trim() }.filter { it.isNotBlank() }
        val kept = lines.takeWhile { line ->
            !line.contains(_q9("8cMeWk9LF3AD/5R3fJYl8c3b"), ignoreCase = true) &&
                !(title.isNotBlank() && line.startsWith(title, ignoreCase = true) && line.contains(_q9("8cMeWk9LF3A="), true))
        }
        return kept.joinToString(" ").trim().takeIf { it.length >= 20 }
    }

    private fun findTrailer(document: Document): String? = document
        .select(_q9("y9AOT0taIGZR1ac/M5kb6tbfgpc="))
        .asSequence()
        .mapNotNull { element ->
            val attr = if (element.hasAttr(_q9("0cQf"))) element.attr(_q9("0cQf")) else element.attr(_q9("ysQZSA=="))
            attr.takeIf { YOUTUBE.containsMatchIn(it) }
        }
        .firstOrNull()

    private fun imageUrl(element: Element): String? = sequenceOf(_q9("xtcITwtMCXY="), _q9("xtcITwtTGm9am4lhcA=="), _q9("0cQf"))
        .mapNotNull { key ->
            element.absUrl(key).ifBlank { element.attr(key).trim() }.takeIf { it.isNotBlank() }
        }
        .firstOrNull()

    private fun firstInt(text: String?): Int? = text?.let { INT.find(it)?.value?.toIntOrNull() }

    private fun parseDurationMinutes(value: String): Int? {
        if (value.isBlank()) return null
        val hourMinute = Regex(_q9("iuoYBQ9jCD8Z6ok5O6QkqY0=")).find(value)
        if (hourMinute != null) return hourMinute.groupValues[1].toIntOrNull()?.times(60)?.plus(hourMinute.groupValues[2].toIntOrNull() ?: 0)
        return firstInt(value)
    }

    private suspend fun ensureMainUrl() {
        if (mainUrlResolved) return
        mainUrlMutex.withLock {
            if (mainUrlResolved) return@withLock
            val remote = runCatching {
                val json = JSONObject(app.get(providerProfile.websiteJsonUrl).text)
                _w5c(json, providerProfile.websiteKey)
            }.getOrDefault(emptyList())
            val candidates = (remote + providerProfile.defaultMainUrl).mapNotNull(::normalizeBase).distinct()
            for (candidate in candidates) {
                val response = runCatching { app.get(candidate) }.getOrNull() ?: continue
                if (!response.isSuccessful) continue
                val resolved = normalizeBase(response.url) ?: continue
                mainUrl = resolved
                mainUrlResolved = true
                return@withLock
            }
            mainUrl = providerProfile.defaultMainUrl
        }
    }

    private fun _w5c(json: JSONObject, key: String): List<String> {
        val value = json.opt(key) ?: return emptyList()
        return when (value) {
            is JSONArray -> (0 until value.length()).mapNotNull { value.optString(it).takeIf(String::isNotBlank) }
            is String -> listOf(value).filter(String::isNotBlank)
            else -> emptyList()
        }
    }

    private fun syncMainUrl(url: String?) {
        normalizeBase(url)?.let { mainUrl = it }
    }

    private fun normalizeBase(url: String?): String? = runCatching {
        val uri = URI(url?.trim().orEmpty())
        if (uri.scheme !in setOf(_q9("ysIIXg=="), _q9("ysIIXlU=")) || uri.host.isNullOrBlank()) return@runCatching null
        buildString {
            append(uri.scheme.lowercase())
            append(_q9("mJlT"))
            append(uri.host.lowercase())
            if (uri.port > 0 && uri.port != 80 && uri.port != 443) append(":${uri.port}")
        }
    }.getOrNull()

    private fun migrateDetailUrl(url: String): String {
        val current = normalizeBase(mainUrl) ?: return url
        return runCatching {
            val uri = URI(url)
            if (uri.scheme !in setOf(_q9("ysIIXg=="), _q9("ysIIXlU=")) || uri.host.isNullOrBlank()) return@runCatching url
            val base = URI(current)
            URI(base.scheme, uri.userInfo, base.host, base.port, uri.path, uri.query, uri.fragment).toString()
        }.getOrDefault(url)
    }

    private fun persistentIdentity(url: String): String = AgoosePersistentIdentity.resolveUniqueUrl(
        providerNamespace = _q9("w9gVQ0NHEns="),
        currentProviderUrl = url,
        hostIndependentPathProven = false,
    )

    private data class DetailMeta(
        val title: String,
        val altTitle: String?,
        val type: String,
        val status: String,
        val year: Int?,
        val durationMinutes: Int?,
        val rating: String?,
        val genres: List<String>,
        val poster: String?,
        val plot: String?,
        val trailer: String?,
    )

    private data class ServerCandidate(val label: String, val url: String)

    companion object {
        private val MOVIE_WORD = Regex(_q9("/tQRQVBWHklB"), RegexOption.IGNORE_CASE)
        private val YEAR = Regex(_q9("/tRUHx9DSSUK6p5oIYUc4A=="))
        private val INT = Regex(_q9("/tJX"))
        private val HTTP_URL = Regex(_q9("ysIIXlUAQToM7aRPYKRipZiEueE="))
        private val YOUTUBE = Regex(_q9("iolGV0lKD2BB06Y9cJctrYyF3r1kR8kjQiPV+mUY/tTH0lMHWkYUYFfDpj1xnW+r"), RegexOption.IGNORE_CASE)
    }
}
