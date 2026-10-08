package love.nairain.huawei.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 实验性功能使用已有配置协调器，页面只管理开关与操作状态。 */
class ExperimentalFeaturesActivity : AppCompatActivity() {
    private var uiState by mutableStateOf(SettingsUiState())
    private lateinit var coordinator: LayoutSettingsCoordinator
    private val settingsListener = LayoutSettingsListener { newState -> uiState = newState }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val moduleApplication = application as ModuleApplication
        coordinator = moduleApplication.layoutSettingsCoordinator
        setContent {
            HuaweiTrimTheme(colorMode = moduleApplication.colorMode, window = window) {
                ExperimentalFeaturesScreen(
                    state = uiState,
                    onSettingChange = { key, checked -> coordinator.save(key, checked) },
                    onClose = ::finish,
                    onOpenServiceBlock = {
                        startActivity(Intent(this, ServiceBlockActivity::class.java))
                    },
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

    companion object {
        fun intent(context: Context): Intent = Intent(context, ExperimentalFeaturesActivity::class.java)
    }
}

@Composable
internal fun ExperimentalFeaturesScreen(
    state: SettingsUiState,
    onSettingChange: (String, Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenServiceBlock: () -> Unit = {},
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
                title = stringResource(R.string.experimental_features_title),
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
                .testTag("settings:experimental")
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
                state.notice?.let { notice ->
                    SettingsNoticeCard(
                        notice = notice,
                        message = notice.message(),
                        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    )
                }
            }
            item(key = "watchface_import") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.watchface_import_title),
                        checked = state.valueOf(SettingsKeys.LOCAL_WATCH_FACE),
                        enabled = state.writable,
                        onCheckedChange = { onSettingChange(SettingsKeys.LOCAL_WATCH_FACE, it) },
                        modifier = Modifier.testTag("setting:${SettingsKeys.LOCAL_WATCH_FACE}"),
                    )
                }
            }
            item(key = "service_block_navigation") {
                NavigationCard(
                    title = stringResource(R.string.service_block_title),
                    testTag = "settings:service-block-nav",
                    onClick = onOpenServiceBlock,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item(key = "navigation_bar_spacer") {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}
