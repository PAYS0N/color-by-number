package com.colorbynumber.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Global app settings persisted across puzzles via SharedPreferences.
 * Call [init] once from Application or MainActivity before accessing any property.
 */
object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_PREVENT_ERRORS = "prevent_errors"
    private const val KEY_PREVENT_OVERWRITE = "prevent_overwrite"
    private const val KEY_VIBRATE = "vibrate"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var preventErrors: Boolean
        get() = prefs.getBoolean(KEY_PREVENT_ERRORS, true)
        set(value) { prefs.edit().putBoolean(KEY_PREVENT_ERRORS, value).apply() }

    var preventOverwrite: Boolean
        get() = prefs.getBoolean(KEY_PREVENT_OVERWRITE, true)
        set(value) { prefs.edit().putBoolean(KEY_PREVENT_OVERWRITE, value).apply() }

    var vibrate: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) { prefs.edit().putBoolean(KEY_VIBRATE, value).apply() }


}
