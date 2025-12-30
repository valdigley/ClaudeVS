package com.valdigley.claudevs.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CrashLogger {
    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.getExternalFilesDir(null), "claudevs_log.txt")

        // Set uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log("CRASH", "Uncaught exception in ${thread.name}: ${throwable.message}")
            log("CRASH", throwable.stackTraceToString())
            defaultHandler?.uncaughtException(thread, throwable)
        }

        log("INFO", "App started")
    }

    fun log(tag: String, message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logLine = "[$timestamp] [$tag] $message\n"
            logFile?.appendText(logLine)
            android.util.Log.d("ClaudeVS", "[$tag] $message")
        } catch (e: Exception) {
            android.util.Log.e("ClaudeVS", "Failed to write log: ${e.message}")
        }
    }

    fun getLogContent(): String {
        return try {
            logFile?.readText() ?: "No log file"
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    fun clearLog() {
        try {
            logFile?.writeText("")
        } catch (e: Exception) {
            // ignore
        }
    }
}
