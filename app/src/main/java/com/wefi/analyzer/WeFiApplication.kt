package com.wefi.analyzer

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Custom Application class dengan Global Uncaught Exception Handler
 * untuk menangkap crash runtime, mencatat stack trace lengkap ke file lokal,
 * dan mencegah app ditutup mendadak tanpa jejak diagnostik.
 */
class WeFiApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTraceString = sw.toString()

                Log.e(TAG, "FATAL CRASH on thread ${thread.name}:", throwable)

                // Simpan crash report ke cache aplikasi
                val crashFile = File(filesDir, "latest_crash.txt")
                crashFile.writeText(
                    "Timestamp: ${System.currentTimeMillis()}\n" +
                    "Thread: ${thread.name}\n" +
                    "Exception: ${throwable.javaClass.name}: ${throwable.message}\n" +
                    "Stacktrace:\n$stackTraceString"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menulis crash log", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        const val TAG = "WeFiApplication"
    }
}
