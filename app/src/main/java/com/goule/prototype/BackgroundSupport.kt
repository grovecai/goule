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

/** Explain recovery without claiming that an app can restore a revoked system grant. */
internal fun accessibilityRecoveryHelp(manufacturer: String = Build.MANUFACTURER): String =
    when (manufacturer.lowercase(java.util.Locale.ROOT)) {
        "oppo" -> "部分 ColorOS 会在重启后关闭通过安装包安装的应用的无障碍服务，即使已允许后台运行。" +
            "请在系统设置中重新开启；若随后出现安全提醒，请核对后选择「保持开启」。斩心魔无法自动恢复这项授权。"
        else -> "若重启或系统清理后服务被关闭，请在系统设置中重新开启。斩心魔无法自动恢复这项授权。"
    }
