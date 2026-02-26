package com.raccoonsquad.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.raccoonsquad.core.log.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val downloadUrl: String,
    val changelog: String,
    val isUpdateAvailable: Boolean
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_REPO = "shrau77/rsquad"
    private const val CURRENT_VERSION = "1.2.0"
    
    suspend fun checkForUpdates(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            LogManager.i(TAG, "Checking for updates...")
            
            val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
            val connection = url.openConnection()
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val response = connection.getInputStream().bufferedReader().readText()
            val json = JSONObject(response)
            
            val latestVersion = json.optString("tag_name", "v1.0.0").removePrefix("v")
            val downloadUrl = json.optString("html_url", "https://github.com/$GITHUB_REPO/releases")
            val changelog = json.optString("body", "No changelog available")
            
            val isUpdateAvailable = compareVersions(latestVersion, CURRENT_VERSION)
            
            LogManager.i(TAG, "Current: $CURRENT_VERSION, Latest: $latestVersion, Update available: $isUpdateAvailable")
            
            Result.success(UpdateInfo(
                latestVersion = latestVersion,
                currentVersion = CURRENT_VERSION,
                downloadUrl = downloadUrl,
                changelog = changelog,
                isUpdateAvailable = isUpdateAvailable
            ))
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to check for updates", e)
            Result.failure(e)
        }
    }
    
    private fun compareVersions(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            
            for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
                val latestPart = latestParts.getOrElse(i) { 0 }
                val currentPart = currentParts.getOrElse(i) { 0 }
                
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }
    
    fun openDownloadPage(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to open download page", e)
        }
    }
}
