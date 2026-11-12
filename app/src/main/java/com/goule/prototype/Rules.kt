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
    val cycleStart: Long? = null,
    val usedMs: Long = 0,
    val activeUseMinutes: Int? = null,
    val activeBlockMinutes: Int? = null,
    val blockedFrom: Long? = null,
    // Retain an existing restriction when upgrading older storage formats.
    val legacyBlockedUntil: Long? = null,
    val accountedUntil: Long? = null,
) {
    private val useLimitMs: Long get() = (activeUseMinutes ?: useMinutes) * MINUTE
    private val cycleMs: Long get() = useLimitMs + (activeBlockMinutes ?: blockMinutes) * MINUTE
    val cycleEnd: Long? get() = cycleStart?.plus(cycleMs)
    val blockedUntil: Long? get() = listOfNotNull(
        cycleEnd?.takeIf { usedMs >= useLimitMs }, legacyBlockedUntil,
    ).maxOrNull()

    fun isBlocked(now: Long) = refresh(now).blockedUntil?.let { it > now } == true
    fun remainingUseMs(now: Long): Long {
        val current = refresh(now)
        return if (current.legacyBlockedUntil != null) 0 else
            (current.useLimitMs - current.usedMs).coerceIn(0, current.useLimitMs)
    }

    fun nextCheckDelayMs(now: Long): Long {
        val current = refresh(now)
        current.blockedUntil?.takeIf { it > now }?.let { return it - now }
        return minOf(current.useLimitMs - current.usedMs, current.cycleEnd?.minus(now) ?: 1_000)
            .coerceIn(1, 1_000)
    }

    fun editWait(now: Long) = editedAt?.let { (it + EDIT_INTERVAL - now).coerceAtLeast(0) } ?: 0
    fun removeWait(now: Long) = (maxOf(createdAt, blockedFrom ?: createdAt) + HOUR - now).coerceAtLeast(0)

    fun refresh(now: Long): Rule {
        val legacy = legacyBlockedUntil?.takeIf { it > now }
        val end = cycleEnd
        if (end == null || now < end) return copy(legacyBlockedUntil = legacy)
        // Advance from the previous boundary, including missed idle cycles. Never re-anchor
        // at the time a screen is opened. Latest parameters take effect at the first boundary.
        val period = (useMinutes + blockMinutes) * MINUTE
        val start = end + (now - end) / period * period
        return copy(cycleStart = start, usedMs = 0,
            activeUseMinutes = useMinutes, activeBlockMinutes = blockMinutes,
            legacyBlockedUntil = legacy)
    }

    fun record(delta: Long, now: Long): Rule {
        if (delta <= 0) return refresh(now)
        var cursor = maxOf(now - delta, accountedUntil ?: Long.MIN_VALUE, cycleStart ?: Long.MIN_VALUE)
        if (cursor >= now) return this
        var current = refresh(cursor)
        // Do not backfill use during an interval that was covered by a restriction.
        if (current.isBlocked(cursor)) return current.refresh(now).copy(accountedUntil = now)
        while (cursor < now) {
            current = current.refresh(cursor)
            if (current.cycleStart == null) current = current.copy(cycleStart = cursor,
                activeUseMinutes = current.useMinutes, activeBlockMinutes = current.blockMinutes)
            val end = checkNotNull(current.cycleEnd)
            val duration = minOf(now - cursor, end - cursor, current.useLimitMs - current.usedMs)
            if (duration <= 0) break
            cursor += duration
            current = current.copy(usedMs = current.usedMs + duration)
            if (cursor < end && current.usedMs >= current.useLimitMs) {
                current = current.copy(blockedFrom = cursor)
                break
            }
        }
        return current.refresh(now).copy(accountedUntil = now)
    }

    fun edit(use: Int, block: Int, now: Long): Rule {
        if (use == useMinutes && block == blockMinutes) return this
        check(editWait(now) == 0L)
        return refresh(now).copy(useMinutes = use, blockMinutes = block, editedAt = now)
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
