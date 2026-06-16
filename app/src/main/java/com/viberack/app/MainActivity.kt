package com.viberack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.viberack.app.core.i18n.AppLanguageManager
import com.viberack.app.ui.VibeRackApp
import com.viberack.app.ui.theme.VibeRackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val appContainer = (application as VibeRackApplication).appContainer
        val languageTag = runBlocking {
            appContainer.userPreferencesRepository.preferences.first().appLanguageTag
        }
        AppLanguageManager.applyLanguage(this, languageTag)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            VibeRackTheme {
                VibeRackApp()
            }
        }
    }
}
