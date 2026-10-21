package love.nairain.huawei.settings

import android.content.res.Resources
import android.os.Build
import android.view.View
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.Window
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import love.nairain.huawei.BuildConfig
import love.nairain.huawei.R
import love.nairain.huawei.app.openSourceLibraries
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.scan.ScanProtocol
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.DateFormat
import java.util.Date

/** 全屏页面只有一个主题和 Scaffold，平台根容器统一消费系统栏及输入法 Insets。 */
@Composable
internal fun HostSettingsScreen(model: HostSettingsUiState, resources: Resources,
    scroll: ScrollState, customList: LazyListState, action: (HostSettingsAction) -> Unit) {
    val configuration = LocalConfiguration.current
    @Suppress("DEPRECATION")
    val strings = remember(resources, configuration) { Resources(resources.assets, resources.displayMetrics, configuration) }
    val theme = remember { ThemeController(ColorSchemeMode.System) }
    val initialFilterRevision = remember { model.filterRevision }
    LaunchedEffect(model.filterRevision) { if (model.filterRevision != initialFilterRevision) customList.scrollToItem(0) }
    MiuixTheme(controller = theme) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                SmallTopAppBar(title = if (model.route.startsWith("sport.")) sportRouteTitle(model.route, model, strings)
                    else strings.getString(routeTitle(model.route)), defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(onClick = { action(HostSettingsAction.Back) },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = strings.getString(R.string.host_ui_back) }) {
                            // 应用自有返回图形，避免为一个图标增加扩展图标依赖。
                            val color = MiuixTheme.colorScheme.onSurface
                            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                            Canvas(Modifier.size(24.dp)) {
                                fun point(x: Float, y: Float) = Offset(size.width * (if (rtl) 1f - x else x), size.height * y)
                                drawLine(color, point(.8f, .5f), point(.2f, .5f), 2.dp.toPx(), StrokeCap.Round)
                                drawLine(color, point(.2f, .5f), point(.5f, .2f), 2.dp.toPx(), StrokeCap.Round)
                                drawLine(color, point(.2f, .5f), point(.5f, .8f), 2.dp.toPx(), StrokeCap.Round)
                            }
                        }
                    })
            },
            bottomBar = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(text = strings.getString(android.R.string.cancel), modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        onClick = { action(HostSettingsAction.Close) })
                    TextButton(text = strings.getString(R.string.host_restart), modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        enabled = model.restartAvailable && model.pendingPreset == null,
                        colors = ButtonDefaults.textButtonColorsPrimary(), onClick = { action(HostSettingsAction.Restart) })
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                SettingsStatus(model, strings, action)
                key(model.route) {
                    if (model.route == "custom") HostSettingsCustomServices(model, strings, customList, action, Modifier.weight(1f))
                    else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (model.route) {
                            "home" -> HostSettingsHome(model, strings, action)
                            "layout" -> SettingsCard { SettingsCategory.entries.forEach { category ->
                                ArrowPreference(title = strings.getString(category.title), onClick = { action(HostSettingsAction.Navigate(category.name)) })
                            } }
                            "experimental" -> {
                                SettingsNotice(strings.getString(R.string.host_watchface_location), strings)
                                SettingsCard { LayoutSwitch(SettingsKeys.LOCAL_WATCH_FACE, strings.getString(R.string.watchface_import_title), model, action) }
                                SettingsCard { ArrowPreference(title = strings.getString(R.string.service_block_title), onClick = { action(HostSettingsAction.Navigate("services")) }) }
                            }
                            "services" -> HostSettingsServices(model, strings, action)
                            "SPORT" -> HostSettingsSportHome(model, strings, action)
                            "sport.top" -> HostSettingsSportTop(model, strings, action)
                            "scan" -> ScanPage(model, strings, action)
                            "about" -> AboutPage(strings, action)
                            "licenses" -> SettingsCard { openSourceLibraries.forEach { library ->
                                ArrowPreference(title = library.name, summary = strings.getString(R.string.about_lib_summary, library.license, library.author),
                                    onClick = { action(HostSettingsAction.OpenLink(library.url)) })
                            } }
                            else -> if (model.route.startsWith("sport.page.")) {
                                model.route.substringAfterLast('.').toIntOrNull()?.let {
                                    HostSettingsSportContent(it, model, strings, action)
                                }
                            } else SettingsCategory.entries.firstOrNull { it.name == model.route }?.let { category ->
                                if (category == SettingsCategory.BOTTOM && model.needsHealthRoute) SettingsNotice(strings.getString(R.string.host_settings_health_route), strings)
                                SettingsCatalog.groupsFor(category).forEach { group ->
                                    SettingsGroup(group.title?.let(strings::getString)) { group.settings.forEach { setting ->
                                        LayoutSwitch(setting.key, strings.getString(setting.title), model, action)
                                    } }
                                }
                            }
                        }
                    }
                }
            }
        }
        model.pendingPreset?.let { preset ->
            WindowDialog(show = true, title = strings.getString(R.string.service_confirm_block), summary = strings.getString(preset.impact),
                largeScreen = true, onDismissRequest = { action(HostSettingsAction.CancelPreset) }) {
                ConfirmationWindowBackInput { action(HostSettingsAction.CancelPreset) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(text = strings.getString(R.string.service_cancel), modifier = Modifier.weight(1f), onClick = { action(HostSettingsAction.CancelPreset) })
                    TextButton(text = strings.getString(android.R.string.ok), modifier = Modifier.weight(1f),
                        enabled = model.presetEditable(preset), colors = ButtonDefaults.textButtonColorsPrimary(), onClick = { action(HostSettingsAction.ConfirmPreset) })
                }
            }
        }
    }
}

/** 只把独立确认窗口的返回事件交给 Miuix；主窗口仍沿用平台 Dialog 的逐级返回。 */
@Composable
private fun ConfirmationWindowBackInput(onBack: () -> Unit) {
    val view = LocalView.current
    val currentOnBack = rememberUpdatedState(onBack)
    val window = remember(view) {
        generateSequence(view as View?) { it.parent as? View }.filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    }
    DisposableEffect(window) {
        val previous = window?.callback
        // 普通宿主不一定启用预测返回；同时覆盖独立 Dialog 的旧按键路径。
        val callback = previous?.let { original -> object : Window.Callback by original {
            // Kotlin 委托不会自动转发 Java 默认方法，显式交还原窗口处理。
            override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
                original.onProvideKeyboardShortcuts(data, menu, deviceId)
            }
            override fun onPointerCaptureChanged(hasCapture: Boolean) {
                original.onPointerCaptureChanged(hasCapture)
            }
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode != KeyEvent.KEYCODE_BACK) return original.dispatchKeyEvent(event)
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) currentOnBack.value()
                return true
            }
        } }
        if (callback != null) window.callback = callback
        val invoked = if (Build.VERSION.SDK_INT >= 33 && window != null)
            android.window.OnBackInvokedCallback { currentOnBack.value() }.also {
                window.onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY, it)
            } else null
        onDispose {
            if (window?.callback === callback) window?.callback = previous
            if (Build.VERSION.SDK_INT >= 33 && invoked != null) window?.onBackInvokedDispatcher?.unregisterOnBackInvokedCallback(invoked)
        }
    }
}

internal fun routeTitle(route: String): Int = when (route) {
    "layout" -> R.string.settings_layout_trim_title
    "experimental" -> R.string.experimental_features_title
    "services" -> R.string.service_block_title
    "custom" -> R.string.service_custom_title
    "scan" -> R.string.scan_title
    "about" -> R.string.about_title
    "licenses" -> R.string.about_open_source_section
    else -> SettingsCategory.entries.firstOrNull { it.name == route }?.title ?: R.string.settings_title
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), content = content)
}

/** 标题和卡片作为一个分组，避免再叠加外层列表的项目间距。 */
@Composable
internal fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        title?.let { SmallTitle(text = it) }
        SettingsCard(content)
    }
}

/** 说明与可关闭提示共用蓝框样式，边框在卡片内部绘制，避免被背景覆盖。 */
@Composable
private fun SettingsInfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MiuixTheme.colorScheme
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(
        color = colors.primary.copy(alpha = .16f).compositeOver(colors.surface), contentColor = colors.onTertiaryContainer)) {
        Column(Modifier.fillMaxWidth().squircleBorder(width = 1.dp, color = colors.primary,
            cornerRadius = CardDefaults.CornerRadius), content = content)
    }
}

/** 关闭仅影响当前提示的显示；文案变化时重新显示，不写入功能配置。 */
@Composable
internal fun SettingsNotice(text: String, strings: Resources, modifier: Modifier = Modifier) {
    var dismissed by remember(text) { mutableStateOf(false) }
    if (dismissed) return
    val colors = MiuixTheme.colorScheme
    SettingsInfoCard(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(text = text, modifier = Modifier.padding(vertical = 8.dp),
                    style = MiuixTheme.textStyles.body2, color = colors.onTertiaryContainer)
            }
            IconButton(onClick = { dismissed = true },
                modifier = Modifier.size(48.dp).semantics { contentDescription = strings.getString(R.string.host_ui_dismiss_notice) }) {
                Canvas(Modifier.size(14.dp)) {
                    drawLine(colors.onTertiaryContainer, Offset.Zero, Offset(size.width, size.height), 1.5.dp.toPx(), StrokeCap.Round)
                    drawLine(colors.onTertiaryContainer, Offset(size.width, 0f), Offset(0f, size.height), 1.5.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
internal fun SettingsNote(text: String) {
    SettingsInfoCard {
        SelectionContainer {
            Text(text = text, modifier = Modifier.padding(16.dp),
                style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onTertiaryContainer)
        }
    }
}

@Composable
internal fun LayoutSwitch(key: String, title: String, model: HostSettingsUiState,
    action: (HostSettingsAction) -> Unit) {
    SwitchPreference(title = title, checked = model.configuration.layout.getValue(key),
        enabled = model.layoutEditable(key), onCheckedChange = { action(HostSettingsAction.Layout(key, it)) })
}

@Composable
private fun SettingsStatus(model: HostSettingsUiState, strings: Resources, action: (HostSettingsAction) -> Unit) {
    val notice = when (model.settings.notice) {
        HostSettingsNotice.LOADING -> R.string.host_ui_loading
        HostSettingsNotice.SAVING -> R.string.host_ui_saving
        HostSettingsNotice.SAVED -> R.string.host_ui_saved
        HostSettingsNotice.SCHEDULED -> R.string.host_ui_scheduled
        HostSettingsNotice.FAILED -> R.string.host_ui_save_failed
        HostSettingsNotice.UNAVAILABLE -> R.string.host_ui_unavailable
        HostSettingsNotice.UNCERTAIN -> R.string.host_ui_uncertain
        HostSettingsNotice.READY -> null
    }
    notice?.let { SettingsNotice(strings.getString(it), strings,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
    if (model.settings.notice in setOf(HostSettingsNotice.FAILED, HostSettingsNotice.UNAVAILABLE, HostSettingsNotice.UNCERTAIN)) {
        TextButton(text = strings.getString(R.string.host_settings_retry), modifier = Modifier.padding(horizontal = 16.dp), onClick = { action(HostSettingsAction.Refresh) })
    }
    if (model.watchFaceBusy) SettingsNotice(strings.getString(R.string.host_restart_watchface_busy), strings,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
}

@Composable
private fun AboutPage(strings: Resources, action: (HostSettingsAction) -> Unit) {
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = strings.getString(R.string.app_name), style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurfaceContainer, textAlign = TextAlign.Center)
            Text(text = strings.getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center)
        }
    }
    SettingsGroup(strings.getString(R.string.about_section_links)) {
        AboutLink(strings.getString(R.string.about_developer), strings.getString(R.string.about_platform_github),
            "https://github.com/nairain233", action)
        AboutLink(strings.getString(R.string.about_repository), strings.getString(R.string.about_platform_github),
            "https://github.com/nairain233/huawei-health-customization", action)
        AboutLink(strings.getString(R.string.about_sponsor), strings.getString(R.string.about_sponsor),
            "https://blog.nairain.love/sponsor/", action)
        ArrowPreference(title = strings.getString(R.string.about_open_source_section), onClick = { action(HostSettingsAction.Navigate("licenses")) })
    }
}

@Composable
private fun AboutLink(title: String, platform: String, url: String, action: (HostSettingsAction) -> Unit) {
    ArrowPreference(title = title, endActions = {
        Text(text = platform, style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }, onClick = { action(HostSettingsAction.OpenLink(url)) })
}

@Composable
private fun ScanPage(model: HostSettingsUiState, strings: Resources, action: (HostSettingsAction) -> Unit) {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = strings.getString(when {
                model.expired -> R.string.scan_expired
                model.scanTimedOut -> R.string.scan_uncertain
                model.report == null -> R.string.scan_not_started
                model.report.phase == "running" -> R.string.scan_running
                model.report.phase == "failed" -> R.string.scan_failed
                else -> R.string.scan_complete
            }), style = MiuixTheme.textStyles.title2)
            model.report?.let { report ->
                Text(text = strings.getString(if (report.phase == "complete" && report.matched.size < ScanProtocol.keys.size)
                    R.string.scan_complete_partial else R.string.host_scan_matched, report.matched.size, ScanProtocol.keys.size))
                Text(text = strings.getString(R.string.scan_checked, report.checked.size, ScanProtocol.keys.size))
                Text(text = strings.getString(R.string.host_scan_version, report.version))
                Text(text = strings.getString(R.string.host_scan_time, DateFormat.getDateTimeInstance().format(Date(report.time))))
                Text(text = strings.getString(R.string.host_scan_service, strings.getString(when (report.service) {
                    "installed" -> R.string.host_scan_service_installed
                    "unsupported" -> R.string.host_scan_service_unsupported
                    else -> R.string.host_scan_service_disabled
                })))
            }
            if (model.scanPending && model.settings.notice != HostSettingsNotice.SCHEDULED)
                Text(text = strings.getString(R.string.scan_scheduled))
        }
    }
    TextButton(text = strings.getString(R.string.scan_rescan), enabled = model.canRescan, modifier = Modifier.fillMaxWidth(), onClick = { action(HostSettingsAction.Rescan) })
    SettingsNote(strings.getString(R.string.host_scan_limit))
}
