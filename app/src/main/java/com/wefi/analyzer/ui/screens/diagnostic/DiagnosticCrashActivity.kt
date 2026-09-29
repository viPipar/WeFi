package com.wefi.analyzer.ui.screens.diagnostic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wefi.analyzer.MainActivity
import com.wefi.analyzer.ui.theme.WeFiTheme

class DiagnosticCrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "Uncaught runtime exception"
        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: ""

        val simulatedThrowable = RuntimeException(
            errorMessage,
            Throwable("Stack: $stackTrace")
        )

        setContent {
            WeFiTheme {
                DiagnosticRecoveryScreen(
                    error = simulatedThrowable,
                    onRetry = {
                        val restartIntent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(restartIntent)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        const val EXTRA_STACK_TRACE = "extra_stack_trace"

        fun start(context: Context, throwable: Throwable) {
            val intent = Intent(context, DiagnosticCrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_ERROR_MESSAGE, DiagnosticUtils.formatExceptionSummary(throwable))
                putExtra(EXTRA_STACK_TRACE, DiagnosticUtils.formatStackTrace(throwable))
            }
            context.startActivity(intent)
        }
    }
}
