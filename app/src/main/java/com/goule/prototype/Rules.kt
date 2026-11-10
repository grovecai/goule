package com.goule.prototype

internal const val MINUTE = 60_000L
internal const val HOUR = 60 * MINUTE
internal const val DAY = 24 * HOUR
internal const val EDIT_INTERVAL = 5 * MINUTE
internal const val DEFAULT_USE = 20
internal const val DEFAULT_BLOCK = 30

internal data class Rule(
    val packageName: String,
    val label: String,
    val useMinutes: Int,
    val blockMinutes: Int,
    val createdAt: Long,
    val editedAt: Long? = null,
    val windowStart: Long? = null,
    val usedMs: Long = 0,
    val blockedFrom: Long? = null,
    val blockedUntil: Long? = null,
) {
    fun isBlocked(now: Long) = blockedUntil?.let { it > now } == true
    fun editWait(now: Long) = editedAt?.let { (it + EDIT_INTERVAL - now).coerceAtLeast(0) } ?: 0
    fun removeWait(now: Long) = (maxOf(createdAt, blockedFrom ?: createdAt) + HOUR - now).coerceAtLeast(0)

    fun refresh(now: Long): Rule = when {
        isBlocked(now) -> this
        blockedUntil != null -> copy(blockedUntil = null, windowStart = null, usedMs = 0)
        windowStart?.let { now >= it + DAY } == true -> copy(windowStart = null, usedMs = 0)
        else -> this
    }

    fun record(delta: Long, now: Long): Rule {
        if (delta <= 0) return refresh(now)
        val from = now - delta
        val current = refresh(from)
        if (current.isBlocked(from)) return current.refresh(now)
        val boundary = current.windowStart?.plus(DAY)
        if (boundary == null || now < boundary) return current.accumulate(delta, now)
        val previousWindow = current.accumulate(boundary - from, boundary)
        return if (previousWindow.isBlocked(now)) previousWindow
            else previousWindow.refresh(now).accumulate(now - boundary, now)
    }

    private fun accumulate(delta: Long, now: Long): Rule {
        if (delta <= 0) return this
        val start = windowStart ?: now - delta
        val used = usedMs + delta
        return if (used >= useMinutes * MINUTE) {
            val reachedAt = now - (used - useMinutes * MINUTE)
            copy(windowStart = start, usedMs = useMinutes * MINUTE,
                blockedFrom = reachedAt, blockedUntil = reachedAt + blockMinutes * MINUTE)
        } else copy(windowStart = start, usedMs = used)
    }

    fun edit(use: Int, block: Int, now: Long): Rule {
        if (use == useMinutes && block == blockMinutes) return this
        check(editWait(now) == 0L)
        val current = refresh(now)
        return current.copy(useMinutes = use, blockMinutes = block, editedAt = now,
            windowStart = null, usedMs = 0)
    }
}

/** Counts only intervals bounded by confirmed observations on both clocks. */
internal fun observedUsage(previousUptime: Long, previousElapsed: Long, uptime: Long, elapsed: Long): Long {
    val awake = uptime - previousUptime
    val real = elapsed - previousElapsed
    return if (awake in 0..2_000L && real - awake in -5..100L) awake else 0
}


internal fun restoreRuleTime(
    savedTime: Long, savedElapsed: Long, elapsed: Long, wall: Long, sameBoot: Boolean,
): Long = when {
    savedTime == 0L -> wall
    sameBoot && elapsed >= savedElapsed -> savedTime + elapsed - savedElapsed
    else -> maxOf(savedTime, wall)
}
