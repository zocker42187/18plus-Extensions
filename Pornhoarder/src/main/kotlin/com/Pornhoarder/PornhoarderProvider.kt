package com.PornhoarderPlugin

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.CXXX.BigWarp
import com.CXXX.LuluStream

@CloudstreamPlugin
class PornhoarderProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(PornhoarderPlugin())
        registerExtractorAPI(BigWarp())
        registerExtractorAPI(LuluStream())
    }
}