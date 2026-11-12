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

internal fun usageWindowLabel(rule: Rule, now: Long): String {
    val current = rule.refresh(now)
    val use = current.activeUseMinutes ?: current.useMinutes
    val block = current.activeBlockMinutes ?: current.blockMinutes
    val pending = use != current.useMinutes || block != current.blockMinutes
    return "任意连续 ${use + block} 分钟内，最多刷 $use 分钟。" +
        if (pending) "新时长待有效用量全部过期后生效。" else "旧用量过期后逐步恢复额度。"
}
