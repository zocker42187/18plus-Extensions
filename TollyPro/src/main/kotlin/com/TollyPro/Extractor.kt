package com.TollyPro

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

class Vidsp : VidhideExtractor() {
    override var mainUrl = "https://vidsp.lol"
}

open class Ds2play : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://ds2play.com"
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        return "https://dood.wf/d/$id"
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val response0 = app.get(url).text // html of DoodStream page to look for /pass_md5/...
        val md5 = mainUrl + (Regex("/pass_md5/[^']*").find(response0)?.value
            ?: return null)  // get https://dood.ws/pass_md5/...
        val trueUrl = app.get(
            md5,
            referer = url
        ).text + "zUEJeL3mUN?token=" + md5.substringAfterLast("/")   //direct link to extract  (zUEJeL3mUN is random)
        val quality = Regex("\\d{3,4}p").find(
            response0.substringAfter("<title>").substringBefore("</title>")
        )?.groupValues?.get(0)
        return listOf(
            newExtractorLink(
                this.name,
                this.name,
                trueUrl,
                INFER_TYPE
            )
            {
                this.referer = mainUrl
                this.quality = getQualityFromName(quality)
            }

        ) // links are valid in 8h

    }
}
