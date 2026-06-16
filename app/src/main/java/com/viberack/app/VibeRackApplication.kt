package com.viberack.app

import android.app.Application
import com.viberack.app.core.AppContainer

class VibeRackApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }
}
