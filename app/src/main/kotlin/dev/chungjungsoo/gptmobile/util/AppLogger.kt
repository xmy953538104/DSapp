package dev.chungjungsoo.gptmobile.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val LOG_FILE_NAME = "gpt_mobile.log"
    private const val MAX_LOG_BYTES = 512 * 1024
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun info(context: Context, tag: String, message: String) {
        append(context, "INFO", tag, message)
    }

    fun error(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        append(
            context = context,
            level = "ERROR",
            tag = tag,
            message = buildString {
                append(message)
                throwable?.let {
                    append("\n")
                    append(it.stackTraceToString())
                }
            }
        )
    }

    fun read(context: Context): String {
        val file = logFile(context)
        return if (file.exists()) file.readText() else ""
    }

    fun writeDiagnosticFile(context: Context, report: String): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "gpt_mobile_diagnostic_${System.currentTimeMillis()}.txt")
        file.writeText(report)
        return file
    }

    @Synchronized
    private fun append(context: Context, level: String, tag: String, message: String) {
        val file = logFile(context)
        file.parentFile?.mkdirs()
        trimIfNeeded(file)
        val timestamp = timestampFormat.format(Date())
        file.appendText("[$timestamp][$level][$tag] $message\n")
    }

    private fun logFile(context: Context): File = File(context.filesDir, LOG_FILE_NAME)

    private fun trimIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_LOG_BYTES) return
        val trimmed = file.readText().takeLast(MAX_LOG_BYTES / 2)
        file.writeText(trimmed)
    }
}
