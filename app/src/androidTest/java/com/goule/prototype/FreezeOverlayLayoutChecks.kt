package com.goule.prototype

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
            overlay.setPadding(configured.dp(16), configured.dp(8), configured.dp(24), configured.dp(16))
            // Resizing the same overlay must switch both ways without a new service window.
            for ((width, height) in listOf(360 to 640, 640 to 320, 800 to 360, 640 to 240, 360 to 640, 640 to 320)) {
                val w = configured.dp(width)
                val h = configured.dp(height)
                overlay.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                overlay.layout(0, 0, w, h)
                check(descendants(overlay).filterIsInstance<TextView>().any { it.text.toString() == deadline }) {
                    "Deadline changed during resize"
                }
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
                if (width == 640 && height == 320) {
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    overlay.draw(Canvas(bitmap))
                    File(context.getExternalFilesDir(null), "overlay-landscape-$fontScale.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
