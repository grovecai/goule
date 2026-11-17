package com.goule.prototype

import android.content.Context
import android.os.Process

/** Independent of rules. Small observations survive a killed process; no periodic wakeups. */
internal class ServiceHealthStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("service_health", Context.MODE_PRIVATE)
    private var observation = ServiceObservation(prefs.getLong("connected", 0), prefs.getLong("seen", 0),
        prefs.getLong("disconnected", 0), prefs.getInt("pid", 0))
    private data class History(val exits: List<ProcessExit> = emptyList(), val ready: Boolean = false,
        val unavailable: Boolean = false)
    @Volatile private var history = History()

    fun connected(now: Long = System.currentTimeMillis()) {
        observation = ServiceObservation(now, now, 0, Process.myPid())
        save()
    }

    fun observed(now: Long = System.currentTimeMillis()) {
        if (observation.processId == Process.myPid() && observation.disconnectedAt == 0L) {
            observation = observation.copy(lastSeenAt = now)
            save()
        }
    }

    fun disconnected(now: Long = System.currentTimeMillis()) {
        if (observation.processId == Process.myPid() && observation.disconnectedAt == 0L) {
            observation = observation.copy(disconnectedAt = now)
            save()
        }
    }

    private fun save() {
        prefs.edit().putLong("connected", observation.connectedAt).putLong("seen", observation.lastSeenAt)
            .putLong("disconnected", observation.disconnectedAt).putInt("pid", observation.processId).apply()
    }

    fun historyLoaded(exit: ProcessExit?, unavailable: Boolean = false) {
        historyLoaded(listOfNotNull(exit), unavailable)
    }

    fun historyLoaded(exits: List<ProcessExit>, unavailable: Boolean = false) {
        history = History(exits.toList(), ready = true, unavailable = unavailable)
    }

    fun evidence(): ServiceHealthEvidence {
        val snapshot = history
        return ServiceHealthEvidence(observation, serviceProcessExit(observation, snapshot.exits),
            snapshot.ready, snapshot.unavailable)
    }

    companion object {
        @Volatile private var instance: ServiceHealthStore? = null
        fun get(context: Context): ServiceHealthStore = instance ?: synchronized(this) {
            instance ?: ServiceHealthStore(context.applicationContext).also { instance = it }
        }
    }
}
