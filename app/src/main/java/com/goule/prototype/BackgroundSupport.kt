package com.goule.prototype

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Add manufacturer instructions here while keeping system actions shared. */
internal data class BackgroundProfile(val title: String, val instructions: String)

internal fun backgroundProfile(manufacturer: String = Build.MANUFACTURER): BackgroundProfile =
    when (manufacturer.lowercase(java.util.Locale.ROOT)) {
        "oppo" -> BackgroundProfile(
            "耗电管理（OPPO）",
            "应用信息 → 耗电管理 → 完全允许后台行为。\n" +
                "这是 OPPO/ColorOS 的路径，其他版本的名称可能不同。",
        )
        else -> BackgroundProfile(
            "后台耗电管理",
            "在应用信息的电池或耗电管理中，允许后台活动或选择「不受限制」。\n" +
                "这是厂商提供的设置，名称和路径因系统而异，没有此选项可略过。",
        )
    }

internal fun recentTaskLockHelp(manufacturer: String = Build.MANUFACTURER): String =
    if (manufacturer.equals("oppo", ignoreCase = true)) {
        "OPPO/ColorOS 操作步骤：\n" +
            "1. 从屏幕底部上滑并停顿，进入最近任务；使用三键导航时，点最近任务键。\n" +
            "2. 找到「斩心魔」卡片，点击卡片上方的「···」。\n" +
            "3. 选择「锁定」。应用名称旁出现小锁图标，即表示已锁定。\n" +
            "不同 ColorOS 版本的入口可能不同，请以手机实际显示为准。"
    } else {
        "1. 从屏幕底部上滑并停顿，进入最近任务；使用三键导航时，点最近任务键。\n" +
            "2. 找到「斩心魔」卡片，在卡片菜单或长按菜单中查找「锁定」。\n" +
            "3. 按系统提示完成锁定，并确认卡片上出现锁定标记。\n" +
            "锁定入口和标记因系统而异，没有此选项可略过。"
    }

/** Public settings actions can be missing on manufacturer ROMs. Try each fallback. */
internal fun Activity.openBackgroundSettings(battery: Boolean): Boolean {
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    val candidates = if (battery) listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails,
        Intent(Settings.ACTION_SETTINGS),
    ) else listOf(appDetails, Intent(Settings.ACTION_SETTINGS))
    for (intent in candidates) {
        try {
            startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // Continue to the next public system entry point.
        } catch (_: SecurityException) {
            // Some ROMs restrict settings activities.
        }
    }
    return false
}

internal fun restrictedSettingsHelp(): String = if (Build.MANUFACTURER.equals("oppo", ignoreCase = true)) {
    "若提示「受限制的设置」：\n1. 打开应用信息。\n2. 进入「权限管理」。\n3. 在右上角菜单中选择「允许受限制的设置」，按系统提示确认。\n4. 返回无障碍设置，开启「斩心魔」服务。\nColorOS 不同版本的入口可能不同，请以手机实际显示为准。"
} else {
    "若提示「受限制的设置」，打开应用信息，在右上角菜单中选择「允许受限制的设置」。部分系统需先进入「权限管理」。确认后返回无障碍设置开启服务。"
}

internal fun riskInterceptionHelp(manufacturer: String = Build.MANUFACTURER): String {
    val service = if (manufacturer.equals("oppo", ignoreCase = true)) {
        "OPPO/ColorOS 的「风险行为拦截」是系统自带「手机管家」中的一项服务。"
    } else {
        "部分厂商在系统自带「手机管家」中提供「风险行为拦截」服务。"
    }
    return service + "它可能拦截「斩心魔」并关闭无障碍服务，导致计时和禁用暂停。\n" +
        "1. 打开系统自带的「手机管家」，查找「风险行为拦截」服务。\n" +
        "2. 找到该功能的白名单，将「斩心魔」加入白名单。\n" +
        "3. 若无障碍服务已被关闭，请到无障碍设置中重新开启；若开关已开但显示「连接中断」，先关闭再开启。返回斩心魔，确认状态为「已设置」。\n" +
        "这是手机厂商提供的服务，并非 Android 通用设置。「手机管家」、服务及白名单的入口和名称因品牌、系统版本而异，请以手机实际显示为准；没有此服务可略过。"
}

/** Explain recovery without claiming that an app can restore a revoked system grant. */
internal fun accessibilityRecoveryHelp(manufacturer: String = Build.MANUFACTURER): String =
    when (manufacturer.lowercase(java.util.Locale.ROOT)) {
        "oppo" -> "部分 ColorOS 会在重启后关闭通过安装包安装的应用的无障碍服务，即使已允许后台运行。" +
            "清理后也可能开关仍在但服务已中断，此时请先关闭再开启；若随后出现安全提醒，请核对后选择「保持开启」。" +
            "返回斩心魔确认「已设置」。应用无法代替你重新开启无障碍。"
        else -> "若重启或系统清理后服务被关闭，请在系统设置中重新开启。若开关已开但显示「连接中断」，请先关闭再开启，返回确认「已设置」。应用无法代替你重新开启无障碍。"
    }
