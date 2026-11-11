package com.goule.prototype

import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Add manufacturer instructions here while keeping system actions shared. */
internal data class BackgroundProfile(val title: String, val instructions: String, val confirmation: String)

internal fun backgroundProfile(manufacturer: String = Build.MANUFACTURER): BackgroundProfile =
    when (manufacturer.lowercase(java.util.Locale.ROOT)) {
        "oppo" -> BackgroundProfile(
            "OPPO 后台运行",
            "1. 打开系统设置中的应用信息。\n" +
                "2. 进入「耗电管理」，选择「完全允许后台行为」。\n" +
                "3. 返回此页，核对状态并确认。\n" +
                "「智能优化后台运行」不满足此项要求。ColorOS 其他版本可能显示为允许后台活动。",
            "我已选择「完全允许后台行为」",
        )
        else -> BackgroundProfile(
            "后台运行",
            "1. 打开系统设置中的应用信息。\n2. 在电池设置中允许后台运行或选择不优化。\n3. 返回此页，核对状态并确认。",
            "我已在系统中允许后台运行",
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
            prototypeSettings().edit().remove(BACKGROUND_CONFIRMATION_KEY).apply()
            return true
        } catch (_: ActivityNotFoundException) {
            // Continue to the next public system entry point.
        } catch (_: SecurityException) {
            // Some ROMs restrict settings activities.
        }
    }
    return false
}

internal fun Activity.backgroundRestricted(): Boolean =
    getSystemService(ActivityManager::class.java).isBackgroundRestricted

internal fun Activity.backgroundConfigured(): Boolean = backgroundReady(
    backgroundRestricted(),
    prototypeSettings().getBoolean(BACKGROUND_CONFIRMATION_KEY, false),
)

internal fun restrictedSettingsHelp(): String = if (Build.MANUFACTURER.equals("oppo", ignoreCase = true)) {
    "若提示「受限制的设置」：\n1. 打开应用信息。\n2. 进入「权限管理」。\n3. 在右上角菜单中选择「允许受限制的设置」，按系统提示确认。\n4. 返回无障碍设置，开启「斩心魔」服务。\nColorOS 不同版本的入口可能不同，请以手机实际显示为准。"
} else {
    "若提示「受限制的设置」，打开应用信息，在右上角菜单中选择「允许受限制的设置」。部分系统需先进入「权限管理」。确认后返回无障碍设置开启服务。"
}
