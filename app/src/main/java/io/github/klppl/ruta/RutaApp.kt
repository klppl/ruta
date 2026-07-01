package io.github.klppl.ruta

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import io.github.klppl.ruta.blocking.BlocklistRepository
import io.github.klppl.ruta.browser.BrowserEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RutaApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var blocklistRepository: BlocklistRepository
    @Inject lateinit var engine: BrowserEngine

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Warm the blocklists from cache and schedule periodic refresh.
        blocklistRepository.initialize()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Each docked site is a live Chromium renderer; under real pressure drop all but the
        // active one instead of letting the OS kill the whole app. Tabs recreate lazily from
        // their saved URL, so only in-page position is lost.
        if (level == TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_MODERATE) {
            engine.trimInactiveWebViews()
        }
    }
}
