package love.nairain.huawei.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
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
import love.nairain.huawei.config.SettingGroup
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 五类精简项共用的配置页与读写逻辑。 */
class CategorySettingsActivity : AppCompatActivity() {
    private var uiState by mutableStateOf(SettingsUiState())
    private lateinit var coordinator: LayoutSettingsCoordinator
    private val settingsListener = LayoutSettingsListener { newState -> uiState = newState }
    private lateinit var category: SettingsCategory
    private var sportQuickEntriesPage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = runCatching {
            SettingsCategory.valueOf(intent.getStringExtra(EXTRA_CATEGORY).orEmpty())
        }.getOrDefault(SettingsCategory.HEALTH)
        sportQuickEntriesPage = category == SettingsCategory.SPORT &&
            intent.getBooleanExtra(EXTRA_SPORT_QUICK_ENTRIES, false)
        enableEdgeToEdge()
        val moduleApplication = application as ModuleApplication
        coordinator = moduleApplication.layoutSettingsCoordinator
        setContent {
            HuaweiTrimTheme(
                colorMode = moduleApplication.colorMode,
                window = window,
            ) {
                CategorySettingsScreen(
                    category = category,
                    title = if (sportQuickEntriesPage) R.string.settings_sport_quick_entry_items else category.title,
                    groups = if (sportQuickEntriesPage) SettingsCatalog.sportQuickEntryGroups
                        else SettingsCatalog.groupsFor(category),
                    state = uiState,
                    onSettingChange = ::updateSetting,
                    sportQuickEntriesPage = sportQuickEntriesPage,
                    onOpenSportQuickEntries = {
                        if (!uiState.valueOf(SettingsKeys.SPORT_QUICK_ENTRIES)) {
                            startActivity(sportQuickEntriesIntent(this))
                        }
                    },
                    onClose = ::finish,
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

    private fun updateSetting(key: String, checked: Boolean) {
        coordinator.save(key, checked)
    }

    companion object {
        private const val EXTRA_CATEGORY = "settings_category"
        private const val EXTRA_SPORT_QUICK_ENTRIES = "sport_quick_entries"

        fun intent(context: Context, category: SettingsCategory): Intent =
            Intent(context, CategorySettingsActivity::class.java)
                .putExtra(EXTRA_CATEGORY, category.name)

        fun sportQuickEntriesIntent(context: Context): Intent =
            intent(context, SettingsCategory.SPORT)
                .putExtra(EXTRA_SPORT_QUICK_ENTRIES, true)
    }
}

@Composable
internal fun CategorySettingsScreen(
    category: SettingsCategory,
    @StringRes title: Int = category.title,
    groups: List<SettingGroup>,
    state: SettingsUiState,
    onSettingChange: (String, Boolean) -> Unit,
    sportQuickEntriesPage: Boolean = false,
    onOpenSportQuickEntries: () -> Unit = {},
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
                title = stringResource(title),
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
                .testTag(if (sportQuickEntriesPage) "settings:sport-quick-entries"
                    else "settings:${category.name.lowercase()}")
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
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                    )
                }
            }
            item(key = "settings") {
                Column {
                    groups.forEach { group ->
                        group.title?.let { title ->
                            SmallTitle(text = stringResource(title))
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .testTag("settings-group:${category.name.lowercase()}:${group.id}"),
                            insideMargin = PaddingValues(0.dp),
                        ) {
                            group.settings.forEach { setting ->
                                SwitchPreference(
                                    title = stringResource(setting.title),
                                    checked = state.valueOf(setting.key),
                                    enabled = state.writable && state.valueOf(SettingsKeys.ENABLED) &&
                                        (!sportQuickEntriesPage || !state.valueOf(SettingsKeys.SPORT_QUICK_ENTRIES)),
                                    onCheckedChange = { onSettingChange(setting.key, it) },
                                    modifier = Modifier.testTag("setting:${setting.key}"),
                                )
                            }
                            if (category == SettingsCategory.SPORT && !sportQuickEntriesPage &&
                                group.id == "quick-entries" && !state.valueOf(SettingsKeys.SPORT_QUICK_ENTRIES)) {
                                ArrowPreference(
                                    title = stringResource(R.string.settings_sport_quick_entry_items),
                                    onClick = onOpenSportQuickEntries,
                                    modifier = Modifier.testTag("settings:sport-quick-entry-nav"),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "navigation_bar_spacer") {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}
