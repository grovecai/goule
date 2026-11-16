package com.goule.prototype

import kotlin.random.Random

/** Presentation only; never participates in a rule's clock or state transitions. */
internal enum class BlockThemeMode(val label: String) {
    FIXED("固定主题"), RANDOM("随机主题");

    companion object {
        fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: FIXED
    }
}

internal data class BlockTheme(
    val id: String,
    val name: String,
    val headline: String,
    val reminder: String,
    val description: String,
    val closeLabel: String = "斩退心魔",
    val homeLabel: String = "回洞府",
    val image: ThemeImage = ThemeImage.Bundled(id),
)

internal sealed interface ThemeImage {
    data class Bundled(val key: String) : ThemeImage
    data class Local(val fileName: String) : ThemeImage
}

internal data class ThemeDraft(
    val id: String? = null,
    val name: String = "我的主题",
    val headline: String = "先放下手机",
    val reminder: String = "把时间留给你真正想做的事。",
    val closeLabel: String = "关闭 App",
    val homeLabel: String = "返回桌面",
) {
    fun trimmed() = copy(name = name.trim(), headline = headline.trim(), reminder = reminder.trim(),
        closeLabel = closeLabel.trim(), homeLabel = homeLabel.trim())

    fun error(): String? {
        val fields = listOf(Triple(name, 20, "主题名"), Triple(headline, 16, "喝止台词"),
            Triple(reminder, 80, "提醒台词"), Triple(closeLabel, 6, "第一个按钮名称"), Triple(homeLabel, 6, "第二个按钮名称"))
        return fields.firstNotNullOfOrNull { (value, limit, label) ->
            when {
                value.isBlank() -> "请填写$label"
                value.codePointCount(0, value.length) > limit -> "$label 不超过 $limit 字"
                else -> null
            }
        }
    }

    companion object {
        fun from(theme: BlockTheme) = ThemeDraft(theme.id, theme.name, theme.headline, theme.reminder, theme.closeLabel, theme.homeLabel)
    }
}

internal object BlockThemes {
    val all = listOf(
        BlockTheme("elder", "戒律长老", "够了！", "再刷下去，你还飞不飞升了？", "戒律长老怒视前方，指向屏幕大声喝止"),
        BlockTheme("rescue", "拯救网瘾少年", "抓住了！", "人先上岸，正事还等着你呢！", "长老拉住被手机漩涡困住的年轻修行者", "挣脱心魔", "上岸去"),
        BlockTheme("magic", "魔法劝退", "收机术！", "手机已封印，快去完成你的主线任务！", "魔法导师用金色法术收起学徒的手机", "封印心魔", "做主线"),
        BlockTheme("kitchen", "锅要糊了", "锅要糊了！", "手机不会跑，锅里的菜可等不了！", "掌勺人举着锅铲，指向冒烟的炒锅", "收起心魔", "去看锅"),
        BlockTheme("garden", "菜地除草", "草比菜高！", "别给心魔浇水了，快去给菜地除草！", "老农扛着锄头，身后菜畦长满杂草", "收起心魔", "去除草"),
        BlockTheme("marriage", "月老催婚", "缘分呢？", "姻缘不在下一条，去见见真实的人！", "月老拿着红线催促，远处有人在桥上相见", "放下心魔", "去见面"),
        BlockTheme("work", "摸鱼警报", "老板来了！", "牛马先归位，别把工位刷成离职位！", "上司探过工位指向屏幕，桌上摆着小金牛", "结束摸鱼", "回去干活"),
        BlockTheme("exam", "挂科预警", "要挂科了！", "这题不会就是不会，下一条也救不了你！", "教授指着厚厚的课本，催促大学生复习", "先别刷了", "去复习"),
        BlockTheme("sunlight", "出门晒太阳", "要发霉了！", "别在屋里长蘑菇了，出去晒晒太阳！", "朋友拉开窗帘，窗外阳光照进紫色房间", "放下心魔", "去晒太阳"),
        BlockTheme("craving", "戒刷心魔", "瘾又来了？", "手指歇一歇，别让下一条替你过一生！", "修行者用金色剑光斩断手机幻影的锁链", "斩断刷瘾", "做正事"),
    )
    val default get() = all.first()
    fun available(deleted: Set<String>, personal: List<BlockTheme> = emptyList()) =
        all.filter { it.id == default.id || it.id !in deleted } + personal

    fun find(id: String?, themes: List<BlockTheme> = all) = themes.firstOrNull { it.id == id } ?: default

    fun choose(mode: BlockThemeMode, fixedId: String?, pool: Set<String>, themes: List<BlockTheme> = all,
        nextInt: (Int) -> Int = { Random.nextInt(it) }): BlockTheme = when (mode) {
        BlockThemeMode.FIXED -> find(fixedId, themes)
        BlockThemeMode.RANDOM -> themes.filter { it.id in pool }.let {
            if (it.isEmpty()) default else it[nextInt(it.size)]
        }
    }
}
