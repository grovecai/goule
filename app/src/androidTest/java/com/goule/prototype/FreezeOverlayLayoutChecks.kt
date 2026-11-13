package com.goule.prototype

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Exercises Android measurement, safe padding, resizing and touch dispatch on the production view. */
internal object FreezeOverlayLayoutChecks {
    fun run(context: Context) {
        for (fontScale in listOf(1f, 1.3f, 2f)) {
            val configured = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                this.fontScale = fontScale
            })
            var closeCount = 0
            var homeCount = 0
            val deadline = "11月14日 下午11:59"
            val overlay = FreezeOverlayView(configured, "这是一个用于检查换行的很长很长的目标应用名称", deadline,
                onCloseTarget = { closeCount++ }, onGoHome = { homeCount++ })
            // Resizing the same overlay must switch both ways without a new service window.
            for ((width, height) in listOf(360 to 640, 640 to 320, 800 to 360, 640 to 240, 360 to 640, 640 to 320)) {
                val bars = Insets.of(0, configured.dp(24), 0, configured.dp(24))
                val cutout = if (width > height) Insets.of(configured.dp(28), 0, 0, 0)
                    else Insets.of(0, configured.dp(32), 0, 0)
                val gestures = Insets.of(configured.dp(16), 0, configured.dp(24), configured.dp(16))
                val safeInsets = Insets.max(Insets.max(bars, cutout), gestures)
                for (visible in listOf(true, false, true)) {
                    overlay.dispatchApplyWindowInsets(WindowInsets.Builder()
                        .setInsets(WindowInsets.Type.systemBars(), if (visible) bars else Insets.NONE)
                        .setInsetsIgnoringVisibility(WindowInsets.Type.systemBars(), bars)
                        .setVisible(WindowInsets.Type.systemBars(), visible)
                        .setInsets(WindowInsets.Type.displayCutout(), cutout)
                        .setInsetsIgnoringVisibility(WindowInsets.Type.displayCutout(), cutout)
                        .setInsets(WindowInsets.Type.mandatorySystemGestures(), gestures)
                        .setInsetsIgnoringVisibility(WindowInsets.Type.mandatorySystemGestures(), gestures)
                        .build())
                    check(overlay.paddingLeft == safeInsets.left && overlay.paddingTop == safeInsets.top &&
                        overlay.paddingRight == safeInsets.right && overlay.paddingBottom == safeInsets.bottom) {
                        "Safe insets lost or added twice: ${width}x$height barsVisible=$visible"
                    }
                }
                val w = configured.dp(width)
                val h = configured.dp(height)
                overlay.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                overlay.layout(0, 0, w, h)
                check(descendants(overlay).filterIsInstance<TextView>().any { it.text.toString() == deadline }) {
                    "Deadline changed during resize"
                }
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                overlay.draw(Canvas(bitmap))
                for ((x, y) in listOf(0 to 0, w / 2 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1)) {
                    check(bitmap.getPixel(x, y) == Color.rgb(32, 17, 37)) {
                        "Overlay background leaks at ($x,$y) in ${width}x$height"
                    }
                }
                if (width == 640 && height == 320 || width == 360 && height == 640) {
                    File(context.getExternalFilesDir(null), "overlay-${width}x$height-$fontScale.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                bitmap.recycle()
                if (width < height) continue
                val safe = Rect(overlay.paddingLeft, overlay.paddingTop, w - overlay.paddingRight, h - overlay.paddingBottom)
                val buttons = descendants(overlay).filterIsInstance<Button>().toList()
                check(buttons.size == 2)
                for (button in buttons) {
                    val bounds = Rect(0, 0, button.width, button.height)
                    overlay.offsetDescendantRectToMyCoords(button, bounds)
                    check(safe.contains(bounds) && button.height >= configured.dp(48)) {
                        "Action clipped at ${width}x$height font=$fontScale: $bounds safe=$safe"
                    }
                    var parent = button.parent
                    while (parent is View && parent !== overlay) {
                        check(parent !is ScrollView) { "Action requires scrolling" }
                        parent = parent.parent
                    }
                    val before = closeCount + homeCount
                    var releases = 0
                    button.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP) releases++
                        false
                    }
                    val time = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(time, time, action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                        overlay.dispatchTouchEvent(event)
                        event.recycle()
                    }
                    check(releases == 1) { "Action did not receive touch" }
                    button.setOnTouchListener(null)
                    // Detached views queue their click until attachment; check the callback explicitly.
                    button.performClick()
                    check(closeCount + homeCount == before + 1) { "Wrong exit callback" }
                }
                check(closeCount == homeCount) { "Exit callbacks are mismatched" }
            }
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
