package to.WatchDirty

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class WatchDirtyProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(WatchDirty())
    }
}
