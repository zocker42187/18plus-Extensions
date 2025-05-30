package to.WatchDirty

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.Coroutines.main
import khttp.get
import org.json.JSONObject
import org.jsoup.select.Elements

class WatchDirty : MainAPI() {
    override var mainUrl = "https://watchdirty.is"
    override var name = "WatchDirty"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "most-popular-week" to "Most popular this week",
        "currently-playing" to "Currently playing",
        "latest-updates" to "New",
        "top-rated" to "Top rated",
        "most-popular" to "Most popular",

        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var url = "$mainUrl/${request.data}/$page"

        if (request.data.contentEquals("currently-playing")) {
            url = "$mainUrl"
        } else if (request.data.endsWith("week-")) {
            url = "$mainUrl/${request.data.removeSuffix("-week")}/$page/?sort_by=video_viewed_week"
        }

        val document = app.get(url).document
        var elements = document.select("div.item")
        if (request.data.endsWith("week-")) {
            elements = Elements(elements.drop(12).take(12))
        }
        val home = elements.mapNotNull { it.toSearchResult() }

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
        val title = fixTitle(this.select("strong.title").text()).trim()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = this.selectFirst("img")?.let {
            fixUrl(it.attr("data-src")).takeIf { url -> url.isNotBlank() }
                ?: fixUrl(it.attr("src"))
        } ?: null
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..10) {
            val document = app.get("${mainUrl}/search/$query?from_videos=$i").document

            val results = document.select("div.item").mapNotNull { it.toSearchResult() }

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
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
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
        val response = app.get(data).document
        val src = response.select("video.fp-engine").first()?.attr("src").orEmpty()

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = src,
                type = INFER_TYPE
            ) {
                this.referer = ""
                this.quality = getIndexQuality(src)
            }
        )

        return true
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}
