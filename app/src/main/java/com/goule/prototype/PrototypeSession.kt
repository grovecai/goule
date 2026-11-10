package com.goule.prototype

internal const val QUOTA_MS = 10_000L
internal const val FREEZE_MS = 20_000L

/** One in-memory round. All timestamps use the same monotonic clock. */
internal class PrototypeSession {
    private var usedMs = 0L
    private var activeSince: Long? = null
    var frozenUntil: Long? = null
        private set

    fun observe(targetVisible: Boolean, now: Long): Long? {
        activeSince?.let { started ->
            val quotaReachedAt = started + QUOTA_MS - usedMs
            if (now >= quotaReachedAt) {
                frozenUntil = quotaReachedAt + FREEZE_MS
                activeSince = null
                usedMs = 0
            } else if (!targetVisible) {
                usedMs += now - started
                activeSince = null
            }
        }

        frozenUntil?.let { deadline ->
            if (now < deadline) return deadline.takeIf { targetVisible }
            frozenUntil = null
        }

        if (!targetVisible) return null
        val started = activeSince ?: now.also { activeSince = it }
        return started + QUOTA_MS - usedMs
    }
}
