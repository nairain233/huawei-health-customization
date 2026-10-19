@file:android.annotation.SuppressLint("DiscouragedApi") // 已核验宿主资源按名称读取。

package love.nairain.huawei.settings

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import androidx.core.net.toUri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import love.nairain.huawei.BuildConfig
import love.nairain.huawei.R
import love.nairain.huawei.app.ServiceItem
import love.nairain.huawei.app.openSourceLibraries
import love.nairain.huawei.app.visibleServices
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServicePreset
import love.nairain.huawei.config.ServiceSection
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.hook.resolver.HostSettingsTargets
import love.nairain.huawei.scan.ScanProtocol
import love.nairain.huawei.scan.ScanReport
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** 保存的只有页面路由及滚动值，配置值始终重新读取确认快照。 */
internal class HostSettingsPageState {
    var open = false
    var route = "home"
    val scroll = mutableMapOf<String, Int>()
    fun save(): Bundle = Bundle().apply {
        putBoolean("open", open); putString("route", route)
        putBundle("scroll", Bundle().also { bundle -> scroll.forEach { (key, value) -> bundle.putInt(key, value) } })
    }
    fun restore(bundle: Bundle?) {
        open = bundle?.getBoolean("open") == true
        route = bundle?.getString("route")?.takeIf { it in routes } ?: "home"
        bundle?.getBundle("scroll")?.let { values -> values.keySet().filter { it in routes }.forEach { scroll[it] = values.getInt(it) } }
    }
    companion object { val routes = setOf("home", "layout", "experimental", "services", "custom", "scan", "about", "licenses") +
        SettingsCategory.entries.map { it.name } }
}

/** 依附打开入口所在 Activity；首页逐步迁入 Miuix，二级页面及标题栏继续复用宿主控件。 */
@Suppress("DEPRECATION")
internal class HostSettingsDialog(private val activity: Activity, private val targets: HostSettingsTargets,
    moduleResources: Resources, private val client: HostSettingsController, private val state: HostSettingsPageState,
    private val restarter: HostRestarter,
    private val composeFailed: (Throwable) -> Unit = {},
    private val failed: (Throwable) -> Unit) : Dialog(activity), HostSettingsWindow {
    override val visible: Boolean get() = isShowing
    private val strings = love.nairain.huawei.watchface.ModuleResources.localized(moduleResources, activity.resources)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "HostSettingsReport").apply { isDaemon = true } }
    private val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(activity).apply { isFillViewport = true; addView(content) }
    private var snapshot = HostSettingsState()
    private var notice = HostSettingsNotice.LOADING
    private var report: ScanReport? = null
    private var expired = false
    private var subscription: AutoCloseable? = null
    private var restartSubscription: AutoCloseable? = null
    private var active = false
    private var disposed = false
    private var backCallback: Any? = null
    private var confirmation: AlertDialog? = null
    private var query = ""
    private var selectedOnly = false
    private val bindings = mutableListOf<() -> Unit>()
    private var serviceSignature = ""
    private var composeHome: HostSettingsComposeHost? = null
    private var composeUnavailable = false
    private val refreshReport = object : Runnable {
        override fun run() {
            if (!active) return
            if (state.route == "scan") worker.execute {
                val next = runCatching {
                    val file = AtomicFile(File(HostStorage.scanDirectory(activity), "report.json"))
                    file.openRead().bufferedReader().use { reader ->
                        val chars = CharArray(32769)
                        var size = 0
                        while (size < chars.size) { val read = reader.read(chars, size, chars.size - size); if (read < 0) break; size += read }
                        require(size <= 32768)
                        ScanReport.decode(String(chars, 0, size))
                    }
                }.getOrNull()
                val invalid = runCatching {
                    val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
                    next != null && (next.versionCode != info.longVersionCode || next.updated != info.lastUpdateTime || next.rules != ScanProtocol.RULES)
                }.getOrDefault(true)
                main.post { if (active && !disposed) { report = next; expired = invalid; update() } }
            }
            main.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.requestFeature(android.view.Window.FEATURE_NO_TITLE)
        root.background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(if (isDark()) Color.rgb(18, 18, 18) else Color.rgb(247, 247, 247))
        }
        root.clipToOutline = true
        setContentView(root)
        setCanceledOnTouchOutside(false)
        setOnDismissListener { state.open = false; dispose() }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val callback = android.window.OnBackInvokedCallback { navigate(parent(state.route)) }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        }
        render()
    }
    override fun show() {
        if (disposed) return
        state.open = true
        super.show()
        // onCreate 的安全回调可能已处置窗口；不能在 show 返回后留下失效窗口。
        if (disposed) { dismiss(); return }
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.35f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            val visible = Rect()
            activity.window.decorView.getWindowVisibleDisplayFrame(visible)
            val metrics = activity.resources.displayMetrics
            val width = if (visible.width() > 0) visible.width() else metrics.widthPixels
            val height = if (visible.height() > 0) visible.height() else metrics.heightPixels
            // 窄屏保留侧边距，宽屏限制宽度；长列表只滚动中间内容，底部操作始终可见。
            setLayout(minOf((width - dp(32)).coerceAtLeast(1), dp(600)),
                minOf((height * 0.82f).toInt().coerceAtLeast(1), dp(720)))
        }
        resume()
    }
    override fun resume() {
        if (active || disposed) return
        active = true
        composeHome?.resume()
        subscription = client.start { value -> safely { snapshot = value; notice = value.notice; update() } }
        restartSubscription = restarter.observe { if (active && !disposed) update() }
        main.post(refreshReport)
    }
    override fun pause() {
        if (!active) return
        active = false
        confirmation?.dismiss(); confirmation = null
        rememberScroll()
        composeHome?.pause()
        subscription?.close(); subscription = null
        restartSubscription?.close(); restartSubscription = null
        main.removeCallbacks(refreshReport)
    }
    override fun captureState() = rememberScroll()
    override fun dispose() {
        if (disposed) return
        pause()
        disposed = true
        closeComposeHome()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            (backCallback as? android.window.OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        }
        backCallback = null
        worker.shutdownNow()
        bindings.clear()
        if (isShowing) {
            dismiss()
        }
    }
    @android.annotation.SuppressLint("GestureBackNavigation") // API 33+ 已注册 OnBackInvokedCallback；此处仅兼容旧系统。
    @Deprecated("API 32 及以下返回键兼容")
    override fun onBackPressed() = navigate(parent(state.route))

    private fun parent(route: String): String = when (route) {
        "home" -> "close"
        "custom" -> "services"
        "services" -> "experimental"
        "licenses" -> "about"
        in SettingsCategory.entries.map { it.name } -> "layout"
        else -> "home"
    }
    private fun rememberScroll() { state.scroll[state.route] = composeHome?.scrollY ?: scroll.scrollY }
    private fun navigate(route: String) = safely {
        if (route == "close") { dismiss(); return@safely }
        rememberScroll()
        state.route = route
        render()
        if (route == "scan") { main.removeCallbacks(refreshReport); main.post(refreshReport) }
    }
    private fun title(): String = s(when (state.route) {
        "layout" -> R.string.settings_layout_trim_title
        "experimental" -> R.string.experimental_features_title
        "services" -> R.string.service_block_title
        "custom" -> R.string.service_custom_title
        "scan" -> R.string.scan_title
        "about" -> R.string.about_title
        "licenses" -> R.string.about_open_source_section
        else -> SettingsCategory.entries.firstOrNull { it.name == state.route }?.title ?: R.string.settings_title
    })
    private fun render() {
        bindings.clear()
        closeComposeHome()
        if (state.route == "services" || state.route == "custom") serviceSignature =
            snapshot.configuration.services.toString() + snapshot.catalog.services.toString()
        root.removeAllViews()
        content.removeAllViews()
        val titleBar = targets.title(activity, title()) { navigate(parent(state.route)) }
        root.addView(titleBar)
        if (state.route == "home" && !composeUnavailable && renderComposeHome()) {
            renderWindowActions()
            update()
            return
        }
        (scroll.parent as? ViewGroup)?.removeView(scroll)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when (state.route) {
            "home" -> {
                val retry = action(s(R.string.host_settings_retry)) { client.refresh() }
                bindings += { retry.visibility = if (notice in setOf(HostSettingsNotice.UNAVAILABLE,
                    HostSettingsNotice.UNCERTAIN, HostSettingsNotice.FAILED)) View.VISIBLE else View.GONE }
                val restartBusy = note(s(R.string.host_restart_watchface_busy))
                bindings += { restartBusy.visibility = if (restarter.waitingForWatchFace) View.VISIBLE else View.GONE }
                layoutSwitch(SettingsKeys.ENABLED, s(R.string.settings_enabled))
                navigation(R.string.settings_layout_trim_title, "layout")
                navigation(R.string.experimental_features_title, "experimental")
                navigation(R.string.scan_title, "scan")
                navigation(R.string.about_title, "about")
            }
            "layout" -> SettingsCategory.entries.forEach { navigation(it.title, it.name) }
            "experimental" -> {
                note(s(R.string.host_watchface_location))
                layoutSwitch(SettingsKeys.LOCAL_WATCH_FACE, s(R.string.watchface_import_title))
                navigation(R.string.service_block_title, "services")
            }
            "services" -> renderServices()
            "custom" -> renderCustom()
            "scan" -> {
                val description = note("")
                bindings += { description.text = scanDescription() }
                val rescan = action(s(R.string.scan_rescan)) { client.scheduleRescan() }
                bindings += { rescan.isEnabled = client.writable && !scanPending() &&
                    (report?.phase != "running" || scanUncertain() || expired) }
            }
            "about" -> {
                note(s(R.string.app_name))
                note(strings.getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                note(s(R.string.about_section_developer))
                note(s(R.string.about_developer_name))
                action(s(R.string.about_repository)) { openLink("https://github.com/nairain233/huawei-health-customization") }
                action(s(R.string.about_platform_github)) { openLink("https://github.com/nairain233") }
                navigation(R.string.about_open_source_section, "licenses")
            }
            "licenses" -> openSourceLibraries.forEach { library ->
                action(library.name + "\n" + strings.getString(R.string.about_lib_summary, library.license, library.author)) { openLink(library.url) }
            }
            else -> SettingsCategory.entries.firstOrNull { it.name == state.route }?.let { category ->
                if (category == SettingsCategory.BOTTOM) {
                    val explanation = note("")
                    bindings += { explanation.text = if (needsHealthRoute()) s(R.string.host_settings_health_route) else "" }
                }
                SettingsCatalog.groupsFor(category).forEach { group ->
                    group.title?.let { note(s(it)) }
                    group.settings.forEach { layoutSwitch(it.key, s(it.title)) }
                }
            }
        }
        renderWindowActions()
        update()
        scroll.post { if (!disposed) scroll.scrollTo(0, state.scroll[state.route] ?: 0) }
    }
    private fun update() {
        if (disposed) return
        composeHome?.update(homeModel())
        if (state.route == "services" || state.route == "custom") {
            val signature = snapshot.configuration.services.toString() + snapshot.catalog.services.toString()
            if (serviceSignature != signature) {
                serviceSignature = signature
                rememberScroll()
                render()
                return
            }
        }
        bindings.toList().forEach { it() }
    }
    private fun homeModel() = HostSettingsHomeModel(
        enabled = snapshot.configuration.layout.getValue(SettingsKeys.ENABLED),
        writable = active && client.writable,
        notice = notice,
        watchFaceBusy = restarter.waitingForWatchFace,
    )
    private fun renderComposeHome(): Boolean = try {
        val home = HostSettingsComposeHost(activity, strings, state.scroll["home"] ?: 0, homeModel(),
            onEnabledChange = { value -> safely { if (active && !disposed && client.writable) client.saveLayout(SettingsKeys.ENABLED, value) } },
            onNavigate = { route -> if (active && !disposed) navigate(route) },
            onRetry = { safely { if (active && !disposed) client.refresh() } },
            onFailure = { error -> fallbackHome(error) })
        composeHome = home
        root.addView(home.view, LinearLayout.LayoutParams(-1, 0, 1f))
        if (active) home.resume()
        true
    } catch (error: Throwable) {
        composeUnavailable = true
        composeFailed(error)
        composeHome?.view?.let { root.removeView(it) }
        closeComposeHome()
        false
    }
    private fun fallbackHome(error: Throwable) = safely {
        if (disposed || composeUnavailable) return@safely
        composeUnavailable = true
        composeFailed(error)
        rememberScroll()
        render()
    }
    private fun closeComposeHome() {
        val home = composeHome ?: return
        composeHome = null
        runCatching { home.close() }.onFailure(composeFailed)
    }
    private fun renderWindowActions() {
        val actions = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        val cancel = Button(activity).apply {
            text = s(android.R.string.cancel)
            isAllCaps = false
            minHeight = dp(48)
            setOnClickListener { dismiss() }
        }
        actions.addView(cancel, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) })
        val button = Button(activity).apply {
            text = s(R.string.host_restart)
            contentDescription = s(R.string.host_restart_description)
            isAllCaps = false
            minHeight = dp(48)
            setOnClickListener { restartHost() }
        }
        actions.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions, LinearLayout.LayoutParams(-1, -2))
        bindings += { button.isEnabled = active && restarter.available }
    }
    private fun restartHost() = safely {
        if (!active || disposed) return@safely
        val result = restarter.restart {
            Toast.makeText(activity, s(R.string.host_restart_starting), Toast.LENGTH_LONG).show()
        }
        when (result) {
            HostRestarter.Result.WATCH_FACE_BUSY -> Toast.makeText(activity,
                s(R.string.host_restart_watchface_busy), Toast.LENGTH_LONG).show()
            HostRestarter.Result.FAILED -> Toast.makeText(activity,
                s(R.string.host_restart_failed), Toast.LENGTH_LONG).show()
            else -> Unit // 配置错误沿用当前页面已确认的状态提示。
        }
        update()
    }
    private fun layoutSwitch(key: String, title: String) {
        switch(title, { snapshot.configuration.layout.getValue(key) },
            { client.writable && (key != SettingsKeys.BOTTOM_HEALTH || !needsHealthRoute()) }) { client.saveLayout(key, it) }
    }
    private fun needsHealthRoute() = snapshot.configuration.layout[SettingsKeys.BOTTOM_MINE] == true ||
        snapshot.configuration.layout[SettingsKeys.MINE_SETTINGS] == true
    private fun serviceConfig() = snapshot.configuration.services
    private fun services(): List<ServiceItem> = snapshot.catalog.services
    private fun saveService(config: ServiceBlockConfig) = client.saveServices(config)
    private fun renderServices() {
        note(s(R.string.service_block_notice))
        action(s(R.string.service_block_refresh)) { client.refresh() }
        switch(s(R.string.service_block_enabled), { serviceConfig().enabled }, { client.writable }) { saveService(serviceConfig().copy(enabled = it)) }
        val declared = services().map { it.component }.toSet()
        ServiceSection.entries.forEach { section ->
            note(s(if (section == ServiceSection.CORE) R.string.service_block_core_title else R.string.service_block_low_title))
            ServicePreset.visible(section).forEach { group ->
                val available = group.components.any(declared::contains)
                switch(s(group.title) + "\n" + s(group.impact) + if (!available) "\n" + s(R.string.service_group_missing) else "",
                    { group.id in serviceConfig().presets }, { client.writable && (available || group.id in serviceConfig().presets) }) { checked ->
                    val commit = { saveService(serviceConfig().copy(presets = ServicePreset.changeSelection(serviceConfig().presets, group, checked))) }
                    if (checked && section == ServiceSection.CORE) confirmation = AlertDialog.Builder(activity)
                        .setTitle(s(R.string.service_confirm_block)).setMessage(s(group.impact))
                        .setPositiveButton(android.R.string.ok) { _, _ -> if (client.writable) commit() }
                        .setNegativeButton(s(R.string.service_cancel), null).show()
                    else commit()
                }
                if (ServicePreset.hasLegacySelection(serviceConfig().presets, group)) {
                    note(s(R.string.service_legacy_partial))
                    val clear = action(s(R.string.service_legacy_clear)) {
                        saveService(serviceConfig().copy(presets = ServicePreset.changeSelection(serviceConfig().presets, group, false)))
                    }
                    bindings += { clear.isEnabled = client.writable }
                }
            }
        }
        switch(s(R.string.service_block_debug_mode) + "\n" + s(R.string.service_block_debug_mode_summary),
            { serviceConfig().debugMode }, { client.writable }) { saveService(serviceConfig().copy(debugMode = it)) }
        val custom = navigation(R.string.service_custom_title, "custom")
        bindings += { custom.isEnabled = serviceConfig().debugMode }
        if (!serviceConfig().debugMode && serviceConfig().components.isNotEmpty()) note(s(R.string.service_block_custom_paused))
        note(strings.getString(R.string.service_effective_count, serviceConfig().effectiveComponents(declared).size))
    }
    private fun renderCustom() {
        val search = EditText(activity).apply { setSingleLine(); setText(query); hint = s(R.string.service_block_search) }
        content.addView(search, LinearLayout.LayoutParams(-1, -2))
        action(s(R.string.host_settings_apply_filter)) { query = search.text.toString(); rememberScroll(); render() }
        switch(s(R.string.service_block_only_selected), { selectedOnly }, { true }) { selectedOnly = it; render() }
        val clear = action(s(R.string.service_custom_clear)) { saveService(serviceConfig().copy(components = emptySet())) }
        bindings += { clear.isEnabled = client.writable && serviceConfig().debugMode && serviceConfig().components.isNotEmpty() }
        val config = serviceConfig()
        val selected = config.components + ServicePreset.selectedComponents(config.presets)
        val rows = visibleServices(services(), selected, query, selectedOnly)
        if (rows.isEmpty()) note(s(R.string.service_block_empty))
        rows.forEach { item ->
            val groupSelected = item.component in ServicePreset.selectedComponents(config.presets)
            val details = if (item.missing) s(R.string.service_block_missing) else strings.getString(R.string.service_block_details,
                item.process, s(if (item.exported) R.string.service_block_yes else R.string.service_block_no),
                s(if (item.enabled) R.string.service_block_yes else R.string.service_block_no))
            switch(item.className + "\n" + details + if (groupSelected) "\n" + s(R.string.service_group_selected) else "",
                { item.component in serviceConfig().components },
                { client.writable && serviceConfig().debugMode && (!item.missing || item.component in serviceConfig().components) }) { checked ->
                val components = serviceConfig().components
                saveService(serviceConfig().copy(components = if (checked) components + item.component else components - item.component))
            }
        }
    }
    private fun switch(label: String, checked: () -> Boolean, enabled: () -> Boolean, changed: (Boolean) -> Unit) {
        val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        val title = targets.text(activity).apply { text = label; textSize = 16f }
        val toggle = targets.toggle(activity).apply { contentDescription = label }
        row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(toggle, LinearLayout.LayoutParams(-2, -2))
        content.addView(row)
        divider()
        val listener = CompoundButton.OnCheckedChangeListener { _, value ->
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = checked()
            safely { if (enabled()) changed(value) }
            toggle.setOnCheckedChangeListener(null)
            update()
        }
        bindings += {
            toggle.setOnCheckedChangeListener(null); toggle.isChecked = checked(); toggle.isEnabled = enabled()
            toggle.setOnCheckedChangeListener(listener); title.isEnabled = toggle.isEnabled
        }
        row.setOnClickListener { if (toggle.isEnabled) toggle.performClick() }
    }
    private fun navigation(label: Int, route: String) = action(s(label) + "  ›") { navigate(route) }
    private fun action(label: String, clicked: () -> Unit): TextView = targets.text(activity).apply {
        text = label; textSize = 16f; gravity = Gravity.CENTER_VERTICAL; minHeight = dp(56)
        setPadding(dp(16), dp(12), dp(16), dp(12)); isFocusable = true
        val value = android.util.TypedValue()
        if (activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) setBackgroundResource(value.resourceId)
        setOnClickListener { safely(clicked) }
        content.addView(this, LinearLayout.LayoutParams(-1, -2))
        divider()
    }
    private fun note(label: String): TextView = targets.text(activity).apply {
        text = label; textSize = 14f; setPadding(dp(16), dp(12), dp(16), dp(8)); setTextIsSelectable(true)
        content.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun divider() { content.addView(targets.divider(activity), LinearLayout.LayoutParams(-1, dp(1))) }
    private fun scanUncertain() = report?.phase == "running" && System.currentTimeMillis() - (report?.time ?: 0) > ScanProtocol.STALE_MS
    private fun scanPending(): Boolean = snapshot.configuration.scanRequest.let {
        if (it.isEmpty()) notice == HostSettingsNotice.SCHEDULED else report?.request != it || report?.phase != "complete" }
    private fun scanDescription(): String = buildList {
        val current = report
        add(s(when {
            expired -> R.string.scan_expired
            scanUncertain() -> R.string.scan_uncertain
            current == null -> R.string.scan_not_started
            current.phase == "running" -> R.string.scan_running
            current.phase == "failed" -> R.string.scan_failed
            else -> R.string.scan_complete
        }))
        if (current != null) {
            add(strings.getString(if (current.phase == "complete" && current.matched.size < ScanProtocol.keys.size)
                R.string.scan_complete_partial else R.string.host_scan_matched, current.matched.size, ScanProtocol.keys.size))
            add(strings.getString(R.string.scan_checked, current.checked.size, ScanProtocol.keys.size))
            add(strings.getString(R.string.host_scan_version, current.version))
            add(strings.getString(R.string.host_scan_time, DateFormat.getDateTimeInstance().format(Date(current.time))))
            add(strings.getString(R.string.host_scan_service, s(when (current.service) {
                "installed" -> R.string.host_scan_service_installed
                "unsupported" -> R.string.host_scan_service_unsupported
                else -> R.string.host_scan_service_disabled
            })))
        }
        if (scanPending()) add(s(R.string.scan_scheduled))
        add(s(R.string.host_scan_limit))
    }.joinToString("\n")
    private fun openLink(url: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onFailure { Toast.makeText(activity, s(R.string.about_link_failed), Toast.LENGTH_SHORT).show() }
    }
    private fun s(id: Int) = strings.getString(id)
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun isDark() = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    private fun safely(action: () -> Unit) {
        try { action() } catch (error: Throwable) { dispose(); failed(error) }
    }
}
