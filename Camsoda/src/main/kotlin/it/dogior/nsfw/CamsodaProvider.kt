package it.dogior.nsfw

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class CamsodaProvider : MainAPI() {
    override var mainUrl = "https://www.camsoda.com"
    override var name = "Camsoda"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "/api/v1/browse/react?gender-hide=m,f,t&perPage=98" to "Couples",
        "/api/v1/browse/react?gender-hide=m,t,c&perPage=98" to "Girls",
        "/api/v1/browse/react?gender-hide=c,f,t&perPage=98" to "Male",
        "/api/v1/browse/react?gender-hide=c,f,m&perPage=98" to "Transgender",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val responseList = app.get("$mainUrl${request.data}&p=$page")
            .parsedSafe<Response>()!!.userList.map { user ->
                newLiveSearchResponse(
                    name = user.username,
                    url = "$mainUrl/${user.username}",
                    type = TvType.Live,
                ){
                    this.posterUrl = if (user.thumbUrl.isEmpty()) user.offlinePictureUrl else user.thumbUrl
                }
            }
        return newHomePageResponse(
            HomePageList(
                request.name,
                responseList,
                isHorizontalImages = true
            ), hasNext = true
        )

    }


    override suspend fun search(query: String): List<SearchResponse> {

        val searchResponse = mutableListOf<LiveSearchResponse>()

        for (i in 0..3) {
            val results = app.get("$mainUrl/api/v1/browse/react/search/$query?p=$i&perPage=98")
                .parsedSafe<Response>()!!.userList.map { user ->
                    newLiveSearchResponse(
                        name = user.username,
                        url = "$mainUrl/${user.username}",
                        type = TvType.Live,
                    )
                    {
                        this.posterUrl =
                            if (user.offlinePictureUrl.isEmpty()) user.thumbUrl else user.offlinePictureUrl
                    }
                }
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

        var title =
            document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" Cam:")?.trim().toString()
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description =
            document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
        return newLiveStreamLoadResponse(
            name = title,
            url = url,
            dataUrl = url,
        ) {
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
        val doc = app.get(data).document
        val script =
            doc.select("script").find { item -> item.html().contains("window.__PRELOADED_STATE__") }
        val json = JSONObject(script?.html()?.replace("window.__PRELOADED_STATE__ = ", "").toString())
        val username = json.getJSONObject("chatPage").getString("username")
        val streamJson =
            json.getJSONObject("chatRoom").getJSONObject("roomByUsername").getJSONObject(username)
                .getJSONObject("stream")
        val serverList = streamJson.getJSONArray("edge_servers")
        val streamName = streamJson.getString("stream_name")
        val token = streamJson.getString("token")
        val streamUrlTemplate =
            "https://streamServer/${streamName}_v1/index.ll.m3u8?multitrack=true&filter=tracks:v4v3v2v1a1a2&token=${token}"
        for (i in 0..<serverList.length()) {
            val finalUrl = streamUrlTemplate.replace("streamServer", serverList[i].toString())
            callback.invoke(
                newExtractorLink(
                    source = "$name Server ${i + 1}",
                    name = "$name Server ${i + 1}",
                    url = finalUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
                        "Accept" to "*/*",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Origin" to "https://www.camsoda.com",
                        "Sec-GPC" to "1",
                        "Connection" to "keep-alive",
                        "Referer" to "https://www.camsoda.com/",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site"
                    )
                }
            )
        }
        return true
    }

    data class User(
        @JsonProperty("username") val username: String = "",
        @JsonProperty("displayName") val displayName: String = "",
        @JsonProperty("offlinePictureUrl") val offlinePictureUrl: String = "",
        @JsonProperty("thumbUrl") val thumbUrl: String = "",
        @JsonProperty("status") val status: String = ""
    )

    data class Response(
        @JsonProperty("userList") val userList: List<User> = arrayListOf()
    )
}
