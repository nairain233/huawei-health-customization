package love.nairain.huawei

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.hook.util.BadgeViews
import love.nairain.huawei.hook.util.LayoutCallbackScope
import love.nairain.huawei.hook.util.ViewSelectors
import love.nairain.huawei.hook.resolver.BadgeTargets
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 验证真实 Android View 可见性及恢复；仅编译不能记为设备通过。 */
@RunWith(AndroidJUnit4::class)
class BadgeViewsTest {
    @Test fun fourCardsAndKakaKeepContentAndClicksAcrossRefreshAndIndependentCleanup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val page = FrameLayout(context)
            val selectors = ViewSelectors(mapOf("id/item_channel_red_point" to 51,
                "id/item_channel_red_point_vertical" to 52, "id/kaka_to_be_collected_text" to 53))
            val gridScope = LayoutCallbackScope { fail(it) }.apply { activate() }
            val kakaScope = LayoutCallbackScope { fail(it) }.apply { activate() }
            val grid = BadgeViews(gridScope, selectors, BadgeTargets.GRID_RESOURCES)
            val kaka = BadgeViews(kakaScope, selectors, BadgeTargets.KAKA_RESOURCES)
            var clicks = 0
            val entries = (0..3).map { index ->
                FrameLayout(context).apply {
                    addView(TextView(context).apply { text = "card-$index" })
                    listOf(51, 52).forEach { resource -> addView(View(context).apply { id = resource }) }
                    setOnClickListener { clicks++ }
                    page.addView(this)
                }
            }
            val value = TextView(context).apply { text = "123" }
            val claim = TextView(context).apply { id = 53; text = "领取" }
            val kakaEntry = FrameLayout(context).apply {
                addView(value); addView(claim)
                setOnClickListener { clicks++ }
                page.addView(this)
            }
            grid.hide(page); kaka.hide(page)
            entries.forEach { entry ->
                assertEquals(View.VISIBLE, entry.visibility)
                assertEquals(View.GONE, entry.getChildAt(1).visibility)
                assertEquals(View.GONE, entry.getChildAt(2).visibility)
                entry.performClick()
            }
            kakaEntry.performClick()
            assertEquals(5, clicks)
            assertEquals("123", value.text.toString())
            assertEquals(View.GONE, claim.visibility)
            val reused = entries.first()
            page.removeView(reused)
            grid.beforeUpdate(reused)
            reused.getChildAt(1).visibility = View.INVISIBLE
            reused.getChildAt(2).visibility = View.VISIBLE
            grid.hide(reused)
            page.addView(reused)
            kaka.beforeUpdate(kakaEntry)
            claim.visibility = View.VISIBLE
            kaka.hide(kakaEntry)
            gridScope.close()
            assertEquals(View.INVISIBLE, reused.getChildAt(1).visibility)
            assertEquals(View.VISIBLE, reused.getChildAt(2).visibility)
            assertEquals(View.GONE, claim.visibility)
            kakaScope.close()
            assertEquals(View.VISIBLE, claim.visibility)
            grid.hide(page); kaka.hide(page)
            assertEquals(View.VISIBLE, claim.visibility)
        }
    }

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
