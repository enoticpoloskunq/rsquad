package com.raccoonsquad

import android.app.Application
import android.os.Environment
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.core.compat.RomCompat
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize logging system FIRST
        LogManager.init(this)
        LogManager.i("App", "=== Raccoon Squad VPN Starting ===")
        LogManager.i("App", "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        LogManager.i("App", "ROM: ${RomCompat.romName}")
        
        // Export crash log to Downloads if exists (accessible without root!)
        exportCrashLogToDownloads()
        
        RomCompat.applyProcessOptimizations()
        
        LogManager.i("App", "Initialization complete")
        LogManager.flush()
    }
    
    /**
     * Export crash log to Downloads folder (accessible without root)
     */
    private fun exportCrashLogToDownloads() {
        try {
            if (LogManager.hasLastCrash()) {
                LogManager.i("App", "Found crash log, exporting to Downloads...")
                
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val crashFile = File(downloadsDir, "raccoon_crash_${System.currentTimeMillis()}.txt")
                
                val crashLog = LogManager.getLastCrash()
                crashFile.writeText(crashLog ?: "")
                
                LogManager.i("App", "Crash log exported to: ${crashFile.absolutePath}")
                
                // Also export full logs
                val logFile = File(downloadsDir, "raccoon_full_log_${System.currentTimeMillis()}.txt")
                logFile.writeText(LogManager.exportLogs())
                
                LogManager.i("App", "Full log exported to: ${logFile.absolutePath}")
                LogManager.flush()
            }
        } catch (e: Exception) {
            LogManager.e("App", "Failed to export crash log", e)
            LogManager.flush()
        }
    }
}
