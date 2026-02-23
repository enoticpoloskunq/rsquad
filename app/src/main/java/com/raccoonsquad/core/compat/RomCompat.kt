package com.raccoonsquad.core.compat

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Process
import com.raccoonsquad.core.log.LogManager

/**
 * Compatibility utilities for different Android ROMs
 * 
 * Handles quirks and workarounds for:
 * - MIUI / HyperOS (Xiaomi)
 * - ColorOS (OPPO/Realme)
 * - OneUI (Samsung)
 * - EMUI (Huawei)
 * - Flyme (Meizu)
 * - Stock AOSP
 */
object RomCompat {
    
    private const val TAG = "RomCompat"
    
    enum class RomType {
        MIUI, HYPEROS, COLOROS, ONEUI, EMUI, FLYME, STOCK
    }
    
    val romType: RomType by lazy { detectRom() }
    val romName: String by lazy { LogManager.getRomInfo() }
    
    private fun detectRom(): RomType {
        val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
        if (miuiVersion.isNotEmpty()) {
            val hyperOsVersion = getSystemProperty("ro.mi.os.version.name")
            return if (hyperOsVersion.isNotEmpty()) RomType.HYPEROS else RomType.MIUI
        }
        
        val colorOsVersion = getSystemProperty("ro.build.version.opporom")
        if (colorOsVersion.isNotEmpty()) return RomType.COLOROS
        
        val oneUiVersion = getSystemProperty("ro.build.version.oneui")
        if (oneUiVersion.isNotEmpty()) return RomType.ONEUI
        
        val emuiVersion = getSystemProperty("ro.build.version.emui")
        if (emuiVersion.isNotEmpty()) return RomType.EMUI
        
        val flymeVersion = getSystemProperty("ro.build.display.id")
        if (flymeVersion.contains("Flyme", ignoreCase = true)) return RomType.FLYME
        
        return RomType.STOCK
    }
    
    fun prepareVpn(activity: Activity, requestCode: Int): Boolean {
        LogManager.i(TAG, "Preparing VPN for ROM: $romName")
        
        return try {
            val intent = VpnService.prepare(activity)
            if (intent != null) {
                LogManager.i(TAG, "VPN permission required, launching intent")
                
                when (romType) {
                    RomType.MIUI, RomType.HYPEROS -> {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    RomType.COLOROS -> {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    else -> {}
                }
                
                activity.startActivityForResult(intent, requestCode)
                false
            } else {
                LogManager.i(TAG, "VPN permission already granted")
                true
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to prepare VPN", e)
            false
        }
    }
    
    fun getRecommendedMtu(): Int {
        return when (romType) {
            RomType.MIUI, RomType.HYPEROS -> 1400
            RomType.COLOROS -> 1400
            RomType.ONEUI -> 1500
            RomType.EMUI -> 1400
            RomType.FLYME -> 1400
            RomType.STOCK -> 1500
        }
    }
    
    fun needsBatteryOptimizationDisable(): Boolean {
        return when (romType) {
            RomType.MIUI, RomType.HYPEROS -> true
            RomType.COLOROS -> true
            RomType.EMUI -> true
            RomType.FLYME -> true
            else -> false
        }
    }
    
    fun getPowerManagementWarning(): String? {
        return when (romType) {
            RomType.MIUI, RomType.HYPEROS -> 
                "MIUI/HyperOS может убивать VPN сервис. Добавьте приложение в исключения энергосбережения."
            RomType.COLOROS -> 
                "ColorOS агрессивно управляет питанием. Добавьте приложение в список разрешённых."
            RomType.EMUI -> 
                "EMUI может ограничивать VPN. Отключите оптимизацию батареи для приложения."
            else -> null
        }
    }
    
    fun applyProcessOptimizations() {
        LogManager.i(TAG, "Applying process optimizations for: $romName")
        
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to set thread priority", e)
        }
    }
    
    private fun getSystemProperty(key: String): String {
        return try {
            val props = Class.forName("android.os.SystemProperties")
            val method = props.getMethod("get", String::class.java)
            method.invoke(null, key) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
