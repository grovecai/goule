package com.goule.prototype

internal data class ServiceObservation(
    val connectedAt: Long = 0,
    val lastSeenAt: Long = 0,
    val disconnectedAt: Long = 0,
    val processId: Int = 0,
)

internal data class ProcessExit(val at: Long, val processId: Int, val reason: Int, val description: String?)

/** A later Activity-only process exit must not hide the recorded service process's exit. */
internal fun serviceProcessExit(observation: ServiceObservation, exits: List<ProcessExit>): ProcessExit? {
    val ordered = exits.filter { it.at > 0 }.sortedByDescending { it.at }
    return ordered.firstOrNull {
        observation.lastSeenAt > 0 && observation.disconnectedAt == 0L &&
            it.processId == observation.processId && it.at >= observation.connectedAt && it.at >= observation.lastSeenAt
    } ?: ordered.firstOrNull()
}

/** Keep vendor text as evidence, never interpret its numbers as a cleaning policy. */
internal fun cleanExitDescription(value: String?): String = value.orEmpty()
    .take(240)
    .map { if (it.isISOControl() || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069') ' ' else it }
    .joinToString("").trim()

internal fun exitReasonLabel(reason: Int): String = when (reason) {
    1 -> "应用进程自行退出"
    2 -> "进程收到信号后退出，具体原因未确定"
    3 -> "系统记录为内存不足"
    4 -> "应用发生异常崩溃"
    5 -> "应用发生原生代码崩溃"
    6 -> "应用无响应"
    7 -> "应用初始化失败"
    8 -> "应用权限发生变化"
    9 -> "系统记录为资源使用过多"
    10 -> "用户操作或系统请求结束进程"
    11 -> "应用所属用户已停止"
    12 -> "依赖的进程退出"
    13 -> "系统结束了应用进程，具体策略未确定"
    14 -> "系统冻结进程时发生异常"
    15 -> "应用或组件状态发生变化"
    16 -> "安装更新时结束了旧进程"
    else -> "系统未提供可识别的退出原因"
}

internal data class ServiceHealthEvidence(
    val observation: ServiceObservation = ServiceObservation(),
    val exit: ProcessExit? = null,
    val historyReady: Boolean = false,
    val historyUnavailable: Boolean = false,
) {
    fun lines(state: ServiceState, time: (Long) -> String): List<String> {
        if (!state.needsRecovery) return emptyList()
        return buildList {
            val seen = observation.lastSeenAt
            if (seen > 0) add("最近确认连接：${time(seen)}")
            else add("暂无服务连接观测记录，启用新版后开始记录。")
            if (observation.disconnectedAt > 0) {
                add("记录到服务断开：${time(observation.disconnectedAt)}")
                add("这条记录无法区分手动关闭和系统停止，请结合当前开关状态恢复。")
            } else if (seen > 0) add("最后观测时间不等于准确停止时间。")
            val previous = exit
            when {
                !historyReady -> add("正在读取本应用的系统退出记录…")
                historyUnavailable -> add("当前系统无法提供退出记录，中断原因尚未确定。")
                previous == null -> add("系统没有保留本应用的退出记录，中断原因尚未确定。")
                previous.at < observation.connectedAt -> add("上次进程退出早于最近一次连接，不能解释本次中断。")
                else -> {
                    val related = seen > 0 && previous.processId == observation.processId &&
                        previous.at >= seen && observation.disconnectedAt == 0L
                    add("${if (related) "服务所在进程退出" else "上次应用进程退出"}：${time(previous.at)}")
                    add("系统记录：${exitReasonLabel(previous.reason)}")
                    val detail = cleanExitDescription(previous.description)
                    if (detail.isNotEmpty()) add("系统原始说明：$detail")
                    if (!related) add("这条退出记录不一定是本次服务中断的原因。")
                }
            }
        }
    }
}
