package com.goule.prototype

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

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

    var rules: List<Rule> = decode(prefs.getString("items", null))
        private set
    private var dirty = false
    private var lastWrite = anchorElapsed
    var onConfigurationChanged: (() -> Unit)? = null

    val consent: Boolean get() = legacy.getBoolean(CONSENT_KEY, false)
    val welcomed: Boolean get() = prefs.getBoolean("welcomed", false) || consent
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
    fun welcome() { prefs.edit().putBoolean("welcomed", true).apply() }
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
        val array = JSONArray()
        items.forEach { rule ->
            array.put(JSONObject().apply {
                put("package", rule.packageName)
                put("label", rule.label)
                put("use", rule.useMinutes)
                put("block", rule.blockMinutes)
                put("created", rule.createdAt)
                put("edited", rule.editedAt ?: JSONObject.NULL)
                put("window", rule.windowStart ?: JSONObject.NULL)
                put("used", rule.usedMs)
                put("from", rule.blockedFrom ?: JSONObject.NULL)
                put("until", rule.blockedUntil ?: JSONObject.NULL)
            })
        }
        if (!prefs.edit().putString("items", array.toString()).putLong("clock", now())
                .putInt("boot", boot).putLong("elapsed", SystemClock.elapsedRealtime()).commit()
        ) return false
        rules = items
        dirty = false
        lastWrite = SystemClock.elapsedRealtime()
        return true
    }

    fun flush() { if (dirty) change(rules, force = true) }

    private fun decode(raw: String?): List<Rule> {
        if (raw == null) return emptyList()
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            fun optional(key: String) = if (item.isNull(key)) null else item.getLong(key)
            Rule(item.getString("package"), item.getString("label"), item.getInt("use"),
                item.getInt("block"), item.getLong("created"), optional("edited"), optional("window"),
                item.getLong("used"), optional("from"), optional("until"))
        }
    }

    companion object {
        private var instance: RuleStore? = null
        fun get(context: Context): RuleStore =
            instance ?: RuleStore(context.applicationContext).also { instance = it }
    }
}
