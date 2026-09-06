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
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

internal data class SettingsUiState(
    val isServiceConnected: Boolean = false,
    val values: Map<String, Boolean> = emptyMap(),
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
) {
    fun valueOf(key: String): Boolean = values[key] ?: false
}

private enum class StatusSeverity {
    Info,
    Error,
}

@Composable
private fun MiuixServiceStatusCard(
    connected: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalAppDarkTheme.current
    val dynamicColor = MiuixTheme.isDynamicColor
    val container = when {
        connected && dynamicColor -> MiuixTheme.colorScheme.secondaryContainer
        connected && darkTheme -> Color(0xFF1A3825)
        connected -> Color(0xFFDFFAE4)
        dynamicColor -> MiuixTheme.colorScheme.errorContainer
        darkTheme -> Color(0xFF3A1E22)
        else -> Color(0xFFFFE4E1)
    }
    val iconTint = when {
        connected && dynamicColor -> MiuixTheme.colorScheme.primary
        connected -> Color(0xFF36D167)
        dynamicColor -> MiuixTheme.colorScheme.error
        darkTheme -> Color(0xFFFF8A80)
        else -> Color(0xFFD32F2F)
    }
    val contentColor = if (dynamicColor) {
        if (connected) {
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
                        imageVector = if (connected) {
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
                                if (connected) R.string.settings_status_connected_summary
                                else R.string.settings_status_disconnected_summary,
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
private fun ColorModeCard(
    colorMode: AppColorMode,
    onColorModeChange: (AppColorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = AppColorMode.entries
    val labels = modes.map { stringResource(it.label) }

    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        OverlayDropdownPreference(
            items = labels,
            selectedIndex = modes.indexOf(colorMode),
            title = stringResource(R.string.settings_color_mode),
            modifier = Modifier.testTag("settings:color-mode"),
            onSelectedIndexChange = { index ->
                modes.getOrNull(index)?.let(onColorModeChange)
            },
        )
    }
}

@Composable
private fun GeneralSettingsCard(
    enabled: Boolean,
    hideLauncherIcon: Boolean,
    configurationEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onHideLauncherIconChange: (Boolean) -> Unit,
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
        SwitchPreference(
            title = stringResource(R.string.settings_hide_launcher_icon),
            checked = hideLauncherIcon,
            enabled = configurationEnabled,
            onCheckedChange = onHideLauncherIconChange,
            modifier = Modifier.testTag("setting:${SettingsKeys.HIDE_LAUNCHER_ICON}"),
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
private fun StatusCard(
    message: String,
    severity: StatusSeverity,
    modifier: Modifier = Modifier,
) {
    val accent = when (severity) {
        StatusSeverity.Info -> MiuixTheme.colorScheme.primary
        StatusSeverity.Error -> MiuixTheme.colorScheme.error
    }
    Card(
        modifier = modifier.fillMaxWidth(),
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

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    colorMode: AppColorMode,
    onSettingChange: (String, Boolean) -> Unit,
    onColorModeChange: (AppColorMode) -> Unit,
    onOpenLayoutTrim: () -> Unit = {},
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
                    connected = state.isServiceConnected,
                    onRefresh = onRefreshStatus,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }
            item(key = "general_settings") {
                GeneralSettingsCard(
                    enabled = state.valueOf(SettingsKeys.ENABLED),
                    hideLauncherIcon = state.valueOf(SettingsKeys.HIDE_LAUNCHER_ICON),
                    configurationEnabled = state.isServiceConnected,
                    onEnabledChange = { onSettingChange(SettingsKeys.ENABLED, it) },
                    onHideLauncherIconChange = {
                        onSettingChange(SettingsKeys.HIDE_LAUNCHER_ICON, it)
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }
            item(key = "color_mode") {
                ColorModeCard(
                    colorMode = colorMode,
                    onColorModeChange = onColorModeChange,
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
            val statusMessage = state.statusMessage
            if (statusMessage != null) {
                item(key = "settings_status_message") {
                    StatusCard(
                        message = statusMessage,
                        severity = if (state.statusIsError) {
                            StatusSeverity.Error
                        } else {
                            StatusSeverity.Info
                        },
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
