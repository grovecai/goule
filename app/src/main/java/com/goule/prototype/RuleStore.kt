package com.goule.prototype

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** Activity and service share this store on the process main thread. */
internal class RuleStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("rules", Context.MODE_PRIVATE)
    private val legacy = context.prototypeSettings()
    private val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private val anchorElapsed = SystemClock.elapsedRealtime()
    private val clockStart = restoreRuleTime(
        prefs.getLong("clock", 0), prefs.getLong("elapsed", 0), anchorElapsed,
        System.currentTimeMillis(), boot >= 0 && prefs.getInt("boot", -1) == boot,
    )

    var rules: List<Rule> = decodeRules(prefs.getString("items", null), prefs.getLong("clock", clockStart))
        private set
    private var dirty = false
    private var lastWrite = anchorElapsed
    var onConfigurationChanged: (() -> Unit)? = null

    val consent: Boolean get() = legacy.getBoolean(CONSENT_KEY, false)
    val onboardingStep: String? get() = prefs.getString("onboardingStep", null)
    val welcomed: Boolean get() = onboardingStep == null && (prefs.getBoolean("welcomed", false) || consent)
    val activated: Boolean get() = prefs.getBoolean("activated", false)

    init {
        if (!prefs.contains("items")) {
            val target = legacy.getString(TARGET_KEY, null)
            if (target != null) {
                val label = context.eligibleTargets().find { it.packageName == target }?.label ?: target
                change(listOf(Rule(target, label, DEFAULT_USE, DEFAULT_BLOCK, now())), force = true)
            }
        }
    }

    fun now() = clockStart + SystemClock.elapsedRealtime() - anchorElapsed
    fun saveOnboardingStep(step: String) { prefs.edit().putString("onboardingStep", step).apply() }
    fun welcome() { prefs.edit().putBoolean("welcomed", true).remove("onboardingStep").apply() }
    fun approve() { legacy.edit().putBoolean(CONSENT_KEY, true).apply() }
    fun activate() { prefs.edit().putBoolean("activated", true).apply() }

    fun configure(items: List<Rule>): Boolean {
        if (!change(items, force = true)) return false
        onConfigurationChanged?.invoke()
        return true
    }

    fun change(items: List<Rule>, force: Boolean = false): Boolean {
        if (!force && items == rules && !dirty) return true
        if (!force && SystemClock.elapsedRealtime() - lastWrite < 5_000) {
            if (items != rules) {
                rules = items
                dirty = true
            }
            return true
        }
        if (!prefs.edit().putString("items", encodeRules(items)).putLong("clock", now())
                .putInt("boot", boot).putLong("elapsed", SystemClock.elapsedRealtime()).commit()
        ) return false
        rules = items
        dirty = false
        lastWrite = SystemClock.elapsedRealtime()
        return true
    }

    fun flush() { if (dirty) change(rules, force = true) }

    companion object {
        private var instance: RuleStore? = null
        fun get(context: Context): RuleStore =
            instance ?: RuleStore(context.applicationContext).also { instance = it }
    }
}
