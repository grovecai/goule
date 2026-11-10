package com.goule.prototype

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri

internal const val TARGET_KEY = "target"
internal const val CONSENT_KEY = "consent"

internal fun Context.prototypeSettings() =
    getSharedPreferences("prototype", Context.MODE_PRIVATE)

internal data class TargetApp(val packageName: String, val label: String)

internal fun Context.eligibleTargets(): List<TargetApp> {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
    val excluded = (packageManager.queryIntentActivities(home, 0) +
        packageManager.queryIntentActivities(dial, 0))
        .mapTo(mutableSetOf(packageName)) { it.activityInfo.packageName }

    val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    return packageManager.queryIntentActivities(launch, 0)
        .asSequence()
        .filter { it.activityInfo.packageName !in excluded }
        .filter { it.activityInfo.applicationInfo.flags and systemFlags == 0 }
        .distinctBy { it.activityInfo.packageName }
        .map { TargetApp(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
        .sortedWith(compareBy(TargetApp::label, TargetApp::packageName))
        .toList()
}
