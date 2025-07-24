package com.XHamster

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixTitle
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class XHamster : MainAPI() {
    override var mainUrl = "https://xhamster.com"
    override var name = "XHamster"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "trending" to "Trending Videos",
        "newest" to "New Videos",
        "best/weekly" to "Top rated",
        "most-viewed/weekly" to "Most Viewed",

        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var url = "$mainUrl/${request.data}/$page/"

        if (request.data.contentEquals("trending")) {
            url = "$mainUrl/$page/"
        }

        val document = app.get(url).document

        val elements = document.select("div.thumb-list__item")

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
        val title = fixTitle(this.select("a.video-thumb-info__name").text()).trim()
        val href = fixUrl(this.select("a.video-thumb-info__name").attr("href"))
        val posterUrl = this.selectFirst("img.thumb-image-container__image")?.let {
            fixUrl(it.attr("data-webp")).takeIf { url -> url.isNotBlank() }
                ?: fixUrl(it.attr("src"))
        }
            ?: "https://www.startpage.com/av/proxy-image?piurl=https%3A%2F%2Fcdn.1min30.com%2Fwp-content%2Fuploads%2F2018%2F12%2FLe-logo-xHamster.jpg&sp=1753371152Tbaafff312b85075022785f7930c1b9a621c7967f26f508179f0642ed6c99e59a"
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..10) {
            val document = app.get("${mainUrl}/search/${query}").document

            val results = document.select("div.thumb-list__item").mapNotNull { it.toSearchResult() }

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

        val actors = mutableListOf<String>()
        val tags = mutableListOf<String>()

        for (item in document.select("div.item-50dd2")) {
            val name = item.selectFirst("span.label-5984a")?.text()?.trim() ?: continue

            if (item.selectFirst("div.avatar-e781b") != null) {
                actors.add(name)
            } else {
                tags.add(name)
            }
        }

        val relatedvids = document.selectFirst("div.thumb-list")?.select("div.thumb-list__item")
            ?.map { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.recommendations = relatedvids
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val vidlink = document.selectFirst("link[rel=preload]")?.attr("href").toString()

        val resolutions = app.get(vidlink).text.lines()
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }

        resolutions.map {
            val vidurl = Regex("_TPL_.*$").replace(vidlink, it)
            M3u8Helper().m3u8Generation(
                M3u8Helper.M3u8Stream(
                    vidurl
                ), true
            ).amap { stream ->
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = this.name,
                        url = stream.streamUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = data
                        this.quality = getIndexQuality(it)
                    }
                )
            }
        }

        return true
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.get(1)
            .let { getQualityFromName(it) }
    }
}
