package com.aispend.platform

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/** Set from MainActivity before composing content. */
object AndroidAppContext {
    var context: Context? = null
}

actual fun platformSettings(): Settings {
    val ctx = AndroidAppContext.context
        ?: error("AndroidAppContext.context not initialised; set it in MainActivity.onCreate")
    val prefs = ctx.getSharedPreferences("ai_spend", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(prefs)
}
