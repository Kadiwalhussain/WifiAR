package com.wifiar.app

import android.app.Application
import android.util.Log
import com.wifiar.app.data.UserPreferences
import com.wifiar.app.data.auth.TokenStore
import com.wifiar.app.data.sync.SyncManager

class WifiArApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UserPreferences.init(this)

        // Last-resort: log uncaught errors instead of silent death with no diagnostics.
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e(TAG, "Uncaught on ${thread.name}", error)
            prior?.uncaughtException(thread, error)
        }

        runCatching {
            if (TokenStore(this).isLoggedIn) {
                SyncManager(this).enqueuePendingSessions()
            }
        }.onFailure {
            Log.w(TAG, "Startup sync skipped: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "WifiArApp"
    }
}
