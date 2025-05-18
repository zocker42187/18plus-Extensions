package com.CXXX

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

class FullPorner : MainAPI() {
    override var mainUrl = "https://fullporner.com"
    override var name = "FullPorner"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "${mainUrl}/home/" to "Featured",
        "${mainUrl}/category/amateur/" to "Amateur",
        "${mainUrl}/category/teen/" to "Teen",
        "${mainUrl}/category/cumshot/" to "CumShot",
        "${mainUrl}/category/deepthroat/" to "DeepThroat",
        "${mainUrl}/category/orgasm/" to "Orgasm",
        "${mainUrl}/category/threesome/" to "ThreeSome",
        "${mainUrl}/category/group-sex/" to "Group Sex",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        val home =
            document.select("div.video-block div.video-card").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.video-card div.video-card-body div.video-title a")?.text()
            ?: return null
        val href = fixUrl(
            this.selectFirst("div.video-card div.video-card-body div.video-title a")!!.attr("href")
        )
        val posterUrl =
            fixUrlNull(this.select("div.video-card div.video-card-image a img").attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..15) {
            val document = app.get("${mainUrl}/search?q=${query.replace(" ", "+")}&p=$i").document

            val results =
                document.select("div.video-block div.video-card").mapNotNull { it.toSearchResult() }

            searchResponse.addAll(results)

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title =
            document.selectFirst("div.video-block div.single-video-left div.single-video-title h2")
                ?.text()?.trim().toString()
        val tags =
            document.select("div.video-block div.single-video-left div.single-video-title p.tag-link span a")
                .map { it.text() }
        val description =
            document.selectFirst("div.video-block div.single-video-left div.single-video-title h2")
                ?.text()?.trim().toString()
        val actors =
            document.select("div.video-block div.single-video-left div.single-video-info-content p a")
                .map { it.text() }
        val recommendations =
            document.select("div.video-block div.video-recommendation div.video-card")
                .mapNotNull { it.toSearchResult() }



        val iframeUrl = fixUrlNull(
            document.selectFirst("div.video-block div.single-video-left div.single-video iframe")
                ?.attr("src")
        ) ?: ""
        val iframeDocument = app.get(iframeUrl).document
        val videoID =
            Regex("""var id = \"(.+?)\"""").find(iframeDocument.html())?.groupValues?.get(1)?.reversed()
//        val script = iframeDocument.select("script").find { it.data().contains("// Player variables") }
        val q = iframeUrl.substringAfterLast("/")
        val qualities = btq(q.toIntOrNull() ?: q)
        val posterUrl = getPoster(videoID, qualities.isNotEmpty())


        return newMovieLoadResponse(title, url, TvType.NSFW, LinkData(qualities, videoID)) {
            this.posterUrl = posterUrl
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    private fun btq(f: Any): List<String> {
        val num = when (f) {
            is String -> f.toInt(2)
            is Int -> f
            else -> throw IllegalArgumentException("Invalid type for f: must be String or Int")
        }
        val result = mutableListOf<String>()
        if (num and 1 != 0) result.add("360")
        if (num and 2 != 0) result.add("480")
        if (num and 4 != 0) result.add("720")
        if (num and 8 != 0) result.add("1080")
        return result
    }

    private fun getPoster(id: String?, quality: Boolean): String? {
        if (id == null) return null
        return if (quality) {
            "https://xiaoshenke.net/vid/$id/720/i"
        } else {
            val path = "${id.toInt() / 1000}000"
            "https://imgx.xiaoshenke.net/posterz/contents/videos_screenshots/$path/$id/preview_720p.mp4.jpg"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val d = parseJson<LinkData>(data)
        if (d.id == null) return false
        d.qualities.map {
            val url = "${d.prefix}/${d.id}/$it"
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    url
                )
            )
        }
        return true
    }

    data class LinkData(
        val qualities: List<String>,
        val id: String?,
        val prefix: String = "https://xiaoshenke.net/vid",
    )

}
