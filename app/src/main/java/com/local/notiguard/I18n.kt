package com.local.notiguard

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class Lang(val code: String) { VI("vi"), EN("en") }

/**
 * In-app language (Vietnamese / English), switchable at runtime without recreating the activity.
 *
 * [lang] is Compose snapshot state, so every composable that calls [tr] recomposes on change.
 * Non-UI code (ViewModel log, FCM Guard service) reads the current value when it formats text.
 * First launch follows the system locale: Vietnamese → VI, anything else → EN.
 */
object I18n {
    var lang by mutableStateOf(Lang.VI)
        private set

    fun init(context: Context) {
        val saved = prefs(context).getString(KEY_LANG, null)
        lang = Lang.entries.firstOrNull { it.code == saved }
            ?: if (Locale.getDefault().language == "vi") Lang.VI else Lang.EN
    }

    fun set(context: Context, value: Lang) {
        lang = value
        prefs(context).edit().putString(KEY_LANG, value.code).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("notiguard_ui", Context.MODE_PRIVATE)

    private const val KEY_LANG = "lang"
}

/** Picks the string for the current language. */
fun tr(vi: String, en: String): String = if (I18n.lang == Lang.EN) en else vi

/** A string stored in both languages (catalog data); resolved when shown, so it follows [I18n.lang]. */
data class Txt(val vi: String, val en: String) {
    val text get() = tr(vi, en)
    override fun toString() = text
}
