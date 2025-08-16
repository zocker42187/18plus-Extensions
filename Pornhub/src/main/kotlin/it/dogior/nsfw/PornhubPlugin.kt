package it.dogior.nsfw

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Coroutines.main

@CloudstreamPlugin
class PornhubPlugin : Plugin() {
    private val sharedPref = activity?.getSharedPreferences("PornHub", Context.MODE_PRIVATE)

    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Pornhub())


        val isOmegaEnabled = sharedPref?.getBoolean("omega", true) ?: true
        if (isOmegaEnabled) {
            val defaultSection = Triple("", "Add your sections in the settings", 1L).toJson()
            val sectionsJson = (sharedPref?.getStringSet("sections", setOf(defaultSection))
                ?: setOf(defaultSection)).map { parseJson<Triple<String, String, Long>>(it) }
            val sections = sectionsJson.sortedBy { it.third }.map {
                MainPageData(it.second, it.first)
            }
            registerMainAPI(PornhubOmega(sections))
        }
        val activity = context as AppCompatActivity
        openSettings = {
            val frag = Settings(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }

}