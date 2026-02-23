package com.raccoonsquad.core.log

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Centralized logging system for Raccoon Squad
 * 
 * Features:
 * - Stores logs in memory and file
 * - Accessible from app UI (no root needed)
 * - Auto-rotates log files
 * - Thread-safe
 */
object LogManager {
    
    private const val TAG = "RaccoonLog"
    private const val MAX_LOG_SIZE = 1000
    private const val LOG_FILE_NAME = "raccoon_log.txt"
    private const val MAX_FILE_SIZE = 500 * 1024 // 500KB
    
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    private var logFile: File? = null
    private var isInitialized = false
    
    data class LogEntry(
        val timestamp: Long,
        val level: Int,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        val levelName: String get() = when(level) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "?"
        }
        
        val formattedTime: String get() = 
            SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }
    
    fun init(context: Context) {
        if (isInitialized) return
        
        logFile = File(context.filesDir, LOG_FILE_NAME)
        isInitialized = true
        
        i(TAG, "LogManager initialized")
        i(TAG, "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        i(TAG, "Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        i(TAG, "ROM: ${getRomInfo()}")
    }
    
    fun getLogs(): List<LogEntry> = logs.toList()
    
    fun getLogsAsText(): String {
        return logs.joinToString("\n") { entry ->
            val throwableStr = entry.throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""
            "${entry.formattedTime} ${entry.levelName}/${entry.tag}: ${entry.message}$throwableStr"
        }
    }
    
    fun clearLogs() {
        logs.clear()
        logFile?.delete()
        i(TAG, "Logs cleared")
    }
    
    fun exportLogs(): String {
        val header = buildString {
            append("=== Raccoon Squad VPN Log ===\n")
            append("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
            append("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})\n")
            append("ROM: ${getRomInfo()}\n")
            append("=============================\n\n")
        }
        return header + getLogsAsText()
    }
    
    fun v(tag: String, message: String) {
        Log.v(tag, message)
        addLog(Log.VERBOSE, tag, message)
    }
    
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addLog(Log.DEBUG, tag, message)
    }
    
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        addLog(Log.INFO, tag, message)
    }
    
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        addLog(Log.WARN, tag, message)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        addLog(Log.ERROR, tag, message, throwable)
    }
    
    private fun addLog(level: Int, tag: String, message: String, throwable: Throwable? = null) {
        if (!isInitialized) return
        
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )
        
        if (logs.size >= MAX_LOG_SIZE) {
            logs.poll()
        }
        logs.offer(entry)
        
        writeToLogFile(entry)
    }
    
    private fun writeToLogFile(entry: LogEntry) {
        try {
            logFile?.let { file ->
                if (file.exists() && file.length() > MAX_FILE_SIZE) {
                    val backup = File(file.parent, "${LOG_FILE_NAME}.old")
                    file.renameTo(backup)
                }
                
                val throwableStr = entry.throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""
                val line = "${entry.formattedTime} ${entry.levelName}/${entry.tag}: ${entry.message}$throwableStr\n"
                file.appendText(line)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    fun getRomInfo(): String {
        return buildString {
            val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
            if (miuiVersion.isNotEmpty()) {
                val hyperOsVersion = getSystemProperty("ro.mi.os.version.name")
                if (hyperOsVersion.isNotEmpty()) {
                    append("HyperOS $hyperOsVersion")
                } else {
                    append("MIUI $miuiVersion")
                }
            }
            
            val colorOsVersion = getSystemProperty("ro.build.version.opporom")
            if (colorOsVersion.isNotEmpty()) {
                append("ColorOS $colorOsVersion")
            }
            
            val oneUiVersion = getSystemProperty("ro.build.version.oneui")
            if (oneUiVersion.isNotEmpty()) {
                append("OneUI $oneUiVersion")
            }
            
            val emuiVersion = getSystemProperty("ro.build.version.emui")
            if (emuiVersion.isNotEmpty()) {
                append("EMUI $emuiVersion")
            }
            
            if (isEmpty()) {
                append("AOSP/Stock")
            }
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
