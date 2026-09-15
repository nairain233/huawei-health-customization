package love.nairain.huawei

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.app.LayoutTrimActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 使用真实 Activity 回调覆盖控制器反复启动、停止与页面重建；不模拟 LSPosed 连接。 */
@RunWith(AndroidJUnit4::class)
class ScanLifecycleTest {
    @Test fun repeatedForegroundAndRecreationRemainResumed() {
        ActivityScenario.launch(LayoutTrimActivity::class.java).use { scenario ->
            repeat(5) {
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
            }
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
