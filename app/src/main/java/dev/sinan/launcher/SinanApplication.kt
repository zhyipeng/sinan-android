package dev.sinan.launcher

import android.app.Application
import android.content.res.Configuration
import dev.sinan.launcher.apps.AppRepository
import dev.sinan.launcher.ball.BallAppearanceStore
import dev.sinan.launcher.ball.BallPositionStore
import dev.sinan.launcher.ball.FloatingBallController

class SinanApplication : Application() {
    private val repository = lazy { AppRepository(this) }
    val apps: AppRepository get() = repository.value
    val ball by lazy { FloatingBallController(this) }
    val ballPositions by lazy { BallPositionStore(this) }
    val ballAppearance by lazy { BallAppearanceStore(this) }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (repository.isInitialized()) apps.invalidate()
    }
}
