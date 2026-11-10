package com.goule.prototype

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val preferences by lazy { prototypeSettings() }
    private lateinit var status: TextView
    private lateinit var target: TextView
    private val refreshConnection = Runnable { updateServiceStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val spacing = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(spacing, spacing, spacing, spacing)
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left + spacing, bars.top + spacing, bars.right + spacing, bars.bottom + spacing)
                insets
            }
        }
        status = TextView(this)
        target = TextView(this)
        content.addView(status)
        content.addView(target)
        content.addView(TextView(this).apply {
            text = getString(R.string.timing, QUOTA_MS / 1000, FREEZE_MS / 1000)
        })
        content.addView(Button(this).apply {
            setText(R.string.choose_target)
            setOnClickListener { chooseTarget() }
        })
        content.addView(Button(this).apply {
            setText(R.string.clear_target)
            setOnClickListener {
                preferences.edit().remove(TARGET_KEY).apply()
                refresh()
            }
        })
        content.addView(Button(this).apply {
            setText(R.string.open_settings)
            setOnClickListener {
                if (preferences.getBoolean(CONSENT_KEY, false)) {
                    openSettings()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.consent_title)
                        .setMessage(R.string.disclosure)
                        .setNegativeButton(R.string.later, null)
                        .setPositiveButton(R.string.consent_open) { _, _ ->
                            preferences.edit().putBoolean(CONSENT_KEY, true).apply()
                            openSettings()
                        }
                        .show()
                }
            }
        })
        setContentView(content)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        status.removeCallbacks(refreshConnection)
        super.onPause()
    }

    private fun refresh() {
        updateServiceStatus()
        val selected = preferences.getString(TARGET_KEY, null)
        val app = eligibleTargets().find { it.packageName == selected }
        target.text = if (app == null) getString(R.string.target_none)
            else getString(R.string.target_selected, app.label)
    }

    private fun updateServiceStatus() {
        status.removeCallbacks(refreshConnection)
        val service = ComponentName(this, FreezeService::class.java)
        val enabledProviders = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            enabledProviders.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == service }
        val label = when {
            !enabled || !preferences.getBoolean(CONSENT_KEY, false) -> R.string.service_disabled
            FreezeService.connected -> R.string.service_connected
            else -> R.string.service_pending
        }
        status.setText(label)
        if (label == R.string.service_pending) {
            status.postDelayed(refreshConnection, 500)
        }
    }

    private fun chooseTarget() {
        val apps = eligibleTargets()
        val dialog = AlertDialog.Builder(this).setTitle(R.string.choose_target)
        if (apps.isEmpty()) {
            dialog.setMessage(R.string.no_targets).setPositiveButton(android.R.string.ok, null)
        } else {
            val items = apps.map { "${it.label}\n${it.packageName}" }.toTypedArray()
            dialog.setItems(items) { _, index ->
                preferences.edit().putString(TARGET_KEY, apps[index].packageName).apply()
                refresh()
            }
        }
        dialog.show()
    }

    private fun openSettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
