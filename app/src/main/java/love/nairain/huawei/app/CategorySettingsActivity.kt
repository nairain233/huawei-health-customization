package love.nairain.huawei.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import io.github.libxposed.service.XposedService
import love.nairain.huawei.R
import love.nairain.huawei.config.SettingDefinition
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 五类精简项共用的配置页与读写逻辑。 */
class CategorySettingsActivity : ComponentActivity(), ModuleApplication.ServiceStateListener {
    private var uiState by mutableStateOf(SettingsUiState())
    private var service: XposedService? = null
    private lateinit var category: SettingsCategory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = runCatching {
            SettingsCategory.valueOf(intent.getStringExtra(EXTRA_CATEGORY).orEmpty())
        }.getOrDefault(SettingsCategory.HEALTH)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MiuixTheme(colors = colors) {
                CategorySettingsScreen(
                    category = category,
                    settings = SettingsCatalog.settingsFor(category),
                    state = uiState,
                    onSettingChange = ::updateSetting,
                    onClose = ::finish,
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
            uiState = SettingsUiState(isServiceConnected = false)
            return
        }
        val preferences = runCatching {
            boundService.getRemotePreferences(SettingsKeys.GROUP)
        }.getOrNull()
        if (preferences == null) {
            uiState = SettingsUiState(
                statusMessage = getString(R.string.settings_status_config_error),
                statusIsError = true,
            )
            return
        }
        runCatching { SettingsCatalog.ensureDefaults(preferences) }
        uiState = SettingsUiState(
            isServiceConnected = true,
            values = SettingsCatalog.read(preferences),
        )
    }

    private fun updateSetting(key: String, checked: Boolean) {
        val boundService = service ?: return
        val previous = uiState.values
        val normalized = SettingsCatalog.normalizeWrite(key, checked, previous)
        uiState = uiState.copy(values = normalized, statusMessage = null)
        try {
            boundService.getRemotePreferences(SettingsKeys.GROUP).edit {
                normalized.forEach { (settingKey, value) ->
                    if (previous[settingKey] != value) putBoolean(settingKey, value)
                }
            }
        } catch (_: RuntimeException) {
            uiState = uiState.copy(
                values = previous,
                statusMessage = getString(R.string.settings_status_save_error),
                statusIsError = true,
            )
        }
    }

    companion object {
        private const val EXTRA_CATEGORY = "settings_category"

        fun intent(context: Context, category: SettingsCategory): Intent =
            Intent(context, CategorySettingsActivity::class.java)
                .putExtra(EXTRA_CATEGORY, category.name)
    }
}

@Composable
internal fun CategorySettingsScreen(
    category: SettingsCategory,
    settings: List<SettingDefinition>,
    state: SettingsUiState,
    onSettingChange: (String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = scrollBehavior,
                title = stringResource(category.title),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings:${category.name.lowercase()}")
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                start = safeInsets.calculateLeftPadding(layoutDirection),
                top = padding.calculateTopPadding() + 8.dp,
                end = safeInsets.calculateRightPadding(layoutDirection),
                bottom = padding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item(key = "notice") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    insideMargin = PaddingValues(16.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.primary.copy(alpha = 0.2f),
                        contentColor = MiuixTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.settings_restart_notice),
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
            item(key = "settings") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    settings.forEach { setting ->
                        SwitchPreference(
                            title = stringResource(setting.title),
                            checked = state.valueOf(setting.key),
                            enabled = state.isServiceConnected && state.valueOf(SettingsKeys.ENABLED),
                            onCheckedChange = { onSettingChange(setting.key, it) },
                            modifier = Modifier.testTag("setting:${setting.key}"),
                        )
                    }
                }
            }
            state.statusMessage?.let { message ->
                item(key = "status") {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Text(message, color = MiuixTheme.colorScheme.error)
                    }
                }
            }
            item(key = "navigation_bar_spacer") {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}
