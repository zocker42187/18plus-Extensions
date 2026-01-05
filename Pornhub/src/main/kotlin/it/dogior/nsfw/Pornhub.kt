package it.dogior.nsfw

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.json.JSONObject

open class Pornhub : MainAPI() {
    private val globalTvType = TvType.NSFW
    final override var mainUrl = "https://www.pornhub.com"
    override var name = "PornHub"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "$mainUrl/video?o=mr&hd=1&page=" to "Recently Featured",
        "$mainUrl/video?o=cm&t=w&hd=1&page=" to "Newest",
        "$mainUrl/video?o=mv&t=w&hd=1&page=" to "Most Viewed",
        "$mainUrl/video?o=ht&t=w&hd=1&page=" to "Hottest",
        "$mainUrl/video?o=tr&t=w&hd=1&page=" to "Top Rated",
        "$mainUrl/video?c=139&o=cm&t=w&hd=1&page=" to "Verified Models",
        "$mainUrl/video?c=138&o=cm&t=w&hd=1&page=" to "Verified Amateurs",
        "$mainUrl/video?c=482&o=cm&t=w&hd=1&page=" to "Verified Couples",

        // I'll probably make another extensions just for the models
//        "${mainUrl}/model/fantasybabe/videos?o=cm&t=w&hd=1&page=" to "FantasyBabe",
    )
    private val cookies = mapOf(Pair("hasVisited", "1"), Pair("accessAgeDisclaimerPH", "1"))

    companion object {
        val thumbnails = mutableMapOf<String, String?>() // Video url to Poster url
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val categoryData = request.data
        val categoryName = request.name
        val pagedLink = if (page > 0) categoryData + page else categoryData
        val soup = app.get(pagedLink, cookies = cookies).document
        val selector = if (categoryData.contains("/channels/")) "#showAllChanelVideos li"
        else "div.sectionWrapper div.wrap"
        val home = soup.select(selector).mapNotNull {
            val title = it.selectFirst("span.title a")?.text() ?: ""
            val link = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val img = fetchImgUrl(it.selectFirst("img"))
            thumbnails[link] = img
            newMovieSearchResponse(
                name = title,
                url = link,
                type = globalTvType,
            ) {
                this.posterUrl = img
            }
        }
        return if (home.isNotEmpty()) {
             newHomePageResponse(
                list = HomePageList(
                    name = categoryName, list = home, isHorizontalImages = true
                ), hasNext = true
            )
        } else null
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/video/search?search=${query}"
        val document = app.get(url, cookies = cookies).document
        return document.select("div.sectionWrapper div.wrap").mapNotNull {
            val title = it.selectFirst("span.title a")?.text() ?: return@mapNotNull null
            val link = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val image = fetchImgUrl(it.selectFirst("img"))
            thumbnails[link] = image
            newMovieSearchResponse(
                name = title,
                url = link,
                type = globalTvType,
            ) {
                this.posterUrl = image
            }
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val soup = app.get(url, cookies = cookies).document
        val title = soup.selectFirst(".title span")?.text() ?: ""
        val poster: String? = thumbnails[url] ?: soup.selectFirst("div.video-wrapper .mainPlayerDiv img")?.attr("src")
            ?: soup.selectFirst("head meta[property=og:image]")?.attr("content")
        val tags = soup.select("div.categoriesWrapper a")
            .map { it.text().trim().replace(", ", "") }

        val recommendations = soup.select("ul#relatedVideosListing li.pcVideoListItem").map {
            val rTitle = it.selectFirst("div.phimage a")?.attr("title") ?: ""
            val rUrl = fixUrl(it.selectFirst("div.phimage a")?.attr("href").toString())
            val rPoster = fixUrl(
                it.selectFirst("div.phimage img.js-videoThumb")?.attr("src").toString()
            )
            newMovieSearchResponse(name = rTitle, url = rUrl) { this.posterUrl = rPoster }
        }

        val channel =
            soup.select("div.video-wrapper div.video-info-row.userRow div.userInfoBlock")
                .map {
                    val name = it.select("div.userInfo").select("a").text()
                    val img = it.select("img").attr("src")
                    Actor(name = name, img)
                }
        val pornstars = soup.select("div.pornstarsWrapper a").mapNotNull {
            val name = it.text()
            if (name == "Suggest") return@mapNotNull null
            val img = it.select("img").attr("src")
            Actor(name = name, img)
        }

        val relatedVideo = soup.select("ul#relatedVideosCenter li.pcVideoListItem").map {
            val rTitle = it.selectFirst("div.phimage a")?.attr("title") ?: ""
            val rUrl = fixUrl(it.selectFirst("div.phimage a")?.attr("href").toString())
            val rPoster = fixUrl(
                it.selectFirst("div.phimage img.js-videoThumb")?.attr("src").toString()
            )
            newMovieSearchResponse(name = rTitle, url = rUrl) { this.posterUrl = rPoster }
        }

        val a = Regex("""'(?<=video_date_published' : ')\d{8}""")
        val datePublished = a.find(soup.head().toString())?.value?.substringAfter("'")
        val formattedDate = datePublished?.substring(0, 4) + "/" + datePublished?.substring(
            4,
            6
        ) + "/" + datePublished?.substring(6, 8)

        val script = soup.selectFirst("#player > script")?.data()
        val qualities =
            script?.let { Regex("""(?<="defaultQuality":\[).*(?=],"vc)""").find(it)?.value }
        val description = (datePublished?.let { "Date published: $formattedDate. " } ?: "") +
                (qualities?.let { "Qualities available: $qualities" } ?: "")

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            addActors(channel + pornstars)
            this.recommendations = recommendations + relatedVideo
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val request = app.get(
            url = data, cookies = cookies
        )
        val document = request.document
        val mediaDefinitions = JSONObject(
            document.selectXpath("//script[contains(text(),'flashvars')]").first()?.data()
                ?.substringAfter("=")?.substringBefore(";")
        ).getJSONArray("mediaDefinitions")

        for (i in 0 until mediaDefinitions.length()) {
            val quality = mediaDefinitions.getJSONObject(i).getString("quality")
            val videoUrl = mediaDefinitions.getJSONObject(i).getString("videoUrl")
            val extlinkList = mutableListOf<ExtractorLink>()
            M3u8Helper().m3u8Generation(
                M3u8Helper.M3u8Stream(
                    videoUrl
                ), true
            ).amap { stream ->
                extlinkList.add(
                    newExtractorLink(
                        source = name,
                        name = this.name,
                        url = stream.streamUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        referer = mainUrl
                        this.quality = Regex("(\\d+)").find(quality ?: "")?.groupValues?.get(1)
                            .let { getQualityFromName(it) }
                    }
                )
            }
            extlinkList.forEach(callback)
        }

        return true
    }

    private fun fetchImgUrl(imgsrc: Element?): String? {
        return try {
            imgsrc?.attr("src") ?: imgsrc?.attr("data-src") ?: imgsrc?.attr("data-mediabook")
            ?: imgsrc?.attr("alt") ?: imgsrc?.attr("data-mediumthumb")
            ?: imgsrc?.attr("data-thumb_url")
        } catch (_: Exception) {
            null
        }
    }
}
