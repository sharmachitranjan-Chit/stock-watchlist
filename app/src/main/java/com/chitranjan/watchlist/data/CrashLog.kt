package com.chitranjan.watchlist.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Records why the app closed. When the app crashes, the error, the phone details and the last
 * things the app was doing ("steps") are saved to a file before Android closes the app.
 * The reports can be read, shared or cleared from Settings → Crash reports.
 */
object CrashLog {
    private const val FILE = "crash-reports.txt"
    private const val UNSEEN = "crash-unseen"
    private const val SEP = "\n==========\n"
    private const val KEEP = 10
    private const val STEPS = 40

    private val TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale.ENGLISH)
    private val IST = ZoneId.of("Asia/Kolkata")
    private val steps = ArrayDeque<String>()
    private lateinit var dir: File
    var version: String = ""

    fun install(context: Context) {
        dir = context.filesDir
        version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(thread, error) }
            // Let Android close the app as it normally would.
            previous?.uncaughtException(thread, error)
        }
        step("App started")
    }

    /** Notes what the app is doing, so a crash report shows the steps that led to it. */
    fun step(what: String) {
        val line = "${ZonedDateTime.now(IST).format(TIME)}  $what"
        synchronized(steps) {
            steps.addLast(line)
            while (steps.size > STEPS) steps.removeFirst()
        }
    }

    private fun save(thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val recent = synchronized(steps) { steps.toList() }
        val report = buildString {
            appendLine("Crash on ${ZonedDateTime.now(IST).format(TIME)} IST")
            appendLine("Reason: ${error.javaClass.simpleName}: ${error.message ?: "(no message)"}")
            appendLine("App ${version.ifEmpty { "?" }} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine("Last steps before the crash:")
            recent.forEach { appendLine("  $it") }
            appendLine()
            appendLine("Technical details:")
            append(trace.take(12_000))
        }
        val all = (listOf(report) + reports()).take(KEEP)
        File(dir, FILE).writeText(all.joinToString(SEP))
        File(dir, UNSEEN).writeText("1")
    }

    /** Saved reports, newest first. */
    fun reports(): List<String> = runCatching {
        File(dir, FILE).takeIf { it.exists() }?.readText()?.split(SEP)?.filter { it.isNotBlank() }
    }.getOrNull().orEmpty()

    /** True once after a crash, until the reports are opened. */
    fun hasUnseen(): Boolean = runCatching { File(dir, UNSEEN).exists() }.getOrDefault(false)
    fun markSeen() { runCatching { File(dir, UNSEEN).delete() } }

    fun clear() {
        runCatching { File(dir, FILE).delete() }
        markSeen()
    }
}
