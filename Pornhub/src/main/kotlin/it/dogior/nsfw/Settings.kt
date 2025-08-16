@file:Suppress("ObjectLiteralToLambda")

package it.dogior.nsfw

import com.lagradost.cloudstream3.app
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.generateViewId
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.RowdyAvocado.BuildConfig
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * A simple [Fragment] subclass.
 * Use the [Settings] factory method to
 * create an instance of this fragment.
 */
class Settings(private val plugin: PornhubPlugin, val sharedPref: SharedPreferences?) :
    BottomSheetDialogFragment() {

    private fun <T : View> View.findView(name: String): T {
        val id = plugin.resources!!.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }

    private fun View.makeTvCompatible() {
        this.setPadding(
            this.paddingLeft + 10,
            this.paddingTop + 10,
            this.paddingRight + 10,
            this.paddingBottom + 10
        )
        this.background = getDrawable("outline")
    }

    private fun getDrawable(name: String): Drawable? {
        val id =
            plugin.resources!!.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return ResourcesCompat.getDrawable(plugin.resources!!, id, null)
    }

    private fun getString(name: String): String? {
        val id =
            plugin.resources!!.getIdentifier(name, "string", BuildConfig.LIBRARY_PACKAGE_NAME)
        return plugin.resources!!.getString(id)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        val id = plugin.resources!!.getIdentifier(
            "homepage_settings",
            "layout",
            BuildConfig.LIBRARY_PACKAGE_NAME
        )
        val layout = plugin.resources!!.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    @OptIn(DelicateCoroutinesApi::class)
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val headerTw = view.findView<TextView>("header_tw")
        headerTw.text = getString("header_tw")

        val omegaSwitch = view.findView<Switch>("omega_switch")
        omegaSwitch.text = getString("omega_switch")
        omegaSwitch.isChecked = sharedPref?.getBoolean("omega", true) ?: true

        val addSectionTw = view.findView<TextView>("addSection_tw")
        addSectionTw.text = getString("addSection_tw")

        val urlEt = view.findView<TextView>("url_editText")
        urlEt.hint = getString("add_url_hint")

        var sectionsSet = mutableSetOf<String>()
        sharedPref?.getStringSet("sections", emptySet())?.let {
            sectionsSet.addAll(it)
            Log.d("PornhubSettings", "Sections: $sectionsSet")
        }
        val sectionsList = view.findView<LinearLayout>("sections_list")

        val tripleList = sectionsSet.map {
            parseJson<Triple<String, String, Long>>(it)
        }.sortedBy { it.third }
        tripleList.forEach {
            sectionsList.addView(
                customRow(it, sharedPref, sectionsSet, sectionsList)
            )
        }


        val addSectionButton = view.findView<ImageButton>("addSection_button")
        addSectionButton.setImageDrawable(getDrawable("add_icon"))
        addSectionButton.makeTvCompatible()

        addSectionButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                addSectionButton.isClickable = false
                GlobalScope.launch {
                    val now = System.currentTimeMillis()
                    val item = try {
                        withContext(Dispatchers.IO) {
                            var url = urlEt.text.toString()
                            if(url.toHttpUrlOrNull() == null) {
                                showToast("Url not valid")
                                return@withContext null
                            }
                            if (!url.contains("/videos?page=")) url += "/videos?page="
                            Triple(
                                url,
                                getName(urlEt.text.toString()),
                                now
                            ).toJson()
                        }
                    } catch (e: NoSuchMethodError) {
                        addSectionButton.isClickable = true
                        showToast("Error")
                        return@launch
                    }
                    if(item == null) return@launch

                    sharedPref?.getStringSet("sections", emptySet())?.let {
                        sectionsSet = mutableSetOf()
                        sectionsSet.addAll(it)
                        sectionsSet.add(item)
                    }
                    with(sharedPref?.edit()) {
                        this?.putStringSet("sections", sectionsSet)
                        this?.apply()
                    }
                    withContext(Dispatchers.Main) {
                        urlEt.text = ""
                        addSectionButton.isClickable = true
                        sectionsList.addView(
                            customRow(
                                item,
                                sharedPref,
                                sectionsSet,
                                sectionsList
                            )
                        )
                    }
                }
            }
        })

        val saveButton = view.findView<ImageButton>("save_button")
        saveButton.setImageDrawable(getDrawable("save_icon"))
        saveButton.makeTvCompatible()

        saveButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                with(sharedPref?.edit()) {
                    this?.putBoolean("omega", omegaSwitch.isChecked)
                    this?.apply()
                }
                dismiss()
            }
        })
    }

    private fun customRow(
        itemjson: String,
        sharedPref: SharedPreferences?,
        playlistsSet: MutableSet<String>,
        playlistList: LinearLayout,
    ): RelativeLayout {
        val item = parseJson<Triple<String, String, Long>>(itemjson)
        return customRow(item, sharedPref, playlistsSet, playlistList)
    }

    private fun customRow(
        item: Triple<String, String, Long>,
        sharedPref: SharedPreferences?,
        playlistsSet: MutableSet<String>,
        sectionList: LinearLayout,
    ): RelativeLayout {
        val title = item.second
        // Create the RelativeLayout
        val relativeLayout = RelativeLayout(this@Settings.requireContext()).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setPadding(
                    0,
                    0,
                    0,
                    dpToPx(this@Settings.requireContext(), 8)
                ) // Convert dp to px
            }
        }

        // Create the TextView (Label)
        val label = TextView(this.context).apply {
            text = title
            textSize = 15f
        }

        val labelParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.CENTER_VERTICAL) // Vertically center in parent
            addRule(RelativeLayout.ALIGN_PARENT_START)
            marginEnd = dpToPx(this@Settings.requireContext(), 8)
        }


        // Create the ImageButton
        val deleteButton = ImageButton(this.context).apply {
            id = generateViewId()
            setImageDrawable(getDrawable("delete_icon"))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
        }

        val buttonParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_END)
            addRule(RelativeLayout.CENTER_VERTICAL) // Vertically center in parent
            marginEnd = dpToPx(this@Settings.requireContext(), 8) // Convert dp to px
        }

        relativeLayout.addView(label, labelParams)
        relativeLayout.addView(deleteButton, buttonParams)

        val delete = relativeLayout.findViewById<ImageButton>(deleteButton.id)
        delete.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val deleteSuccessfull = playlistsSet.remove(item.toJson())
                if (deleteSuccessfull) {
                    with(sharedPref?.edit()) {
                        this?.putStringSet("sections", playlistsSet)
                        this?.apply()
                    }
                    sectionList.removeView(relativeLayout)
                    showToast("$title removed")
                }
            }
        })
        return relativeLayout
    }

    private suspend fun getName(url: String): String? {
        val document = app.get(url).document
        val type = if (url.contains("/model/") || url.contains("/pornstar/")) "model" else if(url.contains("/channels/")) "channel" else return "Unknown"
        val selector = if (type == "model") "div.name  h1" else "div.title > h1"
        val title = document.select(selector).text()
        return title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}