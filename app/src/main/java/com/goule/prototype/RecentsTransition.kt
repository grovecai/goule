package com.goule.prototype

/** Launcher can send focus updates while its recent-tasks pane is closing over a resumed app. */
internal class RecentsTransition {
    enum class Decision { PASS, DEFER, IGNORE }

    private var launcher: String? = null
    private var returnedAt: Long? = null
    private var closedAt: Long? = null

    fun targetResumed(time: Long) {
        if (launcher != null && returnedAt == null) returnedAt = time
    }

    fun launcherEvent(packageName: String, time: Long, appeared: Boolean, disappeared: Boolean): Decision {
        if (appeared) {
            reset()
            launcher = packageName
            return Decision.PASS
        }
        if (packageName != launcher) {
            reset()
            return Decision.PASS
        }
        val resumed = returnedAt
        if (resumed == null) {
            if (disappeared) reset()
            return Decision.PASS
        }
        val closed = closedAt
        if (closed != null && time - closed in 0..SETTLE_MS) return Decision.IGNORE
        if (closed != null || time - resumed !in 0..RETURN_MS) {
            reset()
            return Decision.PASS
        }
        if (disappeared) {
            closedAt = time
            return Decision.IGNORE
        }
        return Decision.DEFER
    }

    fun reset() {
        launcher = null
        returnedAt = null
        closedAt = null
    }

    companion object {
        const val SETTLE_MS = 120L
        private const val RETURN_MS = 1_500L
    }
}
