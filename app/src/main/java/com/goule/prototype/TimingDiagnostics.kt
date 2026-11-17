package com.goule.prototype

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.io.PrintWriter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** No event text, node access, unconfigured app identity, network or additional permission. */
internal class TimingDiagnostics private constructor(context: Context) {
    private val journal = RollingDiagnosticLog(File(context.noBackupFilesDir, "timing-diagnostics"))
    private val dropped = AtomicLong()
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(256)) {
        Thread(it, "goule-diagnostics").apply { isDaemon = true }
    }
    private val session = "${Process.myPid()}-${System.currentTimeMillis()}"

    init {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        record("process start version=${info.versionName}/${info.longVersionCode} sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL}")
        val health = ServiceHealthStore.get(context)
        worker.execute {
            runCatching {
                val exits = context.getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(context.packageName, 0, 10)
                    .filter { it.processName == context.packageName }.sortedByDescending { it.timestamp }
                health.historyLoaded(exits.map {
                    ProcessExit(it.timestamp, it.pid, it.reason, cleanExitDescription(it.description))
                })
                exits.take(3).forEach {
                    journal.append("session=$session previous_exit wall=${it.timestamp} pid=${it.pid} reason=${it.reason} status=${it.status} importance=${it.importance} description=${cleanExitDescription(it.description)}")
                }
            }.onFailure {
                health.historyLoaded(null, unavailable = true)
                journal.append("session=$session exit_history unavailable kind=${it.javaClass.simpleName}")
            }
        }
    }

    fun record(message: String) {
        val line = "wall=${System.currentTimeMillis()} up=${SystemClock.uptimeMillis()} elapsed=${SystemClock.elapsedRealtime()} session=$session ${message.take(2048)}"
        try {
            worker.execute {
                val lost = dropped.getAndSet(0)
                if (lost > 0) journal.append("$line diagnostic queue dropped=$lost")
                journal.append(line)
            }
        } catch (_: RejectedExecutionException) {
            dropped.incrementAndGet()
        }
    }

    fun dump(writer: PrintWriter) {
        val task = FutureTask { journal.snapshot() }
        try {
            worker.execute(task)
            writer.println("Persistent timing diagnostics (local, rotating; pendingDropped=${dropped.get()}):")
            writer.print(task.get(2, TimeUnit.SECONDS))
        } catch (_: Exception) {
            writer.println("Persistent timing diagnostics busy/unavailable; pendingDropped=${dropped.get()}")
        }
    }

    companion object {
        private var instance: TimingDiagnostics? = null
        fun get(context: Context): TimingDiagnostics = synchronized(this) {
            instance ?: TimingDiagnostics(context.applicationContext).also { instance = it }
        }
    }
}
