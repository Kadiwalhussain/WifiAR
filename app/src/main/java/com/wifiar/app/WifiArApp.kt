package com.wifiar.app

import android.app.Application
import com.wifiar.app.data.UserPreferences
import com.wifiar.app.data.auth.TokenStore
import com.wifiar.app.data.sync.SyncManager

class WifiArApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UserPreferences.init(this)
        // If already signed in, try uploading any pending sessions when network is up.
        if (TokenStore(this).isLoggedIn) {
            SyncManager(this).enqueuePendingSessions()
        }
    }
}

