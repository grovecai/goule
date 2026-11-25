package com.goule.prototype

/** Launcher can send focus updates while its recent-tasks pane is closing over a resumed app. */
internal class RecentsTransition {
    enum class Decision { PASS, DEFER, IGNORE }

    private var awaitingLauncher = false
    private var launcher: String? = null
    private var lastLauncherAt = 0L
    private var interruptedBeforeResume = false
    private var returnedAt: Long? = null
    private var closedAt: Long? = null

    fun openRequested() {
        reset()
        awaitingLauncher = true
    }

    // A pane disappearing belongs to the departing app, not the new foreground.
    // After our Recents action, old activity callbacks can precede the launcher.
    fun acceptsTarget(disappeared: Boolean): Boolean = !disappeared && !awaitingLauncher

    fun targetResumed(time: Long) {
        if (launcher == null || returnedAt != null) return
        val closed = closedAt
        if ((interruptedBeforeResume && time - lastLauncherAt !in 0..RETURN_MS) ||
            (closed != null && time - closed !in 0..RETURN_MS)) {
            reset()
            return
        }
        returnedAt = time
        interruptedBeforeResume = false
    }

    // SystemUI and launcher panes can finish in either order after a confirmed return.
    // This freshness window does not change the launcher's shorter post-close tail.
    fun returningToTarget(time: Long): Boolean {
        val resumed = returnedAt ?: return false
        return time - resumed in 0..RETURN_MS
    }

    // Card animations can emit an unrelated window before any target has resumed.
    // Foreground still clears immediately; only unfinished transition metadata survives.
    fun nonTargetWindow() {
        if (launcher != null && returnedAt == null) interruptedBeforeResume = true
        else reset()
    }

    fun launcherEvent(packageName: String, time: Long, appeared: Boolean, disappeared: Boolean): Decision {
        awaitingLauncher = false
        if (appeared) {
            reset()
            launcher = packageName
            lastLauncherAt = time
            return Decision.PASS
        }
        if (packageName != launcher) {
            reset()
            return Decision.PASS
        }
        lastLauncherAt = time
        val resumed = returnedAt
        if (resumed == null) {
            // Closing the pane can precede the target's own window event.
            if (disappeared) closedAt = time
            val closed = closedAt
            if (closed != null && time - closed !in 0..RETURN_MS) reset()
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
        awaitingLauncher = false
        launcher = null
        lastLauncherAt = 0L
        interruptedBeforeResume = false
        returnedAt = null
        closedAt = null
    }

    companion object {
        const val SETTLE_MS = 120L
        // Recents closes around 450 ms on ColorOS; 120 ms is only the post-close tail.
        const val RETURN_MS = 1_500L
    }
}
