package it.dogior.nsfw

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.extractors.MixDropAg
import com.lagradost.cloudstream3.utils.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.select.Elements

class Archivebate : MainAPI() {
    override var mainUrl = URL
    override var name = "Archivebate"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val hasQuickSearch = true

    companion object {
        const val URL = "https://archivebate.com"
        var cookies = mapOf<String, String>()
        var csrfToken = ""
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Origin" to URL,
            "Refer" to "$URL/",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
            "X-Livewire" to "true"
        )
        val durationMap = mutableMapOf<String, String>()
    }

    private fun strToMin(duration: String?): Int? {
        if (duration == null) return null
        val parts = duration.split(":")
        val p = parts.map { it.toInt() }
        return if (p.size == 2) {
            return p[0] + p[1] / 60
        } else if (parts.size == 3) {
            return p[0] * 60 + p[1] + p[2] / 60
        } else {
            null
        }
    }

    private suspend fun setup(
        url: String = "$mainUrl/",
        action: String = "loadVideos"
    ): RequestBody {
        val response = app.get(url)

        cookies = response.cookies
//        Log.d("Archivebate", "Cookie: $cookies")
        csrfToken = response.document.select("meta[name=\"csrf-token\"]").attr("content")
//        Log.d("Archivebate", "TOKEN: $csrfToken")
        headers["X-CSRF-TOKEN"] = csrfToken

        val wire = response.document.select("section[wire:init=\"$action\"]")
        var initialData = wire.attr("wire:initial-data")
        initialData = initialData.replace(
            "\"effects\":{\"listeners\":[],\"path\":\"https:\\/\\/archivebate.com\"},",
            ""
        )
        initialData =
            initialData.substringBeforeLast("}}") + "},\"updates\":[{\"type\":\"callMethod\",\"payload\":{\"id\":\"w018\",\"method\":\"$action\",\"params\":[]}}]}"
        initialData = initialData.replace("\\", "")
        headers["Content-Length"] = initialData.length.toString()
//        Log.d("Archivebate", "Req body: $initialData")
        return initialData.toRequestBody("application/json".toMediaType())
    }


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = setup()

        val resp = app.post(
            "$mainUrl/livewire/message/home-videos",
            cookies = cookies,
            headers = headers,
            requestBody = data
        )
        val body = resp.body.string()
//        Log.d("Archivebate", "Code: ${resp.code}")
//        Log.d("Archivebate", body)
        val html = JSONObject(body).getJSONObject("effects").getString("html")
        val doc = Jsoup.parse(html)
        val items = doc.select("section.video_item").mapNotNull {
            val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = it.select("div.info.d-flex > div").last()?.text() ?: "Unknown"
            val poster = it.select("video").attr("poster")
            val duration = it.select("div.duration.text-white > span").text()
            durationMap[link] = duration
            newMovieSearchResponse(title, link, TvType.NSFW) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(
            HomePageList("Latest Video", items, true),
            false
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val h = mapOf(
            "Refer" to headers["Refer"]!!,
            "X-Requested-With" to "XMLHttpRequest",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
        )
        val data = app.get(
            "$mainUrl/api/v1/search?query=$query",
            headers = h,
            cookies = cookies
        ).body.string()
        val usernames = JSONObject(data).getJSONArray("data").join("|").split("|").map {
            JSONObject(it).getString("username")
        }.toSet().toList()
        return usernames.map {
            val poster = getModelPoster("$mainUrl/profile/$it")
            newMovieSearchResponse(it, "$mainUrl/profile/$it", TvType.NSFW) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        if (url.contains("/profile/")) {
            return getModelProfile(url)
        } else {
            val data = getVideoData(url)
            return newMovieLoadResponse(data.title, url, TvType.NSFW, data.link) {
                this.plot = data.info
                this.posterUrl = data.poster
                this.duration = strToMin(durationMap[url])
                this.recommendations = listOf(
                    newMovieSearchResponse(
                        data.profile.text(),
                        data.profile.attr("href"),
                        TvType.NSFW
                    )
                )
            }
        }
    }

    suspend fun getModelProfile(url: String): LoadResponse? {
        val data = setup(url, action = "load_profile_videos")
        val resp = app.post(
            "$mainUrl/livewire/message/profile.model-profile",
            cookies = cookies,
            headers = headers,
            requestBody = data
        )
        val body = resp.body.string()
//        Log.d("Archivebate", "Code: ${resp.code}")
//        Log.d("Archivebate", body)
        val html = JSONObject(body).getJSONObject("effects").getString("html")
        val doc = Jsoup.parse(html)
        val items = doc.select("section.video_item").mapNotNull {
            val link = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = it.select("div.info.d-flex > div").last()?.text() ?: "Unknown"
            val duration = it.select("div.duration.text-white > span").text()
            val videoData = getVideoData(link)
            newEpisode(videoData.link) {
                this.posterUrl = videoData.poster
                this.name = title
                this.runTime = strToMin(duration)
            }
        }
        val title = url.substringAfter("/profile/").capitalize()

        val photoUrl = getModelPoster(url)
        return newTvSeriesLoadResponse(title, url, TvType.NSFW, items) {
            this.posterUrl = photoUrl
            this.plot = "Latest 20 videos"
        }
    }

    private suspend fun getModelPoster(url: String): String? {
        val photosPage = app.get("$url/photos").document
        val photoUrl = photosPage.selectFirst("img.default_thumbnail")?.attr("src")
        return photoUrl
    }

    suspend fun getVideoData(url: String): VideoInfo {
        val resp = app.get(url, cookies = cookies).document
        val description = resp.select("meta[name=\"description\"]").attr("content")
        val title = description.substringBefore(" - ")
        val info = resp.select(".info").text()
        val poster =
            resp.select("div.player").attr("style").substringAfter("url(").substringBefore(");")
        val link = resp.select("iframe").attr("src")
        val profile = resp.select("div.info.d-flex.align-items-center > div.p-1.pt-2 > a")
        return VideoInfo(title, link, info, poster, profile)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
//        Log.d("Archivebate", data)
        MixDropAg().getUrl(data, referer = null, subtitleCallback, callback)

        return true
    }

    data class VideoInfo(
        val title: String,
        val link: String,
        val info: String?,
        val poster: String?,
        val profile: Elements
    )
}