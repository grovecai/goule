package com.goule.prototype

internal enum class ServiceState { OFF, CONNECTING, ON, PAUSED, INTERRUPTED }

internal const val CONNECTION_GRACE_MS = 5_000L

/** Registration is separate from the global flag, which also falls to zero after a service dies. */
internal class ServiceConnectionState {
    private var waitingSince: Long? = null

    fun observe(consent: Boolean, registered: Boolean, connected: Boolean, activated: Boolean, now: Long): ServiceState {
        if (!consent || !registered) {
            waitingSince = null
            return if (activated) ServiceState.PAUSED else ServiceState.OFF
        }
        if (connected) {
            waitingSince = null
            return ServiceState.ON
        }
        val since = waitingSince ?: now.also { waitingSince = it }
        return if (now - since < CONNECTION_GRACE_MS) ServiceState.CONNECTING else ServiceState.INTERRUPTED
    }
}

internal val ServiceState.needsRecovery: Boolean
    get() = this == ServiceState.PAUSED || this == ServiceState.INTERRUPTED

internal val ServiceState.statusLabel: String
    get() = when (this) {
        ServiceState.OFF -> "未设置"
        ServiceState.CONNECTING -> "正在连接"
        ServiceState.ON -> "已设置"
        ServiceState.PAUSED -> "已关闭"
        ServiceState.INTERRUPTED -> "连接中断"
    }

internal val ServiceState.pauseMessage: String
    get() = if (this == ServiceState.INTERRUPTED)
        "已获得无障碍授权，但服务连接中断，计时和禁用已暂停。请在系统设置中先关闭再开启「斩心魔」。原有戒律和已记录用量仍保留。"
    else "无障碍服务已关闭，计时和禁用已暂停。原有戒律仍保留，重新开启后会继续执行仍有效的禁用。"
