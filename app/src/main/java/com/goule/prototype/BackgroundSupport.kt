package com.goule.prototype

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Add manufacturer instructions here while keeping system actions shared. */
internal data class BackgroundProfile(val title: String, val instructions: String)

internal fun backgroundProfile(manufacturer: String = Build.MANUFACTURER): BackgroundProfile =
    when (manufacturer.lowercase(java.util.Locale.ROOT)) {
        "oppo" -> BackgroundProfile(
            "OPPO 后台运行",
            "1. 打开应用信息，在「耗电管理」中选择「完全允许后台行为」。其他版本可能显示为允许后台活动。\n" +
                "2. 如果系统提供自启动管理，允许「斩心魔」自启动。\n" +
                "3. 打开最近任务，在「斩心魔」卡片菜单中选择锁定，减少一键清理的影响。\n" +
                "ColorOS 不同版本的名称和入口可能不同，请以手机实际显示为准。",
        )
        else -> BackgroundProfile(
            "后台运行",
            "在应用信息的电池设置中允许后台运行。如果系统提供自启动或最近任务锁定，可为「斩心魔」开启。",
        )
    }

internal fun Activity.batteryOptimizationIgnored(): Boolean =
    getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

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
