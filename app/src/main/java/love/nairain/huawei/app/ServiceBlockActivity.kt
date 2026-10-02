package love.nairain.huawei.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 页面只展示已确认配置，远程访问与保存状态由应用级协调器管理。 */
class ServiceBlockActivity : AppCompatActivity() {
    private var state by mutableStateOf(ServiceBlockUiState())
    private val coordinator: ServiceSettingsCoordinator
        get() = (application as ModuleApplication).serviceSettingsCoordinator
    private val settingsListener = ServiceSettingsListener { state = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ModuleApplication
        setContent {
            HuaweiTrimTheme(colorMode = app.colorMode, window = window) {
                ServiceBlockScreen(state, { coordinator.save(it) }, coordinator::refresh, ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        coordinator.addListener(settingsListener)
    }

    override fun onResume() {
        super.onResume()
        coordinator.refresh()
    }

    override fun onStop() {
        coordinator.removeListener(settingsListener)
        super.onStop()
    }
}
