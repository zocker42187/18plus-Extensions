package it.dogior.nsfw

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element


class AZNude : MainAPI() {
    override var mainUrl = "https://www.aznude.com"
    override var name = "AZNude"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "${mainUrl}/browse/videos/trending/" to "Trending Videos",
        "${mainUrl}/browse/videos/recent/" to "New Videos",
        "${mainUrl}/browse/videos/popular/" to "Popular Videos",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}$page.html").document
        val home = document.select("div.col-lg-3 a.video").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("img")?.attr("title") ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val zamanText = this.selectFirst("span.play-icon-active2.video-time")?.text()

        if (zamanText != null && zamanText.matches(Regex("^00:(?:[0-1]\\d|20)$"))) {
            return null
        }
        return newMovieSearchResponse(title, href, TvType.NSFW) { this.posterUrl = posterUrl }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val apiUrl =
            "https://search-aznude.aznude.workers.dev/initial-search?q=${query}&gender=f&type=null&sortByDate=DESC&dateRange=anytime"
        val response = app.get(apiUrl, referer = "${mainUrl}/")

        val jsonString = response.body.string()
        return try {
            val searchWrapper = parseJson<SearchWrapper>(jsonString)
            val results = mutableListOf<SearchResponse>()

            // Celebs'leri ekle (sadece /view/celeb/ içerenler)
            searchWrapper.data.celebs
                .filter { it.url.contains("/view/celeb/") }
                .forEach { celeb ->
                    val href = fixUrlNull(celeb.url).toString()
                    results.add(
                        newMovieSearchResponse(
                            name = celeb.text,
                            url = href,
                            type = TvType.NSFW
                        ) {
                            posterUrl = fixUrlNull("https://cdn2.aznude.com${celeb.thumb}")
                            posterHeaders = mapOf("referer" to "${mainUrl}/")
                        }
                    )
                }

            // Videos'ları ekle (sadece /view/celeb/ içerenler)
            searchWrapper.data.videos
                .filter { it.url.contains("/view/celeb/") }
                .forEach { video ->
                    val href = fixUrlNull(video.url).toString()
                    Log.d("kraptor_$name", "video href = $href")
                    Log.d("kraptor_$name", "video.text = ${video.text}")
                    results.add(
                        newMovieSearchResponse(
                            name = video.text,
                            url = href,
                            type = TvType.NSFW
                        ) {
                            posterUrl = fixUrlNull(video.thumb)
                        }
                    )
                }

            // Stories'leri ekle (sadece /view/celeb/ içerenler)
            searchWrapper.data.stories
                .filter { it.url.contains("/view/celeb/") }
                .forEach { story ->
                    val href = fixUrlNull(story.url).toString()
                    Log.d("kraptor_$name", "story href = $href")
                    Log.d("kraptor_$name", "story.text = ${story.text}")
                    results.add(
                        newMovieSearchResponse(
                            name = story.text,
                            url = href,
                            type = TvType.NSFW
                        ) {
                            posterUrl = fixUrlNull(story.thumb)
                        }
                    )
                }

            Log.d("kraptor_$name", "Total results: ${results.size}")
            results
        } catch (e: Exception) {
            Log.e("kraptor_$name", "Jackson parsing error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        if (url.contains("/view/celeb/")) {
            val title = document.selectFirst("div.col-sm-8 h1")?.text() ?: return null
            val poster = fixUrlNull(document.selectFirst("img.img-circle")?.attr("src"))
            val score = document.selectFirst("span.rating-score")?.text()
            val tags = document.select("div.col-md-12 h2.video-tags a").map { it.text() }
            val recommendations = document.select("div.container:nth-child(18) div.col-lg-2")
                .mapNotNull { it.toRecommendationResult() }
            val bolumler = document.select("div.movie.grid_load").map { bolum ->
                val bolumHref = bolum.selectFirst("a")?.attr("href").toString()
                val poster2 = bolum.selectFirst("img")?.attr("src")
                val bolumIsim = bolum.selectFirst("img")?.attr("title")
                val videoDuration =
                    bolum.select("span.video-time").text().split(":").map { it.toInt() }
                Log.d("BANANA", videoDuration.joinToString(":"))
                val runTime = when (videoDuration.size) {
                    3 -> {
                        videoDuration[0] * 60 + videoDuration[1] + videoDuration[2] / 60
                    }
                    2 -> {
                        videoDuration[0] + videoDuration[1] / 60
                    }
                    else -> {
                        videoDuration[1] / 60
                    }
                }
                newEpisode(bolumHref, {
                    this.name = bolumIsim
                    this.posterUrl = poster2
                    this.runTime = runTime
                })
            }

            return newTvSeriesLoadResponse(title, url, TvType.NSFW, bolumler) {
                this.posterUrl = poster
                this.plot = "$title +18"
                this.tags = tags
                this.recommendations = recommendations
                addScore(Score.from(score, 10))
            }
        } else {
            val title = document.selectFirst("meta[name=title]")?.attr("content") ?: return null
            val poster =
                fixUrlNull(document.selectFirst("link[rel=preload][as=image]")?.attr("href"))
            val description = document.selectFirst("meta[name=description]")?.attr("content")
            val tags = document.select("div.col-md-12 h2.video-tags a").map { it.text() }
            val recommendations =
                document.select("div.col-lg-3 a.video").mapNotNull { it.toRecommendationResult() }
            val durationText = description?.substringAfterLast("(")?.substringBefore(")")
//            Log.d("BANANA", durationText ?:"")
            val hours = (if ((durationText?.contains("hour")) == true)
                durationText.substringBefore("hour").trim() else "0").toInt()

            val minutes = (if ((durationText?.contains("minute")) == true)
                durationText.substringBefore(" minute").substringAfterLast(" ")
                    .trim() else "0").toInt()
            val seconds =
                (durationText?.substringBefore(" second")?.substringAfterLast(" ")?.trim())?.toInt()
            val duration = durationText?.let { hours * 60 + minutes + seconds!! / 60 }

            return newMovieLoadResponse(title, url, TvType.NSFW, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                this.duration = duration // In minutes
            }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("img")?.attr("title") ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val zamanText = this.selectFirst("span.play-icon-active2.video-time")?.text()

        if (zamanText != null && zamanText.matches(Regex("^00:(?:[0-1]\\d|20)$"))) {
            return null
        }

        return newMovieSearchResponse(title, href, TvType.NSFW) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("kraptor_$name", "data = $data")
        val document = app.get(data).document
        val scriptElements = document.select("script")

        scriptElements.forEach { script ->
            val scriptContent = script.html()

            if (scriptContent.contains("jwplayer") && scriptContent.contains("setup") && scriptContent.contains(
                    "playlist"
                )
            ) {

                val sourcesRegex =
                    """sources:\s*\[\s*(.*?)\s*\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
                val sourcesMatch = sourcesRegex.find(scriptContent)

                sourcesMatch?.let { match ->
                    val sourcesContent = match.groupValues[1]
                    val sourceRegex =
                        """\{\s*file:\s*"([^"]+)",\s*label:\s*"([^"]+)"(?:,\s*default:\s*"true")?\s*\}""".toRegex()
                    val sourceMatches = sourceRegex.findAll(sourcesContent)

                    sourceMatches.forEach { sourceMatch ->
                        val videoUrl = sourceMatch.groupValues[1]
                        val quality = sourceMatch.groupValues[2]

                        // Quality değerini Qualities enum'una çevir
                        val qualityValue = when (quality.uppercase()) {
                            "LQ" -> Qualities.P240.value
                            "HQ" -> Qualities.P480.value
                            "HD" -> Qualities.P720.value
                            "FHD" -> Qualities.P1080.value
                            "4K" -> Qualities.P2160.value
                            else -> Qualities.Unknown.value
                        }

                        callback.invoke(
                            newExtractorLink(
                                source = "AZNude $quality",
                                name = "AZNude $quality",
                                url = videoUrl,
                                type = INFER_TYPE
                            ) {
                                this.quality = qualityValue
                                this.referer = "${mainUrl}/"
                            })
                    }
                }
            }
        }

        return true
    }
}


data class SearchWrapper(
    val count: Count,
    val data: Data
)

data class Count(
    val celebs: Int,
    val movies: Int,
    val stories: Int,
    val videos: Int
)

data class Data(
    val celebs: List<Actor>,
    val movies: List<Any> = emptyList(),
    val stories: List<Story> = emptyList(),
    val videos: List<Video> = emptyList()
)

data class Video(
    @JsonProperty("video_id") val id: Long,
    val text: String,
    val thumb: String,
    val url: String,
    val duration: String,
    @JsonProperty("date_added") val dateAdded: String,
    @JsonProperty("views_1day") val views1day: Int = 0,
    @JsonProperty("views_7days") val views7days: Int = 0,
    @JsonProperty("views_month") val viewsMonth: Int = 0,
    @JsonProperty("views_3months") val views3months: Int = 0,
    @JsonProperty("views_6months") val views6months: Int = 0,
    @JsonProperty("views_year") val viewsYear: Int = 0,
    @JsonProperty("views_alltime") val viewsAlltime: Int = 0,
    @JsonProperty("date_modified") val dateModified: String = "",
    @JsonProperty("avg_rating") val avgRating: Int = 0
)

data class Actor(
    @JsonProperty("celeb_id") val id: Long,
    val text: String,
    val thumb: String,
    val url: String,
    @JsonProperty("date_added") val dateAdded: String,
    @JsonProperty("date_modified") val dateModified: String,
    @JsonProperty("views_1day") val views1day: Int = 0,
    @JsonProperty("views_7days") val views7days: Int = 0,
    @JsonProperty("views_month") val viewsMonth: Int = 0,
    @JsonProperty("views_3months") val views3months: Int = 0,
    @JsonProperty("views_6months") val views6months: Int = 0,
    @JsonProperty("views_year") val viewsYear: Int = 0,
    @JsonProperty("views_alltime") val viewsAlltime: Int = 0,
    @JsonProperty("avg_rating") val avgRating: Int = 0
)

data class Story(
    @JsonProperty("story_id") val id: String,
    val text: String,
    val thumb: String,
    val url: String,
    @JsonProperty("date_added") val dateAdded: String,
    @JsonProperty("date_modified") val dateModified: String,
    @JsonProperty("views_1day") val views1day: Int = 0,
    @JsonProperty("views_7days") val views7days: Int = 0,
    @JsonProperty("views_month") val viewsMonth: Int = 0,
    @JsonProperty("views_3months") val views3months: Int = 0,
    @JsonProperty("views_6months") val views6months: Int = 0,
    @JsonProperty("views_year") val viewsYear: Int = 0,
    @JsonProperty("views_alltime") val viewsAlltime: Int = 0,
    @JsonProperty("avg_rating") val avgRating: Int = 0
)