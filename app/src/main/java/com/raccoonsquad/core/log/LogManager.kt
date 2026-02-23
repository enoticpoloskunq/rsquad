package com.raccoonsquad.core.log

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Centralized logging with crash handler
 */
object LogManager {
    
    private const val TAG = "RaccoonLog"
    private const val MAX_LOG_SIZE = 2000
    private const val LOG_FILE_NAME = "raccoon_log.txt"
    private const val CRASH_FILE_NAME = "raccoon_crash.txt"
    private const val MAX_FILE_SIZE = 1024 * 1024
    
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    
    private var logFile: File? = null
    private var crashFile: File? = null
    private var fileWriter: FileWriter? = null
    private var isInitialized = false
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    
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
            crashFile = File(context.filesDir, CRASH_FILE_NAME)
            
            if (logFile!!.exists() && logFile!!.length() > MAX_FILE_SIZE) {
                val backup = File(context.filesDir, "${LOG_FILE_NAME}.old")
                logFile!!.renameTo(backup)
                logFile = File(context.filesDir, LOG_FILE_NAME)
            }
            
            fileWriter = FileWriter(logFile!!, true)
            
            // Install crash handler FIRST
            installCrashHandler()
            
            isInitialized = true
            
            i(TAG, "LogManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init LogManager", e)
        }
    }
    
    /**
     * Install global crash handler to catch native crashes
     */
    private fun installCrashHandler() {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Write crash to file immediately
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println("=== CRASH at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())} ===")
                pw.println("Thread: ${thread.name} (${thread.id})")
                pw.println()
                throwable.printStackTrace(pw)
                pw.println()
                pw.println("=== Recent logs ===")
                pw.println(getLogsAsText())
                pw.flush()
                
                crashFile?.writeText(sw.toString())
                
                // Also try to flush main log
                fileWriter?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash log", e)
            }
            
            // Call default handler
            defaultHandler?.uncaughtException(thread, throwable) 
                ?: Process.killProcess(Process.myPid())
        }
        
        i(TAG, "Crash handler installed")
    }
    
    /**
     * Check if there was a crash last time
     */
    fun hasLastCrash(): Boolean {
        return crashFile?.exists() == true && crashFile!!.length() > 0
    }
    
    /**
     * Get last crash log
     */
    fun getLastCrash(): String? {
        return try {
            crashFile?.takeIf { it.exists() }?.readText()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Clear crash log
     */
    fun clearCrash() {
        crashFile?.delete()
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
        
        // Include crash if exists
        val crash = getLastCrash()
        val crashSection = if (crash != null) {
            "\n\n=== LAST CRASH ===\n$crash\n"
        } else ""
        
        return header + getLogsAsText() + crashSection
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
    
    fun flush() {
        try {
            fileWriter?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush", e)
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
        
        if (logs.size >= MAX_LOG_SIZE) {
            logs.poll()
        }
        logs.offer(entry)
        
        writeToFileSync(entry)
    }
    
    private fun writeToFileSync(entry: LogEntry) {
        try {
            val throwableStr = entry.throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: ""
            val line = "${entry.formattedTime} ${entry.levelName}/${entry.tag}: ${entry.message}$throwableStr\n"
            
            fileWriter?.apply {
                write(line)
                flush()
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
