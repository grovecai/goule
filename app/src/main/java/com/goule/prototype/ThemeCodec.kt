package com.goule.prototype

import org.json.JSONArray
import org.json.JSONObject

internal object ThemeCodec {
    fun safeFile(name: String) = name.matches(Regex("[A-Za-z0-9_-]+\\.webp"))
    private fun safeId(id: String) = id == "custom" || id.matches(Regex("user_[a-f0-9-]{36}"))

    fun encode(themes: List<BlockTheme>): String = JSONArray().apply {
        for (theme in themes) put(JSONObject().apply {
            put("id", theme.id); put("name", theme.name); put("headline", theme.headline)
            put("reminder", theme.reminder); put("close", theme.closeLabel); put("home", theme.homeLabel)
            put("image", (theme.image as ThemeImage.Local).fileName)
        })
    }.toString()

    fun decode(raw: String?): List<BlockTheme> = runCatching {
        if (raw == null || raw.length > 1_000_000) return emptyList()
        val rows = JSONArray(raw)
        (0 until rows.length()).mapNotNull { index -> runCatching {
            val row = rows.getJSONObject(index)
            val draft = ThemeDraft(row.getString("id"), row.getString("name"), row.getString("headline"),
                row.getString("reminder"), row.optString("close", "关闭 App"), row.optString("home", "返回桌面")).trimmed()
            val file = row.getString("image")
            if (!safeId(checkNotNull(draft.id)) || !safeFile(file) || draft.error() != null) null
            else BlockTheme(draft.id, draft.name, draft.headline, draft.reminder, "主题「${draft.name}」的图片",
                draft.closeLabel, draft.homeLabel, ThemeImage.Local(file))
        }.getOrNull() }.distinctBy { it.id }
    }.getOrDefault(emptyList())
}
