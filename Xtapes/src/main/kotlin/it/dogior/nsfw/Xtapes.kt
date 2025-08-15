package it.dogior.nsfw.Xtapes

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Xtapes : MainAPI() {
    override var mainUrl = "https://xtapes.in"
    override var name = "Xtapes"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "porn-movies-hd" to "Latest",
        "765712" to "Blacked",
        "63400" to "Jules Jordan",
        "763817" to "MYLF",
        "6518746" to "Naughty America",
        "401786" to "New Sensations",
        "498759" to "Nubile Films",
        "420064" to "Nubiles Porn",
        "442623" to "Onlyfans",
        "435568" to "PassionHD",
        "784626" to "PornFidelity",
        "729244" to "Sex Art",
        "299653" to "Team Skeet",
        "735415" to "Tiny4K",
        "3146" to "UltraFilms",
        "396351" to "Vixen",
        "50326" to "WowGirls/WowPorn",
        "81014" to "X-Art",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
        val home = document.select("ul.listing-videos li").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("img").attr("title")
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        println(posterUrl)
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("ul.listing-videos li").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val iframe = document.selectFirst("#video-code iframe")?.attr("src").toString()
        val iframeDoc = app.get(iframe).document
        val poster = fixUrlNull(iframeDoc.selectFirst("div#vplayer > img")?.attr("src"))
        val description =
            document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("#video-code iframe").forEach { links ->
            val url = links.attr("src")
            Log.d("Phisher Test", url)
            loadExtractor(url, subtitleCallback, callback)
        }
        return true
    }
}
