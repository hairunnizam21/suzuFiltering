package com.animedantv.iptv

import android.app.Application

class SuzuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePref.applySavedMode(this)
    }
}
