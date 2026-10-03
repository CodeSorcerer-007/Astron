package com.astron.app

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AstronApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Global crash guard to prevent hard app crashes and ensure structured crash diagnostics
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("AstronCrash", "Uncaught fatal exception on thread ${thread.name}: ${throwable.message}", throwable)
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
