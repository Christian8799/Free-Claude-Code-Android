package com.freeclaudecode.android

import android.app.Application
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

class FreeClaudeCodeApplication : Application() {
    companion object {
        lateinit var instance: FreeClaudeCodeApplication
            private set

        // Live log file path
        val logFile: File
            get() = File(instance.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: instance.filesDir, "app_logs.txt")

        // Queue for thread-safe logging
        private val logQueue = ConcurrentLinkedQueue<String>()
        private var isWriting = false
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        setupCrashHandler()
        startLogWriter()
        logLive("=== App started ===")
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            logCrash(e)
            defaultHandler?.uncaughtException(t, e)
        }
    }

    private fun logCrash(e: Throwable) {
        thread {
            try {
                val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
                val crashFile = File(dir, "crash_logs.txt")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                val writer = FileWriter(crashFile, true)
                writer.write("\n========== CRASH $timestamp ==========\n")
                val pw = PrintWriter(writer)
                e.printStackTrace(pw)
                pw.flush()
                writer.close()

                Log.e("CrashHandler", "Crash logged to ${crashFile.absolutePath}", e)
            } catch (ex: Exception) {
                Log.e("CrashHandler", "Failed to write crash log", ex)
            }
        }
    }

    // Call this from anywhere to log live to file
    fun logLive(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        logQueue.offer("[$timestamp] $message")
    }

    private fun startLogWriter() {
        thread(isDaemon = true) {
            while (true) {
                try {
                    val msg = logQueue.poll()
                    if (msg != null) {
                        writeToLogFile(msg)
                    } else {
                        Thread.sleep(100) // Batch writes
                    }
                } catch (e: Exception) {
                    Log.e("LogWriter", "Error in log writer", e)
                    try { Thread.sleep(1000) } catch (ie: InterruptedException) {}
                }
            }
        }
    }

    private fun writeToLogFile(message: String) {
        try {
            val writer = FileWriter(logFile, true)
            writer.write("$message\n")
            writer.flush()
            writer.close()
        } catch (e: Exception) {
            Log.e("LogWriter", "Failed to write log", e)
        }
    }

    // Force flush all queued logs
    fun flushLogs() {
        while (!logQueue.isEmpty()) {
            val msg = logQueue.poll()
            msg?.let { writeToLogFile(it) }
        }
    }
}