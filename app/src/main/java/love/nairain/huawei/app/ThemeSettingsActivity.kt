package love.nairain.huawei.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 管理仅影响模块应用自身的主题与桌面入口设置。 */
class ThemeSettingsActivity : AppCompatActivity() {
    private var hideLauncherIcon by mutableStateOf(false)
    private var statusMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshLauncherIconState()
        val moduleApplication = application as ModuleApplication
        setContent {
            HuaweiTrimTheme(
                colorMode = moduleApplication.colorMode,
                window = window,
            ) {
                ThemeSettingsScreen(
                    colorMode = moduleApplication.colorMode,
                    appLanguage = AppLanguage.fromLocaleList(
                        AppCompatDelegate.getApplicationLocales(),
                    ),
                    hideLauncherIcon = hideLauncherIcon,
                    statusMessage = statusMessage,
                    onColorModeChange = moduleApplication::updateColorMode,
                    onLanguageChange = { language ->
                        AppCompatDelegate.setApplicationLocales(language.toLocaleList())
                    },
                    onHideLauncherIconChange = ::updateLauncherIcon,
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshLauncherIconState()
    }

    private fun updateLauncherIcon(hidden: Boolean) {
        if (hideLauncherIcon == hidden) return
        try {
            packageManager.setComponentEnabledSetting(
                launcherComponent,
                if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            hideLauncherIcon = hidden
            statusMessage = null
        } catch (_: RuntimeException) {
            statusMessage = getString(R.string.settings_launcher_icon_save_error)
        }
    }

    private fun refreshLauncherIconState() {
        try {
            hideLauncherIcon = when (packageManager.getComponentEnabledSetting(launcherComponent)) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
                -> true

                else -> false
            }
        } catch (_: RuntimeException) {
            // 保留当前页面状态，包管理器异常不影响色彩模式。
        }
    }

    private val launcherComponent: ComponentName
        get() = ComponentName(this, "$packageName.LauncherActivity")

    companion object {
        fun intent(context: Context): Intent = Intent(context, ThemeSettingsActivity::class.java)
    }
}

@Composable
internal fun ThemeSettingsScreen(
    colorMode: AppColorMode,
    appLanguage: AppLanguage,
    hideLauncherIcon: Boolean,
    statusMessage: String? = null,
    onColorModeChange: (AppColorMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onHideLauncherIconChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val modes = AppColorMode.entries
    val labels = modes.map { stringResource(it.label) }
    val languages = AppLanguage.entries
    val languageLabels = languages.map { stringResource(it.label) }
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = scrollBehavior,
                title = stringResource(R.string.settings_theme_title),
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
                .testTag("settings:theme")
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
            item(key = "theme_settings") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("settings:theme-card"),
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
                    OverlayDropdownPreference(
                        items = languageLabels,
                        selectedIndex = languages.indexOf(appLanguage),
                        title = stringResource(R.string.settings_language),
                        modifier = Modifier.testTag("settings:language"),
                        onSelectedIndexChange = { index ->
                            languages.getOrNull(index)?.let(onLanguageChange)
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_hide_launcher_icon),
                        checked = hideLauncherIcon,
                        onCheckedChange = onHideLauncherIconChange,
                        modifier = Modifier.testTag(
                            "setting:${SettingsKeys.HIDE_LAUNCHER_ICON}",
                        ),
                    )
                }
            }
            statusMessage?.let { message ->
                item(key = "theme_status") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        insideMargin = PaddingValues(16.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.error.copy(alpha = 0.2f),
                            contentColor = MiuixTheme.colorScheme.error,
                        ),
                    ) {
                        Text(
                            text = message,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }
            item(key = "navigation_bar_spacer") {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}
