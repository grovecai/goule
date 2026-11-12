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
            put("usage", JSONArray().apply {
                rule.usage.forEach { span -> put(JSONArray().put(span.start).put(span.end)) }
            })
            put("activeUse", rule.activeUseMinutes ?: JSONObject.NULL)
            put("activeBlock", rule.activeBlockMinutes ?: JSONObject.NULL)
            put("from", rule.blockedFrom ?: JSONObject.NULL)
            put("legacyUntil", rule.legacyBlockedUntil ?: JSONObject.NULL)
            put("accountedUntil", rule.accountedUntil ?: JSONObject.NULL)
        })
    }
}.toString()

/** savedTime is the last committed rule clock, NOT the time at which the app is reopened. */
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
        if (item.has("usage")) {
            val spans = item.getJSONArray("usage")
            Rule(item.getString("package"), item.getString("label"), use, block, created,
                optional("edited"), List(spans.length()) { i ->
                    val span = spans.getJSONArray(i)
                    UsageSpan(span.getLong(0), span.getLong(1))
                }, optional("activeUse")?.toInt(), optional("activeBlock")?.toInt(), from,
                optional("legacyUntil"), optional("accountedUntil"))
        } else {
            // The legacy aggregate has no segment timestamps. Place it as late as possible
            // rather than inventing an early expiry and granting unearned quota on upgrade.
            val quota = optional("windowUse")?.toInt() ?: use
            val used = item.optLong("used", 0).coerceIn(0, quota * MINUTE)
            val until = optional("until")
            val earliestEnd = (optional("window") ?: created) + used
            val end = maxOf(earliestEnd, if (until != null) from ?: (until - block * MINUTE)
                else savedTime ?: earliestEnd)
            Rule(item.getString("package"), item.getString("label"), use, block, created,
                optional("edited"), if (used > 0) listOf(UsageSpan(end - used, end)) else emptyList(),
                quota.takeIf { used > 0 || until != null }, block.takeIf { used > 0 || until != null },
                from, until, savedTime ?: end)
        }
    }
}
