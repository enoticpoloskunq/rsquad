package com.raccoonsquad.core.xray

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Manager for Xray geo data files (geoip.dat, geosite.dat)
 * 
 * These files are required for routing rules like:
 * - Blocking private IPs
 * - China routing
 * - Ad blocking
 */
object GeoDataManager {
    
    private const val TAG = "GeoDataManager"
    
    // Geo data URLs (from Loyalsoldier/v2ray-rules-dat)
    private const val GEOIP_URL = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"
    private const val GEOSITE_URL = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
    
    // Alternative: Official Xray data
    // private const val GEOIP_URL = "https://github.com/XTLS/Xray-core/releases/latest/download/geoip.dat"
    // private const val GEOSITE_URL = "https://github.com/XTLS/Xray-core/releases/latest/download/geosite.dat"
    
    private const val GEOIP_FILENAME = "geoip.dat"
    private const val GEOSITE_FILENAME = "geosite.dat"
    
    /**
     * Check if geo data files exist
     */
    fun hasGeoData(context: Context): Boolean {
        val geoDir = getGeoDir(context)
        val geoip = File(geoDir, GEOIP_FILENAME)
        val geosite = File(geoDir, GEOSITE_FILENAME)
        return geoip.exists() && geosite.exists()
    }
    
    /**
     * Get geo data directory
     */
    fun getGeoDir(context: Context): File {
        return File(context.filesDir, "xray").apply {
            if (!exists()) mkdirs()
        }
    }
    
    /**
     * Download geo data files
     * @return true if successful
     */
    suspend fun downloadGeoData(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val geoDir = getGeoDir(context)
            
            // Download geoip.dat
            Log.i(TAG, "Downloading geoip.dat...")
            downloadFile(GEOIP_URL, File(geoDir, GEOIP_FILENAME))
            
            // Download geosite.dat
            Log.i(TAG, "Downloading geosite.dat...")
            downloadFile(GEOSITE_URL, File(geoDir, GEOSITE_FILENAME))
            
            Log.i(TAG, "Geo data downloaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download geo data", e)
            false
        }
    }
    
    private fun downloadFile(url: String, dest: File) {
        URL(url).openStream().use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }
    
    /**
     * Delete geo data files
     */
    fun clearGeoData(context: Context) {
        val geoDir = getGeoDir(context)
        File(geoDir, GEOIP_FILENAME).delete()
        File(geoDir, GEOSITE_FILENAME).delete()
        Log.i(TAG, "Geo data cleared")
    }
    
    /**
     * Get geo data info
     */
    fun getGeoDataInfo(context: Context): Map<String, String> {
        val geoDir = getGeoDir(context)
        val geoip = File(geoDir, GEOIP_FILENAME)
        val geosite = File(geoDir, GEOSITE_FILENAME)
        
        return mapOf(
            "geoip" to if (geoip.exists()) "${geoip.length() / 1024} KB" else "Not found",
            "geosite" to if (geosite.exists()) "${geosite.length() / 1024} KB" else "Not found"
        )
    }
}
