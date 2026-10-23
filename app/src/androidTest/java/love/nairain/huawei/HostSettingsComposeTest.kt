package love.nairain.huawei

import android.app.Dialog
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.app.ServiceCatalog
import love.nairain.huawei.app.ServiceItem
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServicePreset
import love.nairain.huawei.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import java.util.Locale

/** 普通 Activity 与平台 Dialog 中实际渲染所有路由，宿主入口另行回归。 */
@RunWith(AndroidJUnit4::class)
class HostSettingsComposeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Suppress("DEPRECATION")
    private val strings get() = instrumentation.targetContext.resources.let {
        Resources(it.assets, it.displayMetrics, Resources.getSystem().configuration)
    }

    private fun ready(route: String = "home"): HostSettingsUiState {
        val services = ServicePreset.entries.flatMap { it.components }.distinct().map { ServiceItem(it) } +
            (0 until 80).map { ServiceItem("com.huawei.health/com.huawei.health.TestService" + it.toString().padStart(3, '0')) }
        return HostSettingsUiState(route = route, settings = HostSettingsState(
            configuration = HostConfiguration(services = ServiceBlockConfig(debugMode = true)),
            catalog = ServiceCatalog(services, true), writable = true, notice = HostSettingsNotice.READY),
            writable = true, restartAvailable = true)
    }

    @Test fun ownerStopsAndCannotResumeAfterDestroy() {
        instrumentation.runOnMainSync {
            val owner = HostComposeOwner()
            assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
            owner.moveTo(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)
            owner.moveTo(Lifecycle.State.CREATED)
            assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
            owner.moveTo(Lifecycle.State.DESTROYED)
            owner.moveTo(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        }
    }

    @Test fun allRoutesKeepActionsVisibleAndReuseComposeView() {
        val initial = ready()
        withHost(initial) { host ->
            val view = host.view.getChildAt(0)
            for (route in HostSettingsPageState.routes) {
                compose.runOnIdle { host.update(initial.copy(route = route)) }
                compose.onNodeWithText(strings.getString(routeTitle(route))).assertIsDisplayed()
                compose.onNodeWithText(strings.getString(android.R.string.cancel)).assertIsDisplayed()
                compose.onNodeWithText(strings.getString(R.string.host_restart)).assertIsDisplayed()
                compose.runOnIdle { assertSame(view, host.view.getChildAt(0)) }
            }
        }
    }

    @Test fun pageTransitionMovesAndRejectsCallbacksFromDepartingPage() {
        val requested = mutableListOf<HostSettingsAction>()
        val initial = ready()
        withAnimatedScreen(initial, action = requested::add) { update ->
            val oldClick = compose.onNodeWithText(strings.getString(R.string.experimental_features_title))
                .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            val restingLeft = compose.onNodeWithTag("settings.page.home").fetchSemanticsNode().boundsInRoot.left
            compose.mainClock.autoAdvance = false
            try {
                instrumentation.runOnMainSync { update(initial.copy(route = "experimental")) }
                compose.mainClock.advanceTimeBy(48)
                val incoming = compose.onNodeWithTag("settings.page.experimental").fetchSemanticsNode().boundsInRoot.left
                assertTrue("进入子页应从右侧滑入", incoming > restingLeft)
                instrumentation.runOnMainSync { oldClick() }
                assertTrue("退场页面的旧点击不得发出导航命令", requested.isEmpty())
                compose.mainClock.advanceTimeBy(96)
                val approaching = compose.onNodeWithTag("settings.page.experimental").fetchSemanticsNode().boundsInRoot.left
                assertTrue(approaching < incoming)
                // 动画中继续进入、返回，最终只能保留当前页。
                instrumentation.runOnMainSync { update(initial.copy(route = "services")) }
                compose.mainClock.advanceTimeBy(48)
                instrumentation.runOnMainSync { update(initial) }
                compose.mainClock.advanceTimeBy(600)
                compose.onNodeWithTag("settings.page.home").assertIsDisplayed()
                compose.onNodeWithTag("settings.page.experimental").assertDoesNotExist()
                compose.onNodeWithTag("settings.page.services").assertDoesNotExist()
            } finally { compose.mainClock.autoAdvance = true }
        }
    }

    @Test fun confirmationRetainsExitAnimationAndCanReopenWhileHiding() {
        val requested = mutableListOf<HostSettingsAction>()
        val initial = ready("services").copy(pendingPreset = ServicePreset.DEVICE_CORE)
        withAnimatedScreen(initial, action = requested::add) { update ->
            val title = strings.getString(R.string.service_confirm_block)
            compose.onNodeWithText(title).assertIsDisplayed()
            val oldConfirm = compose.onNodeWithText(strings.getString(android.R.string.ok))
                .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            compose.mainClock.autoAdvance = false
            try {
                instrumentation.runOnMainSync { update(initial.copy(pendingPreset = null)) }
                compose.mainClock.advanceTimeBy(64)
                compose.onNodeWithText(title).assertExists()
                compose.onNodeWithText(strings.getString(android.R.string.ok)).assertIsNotEnabled()
                instrumentation.runOnMainSync { oldConfirm() }
                assertTrue("退场确认按钮不能重放业务命令", requested.isEmpty())
                instrumentation.runOnMainSync { update(initial) }
                compose.mainClock.advanceTimeBy(600)
                compose.onNodeWithText(title).assertIsDisplayed()
                compose.onNodeWithText(strings.getString(android.R.string.ok)).assertIsEnabled()
                instrumentation.runOnMainSync { update(initial.copy(pendingPreset = null)) }
                compose.mainClock.advanceTimeBy(400)
                compose.onNodeWithText(title).assertDoesNotExist()
            } finally { compose.mainClock.autoAdvance = true }
        }
    }

    @Test fun pageDirectionMirrorsInRtlAndReversesOnBack() {
        for (rtl in listOf(false, true)) {
            val configuration = Configuration(strings.configuration).apply {
                setLocale(Locale.ENGLISH)
                setLayoutDirection(if (rtl) Locale.forLanguageTag("ar") else Locale.ENGLISH)
            }
            val initial = ready("experimental")
            withAnimatedScreen(initial, configuration = configuration) { update ->
                val resting = compose.onNodeWithTag("settings.page.experimental").fetchSemanticsNode().positionInRoot.x
                compose.mainClock.autoAdvance = false
                try {
                    instrumentation.runOnMainSync { update(initial.copy(route = "services")) }
                    compose.mainClock.advanceTimeBy(48)
                    val forward = compose.onNodeWithTag("settings.page.services").fetchSemanticsNode().positionInRoot.x - resting
                    assertTrue("前进方向须跟随布局方向", if (rtl) forward < 0 else forward > 0)
                    captureNoticeScreenshot("motion-${if (rtl) "rtl" else "ltr"}-forward")
                    compose.mainClock.advanceTimeBy(600)
                    instrumentation.runOnMainSync { update(initial) }
                    compose.mainClock.advanceTimeBy(48)
                    val back = compose.onNodeWithTag("settings.page.experimental").fetchSemanticsNode().positionInRoot.x - resting
                    assertTrue("返回须反向播放", if (rtl) back > 0 else back < 0)
                    captureNoticeScreenshot("motion-${if (rtl) "rtl" else "ltr"}-back")
                    compose.mainClock.advanceTimeBy(600)
                } finally { compose.mainClock.autoAdvance = true }
            }
        }
    }

    @Test fun confirmedValuesStayUnchangedAndErrorsKeepEditsDisabled() {
        val requested = mutableListOf<HostSettingsAction>()
        val initial = ready()
        withHost(initial, action = requested::add) { host ->
            compose.onNode(isToggleable()).assertIsOff().performClick()
            compose.runOnIdle { assertTrue(requested.single() is HostSettingsAction.Layout) }
            compose.onNode(isToggleable()).assertIsOff()
            for (notice in listOf(HostSettingsNotice.SAVING, HostSettingsNotice.FAILED, HostSettingsNotice.UNCERTAIN)) {
                compose.runOnIdle { host.update(initial.copy(writable = false, restartAvailable = false,
                    settings = initial.settings.copy(notice = notice, writable = false))) }
                compose.onNode(isToggleable()).assertIsNotEnabled().assertIsOff()
                compose.onNodeWithText(strings.getString(R.string.host_restart)).assertIsNotEnabled()
                if (notice != HostSettingsNotice.SAVING) compose.onNodeWithText(strings.getString(R.string.host_settings_retry)).assertIsDisplayed()
            }
            compose.runOnIdle {
                host.update(initial.copy(settings = initial.settings.copy(configuration = initial.configuration.copy(
                    layout = initial.configuration.layout + (love.nairain.huawei.config.SettingsKeys.ENABLED to true)))))
                host.pause()
                assertEquals(Lifecycle.State.CREATED, host.view.findViewTreeLifecycleOwner()?.lifecycle?.currentState)
                host.resume()
            }
            compose.onNode(isToggleable()).assertIsOn()
        }
    }

    @Test fun windowConfirmationConsumesBackAndUsesExplicitConfirmAction() {
        val requested = mutableListOf<HostSettingsAction>()
        val initial = ready("services").copy(pendingPreset = ServicePreset.DEVICE_CORE)
        withHost(initial, action = requested::add) { host ->
            compose.onNodeWithText(strings.getString(android.R.string.ok)).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(HostSettingsAction.ConfirmPreset, requested.last()) }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
            compose.waitUntil(5000) { requested.contains(HostSettingsAction.CancelPreset) }
            compose.runOnIdle { host.update(initial.copy(pendingPreset = null)) }
            compose.waitUntil(5000) { compose.onAllNodesWithText(strings.getString(R.string.service_confirm_block)).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText(strings.getString(R.string.service_confirm_block)).assertDoesNotExist()
        }
    }

    @Test fun searchDraftDoesNotFilterUntilApplyAndSurvivesSnapshotUpdates() {
        var model = ready("custom")
        lateinit var host: HostSettingsComposeHost
        withHost(model, action = { event ->
            model = when (event) {
                is HostSettingsAction.DraftQuery -> model.copy(draftQuery = event.value)
                HostSettingsAction.ApplyFilter -> model.copy(query = model.draftQuery, filterRevision = model.filterRevision + 1)
                else -> model
            }
            host.update(model)
        }) { current ->
            host = current
            compose.onNode(hasSetTextAction()).performClick().performTextInput("NoMatchingService")
            compose.onNodeWithText(strings.getString(android.R.string.cancel)).assertIsDisplayed()
            compose.onNodeWithText(strings.getString(R.string.host_restart)).assertIsDisplayed()
            compose.runOnIdle { host.update(model.copy(settings = model.settings.copy(notice = HostSettingsNotice.SAVED))) }
            compose.onNode(hasSetTextAction()).assertTextContains("NoMatchingService")
            compose.onNodeWithText(strings.getString(R.string.service_block_empty)).assertDoesNotExist()
            compose.onNodeWithText(strings.getString(R.string.host_settings_apply_filter)).performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(strings.getString(R.string.service_block_empty)))
            compose.onNodeWithText(strings.getString(R.string.service_block_empty)).assertIsDisplayed()
        }
    }

    @Test fun lazyListPositionSurvivesNavigationAndNewWindowOwner() {
        val page = HostSettingsPageState().apply { route = "custom" }
        // 重试复用窗口会话时筛选代次可能非零；不能因此重置已捕获的列表位置。
        val model = ready("custom").copy(filterRevision = 1)
        withHost(model, page) { host ->
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
            compose.runOnIdle { host.captureState(); assertTrue(page.customIndex > 0) }
            val index = page.customIndex
            compose.runOnIdle { host.update(model.copy(route = "about")) }
            compose.runOnIdle { host.update(model) }
            compose.runOnIdle { host.captureState(); assertEquals(index, page.customIndex) }
        }
        val saved = page.customIndex
        withHost(model, page) { host -> compose.runOnIdle { host.captureState(); assertEquals(saved, page.customIndex) } }
    }

    @Test fun rescanButtonRespectsUnavailablePendingAndRunningStates() {
        val model = ready("scan")
        withHost(model) { host ->
            val button = compose.onNodeWithText(strings.getString(R.string.scan_rescan))
            button.assertIsEnabled()
            compose.runOnIdle { host.update(model.copy(writable = false)) }
            button.assertIsNotEnabled()
            compose.runOnIdle { host.update(model.copy(settings = model.settings.copy(configuration = model.configuration.copy(scanRequest = "pending")))) }
            button.assertIsNotEnabled()
            val report = love.nairain.huawei.scan.ScanReport("pending", "17.0.8.300", 1700008300, 1, "", "run", 1, phase = "running", time = 10)
            compose.runOnIdle { host.update(model.copy(report = report)) }
            button.assertIsNotEnabled()
            compose.runOnIdle { host.update(model.copy(report = report, scanTimedOut = true)) }
            button.assertIsEnabled()
        }
    }

    @Test fun lateLayoutFailureIsReportedWithoutEscapingTraversal() {
        val failure = AtomicReference<Throwable>()
        lateinit var current: HostSettingsComposeHost
        withHost(ready(), failure = { error ->
            failure.set(error)
            current.close()
            current.view.removeAllViews()
        }) { host ->
            current = host
            compose.onNodeWithText(strings.getString(R.string.settings_title)).assertIsDisplayed()
            instrumentation.runOnMainSync {
                host.view.addView(object : View(host.view.context) {
                    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { throw IllegalStateException("fixture") }
                })
                val spec = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
                host.view.measure(spec, spec)
            }
            instrumentation.waitForIdleSync()
            assertTrue(failure.get() is IllegalStateException)
        }
    }

    @Test fun localizedDarkRtlAndLargeFontWindowsKeepActionsAndSearchReachable() {
        val variants = listOf("light", "dark", "english", "rtl-large")
        for (name in variants) {
            val configuration = Configuration(strings.configuration).apply {
                setLocale(if (name == "english" || name == "rtl-large") Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE)
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (name == "dark") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                if (name == "rtl-large") { fontScale = 1.7f; setLayoutDirection(Locale.forLanguageTag("ar")) }
            }
            @Suppress("DEPRECATION")
            val localized = Resources(strings.assets, strings.displayMetrics, configuration)
            withHost(ready("custom"), configuration = configuration) { host ->
                compose.runOnIdle { assertEquals(configuration.layoutDirection, host.view.layoutDirection) }
                compose.onNodeWithText(localized.getString(android.R.string.cancel)).assertIsDisplayed()
                compose.onNodeWithText(localized.getString(R.string.host_restart)).assertIsDisplayed()
                compose.onNode(hasSetTextAction()).assertIsDisplayed()
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                assertNotNull(bitmap)
                File(instrumentation.targetContext.cacheDir, "miuix-all-$name.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
        }
    }

    @Test fun noticesDismissWithoutConfigurationActionsAndNewResultsReappear() {
        for (variant in listOf("light", "dark", "english", "rtl-large")) {
            val configuration = Configuration(strings.configuration).apply {
                setLocale(if (variant == "english" || variant == "rtl-large") Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE)
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (variant == "dark") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                if (variant == "rtl-large") { fontScale = 1.7f; setLayoutDirection(Locale.forLanguageTag("ar")) }
            }
            @Suppress("DEPRECATION")
            val localized = Resources(strings.assets, strings.displayMetrics, configuration)
            val actions = mutableListOf<HostSettingsAction>()
            val initial = ready("HEALTH").let { it.copy(settings = it.settings.copy(notice = HostSettingsNotice.SAVED)) }
            withHost(initial, action = actions::add, configuration = configuration) { host ->
                val saved = localized.getString(R.string.host_ui_saved)
                val dismiss = localized.getString(R.string.host_ui_dismiss_notice)
                compose.onNodeWithText(saved).assertIsDisplayed()
                compose.onNodeWithContentDescription(dismiss).assertIsDisplayed()
                captureNoticeScreenshot(variant)
                compose.onNodeWithContentDescription(dismiss).performClick()
                compose.waitUntil(5000) { compose.onAllNodesWithText(saved).fetchSemanticsNodes().isEmpty() }
                compose.onNodeWithText(saved).assertDoesNotExist()
                compose.runOnIdle { host.update(initial) }
                compose.onNodeWithText(saved).assertDoesNotExist()
                compose.onNodeWithText(localized.getString(android.R.string.cancel)).assertIsDisplayed()
                compose.onNodeWithText(localized.getString(R.string.host_restart)).assertIsDisplayed()
                captureNoticeScreenshot("$variant-closed")
                compose.runOnIdle { host.update(initial.copy(settings = initial.settings.copy(notice = HostSettingsNotice.SCHEDULED))) }
                compose.waitUntil(5000) { compose.onNodeWithText(localized.getString(R.string.host_ui_scheduled)).isDisplayed() }
                compose.onNodeWithText(localized.getString(R.string.host_ui_scheduled)).assertIsDisplayed()
                compose.onNodeWithContentDescription(dismiss).assertIsDisplayed()
                compose.runOnIdle { assertTrue(actions.isEmpty()) }
            }
        }
    }

    @Test fun sportHomeHasFixedNavigationWithoutDiscovery() {
        val actions = mutableListOf<HostSettingsAction>()
        withHost(ready("SPORT"), action = actions::add) {
            compose.onNodeWithText(strings.getString(R.string.sport_top_title)).performClick()
            assertEquals(HostSettingsAction.Navigate("sport.top"), actions.last())
            love.nairain.huawei.config.SportFixedItems.pages.forEach { page ->
                compose.onNodeWithTag("sport.page.${page.id}").performSemanticsAction(SemanticsActions.OnClick) { it() }
                compose.waitUntil { actions.lastOrNull() == HostSettingsAction.Navigate("sport.page.${page.id}") }
            }
        }
    }

    @Test fun sportTabPageProtectsLastActualTabWithoutHidingItsConfigurationPage() {
        val initial = ready("sport.top").let { it.copy(settings = it.settings.copy(sportTabs = listOf(258))) }
        withHost(initial) {
            compose.onNodeWithTag("sport.tab.258").assertExists()
            compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("sport.tab.258")), useUnmergedTree = true).assertIsOff().assertIsNotEnabled()
            compose.onNodeWithTag("sport.tab.259").assertHasClickAction()
        }
    }

    @Test fun sportFixedItemsSaveOnlyAfterConfirmationAndRespectParent() {
        val item = love.nairain.huawei.config.SportFixedItems.items(10001).first { it.hostTitle == "全部课程" }
        val initial = ready("sport.page.10001")
        val actions = mutableListOf<HostSettingsAction>()
        withHost(initial, action = actions::add) { host ->
            val node = isToggleable() and hasAnyAncestor(hasTestTag("sport.item.${item.id}"))
            compose.onNodeWithTag("sport.item.${item.id}").assertIsEnabled().performClick()
            compose.waitUntil { actions.lastOrNull() == HostSettingsAction.SportItem(item.id, true) }
            assertEquals(HostSettingsAction.SportItem(item.id, true), actions.last())
            compose.onNode(node, useUnmergedTree = true).assertIsOff()
            compose.runOnIdle {
                val parent = love.nairain.huawei.config.SportItem.fixed(10001, requireNotNull(item.parentKey))
                host.update(initial.copy(settings = initial.settings.copy(configuration = initial.configuration.copy(
                    sport = initial.configuration.sport.copy(overrides = mapOf(parent.id to true, item.id to true))))))
            }
            compose.onNode(node, useUnmergedTree = true).assertIsOn().assertIsNotEnabled()
        }
    }

    @Test fun sportPageScrollAndBackRoutesSurviveWindowRecreation() {
        val page = HostSettingsPageState()
        val model = ready("sport.page.500001")
        var saved = 0
        withHost(model, page) { host ->
            // 真实触摸滚动与路由语义分别验证，避免长 Card 的程序化定位循环。
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            compose.runOnIdle { host.captureState(); saved = page.scroll.getValue(model.route) }
            assertTrue(saved > 0)
            compose.runOnIdle { host.update(ready("sport.page.2")) }
            compose.runOnIdle { host.captureState(); assertEquals(saved, page.scroll.getValue(model.route)) }
            compose.runOnIdle { host.update(model) }
            compose.runOnIdle { host.captureState(); assertEquals(saved, page.scroll.getValue(model.route)) }
        }
        withHost(model, page) { host ->
            compose.runOnIdle { host.captureState(); assertEquals(saved, page.scroll.getValue(model.route)) }
        }
        assertEquals("SPORT", HostSettingsPageState.parent(model.route))
    }

    private fun captureNoticeScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        File(instrumentation.targetContext.cacheDir, "settings-notice-$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    /** 中间帧测试使用框架注入的窗口 Recomposer；生产宿主的独立 Recomposer 另由 withHost 回归。 */
    private fun withAnimatedScreen(initial: HostSettingsUiState, action: (HostSettingsAction) -> Unit = {},
        configuration: Configuration = strings.configuration, check: ((HostSettingsUiState) -> Unit) -> Unit) {
        ActivityScenario.launch(TestHostActivity::class.java).use { scenario ->
            val model = mutableStateOf(initial)
            val scrolls = mutableMapOf<String, ScrollState>()
            val list = LazyListState()
            lateinit var owner: HostComposeOwner
            lateinit var dialog: Dialog
            lateinit var view: ComposeView
            scenario.onActivity { activity ->
                owner = HostComposeOwner()
                @Suppress("DEPRECATION")
                val resources = Resources(activity.resources.assets, activity.resources.displayMetrics, configuration)
                view = ComposeView(activity).apply {
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setViewTreeNavigationEventDispatcherOwner(owner)
                    setContent {
                        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner,
                            LocalConfiguration provides configuration,
                            LocalLayoutDirection provides if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                            HostSettingsScreen(model.value, resources, { scrolls.getOrPut(it) { ScrollState(0) } }, list, action)
                        }
                    }
                }
                dialog = Dialog(activity).apply {
                    setContentView(view)
                    show()
                    window!!.setLayout(activity.resources.displayMetrics.widthPixels, (activity.resources.displayMetrics.heightPixels * .82f).toInt())
                }
                owner.moveTo(Lifecycle.State.RESUMED)
            }
            try { check { model.value = it } }
            finally {
                scenario.onActivity {
                    view.disposeComposition()
                    owner.moveTo(Lifecycle.State.DESTROYED)
                    dialog.dismiss()
                }
            }
        }
    }

    private fun withHost(model: HostSettingsUiState, page: HostSettingsPageState = HostSettingsPageState(),
        action: (HostSettingsAction) -> Unit = {}, failure: (Throwable) -> Unit = { throw AssertionError(it) },
        configuration: Configuration? = null,
        check: (HostSettingsComposeHost) -> Unit) {
        ActivityScenario.launch(TestHostActivity::class.java).use { scenario ->
            lateinit var host: HostSettingsComposeHost
            lateinit var dialog: Dialog
            scenario.onActivity { activity ->
                @Suppress("DEPRECATION")
                val contentResources = Resources(activity.resources.assets, activity.resources.displayMetrics, configuration ?: strings.configuration)
                host = HostSettingsComposeHost(activity, contentResources, page, model, action, failure)
                dialog = Dialog(activity).apply {
                    setContentView(host.view)
                    @Suppress("DEPRECATION")
                    window!!.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                    show()
                    val metrics = activity.resources.displayMetrics
                    window!!.setLayout(minOf(metrics.widthPixels - (32 * metrics.density).toInt(), (600 * metrics.density).toInt()),
                        minOf((metrics.heightPixels * .82f).toInt(), (720 * metrics.density).toInt()))
                }
                host.resume()
            }
            try { check(host) }
            finally {
                scenario.onActivity {
                    host.captureState()
                    host.close()
                    host.close()
                    dialog.dismiss()
                    assertEquals(Lifecycle.State.DESTROYED, host.view.findViewTreeLifecycleOwner()?.lifecycle?.currentState)
                }
            }
        }
    }
}
