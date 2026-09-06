package love.nairain.huawei.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import love.nairain.huawei.R
import love.nairain.huawei.config.SettingsCategory
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 汇总五类布局精简入口，不承载具体配置读写。 */
class LayoutTrimActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val moduleApplication = application as ModuleApplication
        setContent {
            HuaweiTrimTheme(
                colorMode = moduleApplication.colorMode,
                window = window,
            ) {
                LayoutTrimScreen(
                    onOpenBottomTrim = { openCategory(SettingsCategory.BOTTOM) },
                    onOpenHealth = { openCategory(SettingsCategory.HEALTH) },
                    onOpenSport = { openCategory(SettingsCategory.SPORT) },
                    onOpenDevice = { openCategory(SettingsCategory.DEVICE) },
                    onOpenMineTrim = { openCategory(SettingsCategory.MINE) },
                    onClose = ::finish,
                )
            }
        }
    }

    private fun openCategory(category: SettingsCategory) {
        startActivity(CategorySettingsActivity.intent(this, category))
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, LayoutTrimActivity::class.java)
    }
}

@Composable
internal fun LayoutTrimScreen(
    onOpenBottomTrim: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenSport: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenMineTrim: () -> Unit,
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
                title = stringResource(R.string.settings_layout_trim_title),
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
                .testTag("settings:layout-trim")
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
            item(key = "layout_trim_categories") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_bottom_nav_title),
                        onClick = onOpenBottomTrim,
                        modifier = Modifier.testTag("layout-trim:bottom-nav"),
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_health_nav_title),
                        onClick = onOpenHealth,
                        modifier = Modifier.testTag("layout-trim:health-nav"),
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_sport_nav_title),
                        onClick = onOpenSport,
                        modifier = Modifier.testTag("layout-trim:sport-nav"),
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_device_nav_title),
                        onClick = onOpenDevice,
                        modifier = Modifier.testTag("layout-trim:device-nav"),
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_mine_nav_title),
                        onClick = onOpenMineTrim,
                        modifier = Modifier.testTag("layout-trim:mine-nav"),
                    )
                }
            }
            item(key = "navigation_bar_spacer") {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}
