package dev.sinan.launcher

import android.app.Application
import android.content.res.Configuration
import dev.sinan.launcher.apps.AppRepository

class SinanApplication : Application() {
    private val repository = lazy { AppRepository(this) }
    val apps: AppRepository get() = repository.value

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (repository.isInitialized()) apps.invalidate()
    }
}
