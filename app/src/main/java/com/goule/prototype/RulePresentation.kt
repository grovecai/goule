package com.goule.prototype

/** Round up so a positive quota never appears exhausted before it actually is. */
internal fun remainingUseLabel(rule: Rule, now: Long): String {
    val seconds = (rule.remainingUseMs(now) + 999) / 1_000
    val minutes = seconds / 60
    val remainder = seconds % 60
    val duration = when {
        minutes == 0L -> "$seconds 秒"
        remainder == 0L -> "$minutes 分钟"
        else -> "$minutes 分 $remainder 秒"
    }
    return "还可刷 $duration"
}
