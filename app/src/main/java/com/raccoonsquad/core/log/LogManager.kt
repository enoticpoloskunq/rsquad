package com.raccoonsquad.core.log

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Centralized logging system for Raccoon Squad
 * 
 * Features:
 * - Stores logs in memory and file (SYNC for crash safety)
 * - Accessible from app UI (no root needed)
 * - Thread-safe
 */
object LogManager {
    
    private const val TAG = "RaccoonLog"
    private const val MAX_LOG_SIZE = 2000
    private const val LOG_FILE_NAME = "raccoon_log.txt"
    private const val MAX_FILE_SIZE = 1024 * 1024 // 1MB
    
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    
    private var logFile: File? = null
    private var fileWriter: FileWriter? = null
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
        
        try {
            logFile = File(context.filesDir, LOG_FILE_NAME)
            
            // Rotate if too large
            if (logFile!!.exists() && logFile!!.length() > MAX_FILE_SIZE) {
                val backup = File(context.filesDir, "${LOG_FILE_NAME}.old")
                logFile!!.renameTo(backup)
                logFile = File(context.filesDir, LOG_FILE_NAME)
            }
            
            // Open file writer in append mode, auto-flush
            fileWriter = FileWriter(logFile!!, true)
            
            isInitialized = true
            
            i(TAG, "LogManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init LogManager", e)
        }
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
        try {
            fileWriter?.close()
            logFile?.delete()
            fileWriter = FileWriter(logFile!!, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
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
    
    /**
     * Flush logs to disk immediately (call before potential crash)
     */
    fun flush() {
        try {
            fileWriter?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush logs", e)
        }
    }
    
    private fun addLog(level: Int, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )
        
        // Add to memory
        if (logs.size >= MAX_LOG_SIZE) {
            logs.poll()
        }
        logs.offer(entry)
        
        // Write to file SYNC (for crash safety)
        writeToFileSync(entry)
    }
    
    private fun writeToFileSync(entry: LogEntry) {
        try {
            val throwableStr = entry.throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""
            val line = "${entry.formattedTime} ${entry.levelName}/${entry.tag}: ${entry.message}$throwableStr\n"
            
            fileWriter?.apply {
                write(line)
                flush() // SYNC write for crash safety
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
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
