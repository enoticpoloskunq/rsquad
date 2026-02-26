package com.raccoonsquad.core.splittunneling

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.raccoonsquad.core.log.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Split Tunneling - choose which apps go through VPN
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?,
    val isSystemApp: Boolean
)

enum class SplitTunnelingMode {
    DISABLED,           // All traffic through VPN
    INCLUDE_MODE,       // Only selected apps through VPN
    EXCLUDE_MODE        // All except selected apps through VPN
}

data class SplitTunnelingConfig(
    val mode: SplitTunnelingMode,
    val selectedApps: Set<String>,  // Package names
    val isLowBatteryMode: Boolean
)

object SplitTunnelingManager {
    private const val TAG = "SplitTunneling"
    
    private val _config = MutableStateFlow(SplitTunnelingConfig(
        mode = SplitTunnelingMode.DISABLED,
        selectedApps = emptySet(),
        isLowBatteryMode = false
    ))
    val config: StateFlow<SplitTunnelingConfig> = _config.asStateFlow()
    
    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()
    
    fun loadInstalledApps(packageManager: PackageManager) {
        try {
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { appInfo ->
                    // Filter out system apps that aren't useful for VPN tunneling
                    appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                    // But keep some system apps that users might want
                    appInfo.packageName in listOf(
                        "com.android.chrome",
                        "com.google.android.youtube",
                        "com.google.android.gm",
                        "com.whatsapp",
                        "com.telegram.messenger",
                        "com.instagram.android",
                        "com.facebook.katana",
                        "com.twitter.android"
                    )
                }
                .map { appInfo ->
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = packageManager.getApplicationLabel(appInfo).toString(),
                        icon = packageManager.getApplicationIcon(appInfo),
                        isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    )
                }
                .sortedBy { it.appName.lowercase() }
            
            _installedApps.value = apps
            LogManager.i(TAG, "Loaded ${apps.size} installed apps")
            
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to load installed apps", e)
        }
    }
    
    fun setMode(mode: SplitTunnelingMode) {
        _config.value = _config.value.copy(mode = mode)
        LogManager.i(TAG, "Split tunneling mode set to: $mode")
    }
    
    fun toggleApp(packageName: String) {
        val currentApps = _config.value.selectedApps
        val newApps = if (packageName in currentApps) {
            currentApps - packageName
        } else {
            currentApps + packageName
        }
        _config.value = _config.value.copy(selectedApps = newApps)
        LogManager.i(TAG, "Toggled app: $packageName, total selected: ${newApps.size}")
    }
    
    fun selectAllApps() {
        _config.value = _config.value.copy(
            selectedApps = _installedApps.value.map { it.packageName }.toSet()
        )
        LogManager.i(TAG, "Selected all apps")
    }
    
    fun clearSelectedApps() {
        _config.value = _config.value.copy(selectedApps = emptySet())
        LogManager.i(TAG, "Cleared selected apps")
    }
    
    fun setLowBatteryMode(enabled: Boolean) {
        _config.value = _config.value.copy(isLowBatteryMode = enabled)
        LogManager.i(TAG, "Low battery mode: $enabled")
    }
    
    fun shouldTunnel(packageName: String): Boolean {
        val cfg = _config.value
        return when (cfg.mode) {
            SplitTunnelingMode.DISABLED -> true
            SplitTunnelingMode.INCLUDE_MODE -> packageName in cfg.selectedApps
            SplitTunnelingMode.EXCLUDE_MODE -> packageName !in cfg.selectedApps
        }
    }
    
    fun getExcludedPackages(): Set<String> {
        val cfg = _config.value
        return when (cfg.mode) {
            SplitTunnelingMode.DISABLED -> emptySet()
            SplitTunnelingMode.INCLUDE_MODE -> 
                _installedApps.value.map { it.packageName }.toSet() - cfg.selectedApps
            SplitTunnelingMode.EXCLUDE_MODE -> cfg.selectedApps
        }
    }
    
    fun getIncludedPackages(): Set<String> {
        val cfg = _config.value
        return when (cfg.mode) {
            SplitTunnelingMode.DISABLED -> 
                _installedApps.value.map { it.packageName }.toSet()
            SplitTunnelingMode.INCLUDE_MODE -> cfg.selectedApps
            SplitTunnelingMode.EXCLUDE_MODE -> 
                _installedApps.value.map { it.packageName }.toSet() - cfg.selectedApps
        }
    }
    
    // Common app presets
    data class AppPreset(val name: String, val packages: Set<String>)
    
    val presets = listOf(
        AppPreset(
            "Соцсети",
            setOf(
                "com.instagram.android",
                "com.facebook.katana",
                "com.twitter.android",
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill"
            )
        ),
        AppPreset(
            "Мессенджеры",
            setOf(
                "com.whatsapp",
                "com.telegram.messenger",
                "com.viber.voip",
                "com.discord",
                "com.snapchat.android"
            )
        ),
        AppPreset(
            "Медиа",
            setOf(
                "com.google.android.youtube",
                "com.netflix.mediaclient",
                "com.spotify.music",
                "com.google.android.apps.youtube.music"
            )
        ),
        AppPreset(
            "Браузеры",
            setOf(
                "com.android.chrome",
                "org.mozilla.firefox",
                "com.opera.browser",
                "com.brave.browser"
            )
        )
    )
    
    fun applyPreset(preset: AppPreset) {
        val currentApps = _config.value.selectedApps.toMutableSet()
        currentApps.addAll(preset.packages)
        _config.value = _config.value.copy(selectedApps = currentApps)
        LogManager.i(TAG, "Applied preset: ${preset.name}")
    }
}
