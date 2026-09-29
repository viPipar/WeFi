package com.wefi.analyzer

import android.app.Application
import android.os.Process
import android.util.Log
import com.wefi.analyzer.ui.screens.diagnostic.DiagnosticCrashActivity
import com.wefi.analyzer.ui.screens.diagnostic.DiagnosticUtils
import java.io.File
import kotlin.system.exitProcess

/**
 * Custom Application class dengan Global Uncaught Exception Handler
 * untuk menangkap crash runtime, mencatat stack trace lengkap ke file lokal,
 * dan mengalihkan ke DiagnosticCrashActivity agar Android OS tidak pernah
 * menampilkan dialog "Aplikasi ditutup karena memiliki bug".
 */
class WeFiApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "FATAL CRASH on thread ${thread.name}:", throwable)

                // Simpan crash report ke cache aplikasi
                val crashFile = File(filesDir, "latest_crash.txt")
                val report = DiagnosticUtils.generateSystemReport(throwable)
                crashFile.writeText(report)

                // Buka DiagnosticCrashActivity
                DiagnosticCrashActivity.start(this@WeFiApplication, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menangani crash darurat", e)
            } finally {
                // Matikan proses lama secara terkontrol tanpa melempar ke OS dialog
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    companion object {
        const val TAG = "WeFiApplication"
    }
}
