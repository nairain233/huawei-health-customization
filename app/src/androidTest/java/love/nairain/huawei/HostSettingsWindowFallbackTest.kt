package love.nairain.huawei

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.graphics.Bitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.settings.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 在模块测试目录中验证真实窗口的错误降级与 Activity 重建，不写宿主配置。 */
@RunWith(AndroidJUnit4::class)
class HostSettingsWindowFallbackTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val resources get() = instrumentation.targetContext.resources

    private fun fixture(): Pair<Context, HostSettingsController> {
        val base = instrumentation.targetContext
        val directory = File(base.cacheDir, "miuix-window-" + UUID.randomUUID())
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir(): File = directory }
        val client = HostSettingsController(context) { fail(it) }
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync { client.initialize { loaded.countDown() } }
        assertTrue(loaded.await(5, TimeUnit.SECONDS))
        return context to client
    }

    private fun restarter(client: HostSettingsController) = HostRestarter("com.huawei.health", { "com.huawei.health" },
        { client.canRestart }, { error("测试不应启动宿主") }, { error("测试不应结束进程") }, { fail(it) })

    @Test fun settingsFillScreenWithoutFloatingFrameAndBackClosesOnlyHome() {
        val (context, client) = fixture()
        val before = AtomicHostConfigStorage(context).read()
        ActivityScenario.launch(TestHostActivity::class.java).use { scenario ->
            lateinit var dialog: HostSettingsDialog
            scenario.onActivity { activity ->
                dialog = HostSettingsDialog(activity, resources, client, HostSettingsPageState(), restarter(client),
                    failed = { throw AssertionError(it) })
                dialog.show()
            }
            try {
                compose.onNodeWithText(resources.getString(R.string.settings_title)).assertIsDisplayed()
                scenario.onActivity {
                    val window = dialog.window!!
                    val floating = android.util.TypedValue()
                    assertTrue(dialog.context.theme.resolveAttribute(android.R.attr.windowIsFloating, floating, true))
                    assertEquals(0, floating.data)
                    assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, window.attributes.width)
                    assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, window.attributes.height)
                    assertEquals(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    val screen = it.window.decorView
                    assertEquals(screen.width, window.decorView.width)
                    assertEquals(screen.height, window.decorView.height)
                }
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                assertNotNull(bitmap)
                File(instrumentation.targetContext.cacheDir, "settings-fullscreen.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                compose.onNodeWithText(resources.getString(R.string.settings_layout_trim_title)).performClick()
                compose.onNodeWithContentDescription(resources.getString(R.string.host_ui_back)).performClick()
                compose.onNodeWithText(resources.getString(R.string.settings_title)).assertIsDisplayed()
                scenario.onActivity { assertTrue(dialog.visible) }
                compose.onNodeWithContentDescription(resources.getString(R.string.host_ui_back)).performClick()
                scenario.onActivity { assertFalse(dialog.visible) }
                assertEquals(before, AtomicHostConfigStorage(context).read())
            } finally { scenario.onActivity { dialog.dispose() } }
        }
    }

    @Test fun renderingFailureOffersOnlyRetryAndCloseWithoutChangingConfiguration() {
        val (context, client) = fixture()
        val before = AtomicHostConfigStorage(context).read()
        val failures = mutableListOf<Throwable>()
        ActivityScenario.launch(TestHostActivity::class.java).use { scenario ->
            lateinit var dialog: HostSettingsDialog
            scenario.onActivity { activity ->
                dialog = HostSettingsDialog(activity, resources, client, HostSettingsPageState(), restarter(client),
                    composeFailed = failures::add, failed = { throw AssertionError(it) })
                dialog.show()
            }
            try {
                compose.onNodeWithText(resources.getString(R.string.settings_title)).assertIsDisplayed()
                scenario.onActivity {
                    val content = descendants(dialog.window!!.decorView).filterIsInstance<ComposeView>().single()
                    val frame = content.parent as ViewGroup
                    frame.addView(object : View(frame.context) {
                        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { throw IllegalStateException("fixture") }
                    })
                    frame.requestLayout()
                }
                onView(withText(resources.getString(R.string.host_ui_framework_error))).check(matches(isDisplayed()))
                onView(withText(resources.getString(R.string.close))).check(matches(isDisplayed()))
                assertEquals(1, failures.size)
                onView(withText(resources.getString(R.string.host_ui_retry))).perform(click())
                compose.onNodeWithText(resources.getString(R.string.settings_title)).assertIsDisplayed()
                assertEquals(before, AtomicHostConfigStorage(context).read())
                compose.onNodeWithText(resources.getString(android.R.string.cancel)).performClick()
                scenario.onActivity { assertFalse(dialog.visible) }
            } finally { scenario.onActivity { dialog.dispose() } }
        }
    }

    @Test fun activityRecreationRestoresRouteWithoutAConfigurationSnapshotInBundle() {
        val (context, client) = fixture()
        val before = AtomicHostConfigStorage(context).read()
        val app = instrumentation.targetContext.applicationContext as Application
        val windows = HostSettingsWindows(resources, client, restarter(client)) { fail(it) }
        instrumentation.runOnMainSync { app.registerActivityLifecycleCallbacks(windows) }
        try {
            ActivityScenario.launch(TestHostActivity::class.java).use { scenario ->
                scenario.onActivity { assertTrue(windows.show(it)) }
                compose.onNodeWithText(resources.getString(R.string.about_title)).performClick()
                compose.onNodeWithText(resources.getString(R.string.about_open_source_section)).performClick()
                scenario.recreate()
                compose.onNodeWithText(resources.getString(R.string.about_open_source_section)).assertIsDisplayed()
                compose.onNodeWithText("Miuix").assertIsDisplayed()
                assertEquals(before, AtomicHostConfigStorage(context).read())
            }
        } finally { instrumentation.runOnMainSync { app.unregisterActivityLifecycleCallbacks(windows) } }
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
}
