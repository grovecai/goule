package com.goule.prototype

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.Editable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private enum class Page { WELCOME, PERMISSION, RULES, SETTINGS, PICKER, FORM, DETAIL, REMOVE, DISCARD, HELP, BACKGROUND }
    private enum class ServiceState { OFF, CONNECTING, ON, PAUSED }

    private val store by lazy { RuleStore.get(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var onboarding = false
    private var showSetupHelp = false
    private var showBackgroundExtras = false
    private var page = Page.WELCOME
    private var permissionOrigin = Page.WELCOME
    private var helpOrigin = Page.RULES
    private var targets = emptyList<TargetApp>()
    private var targetId: String? = null
    private var replacingId: String? = null
    private var editing = false
    private var draftUse = ""
    private var draftBlock = ""
    private var dirty = false
    private var query = ""
    private var awaitingFirstConnection = false
    private var pickerReturn = Page.RULES
    private var appState = ServiceState.OFF
    private var visibleSignature = emptyList<String>()
    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private val refresh = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = when (val savedPage = savedInstanceState?.getString("page")) {
            "TIMING", "PRIVACY" -> Page.SETTINGS
            else -> Page.entries.firstOrNull { it.name == savedPage }
        }
            ?: if (store.welcomed) Page.RULES else Page.WELCOME
        savedInstanceState?.let {
            onboarding = it.getBoolean("onboarding")
            permissionOrigin = Page.valueOf(it.getString("origin", Page.WELCOME.name))
            helpOrigin = Page.valueOf(it.getString("helpOrigin", Page.RULES.name))
            pickerReturn = Page.valueOf(it.getString("pickerReturn", Page.RULES.name))
            targetId = it.getString("target")
            replacingId = it.getString("replacing")
            editing = it.getBoolean("editing")
            draftUse = it.getString("use", "")
            draftBlock = it.getString("block", "")
            dirty = it.getBoolean("dirty")
            query = it.getString("query", "")
            awaitingFirstConnection = it.getBoolean("awaiting")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { back() }
        }
        targets = eligibleTargets()
        appState = serviceState()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page.name)
        outState.putBoolean("onboarding", onboarding)
        outState.putString("origin", permissionOrigin.name)
        outState.putString("helpOrigin", helpOrigin.name)
        outState.putString("pickerReturn", pickerReturn.name)
        outState.putString("target", targetId)
        outState.putString("replacing", replacingId)
        outState.putBoolean("editing", editing)
        outState.putString("use", draftUse)
        outState.putString("block", draftBlock)
        outState.putBoolean("dirty", dirty)
        outState.putString("query", query)
        outState.putBoolean("awaiting", awaitingFirstConnection)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        val freshTargets = eligibleTargets()
        val changed = freshTargets != targets
        targets = freshTargets
        updateStatus(changed)
        handler.postDelayed(refresh, 1_000)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    @Deprecated("Used by Android 11 and 12")
    override fun onBackPressed() = back()

    private fun serviceState(): ServiceState {
        val component = ComponentName(this, FreezeService::class.java)
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
        return when {
            store.consent && enabled && FreezeService.connected -> ServiceState.ON
            store.consent && enabled -> ServiceState.CONNECTING
            store.activated -> ServiceState.PAUSED
            else -> ServiceState.OFF
        }
    }

    private fun updateStatus(targetsChanged: Boolean = false) {
        val previous = appState
        appState = serviceState()
        if (appState == ServiceState.ON) {
            if (!store.activated) store.activate()
            if (awaitingFirstConnection && (page == Page.RULES || page == Page.SETTINGS)) {
                awaitingFirstConnection = false
                go(Page.BACKGROUND)
                return
            }
        }
        if (page in listOf(Page.RULES, Page.DETAIL, Page.SETTINGS, Page.PICKER, Page.BACKGROUND, Page.PERMISSION) &&
            (targetsChanged || previous != appState || visibleSignature != signature())
        ) render(keepScroll = true)
    }

    private fun backgroundStatus(): String = when {
        backgroundConfigured() -> "已设置"
        backgroundRestricted() -> "未设置"
        else -> "待确认"
    }

    private fun setupMissing(): List<String> = buildList {
        if (appState != ServiceState.ON) add("无障碍服务${serviceStatusLabel()}")
        if (!backgroundConfigured()) add("允许后台运行${backgroundStatus()}")
    }

    private fun setupStatus() = "无障碍服务 · ${serviceStatusLabel()}\n允许后台运行 · ${backgroundStatus()}"

    private fun serviceStatusLabel() = when (appState) {
        ServiceState.ON -> "已设置"
        ServiceState.CONNECTING -> "正在连接"
        ServiceState.PAUSED, ServiceState.OFF -> "未设置"
    }

    private fun signature(): List<String> {
        val now = store.now()
        return when (page) {
            Page.RULES -> store.rules.map { ruleStatus(it, now) } + setupStatus()
            Page.BACKGROUND, Page.SETTINGS -> listOf(setupStatus(), batteryOptimizationIgnored().toString(), backgroundRestricted().toString())
            Page.DETAIL -> currentRule()?.let {
                listOf(ruleStatus(it, now), waitMinutes(it.editWait(now)).toString(), waitMinutes(it.removeWait(now)).toString())
            }.orEmpty()
            else -> emptyList()
        }
    }

    private fun go(destination: Page) {
        page = destination
        render()
    }

    private val welcomeBackdrop by lazy { IllustratedBackdrop(this, welcome = true) }
    private val rulesBackdrop by lazy { IllustratedBackdrop(this, welcome = false) }

    private fun render(keepScroll: Boolean = false) {
        val oldY = if (keepScroll) scroll.scrollY else 0
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = when (page) {
                Page.WELCOME -> welcomeBackdrop
                Page.RULES -> if (store.rules.isEmpty()) nightSurface() else rulesBackdrop
                else -> nightSurface()
            }
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                insets
            }
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            if (page == Page.RULES && store.rules.isNotEmpty()) {
                setOnScrollChangeListener { view, _, y, _, _ ->
                    val opacity = (y.toFloat() / dp(136)).coerceIn(0f, 1f) * 232
                    view.setBackgroundColor((NIGHT and 0x00ffffff) or (opacity.toInt() shl 24))
                }
            }
        }
        content = column(if (page == Page.RULES && store.rules.isNotEmpty()) 11 else 14).apply {
            clipToPadding = false
            setPadding(dp(19), if (page == Page.RULES && store.rules.isNotEmpty()) 0 else dp(12), dp(19), dp(16))
            isFocusableInTouchMode = true
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when (page) {
            Page.WELCOME -> welcome()
            Page.PERMISSION -> permission()
            Page.RULES -> rules()
            Page.SETTINGS -> settings()
            Page.PICKER -> picker()
            Page.FORM -> form()
            Page.DETAIL -> detail()
            Page.REMOVE -> removePage()
            Page.DISCARD -> discardPage()
            Page.HELP -> help()
            Page.BACKGROUND -> backgroundSettings()
        }
        if (page == Page.RULES || page == Page.SETTINGS) bottomNavigation()
        visibleSignature = signature()
        setContentView(root)
        val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(0, lightBars)
        root.requestApplyInsets()
        if (oldY > 0) scroll.post { scroll.scrollTo(0, oldY) }
    }

    private fun column(gap: Int = 0): LinearLayout = object : LinearLayout(this) {
        override fun onViewAdded(child: View) {
            super.onViewAdded(child)
            if (childCount > 1 && gap > 0) (child.layoutParams as LayoutParams).topMargin = dp(gap)
        }
    }.apply { orientation = LinearLayout.VERTICAL }

    private fun row() = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }

    private fun card(gap: Int = 8, fill: Int = NIGHT_PANEL, border: Int? = NIGHT_LINE) = column(gap).apply {
        background = surface(fill, 12, border)
        setPadding(dp(13), dp(13), dp(13), dp(13))
    }

    private fun button(
        label: String, primary: Boolean = false, soft: Boolean = false, action: () -> Unit,
    ) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        includeFontPadding = false
        minHeight = dp(44)
        minimumHeight = dp(44)
        minWidth = 0
        minimumWidth = 0
        stateListAnimator = null
        backgroundTintList = null
        gravity = if (primary || soft) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
        val inset = if (primary || soft) dp(13) else 0
        setPadding(inset, dp(4), inset, dp(4))
        setTextColor(if (primary) INK else NIGHT_TEXT)
        val fill = if (primary) NIGHT_GOLD else if (soft) NIGHT_PANEL else Color.TRANSPARENT
        val radius = if (soft) 22 else 12
        val fillDrawable = if (primary) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xffffe386.toInt(), 0xffffbc3d.toInt())).apply {
            cornerRadius = dp(radius).toFloat()
        } else surface(fill, radius, if (soft) NIGHT_LINE else null)
        background = RippleDrawable(ColorStateList.valueOf(if (primary) 0x22000000 else 0x22ffffff),
            fillDrawable, surface(Color.WHITE, radius))
        if (primary) {
            setTypeface(typeface, Typeface.BOLD)
            paint.isFakeBoldText = true
            minimumHeight = dp(48)
        }
        setOnClickListener { action() }
    }

    private fun heading(label: String, size: Float = 29f) = copyText(label, size, bold = true).apply {
        isAccessibilityHeading = true
        letterSpacing = -.04f
    }

    private fun up() {
        content.addView(button("← 返回") { back() }.apply { setTextColor(NIGHT_MUTED) },
            LinearLayout.LayoutParams(-2, -2))
    }

    private fun footer(
        label: String, secondaryLabel: String? = null,
        secondary: (() -> Unit)? = null, action: () -> Unit,
    ) {
        val group = column(4)
        group.addView(button(label, primary = true, action = action).apply {
            if (page == Page.WELCOME) {
                typeface = resources.getFont(R.font.welcome_kai)
                paint.isFakeBoldText = true
            }
        })
        if (secondaryLabel != null && secondary != null) {
            group.addView(button(secondaryLabel, soft = true, action = secondary))
        }
        if (page == Page.WELCOME) content.addView(group)
        else root.addView(group, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(19), dp(4), dp(19), dp(12))
        })
    }

    private fun welcome() {
        val title = "斩断无尽的下滑\n守住道心"
        val height = resources.configuration.screenHeightDp
        val short = height < 700
        content.setPadding(dp(19), dp(if (short) 22 else 48), dp(19), dp(18))
        content.addView(copyText(title, 34f, Color.TRANSPARENT, true).apply {
            isAccessibilityHeading = true
            setLineSpacing(0f, 1.06f)
            letterSpacing = 0f
        })
        content.addView(Space(this), LinearLayout.LayoutParams(-1, dp(if (short) 110 else 150), 1f))
        content.addView(copyText("你清醒时立下戒律\n心魔上头时，替你斩断刷屏循环", 16f, NIGHT_TEXT).apply {
            typeface = resources.getFont(R.font.welcome_kai)
        })
        val example = card(fill = 0xfffff3da.toInt(), border = null).apply {
            setPadding(dp(13), dp(8), dp(13), dp(8))
            addView(row().apply {
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.sample_video)
                    contentDescription = "示例短视频 App"
                }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) })
                addView(durations(DEFAULT_USE, DEFAULT_BLOCK, INK, inline = true), LinearLayout.LayoutParams(0, -2, 1f))
            })
        }
        content.addView(example)
        content.addView(Space(this), LinearLayout.LayoutParams(-1, dp(if (short) 0 else 8)))
        footer("立下第一条戒律") {
            onboarding = true
            permissionOrigin = Page.WELCOME
            go(Page.PERMISSION)
        }
    }

    private fun guideHeader(title: String, status: String, purpose: String) {
        up()
        content.addView(heading(title))
        if (onboarding) content.addView(copyText(
            if (page == Page.PERMISSION) "第 1 步，共 2 步" else "第 2 步，共 2 步", 14f, NIGHT_MUTED,
        ))
        content.addView(tag(status))
        content.addView(copyText(purpose, 14f, NIGHT_MUTED))
    }

    private fun permission() {
        guideHeader("无障碍服务", serviceStatusLabel(), "识别目标 App、累计使用时间，并在用满后显示禁用页。")
        content.addView(copyText("1. 打开系统设置。\n2. 在「已下载的应用」中找到「斩心魔」，开启服务。\n3. 返回此页，确认状态为「已设置」。"))
        content.addView(copyText(getString(R.string.disclosure), 14f, NIGHT_MUTED))
        content.addView(button(if (store.consent) "打开系统设置" else "同意并打开系统设置", primary = true) {
            store.approve()
            openSystemSettings()
        })
        content.addView(button("遇到问题", soft = true) {
            showSetupHelp = !showSetupHelp
            render(keepScroll = true)
        })
        if (showSetupHelp) {
            content.addView(copyText("若提示「受限制的设置」，打开应用信息，在右上角菜单中允许受限制的设置，再返回开启服务。服务一直未连接时，可在系统中关闭后重新开启。", 14f, NIGHT_MUTED))
            content.addView(button("打开应用信息", soft = true) {
                if (!openBackgroundSettings(battery = false)) toast("请手动打开系统设置")
            })
        }
        content.addView(button("下一步：允许后台运行", soft = true) {
            if (appState != ServiceState.ON) {
                toast("请先开启无障碍服务并等待连接")
                return@button
            }
            awaitingFirstConnection = false
            go(Page.BACKGROUND)
        }.apply {
            isEnabled = appState == ServiceState.ON
            alpha = if (isEnabled) 1f else .45f
        })
    }

    private fun rules() {
        val now = store.now()
        if (store.rules.isEmpty()) content.addView(heading("戒律"))
        else {
            val height = resources.configuration.screenHeightDp
            content.addView(Space(this), LinearLayout.LayoutParams(-1, dp((height * .43f).toInt())))
            content.addView(row().apply {
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.rules_slogan)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_START
                    contentDescription = "道心自己守，心魔上头，一剑斩断"
                    isAccessibilityHeading = true
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(button("＋", soft = true) { openPicker() }.apply {
                    contentDescription = "添加心魔"
                    textSize = 25f
                    setPadding(0, 0, 0, 0)
                    setTextColor(WARNING)
                    background = surface(0xfff8d990.toInt(), 9)
                }, LinearLayout.LayoutParams(dp(44), -2).apply { marginStart = dp(8) })
            })
        }
        if (setupMissing().isNotEmpty()) content.addView(card(3).apply {
            addView(copyText("配置未完成", 15f, NIGHT_GOLD, true))
            addView(copyText(setupMissing().joinToString("\n"), 14f, NIGHT_TEXT))
            addView(button("完成必要设置", primary = true) {
                permissionOrigin = Page.RULES
                go(if (appState == ServiceState.ON) Page.BACKGROUND else Page.PERMISSION)
            })
        })
        if (store.rules.isEmpty()) {
            content.addView(column(8).apply {
                addView(copyText("挑一个让你刷到停不下来的 App，给它立条戒", 17f, NIGHT_TEXT, true))
                addView(button("立下第一条戒律") { openPicker() })
            })
            return
        }
        val items = store.rules.sortedWith(compareByDescending<Rule> { it.isBlocked(now) }
            .thenBy { if (it.isBlocked(now)) it.blockedUntil else null }.thenBy { it.label })
        content.addView(column().apply {
            divider(this, NIGHT_LINE)
            for (rule in items) {
                addView(column(7).apply {
                    setPadding(0, dp(11), 0, dp(11))
                    isFocusable = true
                    setOnClickListener { showRule(rule.packageName) }
                    addView(appRow(rule.packageName, rule.label, ruleStatus(rule, now), NIGHT_TEXT))
                    addView(durations(rule.useMinutes, rule.blockMinutes, NIGHT_TEXT, inline = true),
                        LinearLayout.LayoutParams(-1, -2).apply { marginStart = dp(48) })
                })
                divider(this, NIGHT_LINE)
            }
        })
    }

    private fun divider(parent: LinearLayout, color: Int = NIGHT_LINE) {
        parent.addView(View(this).apply { setBackgroundColor(color) }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun iconFor(id: String): ImageView = ImageView(this).apply {
        val icon = runCatching { packageManager.getApplicationIcon(id) }.getOrNull()
        setImageDrawable(icon ?: getDrawable(android.R.drawable.ic_menu_help))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = surface(MINT, 12)
        clipToOutline = true
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    private fun tag(status: String): TextView {
        val (fill, color) = when {
            status.startsWith("禁用至") -> COLD to BLUE
            status in listOf("找不到此 App", "未生效", "未开启", "正在连接") -> WARM to WARNING
            else -> MINT to TEAL
        }
        return copyText(status, 12f, color).apply {
            background = surface(fill, 20)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
    }

    private fun appRow(id: String, label: String, status: String? = null, color: Int = NIGHT_TEXT) = row().apply {
        addView(iconFor(id), LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) })
        addView(copyText(label, 14f, color, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (status != null) addView(tag(status), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
    }

    private fun durations(use: Int, block: Int, textColor: Int = NIGHT_TEXT, inline: Boolean = false) = row().apply {
        gravity = Gravity.TOP
        for ((label, minutes, color) in listOf(Triple("刷", use, TEAL), Triple("禁", block, 0xffaf3541.toInt()))) {
            val chip = if (inline) row() else column()
            chip.addView(copyText(label, 14f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                background = surface(color, 4)
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                if (inline) marginEnd = dp(5)
            })
            chip.addView(copyText("$minutes 分钟", if (inline) 15f else 18f, textColor, true))
            addView(chip, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun currentRule() = store.rules.find { it.packageName == targetId }
    private fun target(id: String?) = targets.find { it.packageName == id }

    private fun ruleStatus(rule: Rule, now: Long): String = when {
        target(rule.packageName) == null -> "找不到此 App"
        appState != ServiceState.ON -> "未生效"
        rule.isBlocked(now) -> "禁用至 " + deadlineLabel(checkNotNull(rule.blockedUntil), now)
        else -> "可刷"
    }

    private fun showRule(id: String) {
        targetId = id
        go(Page.DETAIL)
    }

    private fun detail() {
        val rule = currentRule() ?: return
        val now = store.now()
        val missing = target(rule.packageName) == null
        up()
        content.addView(heading(rule.label))
        content.addView(card().apply {
            addView(row().apply {
                addView(heading("当前戒律", 17f), LinearLayout.LayoutParams(0, -2, 1f))
                addView(tag(ruleStatus(rule, now)))
            })
            addView(durations(rule.useMinutes, rule.blockMinutes))
            if (appState != ServiceState.ON && rule.isBlocked(now)) {
                addView(copyText("服务连接后，禁用至 " + deadlineLabel(checkNotNull(rule.blockedUntil), now)))
            }
        })
        val editWait = rule.editWait(now)
        val removeWait = rule.removeWait(now)
        content.addView(button(if (missing) "重新选择心魔" else "修改戒律") {
            if (missing) openPicker(rule.packageName) else openForm(rule.packageName, rule)
        }.apply { isEnabled = editWait == 0L; if (!isEnabled) alpha = .55f })
        if (editWait > 0) content.addView(copyText("再等 ${waitMinutes(editWait)} 分钟可修改。", 14f, NIGHT_MUTED))
        content.addView(button("移除戒律") { confirmRemove(rule.packageName) }.apply {
            isEnabled = removeWait == 0L
            if (isEnabled) setTextColor(NIGHT_ERROR) else alpha = .55f
        })
        if (removeWait > 0) content.addView(copyText("再等 ${waitMinutes(removeWait)} 分钟可移除。", 14f, NIGHT_MUTED))
    }

    private fun confirmRemove(id: String) {
        val rule = store.rules.find { it.packageName == id } ?: return
        if (rule.removeWait(store.now()) == 0L) go(Page.REMOVE)
    }

    private fun removePage() {
        val rule = currentRule() ?: return
        content.addView(heading("移除戒律"))
        content.addView(copyText(if (rule.isBlocked(store.now())) "移除后，此 App 会恢复使用。"
            else "移除后，此 App 将结束本轮戒律。", 14f, NIGHT_MUTED))
        footer("移除", secondaryLabel = "取消", secondary = { go(Page.DETAIL) }) {
            val current = currentRule()
            if (current != null && current.removeWait(store.now()) == 0L &&
                store.configure(store.rules.filterNot { it.packageName == current.packageName })
            ) go(Page.RULES) else toast("移除失败，请重试。")
        }
    }

    private fun discardPage() {
        content.addView(heading("放弃修改？"))
        content.addView(copyText("尚未保存本次修改。", 14f, NIGHT_MUTED))
        footer("放弃修改", secondaryLabel = "继续编辑", secondary = { go(Page.FORM) }) {
            go(if (editing) Page.DETAIL else Page.PICKER)
        }
    }

    private fun openPicker(replacing: String? = null) {
        if (setupMissing().isNotEmpty()) {
            permissionOrigin = page
            go(if (appState == ServiceState.ON) Page.BACKGROUND else Page.PERMISSION)
            return
        }

        replacingId = replacing
        pickerReturn = if (replacing == null) Page.RULES else Page.DETAIL
        targets = eligibleTargets()
        query = ""
        go(Page.PICKER)
    }

    private fun picker() {
        up()
        content.addView(heading(if (replacingId == null) "添加心魔" else "重新选择心魔"))
        content.addView(copyText("选择一个容易让你刷到停不下来的 App。", 14f, NIGHT_MUTED))
        val list = column()
        val search = EditText(this).apply {
            hint = "搜索心魔"
            setSingleLine(true)
            setText(query)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        styleInput(search)
        content.addView(search)
        content.addView(list)
        fun matches() {
            list.removeAllViews()
            val found = targets.filter { it.label.contains(query.trim(), ignoreCase = true) }
            if (found.isEmpty()) {
                list.addView(copyText(if (targets.isEmpty()) "没有可添加的心魔" else "未找到匹配的心魔"))
                if (query.isNotEmpty()) list.addView(button("清除搜索") { search.setText("") })
            }
            val duplicateNames = targets.groupingBy { it.label }.eachCount()
            found.forEachIndexed { index, app ->
                if (index > 0) divider(list)
                val exists = store.rules.any { it.packageName == app.packageName }
                list.addView(column(3).apply {
                    setPadding(0, dp(11), 0, dp(11))
                    isClickable = true
                    isFocusable = true
                    addView(appRow(app.packageName, app.label, if (exists) "已添加" else null))
                    if ((duplicateNames[app.label] ?: 0) > 1) addView(copyText(app.packageName, 13f, NIGHT_MUTED))
                    setOnClickListener {
                        if (exists) {
                            replacingId = null
                            showRule(app.packageName)
                        } else openForm(app.packageName)
                    }
                })
            }
        }
        search.onText { query = it; matches() }
        matches()
    }

    private fun openForm(id: String, existing: Rule? = null) {
        if (existing != null && existing.editWait(store.now()) > 0) return
        targetId = id
        editing = existing != null
        dirty = false
        val initial = existing ?: store.rules.find { it.packageName == replacingId }
        draftUse = (initial?.useMinutes ?: DEFAULT_USE).toString()
        draftBlock = (initial?.blockMinutes ?: DEFAULT_BLOCK).toString()
        go(Page.FORM)
    }

    private fun form() {
        up()
        content.addView(heading(if (editing) "修改戒律" else "设置时长"))
        targetId?.let { content.addView(appRow(it, target(it)?.label ?: currentRule()?.label ?: it)) }
        val use = numberField("可刷时长（分钟）", draftUse, "1–180 分钟")
        val block = numberField("禁用时长（分钟）", draftBlock, "15–180 分钟")
        val preview = copyText("")
        content.addView(card().apply {
            addView(preview)
            if (editing && currentRule()?.isBlocked(store.now()) == true) {
                addView(copyText("保留本次禁用截止时间，新时长从下一轮生效。", 14f, NIGHT_MUTED))
            }
        })
        val error = copyText("", 14f, NIGHT_ERROR).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(error)
        fun showPreview() {
            preview.text = if (draftUse.toIntOrNull() in 1..180 && draftBlock.toIntOrNull() in 15..180)
                "刷 $draftUse 分钟，禁 $draftBlock 分钟。" else "填写时长后查看戒律预览。"
        }
        use.onText { draftUse = it; dirty = true; use.error = null; error.text = ""; error.visibility = View.GONE; showPreview() }
        block.onText { draftBlock = it; dirty = true; block.error = null; error.text = ""; error.visibility = View.GONE; showPreview() }
        showPreview()
        footer("保存戒律") {
            val u = draftUse.toIntOrNull()
            val b = draftBlock.toIntOrNull()
            if (u == null || u !in 1..180) use.error = "输入 1–180 的整数"
            if (b == null || b !in 15..180) block.error = "输入 15–180 的整数"
            if (u != null && u in 1..180 && b != null && b in 15..180) {
                saveRule(u, b, error)
            }
        }
    }

    private fun numberField(label: String, value: String, range: String): EditText {
        val input = EditText(this).apply {
            id = View.generateViewId()
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(value)
        }
        styleInput(input)
        content.addView(column(5).apply {
            addView(copyText(label, 14f, bold = true).apply { labelFor = input.id })
            addView(input)
            addView(copyText(range, 12f, NIGHT_MUTED))
        })
        return input
    }

    private fun styleInput(view: EditText) {
        view.backgroundTintList = null
        view.background = surface(NIGHT_PANEL, 12, NIGHT_LINE)
        view.minHeight = dp(44)
        view.setPadding(dp(11), dp(8), dp(11), dp(8))
        view.textSize = 16f
        view.setTextColor(NIGHT_TEXT)
        view.setHintTextColor(NIGHT_MUTED)
    }

    private fun saveRule(use: Int, block: Int, error: TextView) {
        val id = targetId ?: return
        val selected = eligibleTargets().find { it.packageName == id }
        if (selected == null) {
            error.visibility = View.VISIBLE
            error.text = "找不到此 App，请重新选择。"
            return
        }
        val now = store.now()
        val current = currentRule()
        val source = store.rules.find { it.packageName == replacingId }
        if ((editing && current == null) || (!editing && current != null) ||
            (replacingId != null && (source == null || target(source.packageName) != null))
        ) {
            error.visibility = View.VISIBLE
            error.text = "戒律已变化，请返回后重试。"
            return
        }
        if ((current?.editWait(now) ?: 0L) > 0 || (source?.editWait(now) ?: 0L) > 0) {
            error.visibility = View.VISIBLE
            error.text = "同一条戒律每 5 分钟可修改一次。"
            return
        }
        val next = current?.edit(use, block, now)
            ?: Rule(id, selected.label, use, block, source?.createdAt ?: now,
                editedAt = now.takeIf { source != null }, blockedFrom = source?.blockedFrom)
        val result = store.rules.filterNot { it.packageName == id || it.packageName == source?.packageName } + next
        if (!store.configure(result)) {
            error.visibility = View.VISIBLE
            error.text = "保存失败，请重试。"
            return
        }
        replacingId = null
        dirty = false
        toast(if (appState == ServiceState.ON) "戒律已保存" else "戒律已保存，尚未生效")
        go(Page.RULES)
    }

    private fun settings() {
        content.addView(heading("设置").apply {
            if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                setOnLongClickListener {
                    awaitingFirstConnection = false
                    go(Page.WELCOME)
                    toast("已打开欢迎流程，现有戒律与授权保留")
                    true
                }
            }
        })
        if (setupMissing().isEmpty()) content.addView(copyText("必要设置已完成", 14f, NIGHT_MUTED))
        content.addView(column(8).apply {
            for ((title, status, destination) in listOf(
                Triple("无障碍服务", serviceStatusLabel(), Page.PERMISSION),
                Triple("允许后台运行", backgroundStatus(), Page.BACKGROUND),
            )) {
                divider(this, NIGHT_LINE)
                addView(row().apply {
                    addView(heading(title, 18f), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(tag(status))
                })
                addView(button("去设置", primary = true) {
                    permissionOrigin = Page.SETTINGS
                    go(destination)
                })
            }
            divider(this, NIGHT_LINE)
            addView(heading("戒律保护", 18f).apply {
                setPadding(0, dp(6), 0, 0)
            })
            addView(policyText("修改：每条戒律两次修改至少间隔 5 分钟。", "5 分钟"))
            addView(policyText("移除：创建后要等 1 小时。每次开始禁用时，重新等 1 小时。", "1 小时"))
        })
    }

    private fun backgroundSettings() {
        val profile = backgroundProfile()
        guideHeader("允许后台运行", backgroundStatus(), "减少系统清理导致的计时和禁用中断。")
        content.addView(copyText(profile.instructions))
        content.addView(button("打开系统设置", primary = true) {
            if (!openBackgroundSettings(battery = false)) toast("请手动打开系统设置")
        })
        content.addView(copyText("电池优化 · ${if (batteryOptimizationIgnored()) "已豁免" else "未豁免（不代表后台开关未开启）"}\n后台限制 · ${if (backgroundRestricted()) "受限制" else "未受限制"}", 14f, NIGHT_MUTED))
        if (!batteryOptimizationIgnored()) {
            content.addView(copyText("系统电池优化名单可能不反映厂商后台开关。若已允许后台运行，请在系统中核对后确认。", 14f, NIGHT_MUTED))
            content.addView(button("已在系统中允许后台运行", soft = true) {
                prototypeSettings().edit().putBoolean("background_confirmed", true).apply()
                render(keepScroll = true)
            })
        }
        if (prototypeSettings().getBoolean("background_confirmed", false)) {
            content.addView(copyText("厂商后台设置 · 用户已确认", 14f, NIGHT_MUTED))
        }
        content.addView(button("更多稳定性设置", soft = true) {
            showBackgroundExtras = !showBackgroundExtras
            render(keepScroll = true)
        })
        if (showBackgroundExtras) {
            content.addView(copyText("如果手机提供自启动管理，可允许「斩心魔」自启动；也可在最近任务中锁定卡片。这些选项按系统实际提供的功能设置。", 14f, NIGHT_MUTED))
            content.addView(button("打开电池优化列表", soft = true) {
                if (!openBackgroundSettings(battery = true)) toast("请手动打开系统设置")
            })
            content.addView(button("撤销后台设置确认", soft = true) {
                prototypeSettings().edit().putBoolean("background_confirmed", false).apply()
                render(keepScroll = true)
            })
        }
        content.addView(button(if (store.rules.isEmpty()) "继续添加心魔" else "返回戒律", primary = true) {
            if (setupMissing().isNotEmpty()) {
                if (appState != ServiceState.ON) {
                    permissionOrigin = Page.BACKGROUND
                    go(Page.PERMISSION)
                    return@button
                }
                toast("请先完成后台运行设置")
                render(keepScroll = true)
                return@button
            }
            store.welcome()
            onboarding = false
            awaitingFirstConnection = false
            if (store.rules.isEmpty()) openPicker() else go(Page.RULES)
        }.apply {
            isEnabled = setupMissing().isEmpty()
            alpha = if (isEnabled) 1f else .45f
        })
    }

    private fun policyText(text: String, emphasis: String): TextView {
        val start = text.indexOf(emphasis)
        val marked = SpannableString(text).apply {
            setSpan(ForegroundColorSpan(NIGHT_GOLD), start, start + emphasis.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return copyText(marked, 15f, NIGHT_MUTED)
    }

    private fun help() = permission()

    private fun bottomNavigation() {
        divider(root, NIGHT_LINE)
        val nav = row().apply { setPadding(dp(10), dp(5), dp(10), dp(5)) }
        for ((label, destination, icon) in listOf(
            Triple("戒律", Page.RULES, R.drawable.nav_rules), Triple("设置", Page.SETTINGS, R.drawable.nav_settings),
        )) {
            val selected = page == destination
            val tab = column(2).apply {
                gravity = Gravity.CENTER
                isSelected = selected
                isFocusable = true
                minimumHeight = dp(44)
                contentDescription = label
                val color = if (selected) NIGHT_GOLD else NIGHT_MUTED
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(color)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(19), dp(19)))
                addView(copyText(label, 12f, color).apply {
                    gravity = Gravity.CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                setOnClickListener { go(destination) }
            }
            nav.addView(tab, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        root.addView(nav)
    }

    private fun requestPermission() {
        permissionOrigin = page
        go(Page.PERMISSION)
    }

    private fun openSystemSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .onFailure { toast("请在系统设置中打开无障碍服务。") }
    }

    private fun back() {
        when (page) {
            Page.WELCOME, Page.RULES -> finish()
            Page.PERMISSION -> go(permissionOrigin)
            Page.SETTINGS, Page.DETAIL -> go(Page.RULES)
            Page.REMOVE -> go(Page.DETAIL)
            Page.DISCARD -> go(Page.FORM)
            Page.HELP -> go(helpOrigin)
            Page.BACKGROUND -> if (onboarding) go(Page.PERMISSION) else go(Page.SETTINGS)
            Page.PICKER -> {
                if (replacingId != null) targetId = replacingId
                replacingId = null
                go(pickerReturn)
            }
            Page.FORM -> {
                val destination = if (editing) Page.DETAIL else Page.PICKER
                if (!dirty) go(destination)
                else go(Page.DISCARD)
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun EditText.onText(action: (String) -> Unit) = addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action(s.toString())
        override fun afterTextChanged(s: Editable?) = Unit
    })
}
