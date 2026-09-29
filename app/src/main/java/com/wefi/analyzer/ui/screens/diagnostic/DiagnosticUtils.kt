package com.wefi.analyzer.ui.screens.diagnostic

import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter

object DiagnosticUtils {

    fun formatExceptionSummary(throwable: Throwable): String {
        val name = throwable.javaClass.simpleName.ifEmpty { throwable.javaClass.name }
        val msg = throwable.localizedMessage ?: throwable.message ?: "No error message provided"
        return "$name: $msg"
    }

    fun formatStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString().trim()
    }

    fun generateSystemReport(throwable: Throwable): String {
        return buildString {
            appendLine("=== WEFI DIAGNOSTIC REPORT ===")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("----------------------------------------")
            appendLine("SUMMARY:")
            appendLine(formatExceptionSummary(throwable))
            appendLine("----------------------------------------")
            appendLine("STACK TRACE:")
            appendLine(formatStackTrace(throwable))
            appendLine("========================================")
        }
    }
}
