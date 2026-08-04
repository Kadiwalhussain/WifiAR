package com.wifiar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wifiar.app.data.UserPreferences
import com.wifiar.app.ui.AppShell
import com.wifiar.app.ui.OnboardingScreen
import com.wifiar.app.ui.theme.WifiARTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch from splash theme to normal once Activity is ready.
        setTheme(R.style.Theme_WifiAR)
        super.onCreate(savedInstanceState)
        UserPreferences.init(this)
        enableEdgeToEdge()
        setContent {
            WifiARTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showOnboarding by remember {
                        mutableStateOf(!UserPreferences.onboardingDone)
                    }
                    if (showOnboarding) {
                        OnboardingScreen(onFinished = { showOnboarding = false })
                    } else {
                        AppShell()
                    }
                }
            }
        }
    }
}
