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
    private var scopeState by mutableStateOf(ScopeUiState())
    private lateinit var coordinator: LayoutSettingsCoordinator
    private lateinit var scopeCoordinator: ScopeSettingsCoordinator
    private val settingsListener = LayoutSettingsListener { newState -> uiState = newState }
    private val scopeListener = ScopeSettingsListener { newState -> scopeState = newState }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val moduleApplication = application as ModuleApplication
        coordinator = moduleApplication.layoutSettingsCoordinator
        scopeCoordinator = moduleApplication.scopeSettingsCoordinator
        setContent {
            HuaweiTrimTheme(
                colorMode = moduleApplication.colorMode,
                window = window,
            ) {
                SettingsScreen(
                    state = uiState,
                    scopeState = scopeState,
                    onSettingChange = ::updateSetting,
                    onScopeChange = ::updateScope,
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
                    onRefreshStatus = {
                        coordinator.refresh()
                        scopeCoordinator.refresh()
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        coordinator.addListener(settingsListener)
        scopeCoordinator.addListener(scopeListener)
    }

    override fun onStop() {
        coordinator.removeListener(settingsListener)
        scopeCoordinator.removeListener(scopeListener)
        super.onStop()
    }

    private fun updateSetting(key: String, checked: Boolean) {
        coordinator.save(key, checked)
    }

    private fun updateScope(granted: Boolean) {
        scopeCoordinator.setGranted(granted)
    }
}
