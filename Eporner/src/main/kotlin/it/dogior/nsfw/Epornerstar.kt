package it.dogior.nsfw

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class Epornerstar : MainAPI() {
    override var mainUrl = "https://www.eporner.com"
    override var name = "Epornerstar"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 200L
    override var sequentialMainPageScrollDelay = 200L

    override val mainPage = mainPageOf(
        "pornstar/candy-love-C0RYS" to "Candy Love",
        "pornstar/molly-redwolf" to "MollyRedWolf",
        "pornstar/little-reislin-1yzCC" to "Reislin",
        "pornstar/eliza-ibarra" to "Eliza Ibarra",
        "pornstar/stacy-cruz" to "Stacy Cruz",
        "pornstar/ellie-nova-dKppY" to "Ellie Nova",
        "pornstar/hazel-moore" to "Hazel Moore",
        "pornstar/rae-lil-black-woUfN" to "Rae Lil Black",
        "pornstar/jia-lissa-IpgNW" to "Jia Lissa",
        "pornstar/maria-kazi-0FbT8" to "Maria Kazi",
        "pornstar/eva-elfie-Ojgho" to "Eva Elfie",
        "pornstar/sweetie-fox-Y3xBY" to "Sweetie Fox",
        "pornstar/sonya-blaze" to "Sonya Blaze",
        "pornstar/holly-michaels" to "Holly Michaels",
        "pornstar/liya-silver-ajRED" to "Liya Silver",
        "pornstar/autumn-falls-CKYp3" to "Autumn Falls",
        "pornstar/octavia-red" to "Octavia Red",
        "pornstar/martina-smeraldi-cyVFA-h1ylt" to "Martina Smeraldi",
        "pornstar/mia-malkova-oPgtJ" to "Mia Malkova",
        "pornstar/busty-clary" to "Clary",
        "pornstar/valentina-nappi-5RAa3" to "Valentina Nappi",
        "pornstar/lena-paul-v5WPE-xmrhw" to "Lena Paul",
        "pornstar/kendra-sunderland" to "Kendra Sunderland",
        "pornstar/madison-ivy" to "Madison Ivy",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/$page/").document
//        Log.d("EPORNER", document.toString())
        val home = document.select("div.mb.hdy").mapNotNull { it.toSearchResult() }

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
        val title = fixTitle(this.select("div.mbunder p a").text()).trim()
        val href = fixUrl(this.select("div.mbcontent a").attr("href"))
        val posterUrl = this.selectFirst("img")?.let {
            fixUrl(it.attr("data-src")).takeIf { url -> url.isNotBlank() }
                ?: fixUrl(it.attr("src"))
        }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    /*override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..10) {
            val document = app.get("${mainUrl}/search/$query/$i").document

            val results = document.select("div.mb.hdy").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }*/

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description =
            document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val relatedDiv = document.select("#relateddiv")
        val relatedVideos = relatedDiv.select(".mb").map {
            val a = it.select(".mbcontent > a")
            val img = a.select("img")
            val relatedPoster = img.attr("data-src")
            val relatedTitle = img.attr("alt")
            val relatedLink = a.attr("href")
            newMovieSearchResponse(relatedTitle, relatedLink){
                this.posterUrl = relatedPoster
            }
        }


        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = relatedVideos
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(
            data, interceptor = WebViewResolver(Regex("""https://www\.eporner\.com/xhr/video"""))
        )
        val json = response.text

        val jsonObject = JSONObject(json)
        val sources = jsonObject.getJSONObject("sources")
        val mp4Sources = sources.getJSONObject("mp4")
        val qualities = mp4Sources.keys()
        while (qualities.hasNext()) {
            val quality = qualities.next() as String
            val sourceObject = mp4Sources.getJSONObject(quality)
            val src = sourceObject.getString("src")
            val labelShort = sourceObject.getString("labelShort") ?: ""
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = src,
                    type = INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getIndexQuality(labelShort)
                }
            )
        }
        return true
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}
