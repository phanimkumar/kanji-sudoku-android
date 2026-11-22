package com.kaajjo.libresudoku

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.kaajjo.libresudoku.daily.PlayGamesHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LibreSudokuApp : Application(), Configuration.Provider {

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        PlayGamesHelper.initialize(this)
        // AdMob is no longer initialised here. It is initialised by
        // AdManager.initOnce() from MainActivity, after the consent (UMP) check,
        // and on a background thread.
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()
}
