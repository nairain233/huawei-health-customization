package love.nairain.huawei

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.app.LayoutTrimActivity
import love.nairain.huawei.hook.util.ActivePageObserver
import love.nairain.huawei.hook.util.LayoutCallbackScope
import love.nairain.huawei.hook.util.PageLayoutObserver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 使用实际 Android 回调验证异步失败隔离与页面脱离；编译不等于这些设备测试已执行。 */
@RunWith(AndroidJUnit4::class)
class PageObserversTest {
    @Test fun firstReplayFailureDoesNotInterruptOtherActivityListeners() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val errors = mutableListOf<String>()
        val failing = LayoutCallbackScope(errors::add)
        val healthy = LayoutCallbackScope { fail(it) }
        var calls = 0
        lateinit var activity: Activity
        instrumentation.runOnMainSync {
            activity = Activity()
            ActivePageObserver.onActivityResumed(activity)
            ActivePageObserver.observe(failing) { error("private activity parameter") }
            ActivePageObserver.observe(healthy) { calls++ }
            failing.activate()
            healthy.activate()
        }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            ActivePageObserver.onActivityResumed(activity)
            assertEquals(listOf("IllegalStateException"), errors)
            assertEquals(2, calls)
            assertFalse(failing.isActive)
            healthy.close()
            ActivePageObserver.onActivityDestroyed(activity)
        }
    }

    @Test fun sameRootKeepsIndependentSourcesAndRemovesFailedLayoutListener() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val errors = mutableListOf<String>()
        val failing = LayoutCallbackScope(errors::add).apply { activate() }
        val healthy = LayoutCallbackScope { fail(it) }.apply { activate() }
        var calls = 0
        ActivityScenario.launch(LayoutTrimActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = FrameLayout(activity)
                (activity.window.decorView as ViewGroup).addView(root, ViewGroup.LayoutParams(10, 10))
                PageLayoutObserver.observe(root, failing) { error("private view parameter") }
                PageLayoutObserver.observe(root, healthy) { calls++ }
                root.viewTreeObserver.dispatchOnGlobalLayout()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertFalse(failing.isActive)
                assertTrue(healthy.isActive)
                assertEquals(listOf("IllegalStateException"), errors)
                assertTrue(calls > 0)
                healthy.close()
            }
        }
    }

    @Test fun detachingCancelsPendingTaskAndSameViewCanBeObservedAfterReattach() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scope = LayoutCallbackScope { fail(it) }.apply { activate() }
        var calls = 0
        lateinit var root: FrameLayout
        ActivityScenario.launch(LayoutTrimActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                root = FrameLayout(activity)
                val parent = activity.window.decorView as ViewGroup
                parent.addView(root, ViewGroup.LayoutParams(10, 10))
                PageLayoutObserver.observe(root, scope) { calls++ }
                root.viewTreeObserver.dispatchOnGlobalLayout()
                parent.removeView(root)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(0, calls)
                (activity.window.decorView as ViewGroup).addView(root, ViewGroup.LayoutParams(10, 10))
                PageLayoutObserver.observe(root, scope) { calls++ }
                root.viewTreeObserver.dispatchOnGlobalLayout()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertTrue(calls > 0)
                scope.close()
            }
        }
    }
}
