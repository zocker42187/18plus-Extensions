package com.CXXX

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

class NoodleMagazineProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://tyler-brown.com"
    override var name = "Noodle Magazine"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.NSFW
    )

    override val mainPage = mainPageOf(
        "latest" to "Latest",
        "onlyfans" to "Onlyfans",
        "latina" to "Latina",
        "blonde" to "Blonde",
        "milf" to "MILF",
        "jav" to "JAV",
        "hentai" to "Hentai",
        "lesbian" to "Lesbian"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val curpage = page - 1
        val link = "$mainUrl/video/${request.data}?p=$curpage"
        val document = app.get(link).document
        val home = document.select("div.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): MovieSearchResponse? {
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val title = this.selectFirst("a div.i_info div.title")?.text() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a div.i_img img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<MovieSearchResponse> {
        val searchresult = mutableListOf<MovieSearchResponse>()

        (0..10).toList().amap { page ->
            val doc = app.get("$mainUrl/video/$query?p=$page").document
            doc.select("div.item").mapNotNull { res ->
                res.toSearchResult()?.let { searchresult.add(it) }
            }
        }

        return searchresult
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("div.l_info h1")?.text()?.trim() ?: "null"
        val poster =
            document.selectFirst("""meta[property="og:image"]""")?.attr("content") ?: "null"

        val recommendations = document.select("div.item").mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val script = document.selectFirst("script:containsData(playlist)")

        if (script != null) {
            val jsonString = script.data()
                .substringAfter("window.playlist = ")
                .substringBefore(";")
            val jsonObject = JSONObject(jsonString)
            val sources = jsonObject.getJSONArray("sources")

            for (i in 0 until sources.length()) {
                val source = sources.getJSONObject(i)
                val file = source.getString("file")
                val quality = source.getString("label")
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = file,
                        type = ExtractorLinkType.VIDEO
                    )
                    {
                        this.referer = data.substringBefore("watch/")
                        this.quality = getQualityFromName(quality)
                        this.headers = mapOf(
                            "Host" to file.toHttpUrl().host,
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0"
                        )
                    }
                )
            }
        }
        return true
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request()
                val response = chain.proceed(request)
                if (response.code != 200) {
                    Log.d("$name: interceptor", response.peekBody(1024).string())
                }
                return response
            }

        }
    }
}
