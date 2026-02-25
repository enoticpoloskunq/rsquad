package com.raccoonsquad

import android.app.Application
import android.content.SharedPreferences
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.core.compat.RomCompat
import com.raccoonsquad.core.xray.XrayWrapper

class App : Application() {
    
    companion object {
        lateinit var prefs: SharedPreferences private set
        var hasPendingCrashExport = false
            private set
        
        fun markCrashExported() {
            hasPendingCrashExport = false
            prefs.edit().putBoolean("crash_exported", true).apply()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        prefs = getSharedPreferences("raccoon_prefs", MODE_PRIVATE)
        
        // Initialize logging system FIRST
        LogManager.init(this)
        LogManager.i("App", "=== Raccoon Squad VPN Starting ===")
        LogManager.i("App", "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        LogManager.i("App", "ROM: ${RomCompat.romName}")
        
        // Check for crash
        if (LogManager.hasLastCrash()) {
            val wasExported = prefs.getBoolean("crash_exported", false)
            if (!wasExported) {
                hasPendingCrashExport = true
                LogManager.i("App", "Found unexported crash log!")
            } else {
                LogManager.i("App", "Crash log already exported, clearing...")
                LogManager.clearCrash()
            }
        }
        
        RomCompat.applyProcessOptimizations()
        
        // Initialize Xray core (loads geoip.dat, geosite.dat)
        try {
            XrayWrapper.init(this)
        } catch (e: Exception) {
            LogManager.e("App", "Failed to initialize Xray", e)
        }
        
        LogManager.i("App", "Initialization complete")
        LogManager.flush()
    }
}
