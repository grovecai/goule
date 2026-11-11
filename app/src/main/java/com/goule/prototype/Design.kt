package com.goule.prototype

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val WARM = Color.rgb(255, 240, 199)
internal val WARNING = Color.rgb(104, 71, 24)
internal val COLD = Color.rgb(227, 240, 250)
internal val BLUE = Color.rgb(44, 85, 120)
internal val INK = Color.rgb(34, 52, 45)
internal val TEAL = Color.rgb(30, 110, 93)
internal val MINT = Color.rgb(223, 243, 233)

internal val NIGHT = 0xff211327.toInt()
internal val NIGHT_TEXT = 0xfffff4dd.toInt()
internal val NIGHT_PANEL = 0xff34243b.toInt()
internal val NIGHT_ERROR = 0xffffabb3.toInt()
internal val NIGHT_MUTED = 0xffe1cfe0.toInt()
internal val NIGHT_LINE = 0x42fff4dd
internal val NIGHT_GOLD = 0xffffd280.toInt()

internal fun nightSurface() = GradientDrawable(
    GradientDrawable.Orientation.TL_BR,
    intArrayOf(0xff2b1931.toInt(), NIGHT, 0xff190e20.toInt()),
)

internal class IllustratedBackdrop(context: Context, private val welcome: Boolean) : Drawable() {
    private val bitmap = BitmapFactory.decodeResource(
        context.resources, if (welcome) R.drawable.welcome_composed else R.drawable.rules_sword,
    )
    private val image = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shade = Paint()

    override fun onBoundsChange(bounds: Rect) {
        if (welcome) return
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        if (width <= 0f || height <= 0f) return
        val widthScale = width / bitmap.width
        val coverScale = maxOf(widthScale, height / bitmap.height)
        val baseScale = if (welcome) coverScale else widthScale
        image.shader = artShader(
            baseScale, bounds.left + (width - bitmap.width * baseScale) * .55f,
            bounds.top + if (welcome) (height - bitmap.height * baseScale) * .5f else 0f,
        )
        val colors = if (welcome) intArrayOf(NIGHT, 0xec211327.toInt(), 0xd9211327.toInt(), NIGHT)
            else intArrayOf(0x66110917, 0, 0, 0x80190d24.toInt(), 0xa6190d24.toInt(), NIGHT)
        val stops = if (welcome) floatArrayOf(0f, .30f, .64f, 1f)
            else floatArrayOf(0f, .10f, .34f, .47f, .68f, 1f)
        shade.shader = LinearGradient(
            0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(), colors, stops, Shader.TileMode.CLAMP,
        )

    }

    private fun artShader(scale: Float, left: Float, top: Float) =
        BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(left, top)
            })
        }

    override fun draw(canvas: Canvas) {
        if (welcome) {
            canvas.drawBitmap(bitmap, null, bounds, image)
            return
        }
        canvas.drawRect(bounds, image)
        canvas.drawRect(bounds, shade)
    }

    override fun setAlpha(alpha: Int) {
        image.alpha = alpha
        shade.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        image.colorFilter = colorFilter
        shade.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Android no longer uses this for optimization")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

internal fun Context.surface(color: Int, radius: Int = 12, border: Int? = null) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radius).toFloat()
    if (border != null) setStroke(dp(1), border)
}

internal fun Context.copyText(value: CharSequence, size: Float = 16f, color: Int = NIGHT_TEXT, bold: Boolean = false) =
    TextView(this).apply {
        text = value
        textSize = size
        includeFontPadding = false
        setLineSpacing(0f, 1.18f)
        setTextColor(color)
        if (bold) {
            setTypeface(typeface, Typeface.BOLD)
            // Some system CJK fallback fonts render the bold face at regular weight.
            paint.isFakeBoldText = true
        }
    }

internal fun Context.deadlineLabel(deadline: Long, ruleNow: Long): String {
    val realNow = System.currentTimeMillis()
    val display = Date(realNow + deadline - ruleNow)
    val sameDay = DateFormat.format("yyyyMMdd", display) == DateFormat.format("yyyyMMdd", realNow)
    val locale = Locale.getDefault()
    val hours = if (DateFormat.is24HourFormat(this)) "Hm" else "hm"
    val skeleton = if (sameDay) hours else "MMMd$hours"
    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(display)
}

internal fun waitMinutes(ms: Long) = (ms + MINUTE - 1) / MINUTE
