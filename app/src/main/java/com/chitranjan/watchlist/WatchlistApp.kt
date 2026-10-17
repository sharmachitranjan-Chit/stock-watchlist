package com.chitranjan.watchlist

import android.app.Application
import com.chitranjan.watchlist.data.CrashLog

/** Starts the crash recorder before anything else runs. */
class WatchlistApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
