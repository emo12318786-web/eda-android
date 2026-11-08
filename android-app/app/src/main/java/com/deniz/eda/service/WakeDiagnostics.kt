package com.deniz.eda.service

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Private on-device diagnostics for wake-engine and process failures. */
object WakeDiagnostics {
    private const val TAG = "EdaWakeDiagnostics"
    private const val CRASH_FILE = "last_crash.txt"
    private const val STEPS_FILE = "wake_steps.txt"
    @Volatile private var installed = false

    @Synchronized
    fun install(context: Context) {
        val app = context.applicationContext
        if (installed) return
        installed = true
        recordHistoricalExit(app)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))
                File(app.filesDir, CRASH_FILE).appendText(
                    "\n=== UNCAUGHT ${timestamp()} thread=${thread.name} ===\n${writer}\n"
                )
            } catch (writeError: Throwable) {
                Log.e(TAG, "Could not persist uncaught crash", writeError)
            } finally {
                previous?.uncaughtException(thread, error)
            }
        }
        breadcrumb(app, "crash reporter installed")
    }

    fun breadcrumb(context: Context, step: String) {
        try {
            File(context.applicationContext.filesDir, STEPS_FILE)
                .appendText("${timestamp()} | ${step.replace('\n', ' ')}\n")
        } catch (error: Throwable) {
            Log.w(TAG, "Could not write wake breadcrumb", error)
        }
    }

    fun readSteps(context: Context): String = read(context, STEPS_FILE)
    fun readCrash(context: Context): String = read(context, CRASH_FILE)

    private fun read(context: Context, name: String): String = try {
        File(context.applicationContext.filesDir, name).takeIf { it.exists() }?.readText()
            ?.takeLast(12000).orEmpty().ifBlank { "Henüz kayıt yok." }
    } catch (error: Throwable) {
        "Kayıt okunamadı: ${error.message}"
    }

    private fun recordHistoricalExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exit = manager.getHistoricalProcessExitReasons(context.packageName, 0, 1)
                .firstOrNull() ?: return
            val reason = when (exit.reason) {
                4 -> "CRASH"
                5 -> "CRASH_NATIVE"
                6 -> "ANR"
                else -> "reason=${exit.reason}"
            }
            File(context.filesDir, CRASH_FILE).appendText(
                "\n=== HISTORICAL EXIT ${timestamp()} ===\nreason=${reason}\n" +
                    "timestamp=${exit.timestamp}\ndescription=${exit.description ?: "(none)"}\n"
            )
            breadcrumb(context, "historical process exit read: $reason")
        } catch (error: Throwable) {
            Log.w(TAG, "Could not read historical process exit", error)
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
