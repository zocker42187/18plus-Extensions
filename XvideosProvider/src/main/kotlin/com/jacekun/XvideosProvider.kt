package com.jacekun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson


class XvideosProvider : MainAPI() {
    private val globalTvType = TvType.NSFW

    override var mainUrl = "https://www.xvideos.com"
    override var name = "Xvideos"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        Pair(mainUrl, "Main Page"),
        Pair("$mainUrl/new/", "New")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categoryData = request.data
        val categoryName = request.name
        val isPaged = categoryData.endsWith('/')
        val pagedLink = if (isPaged) categoryData + page else categoryData
        try {
            if (!isPaged && page < 2 || isPaged) {
                val soup = app.get(pagedLink).document
                val home = soup.select("div.thumb-block").mapNotNull {
                    val title = it.selectFirst("p.title a")?.text() ?: ""
                    val link = fixUrlNull(it.selectFirst("div.thumb a")?.attr("href"))
                        ?: return@mapNotNull null
                    val image = it.selectFirst("div.thumb a img")?.attr("data-src")
                    newMovieSearchResponse(
                        name = title,
                        url = link,
                        type = globalTvType,
                    ) {
                        this.posterUrl = image
                    }
                }
                if (home.isNotEmpty()) {
                    return newHomePageResponse(
                        list = HomePageList(
                            name = categoryName,
                            list = home,
                            isHorizontalImages = true
                        ),
                        hasNext = true
                    )
                } else {
                    throw ErrorLoadingException("No homepage data found!")
                }
            }
        } catch (e: Exception) {
            //e.printStackTrace()
            logError(e)
        }
        throw ErrorLoadingException()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl?k=${query}"
        val document = app.get(url).document
        return document.select("div.thumb-block").mapNotNull {
            val title = it.selectFirst("p.title a")?.text()
                ?: it.selectFirst("p.profile-name a")?.text()
                ?: ""
            val href =
                fixUrlNull(it.selectFirst("div.thumb a")?.attr("href")) ?: return@mapNotNull null
            val image =
                if (href.contains("channels") || href.contains("pornstars")) null else it.selectFirst(
                    "div.thumb-inside a img"
                )?.attr("data-src")
            val finaltitle =
                if (href.contains("channels") || href.contains("pornstars")) "" else title
            newMovieSearchResponse(
                name = finaltitle,
                url = href,
                type = globalTvType,
            ) {
                this.posterUrl = image
            }

        }.toList()
    }

    override suspend fun load(url: String): LoadResponse {
        val soup = app.get(url).document
        val title =
            if (url.contains("channels") || url.contains("pornstars")) soup.selectFirst("html.xv-responsive.is-desktop head title")
                ?.text() else
                soup.selectFirst(".page-title")?.text()
        val poster: String? =
            if (url.contains("channels") || url.contains("pornstars")) soup.selectFirst(".profile-pic img")
                ?.attr("data-src") else
                soup.selectFirst("head meta[property=og:image]")?.attr("content")
        val tags = soup.select(".video-tags-list li a")
            .map { it.text().trim().replace(", ", "") }
        val episodes = soup.select("div.thumb-block").mapNotNull {
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val name = it.selectFirst("p.title a")?.text() ?: ""
            val epthumb = it.selectFirst("div.thumb a img")?.attr("data-src")
            newEpisode(
                data = href,
            ) {
                this.name = name
                posterUrl = epthumb
            }
        }
        val tvType =
            if (url.contains("channels") || url.contains("pornstars")) TvType.TvSeries else globalTvType
        return when (tvType) {
            TvType.TvSeries -> {
                newTvSeriesLoadResponse(
                    name = title ?: "",
                    url = url,
                    type = globalTvType,
                    episodes = episodes,
                ) {
                    posterUrl = poster
                    plot = title
                    showStatus = ShowStatus.Ongoing
                    this.tags = tags
                }
            }

            else -> {
                newMovieLoadResponse(
                    name = title ?: "",
                    url = url,
                    type = globalTvType,
                    dataUrl = url,
                ) {
                    this.posterUrl = poster
                    this.plot = title
                    this.tags = tags
                    this.duration = getDurationFromString(title)
                }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val page = app.get(data).document
        val script = page.select("script")
            .find { it.data().contains("var html5player = new HTML5Player") } ?: return false

        val scriptdata = script.data()
        val videoRegex = Regex("""(?<=HLS\(')https:.+(?=')""")
        if (scriptdata.isBlank()) {
            return false
        }
        val url = videoRegex.find(scriptdata)?.value ?: return false
        callback(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = url,
                type = ExtractorLinkType.M3U8
            ) {
                referer = data
            }
        )
        return true
    }
}
