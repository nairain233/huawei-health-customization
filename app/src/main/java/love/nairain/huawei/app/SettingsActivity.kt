package love.nairain.huawei.app

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import io.github.libxposed.service.XposedService
import love.nairain.huawei.R
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 模块设置界面：只管理配置，不承载 Hook 逻辑。
 */
class SettingsActivity : ComponentActivity(), ModuleApplication.ServiceStateListener {
    private var uiState by mutableStateOf(SettingsUiState())
    private var service: XposedService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MiuixTheme(colors = colors) {
                SettingsScreen(
                    state = uiState,
                    onSettingChange = ::updateSetting,
                    onOpenMineTrim = {
                        openCategory(SettingsCategory.MINE)
                    },
                    onOpenBottomTrim = {
                        openCategory(SettingsCategory.BOTTOM)
                    },
                    onOpenHealth = {
                        openCategory(SettingsCategory.HEALTH)
                    },
                    onOpenSport = {
                        openCategory(SettingsCategory.SPORT)
                    },
                    onOpenDevice = {
                        openCategory(SettingsCategory.DEVICE)
                    },
                    onOpenAbout = {
                        startActivity(AboutActivity.intent(this))
                    },
                    onRefreshStatus = { refresh(ModuleApplication.service) },
                    onClose = { finish() },
                )
            }
        }
        (application as ModuleApplication).addServiceStateListener(this)
    }

    override fun onDestroy() {
        (application as ModuleApplication).removeServiceStateListener(this)
        super.onDestroy()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        runOnUiThread { refresh(service) }
    }

    private fun refresh(boundService: XposedService?) {
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
            uiState = SettingsUiState(
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
        uiState = SettingsUiState(
            isServiceConnected = true,
            values = values,
        )
        values[SettingsKeys.HIDE_LAUNCHER_ICON]?.let { hidden ->
            try {
                setLauncherEnabled(!hidden)
            } catch (_: RuntimeException) {
                // 包管理器不可用不应阻断其他配置加载。
            }
        }
    }

    private fun updateSetting(key: String, checked: Boolean) {
        val boundService = service ?: return
        val previousValues = uiState.values
        val normalized = SettingsCatalog.normalizeWrite(key, checked, previousValues)
        uiState = uiState.copy(values = normalized)

        try {
            if (SettingsKeys.HIDE_LAUNCHER_ICON == key) {
                setLauncherEnabled(!checked)
            }
            boundService.getRemotePreferences(SettingsKeys.GROUP).edit {
                normalized.forEach { (settingKey, value) ->
                    if (previousValues[settingKey] != value) putBoolean(settingKey, value)
                }
            }
        } catch (_: RuntimeException) {
            if (SettingsKeys.HIDE_LAUNCHER_ICON == key) {
                try {
                    setLauncherEnabled(!(previousValues[key] ?: false))
                } catch (_: RuntimeException) {
                    // 回滚桌面入口失败时仍恢复页面状态。
                }
            }
            uiState = uiState.copy(values = previousValues)
        }
    }

    private fun openCategory(category: SettingsCategory) {
        startActivity(CategorySettingsActivity.intent(this, category))
    }

    private fun setLauncherEnabled(enabled: Boolean) {
        val launcher = ComponentName(this, "$packageName.LauncherActivity")
        packageManager.setComponentEnabledSetting(
            launcher,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
