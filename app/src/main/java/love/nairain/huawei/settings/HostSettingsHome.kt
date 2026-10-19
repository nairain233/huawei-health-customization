package love.nairain.huawei.settings

import android.content.res.Resources
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import love.nairain.huawei.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 仅展示控制器确认值；保存期间不在 UI 内先行翻转开关。 */
internal data class HostSettingsHomeModel(
    val enabled: Boolean = false,
    val writable: Boolean = false,
    val notice: HostSettingsNotice = HostSettingsNotice.LOADING,
    val watchFaceBusy: Boolean = false,
)

/** 标题与系统栏仍由外层宿主 Dialog 管理，本 Scaffold 只管理内容与后续 Overlay 宿主。 */
@Composable
internal fun HostSettingsHome(
    model: HostSettingsHomeModel,
    strings: Resources,
    scroll: ScrollState,
    onEnabledChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(controller = controller) {
        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .verticalScroll(scroll).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val notice = when (model.notice) {
                    HostSettingsNotice.LOADING -> R.string.host_ui_loading
                    HostSettingsNotice.SAVING -> R.string.host_ui_saving
                    HostSettingsNotice.SAVED -> R.string.host_ui_saved
                    HostSettingsNotice.SCHEDULED -> R.string.host_ui_scheduled
                    HostSettingsNotice.FAILED -> R.string.host_ui_save_failed
                    HostSettingsNotice.UNAVAILABLE -> R.string.host_ui_unavailable
                    HostSettingsNotice.UNCERTAIN -> R.string.host_ui_uncertain
                    HostSettingsNotice.READY -> null
                }
                if (notice != null) {
                    Text(
                        text = strings.getString(notice),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (model.notice in setOf(HostSettingsNotice.UNAVAILABLE, HostSettingsNotice.UNCERTAIN, HostSettingsNotice.FAILED)) {
                    Card(Modifier.fillMaxWidth()) {
                        ArrowPreference(title = strings.getString(R.string.host_settings_retry), onClick = onRetry)
                    }
                }
                if (model.watchFaceBusy) {
                    Text(
                        text = strings.getString(R.string.host_restart_watchface_busy),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Card(Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = strings.getString(R.string.settings_enabled),
                        checked = model.enabled,
                        enabled = model.writable,
                        onCheckedChange = onEnabledChange,
                    )
                }
                Card(Modifier.fillMaxWidth()) {
                    ArrowPreference(title = strings.getString(R.string.settings_layout_trim_title), onClick = { onNavigate("layout") })
                    ArrowPreference(title = strings.getString(R.string.experimental_features_title), onClick = { onNavigate("experimental") })
                }
                Card(Modifier.fillMaxWidth()) {
                    ArrowPreference(title = strings.getString(R.string.scan_title), onClick = { onNavigate("scan") })
                    ArrowPreference(title = strings.getString(R.string.about_title), onClick = { onNavigate("about") })
                }
            }
        }
    }
}
