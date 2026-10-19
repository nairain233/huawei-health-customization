package love.nairain.huawei

import android.app.Dialog
import android.view.View
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import love.nairain.huawei.settings.HostComposeOwner
import love.nairain.huawei.settings.HostSettingsComposeHost
import love.nairain.huawei.settings.HostSettingsHomeModel
import love.nairain.huawei.settings.HostSettingsNotice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** 使用普通 Activity 和平台 Dialog，验证不借用 ComponentActivity 的 owner；宿主注入仍须单独验证。 */
@RunWith(AndroidJUnit4::class)
class HostSettingsComposeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

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

    @Test fun homeUsesConfirmedStateAndKeepsNavigationAvailableWhileSaving() {
        var requested: Boolean? = null
        var route: String? = null
        withHome(onEnabled = { requested = it }, onNavigate = { route = it }) { home ->
            compose.onNode(isToggleable()).assertIsOff().performClick()
            compose.runOnIdle { assertEquals(true, requested) }
            compose.onNode(isToggleable()).assertIsOff()
            compose.runOnIdle { home.update(HostSettingsHomeModel(writable = false, notice = HostSettingsNotice.SAVING)) }
            compose.onNode(isToggleable()).assertIsNotEnabled().assertIsOff()
            val title = instrumentation.targetContext.getString(R.string.settings_layout_trim_title)
            compose.onNodeWithText(title).performClick()
            compose.runOnIdle { assertEquals("layout", route) }
            compose.runOnIdle { home.update(HostSettingsHomeModel(enabled = true, writable = true, notice = HostSettingsNotice.SAVED)) }
            compose.onNode(isToggleable()).assertIsOn()
            compose.runOnIdle {
                home.pause()
                assertEquals(Lifecycle.State.CREATED, home.view.findViewTreeLifecycleOwner()?.lifecycle?.currentState)
                home.resume()
                assertEquals(Lifecycle.State.RESUMED, home.view.findViewTreeLifecycleOwner()?.lifecycle?.currentState)
            }
        }
    }

    @Test fun lateLayoutFailureIsReportedWithoutEscapingViewTraversal() {
        val failure = AtomicReference<Throwable>()
        withHome(onFailure = failure::set) { home ->
            compose.runOnIdle {
                home.view.addView(object : View(home.view.context) {
                    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                        throw IllegalStateException("fixture")
                    }
                })
                val spec = View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
                home.view.measure(spec, spec)
            }
            instrumentation.waitForIdleSync()
            assertTrue(failure.get() is IllegalStateException)
        }
    }

    private fun withHome(
        onEnabled: (Boolean) -> Unit = {},
        onNavigate: (String) -> Unit = {},
        onFailure: (Throwable) -> Unit = { throw AssertionError(it) },
        check: (HostSettingsComposeHost) -> Unit,
    ) {
        ActivityScenario.launch(TestHostActivity::class.java).use { scenario ->
            lateinit var home: HostSettingsComposeHost
            lateinit var dialog: Dialog
            scenario.onActivity { activity ->
                home = HostSettingsComposeHost(activity, activity.resources, 0,
                    HostSettingsHomeModel(writable = true, notice = HostSettingsNotice.READY),
                    onEnabled, onNavigate, {}, onFailure)
                dialog = Dialog(activity).apply { setContentView(home.view); show() }
                home.resume()
            }
            try { check(home) }
            finally {
                scenario.onActivity {
                    home.close()
                    home.close()
                    dialog.dismiss()
                    assertEquals(Lifecycle.State.DESTROYED, home.view.findViewTreeLifecycleOwner()?.lifecycle?.currentState)
                }
            }
        }
    }
}
