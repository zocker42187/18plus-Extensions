package com.jacekun

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.JsonObject
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.NiceResponse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class Vlxx : MainAPI() {
    private val DEV = "DevDebug"
    private val globaltvType = TvType.NSFW

    override var name = "Vlxx"
    override var mainUrl = "https://vlxx.now"
    override val supportedTypes = setOf(TvType.NSFW)
    override val hasDownloadSupport = false
    override val hasMainPage = true
    override val hasQuickSearch = false
    private val interceptor = CloudflareKiller()

    companion object {
        val images = mutableMapOf<String, String>() // page url, poster url
    }

    private suspend fun getPage(url: String, referer: String): NiceResponse {
        var count = 0
        var resp = app.get(url, referer = referer, interceptor = interceptor)
        Log.i(DEV, "Page Response => ${resp}")
//        while (!resp.isSuccessful) {
//            resp = app.get(url, interceptor = interceptor)
//            count++
//            if (count > 4) {
//                return resp
//            }
//        }
        return resp
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = getPage(mainUrl, mainUrl).document
        val all = ArrayList<HomePageList>()
        val title = "Homepage"
        Log.i(DEV, "Fetching videos..")
        val elements = document.select("div#video-list > div.video-item")
            .mapNotNull {
                val firstA = it.selectFirst("a")
                val link = fixUrlNull(firstA?.attr("href")) ?: return@mapNotNull null
                val img = it.selectFirst("img")?.attr("data-original")
                val name = it.selectFirst("div.video-name")?.text() ?: it.text()
                Log.i(DEV, "Result => $link")
                newMovieSearchResponse(
                    name = name,
                    url = link,
                    type = globaltvType,
                ) {
                    //this.apiName = apiName
                    img?.let { i ->
                        images[link] = i
                    }
                    this.posterUrl = img
                }
            }.distinctBy { it.url }

        if (elements.isNotEmpty()) {
            all.add(
                HomePageList(
                    title, elements
                )
            )
        }
        return newHomePageResponse(all)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return getPage("$mainUrl/search/${query}/", mainUrl).document
            .select("#container .box .video-list")
            .mapNotNull {
                val link = fixUrlNull(it.select("a").attr("href")) ?: return@mapNotNull null
                val imgArticle = it.select(".video-image").attr("src")
                val name = it.selectFirst(".video-name")?.text() ?: ""
                val year = null

                newMovieSearchResponse(
                    name = name,
                    url = link,
                    type = globaltvType,
                ) {
                    //this.apiName = apiName
                    images[link] = imgArticle
                    this.posterUrl = imgArticle
                    this.year = year
                    this.posterHeaders = interceptor.getCookieHeaders(url).toMap()
                }
            }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = getPage(url, url).document

        val container = doc.selectFirst("div#container")
        val title = container?.selectFirst("h2")?.text() ?: "No Title"
        val descript = container?.selectFirst("div.video-description")?.text()
        val year = null
        return newMovieLoadResponse(
            name = title,
            url = url,
            dataUrl = url,
            type = globaltvType,
        ) {
            this.posterUrl = images[url]
            this.year = year
            this.plot = descript
            this.posterHeaders = interceptor.getCookieHeaders(url).toMap()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pathSplits = data.split("/")
        val id = pathSplits[pathSplits.size - 2]
        Log.i(DEV, "Data -> $data id -> $id")
        val res = app.post(
            "${mainUrl}/ajax.php",
            headers = interceptor.getCookieHeaders(data).toMap(),
            data = mapOf(
                Pair("vlxx_server", "1"),
                Pair("id", id),
                Pair("server", "1"),
            ),
            referer = mainUrl
        ).text
        Log.i(DEV, "res $res")

        val json = JSONObject(res)
        val iframe =
            json.get("player").toString().substringAfter("src=").substringBefore(" scrolling=")
                .replace("\"", "")
        Log.i(DEV, "json $iframe")
        val resp = app.get(iframe).document
        val script = resp.select("script").find { it.data().contains("var jwplayer_mute") }?.data()
            ?: return false
        val sources = script.substringAfter("sources: ").substringBefore("],") + "]"
        val array = JSONArray(sources).get(0) as JSONObject
        val url = array.getString("file")
        Log.i(DEV, "url $url")
        callback(newExtractorLink(
            this.name,
            this.name,
            url,
            ExtractorLinkType.M3U8
        ) {
            this.headers = mapOf("Host" to url.toHttpUrl().host)
        })
        return true
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request()
                val response = chain.proceed(request)
                Log.d(DEV, response.peekBody(1024).string())
                // Apparently the app has some problems with this site m3u8 links
                return response
            }

        }
    }
}
