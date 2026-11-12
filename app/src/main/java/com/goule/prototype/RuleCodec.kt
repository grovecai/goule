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
            put("window", rule.windowStart ?: JSONObject.NULL)
            put("used", rule.usedMs)
            put("windowUse", rule.windowUseMinutes ?: JSONObject.NULL)
            put("from", rule.blockedFrom ?: JSONObject.NULL)
            put("until", rule.blockedUntil ?: JSONObject.NULL)
        })
    }
}.toString()

internal fun decodeRules(raw: String?): List<Rule> {
    if (raw == null) return emptyList()
    val array = JSONArray(raw)
    return List(array.length()) { index ->
        val item = array.getJSONObject(index)
        fun optional(key: String) = if (item.isNull(key)) null else item.getLong(key)
        Rule(item.getString("package"), item.getString("label"), item.getInt("use"),
            item.getInt("block"), item.getLong("created"), optional("edited"), optional("window"),
            item.getLong("used"), optional("from"), optional("until"), optional("windowUse")?.toInt())
    }
}
