package it.dogior.nsfw


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class Chatrubate : MainAPI() {
    override var mainUrl = "https://chaturbate.com"
    override var name = "Chatrubate"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "/api/ts/roomlist/room-list/?limit=90" to "Featured",
        "/api/ts/roomlist/room-list/?genders=f&limit=90" to "Female",
        "/api/ts/roomlist/room-list/?genders=c&limit=90" to "Couples",
        "/api/ts/roomlist/room-list/?genders=m&limit=90" to "Male",
        "/api/ts/roomlist/room-list/?genders=t&limit=90" to "Trans",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val offset: Int = if (page == 1) {
            0
        } else {
            90 * (page - 1)
        }
        val responseList = app.get("$mainUrl${request.data}&offset=$offset")
            .parsedSafe<Response>()!!.rooms.map { room ->
                newLiveSearchResponse(
                    name = room.username,
                    url = "$mainUrl/${room.username}",
                    type = TvType.Live,
                ) { this.posterUrl = room.img }
                /*LiveSearchResponse(
                    name = room.username,
                    url = "$mainUrl/${room.username}",
                    apiName = this@ChatrubateProvider.name,
                    type = TvType.Live,
                    posterUrl = room.img,
                    lang = null
                )*/
            }
        return newHomePageResponse(
            HomePageList(
                request.name,
                responseList,
                isHorizontalImages = true
            ), hasNext = true
        )

    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val searchResponse = mutableListOf<LiveSearchResponse>()

        val resp =
            app.get("$mainUrl/api/ts/roomlist/room-list/?keywords=$query&limit=90&offset=${(page - 1) * 90}")
        val response = parseJson<Response>(resp.body.string())
        val hasNext = 90 <= response.rooms.size

        val results = response.rooms.map { room ->
            newLiveSearchResponse(
                name = room.username,
                url = "$mainUrl/${room.username}",
                type = TvType.Live,
            ) { this.posterUrl = room.img }
        }
        if (!searchResponse.containsAll(results)) {
            searchResponse.addAll(results)
        } else {
            return null
        }

        if (results.isEmpty()) return null
        return newSearchResponseList(searchResponse, hasNext)

    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = url.substringAfterLast("/")
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description =
            document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()


        return newLiveStreamLoadResponse(
            name = title,
            url = url,
            dataUrl = url
        ) {
            this.posterUrl = poster
            this.plot = description
        }
        /*LiveStreamLoadResponse(
            name = title,
            url = url,
            apiName = this.name,
            dataUrl = url,
            posterUrl = poster,
            plot = description,
        )*/
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val script =
            doc.select("script").find { item -> item.html().contains("window.initialRoomDossier") }
        val json =
            script!!.html().substringAfter("window.initialRoomDossier = \"").substringBefore(";")
                .unescapeUnicode()
        val m3u8Url = "\"hls_source\": \"(.*).m3u8\"".toRegex().find(json)?.groups?.get(1)?.value
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = "$m3u8Url.m3u8",
                type = ExtractorLinkType.M3U8
            ) {
                referer = ""
                Qualities.Unknown.value
            }
        )

        return true
    }

    data class Room(
        @JsonProperty("img") val img: String = "",
        @JsonProperty("username") val username: String = "",
        @JsonProperty("subject") val subject: String = "",
        @JsonProperty("tags") val tags: List<String> = arrayListOf()

    )

    data class Response(
        @JsonProperty("all_rooms_count") val all_rooms_count: String = "",
        @JsonProperty("room_list_id") val room_list_id: String = "",
        @JsonProperty("total_count") val total_count: Int = 0,
        @JsonProperty("rooms") val rooms: List<Room> = arrayListOf()
    )
}

fun String.unescapeUnicode() = replace("\\\\u([0-9A-Fa-f]{4})".toRegex()) {
    String(Character.toChars(it.groupValues[1].toInt(radix = 16)))
}
