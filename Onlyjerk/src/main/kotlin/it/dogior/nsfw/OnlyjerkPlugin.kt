package it.dogior.nsfw

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class OnlyjerkPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Onlyjerk())
        registerExtractorAPI(Dooodster())
        registerExtractorAPI(Listeamed())
        registerExtractorAPI(Beamed())
        registerExtractorAPI(BigwarpIO())
    }
}
