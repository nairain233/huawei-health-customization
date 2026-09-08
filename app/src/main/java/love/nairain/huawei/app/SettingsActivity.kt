package love.nairain.huawei.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import io.github.libxposed.service.XposedService
import love.nairain.huawei.R
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsKeys

/**
 * 模块设置界面：只管理配置，不承载 Hook 逻辑。
 */
class SettingsActivity : ComponentActivity(), ModuleApplication.ServiceStateListener {
    private var uiState by mutableStateOf(SettingsUiState())
    private var service: XposedService? = null
    private val scanController by lazy { ScanStatusController(this) { uiState = uiState.copy(scan = it) } }

    override fun onStart() { super.onStart(); scanController.start() }
    override fun onStop() { scanController.stop(); super.onStop() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val moduleApplication = application as ModuleApplication
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
                    onRefreshStatus = { refresh(ModuleApplication.service) },
                    onClose = { finish() },
                    onRescan = { scanController.request() },
                )
            }
        }
        moduleApplication.addServiceStateListener(this)
    }

    override fun onDestroy() {
        (application as ModuleApplication).removeServiceStateListener(this)
        super.onDestroy()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        runOnUiThread { refresh(service) }
    }

    private fun refresh(boundService: XposedService?) {
        scanController.bind(boundService)
        service = boundService
        if (boundService == null) {
            uiState = uiState.copy(isServiceConnected = false)
            return
        }

        val preferences = try {
            boundService.getRemotePreferences(SettingsKeys.GROUP)
        } catch (_: RuntimeException) {
            null
        }
        if (preferences == null) {
            uiState = uiState.copy(
                isServiceConnected = false,
                statusMessage = getString(R.string.settings_status_config_error),
                statusIsError = true,
            )
            return
        }
        try {
            SettingsCatalog.ensureDefaults(preferences)
        } catch (_: RuntimeException) {
            // 默认值补齐失败不应阻断已经可读的配置。
        }
        val values = SettingsCatalog.read(preferences)
        uiState = uiState.copy(
            isServiceConnected = true,
            values = values,
            statusMessage = null,
            statusIsError = false,
        )
    }

    private fun updateSetting(key: String, checked: Boolean) {
        val boundService = service ?: return
        val previousValues = uiState.values
        val normalized = SettingsCatalog.normalizeWrite(key, checked, previousValues)
        uiState = uiState.copy(values = normalized)

        try {
            boundService.getRemotePreferences(SettingsKeys.GROUP).edit {
                normalized.forEach { (settingKey, value) ->
                    if (previousValues[settingKey] != value) putBoolean(settingKey, value)
                }
            }
        } catch (_: RuntimeException) {
            uiState = uiState.copy(values = previousValues)
        }
    }

}
