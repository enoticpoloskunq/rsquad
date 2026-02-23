package com.raccoonsquad

import android.app.Application
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.core.compat.RomCompat

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize logging system
        LogManager.init(this)
        LogManager.i("App", "=== Raccoon Squad VPN Starting ===")
        LogManager.i("App", "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        LogManager.i("App", "ROM: ${RomCompat.romName}")
        
        // Apply ROM-specific optimizations
        RomCompat.applyProcessOptimizations()
        
        LogManager.i("App", "Initialization complete")
    }
}
