package com.animedantv.iptv

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Persists the user's preferred theme (system / light / dark) and applies it
 * via [AppCompatDelegate.setDefaultNightMode]. The choice survives restarts via
 * SharedPreferences.
 */
object ThemePref {

    private const val PREFS = "theme_prefs"
    private const val KEY_MODE = "night_mode"

    const val MODE_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    const val MODE_LIGHT = AppCompatDelegate.MODE_NIGHT_NO
    const val MODE_DARK = AppCompatDelegate.MODE_NIGHT_YES

    fun applySavedMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getMode(context))
    }

    fun getMode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MODE, MODE_SYSTEM)

    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MODE, mode)
            .apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
