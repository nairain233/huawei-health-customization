package love.nairain.huawei.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import love.nairain.huawei.R
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
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

internal enum class StatusSeverity {
    Info,
    Error,
}

@Composable
private fun MiuixServiceStatusCard(
    state: SettingsUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state.isServiceConnected
    val configurationReady = connected && state.isConfigAvailable
    val darkTheme = LocalAppDarkTheme.current
    val dynamicColor = MiuixTheme.isDynamicColor
    val container = when {
        configurationReady && dynamicColor -> MiuixTheme.colorScheme.secondaryContainer
        configurationReady && darkTheme -> Color(0xFF1A3825)
        configurationReady -> Color(0xFFDFFAE4)
        dynamicColor -> MiuixTheme.colorScheme.errorContainer
        darkTheme -> Color(0xFF3A1E22)
        else -> Color(0xFFFFE4E1)
    }
    val iconTint = when {
        configurationReady && dynamicColor -> MiuixTheme.colorScheme.primary
        configurationReady -> Color(0xFF36D167)
        dynamicColor -> MiuixTheme.colorScheme.error
        darkTheme -> Color(0xFFFF8A80)
        else -> Color(0xFFD32F2F)
    }
    val contentColor = if (dynamicColor) {
        if (configurationReady) {
            MiuixTheme.colorScheme.onSecondaryContainer
        } else {
            MiuixTheme.colorScheme.onErrorContainer
        }
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings:service-status"),
            colors = CardDefaults.defaultColors(
                color = container,
                contentColor = contentColor,
            ),
            onClick = onRefresh,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Tilt,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(27.dp, 31.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(
                        modifier = Modifier.size(110.dp),
                        imageVector = if (configurationReady) {
                            Icons.Rounded.CheckCircleOutline
                        } else {
                            Icons.Rounded.ErrorOutline
                        },
                        tint = iconTint,
                        contentDescription = null,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp, 14.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(
                                if (connected) R.string.settings_status_connected
                                else R.string.settings_status_disconnected,
                            ),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                        Text(
                            text = stringResource(
                                when {
                                    !connected -> R.string.settings_status_disconnected_summary
                                    state.isLoading -> R.string.settings_status_loading
                                    state.isConfigAvailable -> R.string.settings_status_connected_summary
                                    else -> R.string.settings_status_connected_unavailable_summary
                                },
                            ),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralSettingsCard(
    enabled: Boolean,
    configurationEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        SwitchPreference(
            title = stringResource(R.string.settings_enabled),
            checked = enabled,
            enabled = configurationEnabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.testTag("setting:${SettingsKeys.ENABLED}"),
        )
    }
}

@Composable
private fun NavigationCard(
    title: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        ArrowPreference(
            title = title,
            onClick = onClick,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
internal fun SettingsNoticeCard(
    notice: SettingsNotice,
    message: String,
    modifier: Modifier = Modifier,
) {
    val severity = notice.severity()
    val accent = when (severity) {
        StatusSeverity.Info -> MiuixTheme.colorScheme.primary
        StatusSeverity.Error -> MiuixTheme.colorScheme.error
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings:notice:${notice.kind.name.lowercase()}"),
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(
            color = accent.copy(alpha = 0.2f),
            contentColor = accent,
        ),
    ) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

internal fun SettingsNotice.severity(): StatusSeverity = when (kind) {
    SettingsNoticeKind.LOADING,
    SettingsNoticeKind.SAVING,
    SettingsNoticeKind.RESTART_REQUIRED,
    -> StatusSeverity.Info
    SettingsNoticeKind.DEFAULTS_NOT_PERSISTED,
    SettingsNoticeKind.SAVE_FAILED,
    SettingsNoticeKind.STATE_UNCERTAIN,
    SettingsNoticeKind.CONFIG_UNAVAILABLE,
    -> StatusSeverity.Error
}

@Composable
internal fun SettingsNotice.message(): String = stringResource(
    when (kind) {
        SettingsNoticeKind.LOADING -> R.string.settings_status_loading
        SettingsNoticeKind.SAVING -> R.string.settings_status_saving
        SettingsNoticeKind.RESTART_REQUIRED -> R.string.settings_restart_notice
        SettingsNoticeKind.DEFAULTS_NOT_PERSISTED -> R.string.settings_status_defaults_not_persisted
        SettingsNoticeKind.SAVE_FAILED -> R.string.settings_status_save_error
        SettingsNoticeKind.STATE_UNCERTAIN -> R.string.settings_status_state_uncertain
        SettingsNoticeKind.CONFIG_UNAVAILABLE -> R.string.settings_status_config_error
    },
)

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onSettingChange: (String, Boolean) -> Unit,
    onOpenThemeSettings: () -> Unit = {},
    onOpenLayoutTrim: () -> Unit = {},
    onOpenServiceBlock: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onRefreshStatus: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val horizontalSafeInsets = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Horizontal)
        .asPaddingValues()
    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = scrollBehavior,
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings:list")
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = lazyListState,
            contentPadding = PaddingValues(
                start = horizontalSafeInsets.calculateLeftPadding(layoutDirection),
                top = paddingValues.calculateTopPadding() + 8.dp,
                end = horizontalSafeInsets.calculateRightPadding(layoutDirection),
                bottom = paddingValues.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item(key = "settings_status") {
                MiuixServiceStatusCard(
                    state = state,
                    onRefresh = onRefreshStatus,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }
            item(key = "general_settings") {
                GeneralSettingsCard(
                    enabled = state.valueOf(SettingsKeys.ENABLED),
                    configurationEnabled = state.writable,
                    onEnabledChange = { onSettingChange(SettingsKeys.ENABLED, it) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }
            item(key = "theme_settings_navigation") {
                NavigationCard(
                    title = stringResource(R.string.settings_theme_title),
                    testTag = "settings:theme-nav",
                    onClick = onOpenThemeSettings,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }
            item(key = "layout_trim_navigation") {
                NavigationCard(
                    title = stringResource(R.string.settings_layout_trim_title),
                    testTag = "settings:layout-trim-nav",
                    onClick = onOpenLayoutTrim,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }
            item(key = "service_block_navigation") {
                NavigationCard(
                    title = stringResource(R.string.service_block_title),
                    testTag = "settings:service-block-nav",
                    onClick = onOpenServiceBlock,
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                )
            }
            item(key = "about_navigation") {
                NavigationCard(
                    title = stringResource(R.string.about_title),
                    testTag = "settings:about-nav",
                    onClick = onOpenAbout,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }
            val notice = state.notice
            if (notice != null) {
                item(key = "settings_status_message") {
                    SettingsNoticeCard(
                        notice = notice,
                        message = notice.message(),
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                    )
                }
            }
            item(key = "navigation_bar_spacer") {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}
