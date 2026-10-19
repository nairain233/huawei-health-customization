package love.nairain.huawei

import android.app.Dialog
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
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

    @Test fun allThirteenRoutesKeepActionsVisibleAndReuseComposeView() {
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
                compose.onNodeWithText(saved).assertDoesNotExist()
                compose.runOnIdle { host.update(initial) }
                compose.onNodeWithText(saved).assertDoesNotExist()
                compose.onNodeWithText(localized.getString(android.R.string.cancel)).assertIsDisplayed()
                compose.onNodeWithText(localized.getString(R.string.host_restart)).assertIsDisplayed()
                captureNoticeScreenshot("$variant-closed")
                compose.runOnIdle { host.update(initial.copy(settings = initial.settings.copy(notice = HostSettingsNotice.SCHEDULED))) }
                compose.onNodeWithText(localized.getString(R.string.host_ui_scheduled)).assertIsDisplayed()
                compose.onNodeWithContentDescription(dismiss).assertIsDisplayed()
                compose.runOnIdle { assertTrue(actions.isEmpty()) }
            }
        }
    }

    private fun captureNoticeScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        File(instrumentation.targetContext.cacheDir, "settings-notice-$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
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
