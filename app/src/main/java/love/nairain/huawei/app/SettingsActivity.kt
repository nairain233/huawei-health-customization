package love.nairain.huawei.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 模块设置界面：只管理配置，不承载 Hook 逻辑。
 */
class SettingsActivity : AppCompatActivity() {
    private var uiState by mutableStateOf(SettingsUiState())
    private lateinit var coordinator: LayoutSettingsCoordinator
    private val settingsListener = LayoutSettingsListener { newState -> uiState = newState }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val moduleApplication = application as ModuleApplication
        coordinator = moduleApplication.layoutSettingsCoordinator
        setContent {
            HuaweiTrimTheme(
                colorMode = moduleApplication.colorMode,
                window = window,
            ) {
                SettingsScreen(
                    state = uiState,
                    onSettingChange = ::updateSetting,
                    onOpenThemeSettings = {
                        startActivity(ThemeSettingsActivity.intent(this))
                    },
                    onOpenLayoutTrim = {
                        startActivity(LayoutTrimActivity.intent(this))
                    },
                    onOpenAbout = {
                        startActivity(AboutActivity.intent(this))
                    },
                    onOpenServiceBlock = {
                        startActivity(android.content.Intent(this, ServiceBlockActivity::class.java))
                    },
                    onRefreshStatus = coordinator::refresh,
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        coordinator.addListener(settingsListener)
    }

    override fun onStop() {
        coordinator.removeListener(settingsListener)
        super.onStop()
    }

    private fun updateSetting(key: String, checked: Boolean) {
        coordinator.save(key, checked)
    }
}
