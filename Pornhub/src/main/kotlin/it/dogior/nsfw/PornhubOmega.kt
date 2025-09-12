package it.dogior.nsfw

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.newHomePageResponse
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class PornhubOmega(sections: List<MainPageData>) : Pornhub() {
    override var name = "PornHub Ω"

    override val mainPage = sections

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (request.data.toHttpUrlOrNull() == null) return newHomePageResponse(
            request.name, emptyList(), false
        )
        return super.getMainPage(page, request)
    }
}