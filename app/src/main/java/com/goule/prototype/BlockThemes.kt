package com.goule.prototype

import kotlin.random.Random

/** Presentation only; never participates in a rule's clock or state transitions. */
internal enum class BlockThemeMode(val label: String) {
    FIXED("固定主题"), RANDOM("随机主题"), CUSTOM("自定义图片");

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
)

internal object BlockThemes {
    val all = listOf(
        BlockTheme("elder", "戒律长老", "够了！", "再刷下去，你还飞不飞升了？", "戒律长老怒视前方，指向屏幕大声喝止"),
        BlockTheme("rescue", "拯救网瘾少年", "抓住了！", "人先上岸，正事还等着你呢！", "长老拉住被手机漩涡困住的年轻修行者"),
        BlockTheme("magic", "魔法劝退", "收机术！", "手机已封印，快去完成你的主线任务！", "魔法导师用金色法术收起学徒的手机"),
        BlockTheme("kitchen", "锅要糊了", "锅要糊了！", "手机不会跑，锅里的菜可等不了！", "掌勺人举着锅铲，指向冒烟的炒锅"),
        BlockTheme("garden", "菜地除草", "草比菜高！", "别给心魔浇水了，快去给菜地除草！", "老农扛着锄头，身后菜畦长满杂草"),
        BlockTheme("marriage", "月老催婚", "缘分呢？", "姻缘不在下一条，去见见真实的人！", "月老拿着红线催促，远处有人在桥上相见"),
        BlockTheme("baby", "奶瓶召唤", "别光刷娃！", "想要的小日子，得放下手机一起经营！", "抱着宝宝的大人举起奶瓶，催人回归生活"),
        BlockTheme("work", "摸鱼警报", "老板来了！", "牛马先归位，别把工位刷成离职位！", "上司探过工位指向屏幕，桌上摆着小金牛"),
        BlockTheme("exam", "挂科预警", "要挂科了！", "这题不会就是不会，下一条也救不了你！", "教授指着厚厚的课本，催促大学生复习"),
        BlockTheme("sunlight", "出门晒太阳", "要发霉了！", "别在屋里长蘑菇了，出去晒晒太阳！", "朋友拉开窗帘，窗外阳光照进紫色房间"),
        BlockTheme("craving", "戒刷心魔", "瘾又来了？", "手指歇一歇，别让下一条替你过一生！", "修行者用金色剑光斩断手机幻影的锁链"),
    )
    val default get() = all.first()
    val custom = BlockTheme("custom", "自定义图片", "先放下手机", "把时间留给你真正想做的事。", "你选择的禁用页图片")

    fun find(id: String?) = all.firstOrNull { it.id == id } ?: default

    fun choose(mode: BlockThemeMode, fixedId: String?, pool: Set<String>, hasCustom: Boolean,
        nextInt: (Int) -> Int = { Random.nextInt(it) }): BlockTheme = when (mode) {
        BlockThemeMode.FIXED -> find(fixedId)
        BlockThemeMode.CUSTOM -> if (hasCustom) custom else default
        BlockThemeMode.RANDOM -> all.filter { it.id in pool }.let {
            if (it.isEmpty()) default else it[nextInt(it.size)]
        }
    }
}
