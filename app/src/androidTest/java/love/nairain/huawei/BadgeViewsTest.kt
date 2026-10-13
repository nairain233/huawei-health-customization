package love.nairain.huawei

import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.hook.util.BadgeViews
import love.nairain.huawei.hook.util.LayoutCallbackScope
import love.nairain.huawei.hook.util.ViewSelectors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 验证真实 Android View 可见性及恢复；仅编译不能记为设备通过。 */
@RunWith(AndroidJUnit4::class)
class BadgeViewsTest {
    @Test fun repeatedIdsAndAsyncUpdatesStayHiddenWithoutChangingEntryOrDimensions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val scope = LayoutCallbackScope { fail(it) }.apply { activate() }
            val root = FrameLayout(context)
            val first = View(context).apply { id = 42; layoutParams = FrameLayout.LayoutParams(12, 13) }
            val second = View(context).apply { id = 42; visibility = View.INVISIBLE }
            val entry = View(context).apply { id = 43; isClickable = true }
            root.addView(first); root.addView(second); root.addView(entry)
            val badges = BadgeViews(scope, ViewSelectors(mapOf("id/dot" to 42)), setOf("dot"))
            badges.hide(root)
            assertEquals(View.GONE, first.visibility)
            assertEquals(View.GONE, second.visibility)
            assertEquals(View.VISIBLE, entry.visibility)
            assertTrue(entry.isClickable)
            assertEquals(12, first.layoutParams.width)
            assertEquals(13, first.layoutParams.height)
            first.visibility = View.VISIBLE
            badges.hide(root)
            assertEquals(View.GONE, first.visibility)
            scope.close()
            assertEquals(View.VISIBLE, first.visibility)
            assertEquals(View.INVISIBLE, second.visibility)
            badges.hide(root)
            assertEquals("撤销后的调用不能重新隐藏", View.VISIBLE, first.visibility)
        }
    }

    @Test fun rebindingCapturesLatestHostVisibilityAndClosingRestoresIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val errors = mutableListOf<String>()
            val scope = LayoutCallbackScope(errors::add).apply { activate() }
            val badge = View(context).apply { id = 42 }
            val badges = BadgeViews(scope, ViewSelectors(mapOf("id/dot" to 42)), setOf("dot"))
            badges.hide(badge)
            badges.beforeUpdate(badge)
            assertEquals(View.VISIBLE, badge.visibility)
            badge.visibility = View.INVISIBLE
            badges.hide(badge)
            scope.run { error("private failure") }
            assertEquals(View.INVISIBLE, badge.visibility)
            assertFalse(scope.isActive)
            assertEquals(listOf("IllegalStateException"), errors)
        }
    }
}
