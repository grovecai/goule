package com.goule.prototype

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

/** Uses the overlay's safe bounds, which can change while a target app is blocked. */
internal class FreezeOverlayView(
    context: Context,
    private val appLabel: String,
    private val deadline: String,
    private val onCloseTarget: () -> Unit,
    private val onGoHome: () -> Unit,
) : FrameLayout(context) {
    private val paper = Color.rgb(255, 240, 220)
    private val backdrop = Color.rgb(32, 17, 37)
    private val outline = Color.rgb(50, 29, 43)
    private var layoutBounds: Pair<Int, Int>? = null
    private var dialogueView: View? = null
    private var appeared = false

    init {
        isClickable = true
        setBackgroundColor(backdrop)
        setOnApplyWindowInsetsListener { _, insets ->
            val safe = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or
                WindowInsets.Type.displayCutout() or WindowInsets.Type.mandatorySystemGestures())
            setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }

    override fun getAccessibilityClassName(): CharSequence = EVENT_CLASS

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!appeared && isAttachedToWindow) {
            appeared = true
            if (ValueAnimator.areAnimatorsEnabled()) dialogueView?.let {
                it.translationY = -context.dp(12).toFloat()
                it.animate().translationY(0f).setDuration(180).start()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(1)
        val height = (MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(1)
        val bounds = width to height
        if (layoutBounds != bounds) {
            layoutBounds = bounds
            removeAllViews()
            if (width > height) buildLandscape(width) else buildPortrait(width, height)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun art(crop: Boolean) = ImageView(context).apply {
        setImageResource(R.drawable.discipline_elder)
        contentDescription = "戒律长老怒视前方，指向屏幕大声喝止"
        adjustViewBounds = !crop
        scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
    }

    private fun speech(widthDp: Int, compact: Boolean): View {
        val dialogue = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(if (compact) 10 else 15), context.dp(16), context.dp(if (compact) 10 else 17))
            background = context.surface(paper, 5).apply { setStroke(context.dp(3), outline) }
        }
        val headline = if (compact) 34f else ((widthDp - 56) * .19f).coerceIn(40f, 64f)
        val appLine = if (compact) 22f else ((widthDp - 56) * .095f).coerceIn(20f, 33f)
        dialogue.addView(context.copyText("够了！", headline, Color.rgb(172, 48, 32), true).apply {
            isAccessibilityHeading = true
            letterSpacing = -.055f
        })
        val prefix = "别刷 "
        val warning = SpannableString("$prefix$appLabel 了！").apply {
            setSpan(ForegroundColorSpan(0xff70238a.toInt()), prefix.length,
                prefix.length + appLabel.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        dialogue.addView(context.copyText(warning, appLine, outline, true).apply {
            letterSpacing = -.055f
            setLineSpacing(0f, 1.13f)
        })
        dialogue.addView(context.copyText("再刷下去，你还飞不飞升了？", if (compact) 16f else 18f, outline, true).apply {
            setPadding(0, context.dp(if (compact) 4 else 8), 0, 0)
        })
        return FrameLayout(context).apply {
            clipChildren = false
            addView(dialogue, LayoutParams(-1, -2).apply { topMargin = context.dp(9) })
            addView(View(context).apply {
                background = context.surface(paper, 0)
                rotation = 45f
            }, LayoutParams(context.dp(16), context.dp(16), Gravity.TOP or Gravity.START).apply {
                marginStart = context.dp((widthDp * .34f).toInt())
                topMargin = context.dp(3)
            })
        }.also { dialogueView = it }
    }

    private fun action(primary: Boolean) = Button(context).apply {
        setText(if (primary) R.string.close_target else R.string.go_home)
        setTextColor(if (primary) outline else paper)
        isAllCaps = false
        stateListAnimator = null
        minHeight = context.dp(48)
        backgroundTintList = null
        background = if (primary) context.surface(paper, 9)
            else context.surface(backdrop, 9, Color.rgb(143, 107, 94))
        setOnClickListener { if (primary) onCloseTarget() else onGoHome() }
    }

    private fun footer(compact: Boolean) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(if (compact) 0 else 20), context.dp(if (compact) 8 else 16),
            context.dp(if (compact) 0 else 20), context.dp(if (compact) 0 else 12))
        addView(LinearLayout(context).apply {
            orientation = if (compact) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(context.copyText("解禁时刻", 14f, Color.rgb(232, 190, 176), true),
                if (compact) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
            addView(context.copyText(deadline, if (compact) 22f else 24f, paper, true))
        })
        if (compact) {
            addView(LinearLayout(context).apply {
                addView(action(true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = context.dp(8) })
                addView(action(false), LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(8) })
        } else {
            addView(action(true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(8) })
            addView(action(false), LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(8) })
        }
    }

    private fun buildLandscape(width: Int) {
        val dialogueWidthDp = ((width * .62f - context.dp(32)) / resources.displayMetrics.density).toInt()
        val row = LinearLayout(context)
        addView(row, LayoutParams(-1, -1))
        row.addView(art(true), LinearLayout.LayoutParams(0, -1, .38f))
        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(12))
            // Only the dialogue scrolls for long names or large fonts; actions stay in view.
            addView(ScrollView(context).apply {
                addView(speech(dialogueWidthDp, compact = true))
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(footer(compact = true), LinearLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(0, -1, .62f))
    }

    private fun buildPortrait(width: Int, height: Int) {
        val heightDp = (height / resources.displayMetrics.density).toInt()
        val widthDp = (width / resources.displayMetrics.density).toInt()
        val short = heightDp < 700
        addView(ScrollView(context).apply {
            isFillViewport = true
            isClickable = true
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(art(short), LinearLayout.LayoutParams(-1, if (short) (height * .30f).toInt() else -2))
                    addView(speech(widthDp, compact = false), LinearLayout.LayoutParams(-1, -2).apply {
                        marginStart = context.dp(12)
                        marginEnd = context.dp(12)
                        topMargin = -context.dp(18)
                    })
                }, LinearLayout.LayoutParams(-1, -2, 1f))
                addView(footer(compact = false))
            })
        }, LayoutParams(-1, -1))
    }

    companion object {
        const val EVENT_CLASS = "com.goule.prototype.FreezeOverlay"
    }
}
