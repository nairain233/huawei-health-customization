package love.nairain.huawei.settings

import android.content.res.Resources
import androidx.compose.runtime.Composable
import love.nairain.huawei.R
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun HostSettingsHome(model: HostSettingsUiState, strings: Resources, action: (HostSettingsAction) -> Unit) {
    SettingsCard {
        LayoutSwitch(SettingsKeys.ENABLED, strings.getString(R.string.settings_enabled), model, action)
        SettingsCategory.entries.forEach { category ->
            ArrowPreference(title = strings.getString(category.title), onClick = { action(HostSettingsAction.Navigate(category.name)) })
        }
    }
    SettingsCard {
        ArrowPreference(title = strings.getString(R.string.scan_title), onClick = { action(HostSettingsAction.Navigate("scan")) })
        ArrowPreference(title = strings.getString(R.string.experimental_features_title), onClick = { action(HostSettingsAction.Navigate("experimental")) })
    }
    SettingsCard {
        ArrowPreference(title = strings.getString(R.string.about_title), onClick = { action(HostSettingsAction.Navigate("about")) })
    }
}
