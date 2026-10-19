package love.nairain.huawei

import android.app.Application
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.hook.feature.HomeMoreLongPress
import love.nairain.huawei.hook.feature.HomeMorePageObserver
import love.nairain.huawei.hook.resolver.HomeMoreTargets
import love.nairain.huawei.hook.util.LayoutCallbackScope
import love.nairain.huawei.settings.HostSettingsPageState
import love.nairain.huawei.settings.HostSettingsWindow
import love.nairain.huawei.settings.HostSettingsWindows
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 使用真实 Android 手势与窗口生命周期；不替代宿主及 LSPosed 回归。 */
@RunWith(AndroidJUnit4::class)
class HomeMoreEntryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun shortClickRemainsNativeAndLongPressConsumesReleaseEvenWhenOpeningFails() {
        var clicks = 0
        var opens = 0
        var failures = 0
        val binding = HomeMoreLongPress { failures++ }
        ActivityScenario.launch(TestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val button = LinearLayout(activity).apply {
                    id = BUTTON
                    setOnClickListener { clicks++ }
                    minimumWidth = 180; minimumHeight = 180
                }
                activity.setContentView(button)
                binding.bind(button) { opens++ }
                binding.bind(button) { error("重复绑定不得替换原监听") }
            }
            onView(withId(BUTTON)).perform(click())
            instrumentation.runOnMainSync { assertEquals(1, clicks); assertEquals(0, opens) }
            onView(withId(BUTTON)).perform(longClick())
            instrumentation.runOnMainSync { assertEquals(1, clicks); assertEquals(1, opens) }
            scenario.onActivity { activity ->
                binding.close()
                val button = activity.findViewById<View>(BUTTON)
                assertFalse(button.isLongClickable)
                HomeMoreLongPress { failures++ }.bind(button) { error("打开失败") }
            }
            onView(withId(BUTTON)).perform(longClick())
            instrumentation.runOnMainSync { assertEquals(1, clicks); assertEquals(1, failures) }
        }
    }

    @Test fun selectsOnlyVisibleUniqueMoreContainerAndDoesNotTouchExistingHostLongPress() {
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val root = FrameLayout(context)
            val bar = LinearLayout(context).apply { id = BAR }
            val button = LinearLayout(context).apply { id = BUTTON; isClickable = true }
            val icon = ImageView(context).apply { id = ICON }
            root.addView(bar); bar.addView(button); button.addView(icon)
            assertSame(button, HomeMoreTargets.selectButton(root, BAR, BUTTON, ICON))
            assertNull(HomeMoreTargets.selectButton(root, 0, BUTTON, ICON))
            assertNull(HomeMoreTargets.selectButton(root, BAR, BAR, ICON))
            icon.visibility = View.GONE
            assertNull(HomeMoreTargets.selectButton(root, BAR, BUTTON, ICON))
            icon.visibility = View.VISIBLE
            bar.visibility = View.INVISIBLE
            assertNull(HomeMoreTargets.selectButton(root, BAR, BUTTON, ICON))
            bar.visibility = View.VISIBLE
            val duplicate = LinearLayout(context).apply { id = BUTTON }
            root.addView(duplicate)
            assertNull(HomeMoreTargets.selectButton(root, BAR, BUTTON, ICON))
            root.removeView(duplicate)
            button.removeView(icon); bar.addView(icon)
            assertNull(HomeMoreTargets.selectButton(root, BAR, BUTTON, ICON))
            bar.removeView(icon); button.addView(icon)
            button.isClickable = false
            assertNull(HomeMoreTargets.selectButton(root, BAR, BUTTON, ICON))
            var native = 0
            button.setOnLongClickListener { native++; true }
            HomeMoreLongPress().bind(button) { error("不得覆盖宿主长按") }
            assertTrue(button.performLongClick())
            assertEquals(1, native)
        }
    }

    @Test fun latePageReplayRebindsReplacementAndClosingScopeRemovesLongPress() {
        val scope = LayoutCallbackScope { error(it) }.apply { activate() }
        val binding = HomeMoreLongPress()
        val pages = HomeMorePageObserver(scope) { page, _ -> page.findViewById<View>(BUTTON)?.let { binding.bind(it) {} } }
        scope.onClose(binding::close)
        ActivityScenario.launch(TestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                val old = View(activity).apply { id = BUTTON }
                root.addView(old); activity.setContentView(root)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val old = activity.findViewById<View>(BUTTON)
                val root = old.parent as FrameLayout
                pages.observe(root, activity)
                assertTrue(old.isLongClickable)
                root.removeView(old)
                assertFalse(old.isLongClickable)
                val replacement = View(activity).apply { id = BUTTON }
                root.addView(replacement)
                root.viewTreeObserver.dispatchOnGlobalLayout()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(BUTTON).parent as FrameLayout
                val parent = root.parent as android.view.ViewGroup
                parent.removeView(root)
                assertFalse(root.findViewById<View>(BUTTON).isLongClickable)
                parent.addView(root)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(BUTTON).isLongClickable)
                scope.close()
                assertFalse(activity.findViewById<View>(BUTTON).isLongClickable)
            }
        }
    }

    @Test fun sharedWindowsRestoreHomeActivityAndRemainUsableAfterAnEntryFails() {
        val made = mutableListOf<FakeWindow>()
        val callbacks = mutableListOf<(Throwable) -> Unit>()
        var reject = false
        val windows = HostSettingsWindows({ _, state, failed ->
            check(!reject)
            callbacks += failed
            FakeWindow(state).also(made::add)
        }, {})
        ActivityScenario.launch(TestHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                reject = true
                assertFalse(windows.show(activity))
                reject = false
                assertTrue(windows.show(activity))
                assertTrue(windows.show(activity))
                assertEquals(1, made.size)
                made.single().dispose()
                assertTrue(windows.show(activity))
                callbacks.first()(IllegalStateException("旧窗口的延迟失败"))
                val first = made.last()
                assertTrue(first.visible)
                first.state.route = "layout"; first.state.scroll["layout"] = 120
                windows.onActivityStopped(activity)
                assertEquals(1, first.pauses)
                windows.onActivityResumed(activity)
                assertEquals(1, first.resumes)
                val saved = Bundle()
                windows.onActivitySaveInstanceState(activity, saved)
                windows.onActivityDestroyed(activity)
                assertTrue(first.disposed)
                windows.onActivityCreated(activity, saved)
                windows.onActivityResumed(activity)
                assertEquals(3, made.size)
                assertEquals("layout", made.last().state.route)
                assertEquals(120, made.last().state.scroll["layout"])
                callbacks.first()(IllegalStateException("旧窗口的延迟失败"))
                assertTrue(made.last().visible)
                windows.onActivityDestroyed(activity)
                assertTrue(made.last().disposed)
            }
        }
    }

    @Test fun activityRecreationRestoresNavigationAndDisposesOldWindow() {
        val made = mutableListOf<FakeWindow>()
        val windows = HostSettingsWindows({ _, state, _ -> FakeWindow(state).also(made::add) }, {})
        val application = instrumentation.targetContext.applicationContext as Application
        instrumentation.runOnMainSync { application.registerActivityLifecycleCallbacks(windows) }
        try {
            ActivityScenario.launch(TestHostActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertTrue(windows.show(activity))
                    made.single().state.route = "services"
                    made.single().state.scroll["services"] = 160
                }
                scenario.recreate()
                scenario.onActivity {
                    assertEquals(2, made.size)
                    assertTrue(made.first().disposed)
                    assertEquals("services", made.last().state.route)
                    assertEquals(160, made.last().state.scroll["services"])
                }
            }
            assertTrue(made.last().disposed)
        } finally {
            instrumentation.runOnMainSync { application.unregisterActivityLifecycleCallbacks(windows) }
        }
    }

    private class FakeWindow(val state: HostSettingsPageState) : HostSettingsWindow {
        override var visible = false
        var disposed = false
        var pauses = 0
        var resumes = 0
        override fun show() { visible = true; state.open = true }
        override fun pause() { pauses++ }
        override fun resume() { resumes++ }
        override fun captureState() = Unit
        override fun dispose() { disposed = true; visible = false; state.open = false }
    }
    private companion object {
        const val BAR = 0x123001
        const val BUTTON = 0x123002
        const val ICON = 0x123003
    }
}
