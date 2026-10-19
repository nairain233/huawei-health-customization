package love.nairain.huawei

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.hook.resolver.HostSettingsTargets
import love.nairain.huawei.settings.HostSettingsPageState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android 原语和原生入口验证；仅编译不代表已在宿主、LSPosed 或设备上执行。 */
@RunWith(AndroidJUnit4::class)
class HostSettingsNativeTest {

    @Test fun rejectsAmbiguousHiddenAndNonDirectGoalRows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val root = FrameLayout(context)
            val parent = LinearLayout(context).apply { id = 101 }
            val goal = View(context).apply { id = 102 }
            root.addView(parent); parent.addView(goal)
            assertSame(parent, HostSettingsTargets.selectContainer(root, 101, 102)?.first)
            assertSame(goal, HostSettingsTargets.selectContainer(root, 101, 102)?.second)
            assertNull(HostSettingsTargets.selectContainer(root, 0, 102))
            val duplicate = View(context).apply { id = 102 }
            parent.addView(duplicate)
            assertNull(HostSettingsTargets.selectContainer(root, 101, 102))
            parent.removeView(duplicate)
            goal.visibility = View.GONE
            assertNull(HostSettingsTargets.selectContainer(root, 101, 102))
            goal.visibility = View.VISIBLE
            parent.removeView(goal); root.addView(goal)
            assertNull(HostSettingsTargets.selectContainer(root, 101, 102))
        }
    }
    @Test fun pageRestoreKeepsNavigationAndScrollWithoutPreferenceSnapshots() {
        val page = HostSettingsPageState().apply { open = true; route = "services"; scroll["services"] = 240 }
        val restored = HostSettingsPageState().apply { restore(page.save()) }
        assertTrue(restored.open)
        assertEquals("services", restored.route)
        assertEquals(240, restored.scroll["services"])
        assertFalse(restored.save().containsKey("layout"))
        restored.restore(Bundle().apply { putString("route", "unknown") })
        assertEquals("home", restored.route)
    }

}
