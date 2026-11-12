package com.goule.prototype

import org.json.JSONArray
import org.json.JSONObject

internal fun encodeRules(rules: List<Rule>): String = JSONArray().apply {
    rules.forEach { rule ->
        put(JSONObject().apply {
            put("package", rule.packageName)
            put("label", rule.label)
            put("use", rule.useMinutes)
            put("block", rule.blockMinutes)
            put("created", rule.createdAt)
            put("edited", rule.editedAt ?: JSONObject.NULL)
            put("cycleStart", rule.cycleStart ?: JSONObject.NULL)
            put("used", rule.usedMs)
            put("cycleUse", rule.activeUseMinutes ?: JSONObject.NULL)
            put("cycleBlock", rule.activeBlockMinutes ?: JSONObject.NULL)
            put("from", rule.blockedFrom ?: JSONObject.NULL)
            put("legacyUntil", rule.legacyBlockedUntil ?: JSONObject.NULL)
            put("accountedUntil", rule.accountedUntil ?: JSONObject.NULL)
        })
    }
}.toString()

/** Migration anchors use the last saved rule clock so reopening never restarts a cycle. */
internal fun decodeRules(raw: String?, savedTime: Long? = null): List<Rule> {
    if (raw == null) return emptyList()
    val array = JSONArray(raw)
    return List(array.length()) { index ->
        val item = array.getJSONObject(index)
        fun optional(key: String) = if (item.isNull(key)) null else item.getLong(key)
        val use = item.getInt("use")
        val block = item.getInt("block")
        val created = item.getLong("created")
        val from = optional("from")
        if (item.has("cycleStart")) {
            Rule(item.getString("package"), item.getString("label"), use, block, created,
                optional("edited"), optional("cycleStart"), item.getLong("used"),
                optional("cycleUse")?.toInt(), optional("cycleBlock")?.toInt(), from,
                optional("legacyUntil"), optional("accountedUntil"))
        } else {
            val rolling = item.has("usage")
            val activeUse = (optional(if (rolling) "activeUse" else "windowUse")?.toInt() ?: use)
            val activeBlock = (optional("activeBlock")?.toInt() ?: block)
            val period = (activeUse + activeBlock) * MINUTE
            val spans = item.optJSONArray("usage")
            val used = (if (spans != null) (0 until spans.length()).sumOf { i ->
                val span = spans.getJSONArray(i)
                span.getLong(1) - span.getLong(0)
            } else item.optLong("used", 0)).coerceIn(0, activeUse * MINUTE)
            val until = optional(if (rolling) "legacyUntil" else "until")
            val oldStart = if (spans != null && spans.length() > 0) spans.getJSONArray(0).getLong(0)
                else optional("window")
            val saved = savedTime ?: optional("accountedUntil") ?: oldStart ?: created
            // If rolling release was already underway, retain the currently saved aggregate
            // in one new fixed cycle instead of immediately granting a fresh quota.
            val anchor = if (used > 0 || until != null) {
                oldStart?.takeIf { it + period > saved } ?: saved
            } else null
            Rule(item.getString("package"), item.getString("label"), use, block, created,
                optional("edited"), anchor, used, activeUse.takeIf { anchor != null },
                activeBlock.takeIf { anchor != null }, from, until,
                optional("accountedUntil") ?: savedTime)
        }
    }
}
