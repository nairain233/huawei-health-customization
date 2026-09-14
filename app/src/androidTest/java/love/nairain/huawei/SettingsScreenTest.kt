package love.nairain.huawei

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.app.CategorySettingsScreen
import love.nairain.huawei.app.SettingsScreen
import love.nairain.huawei.app.SettingsNotice
import love.nairain.huawei.app.SettingsNoticeKind
import love.nairain.huawei.app.SettingsUiState
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disablesSettingsWhenServiceIsDisconnected() {
        setScreen(SettingsUiState())
        composeRule.onNodeWithTag("scan:card").assertDoesNotExist()

        composeRule.onNodeWithText(resourceString(R.string.settings_status_disconnected))
            .assertExists()
        composeRule.onNodeWithText(resourceString(R.string.settings_status_disconnected_summary))
            .assertExists()
        composeRule.onAllNodes(
            isToggleable() and hasAnyAncestor(hasTestTag("setting:${SettingsKeys.ENABLED}")),
            useUnmergedTree = true,
        ).apply {
            assertCountEquals(1)
            get(0).assertIsNotEnabled()
        }
        composeRule.onNodeWithTag("settings:color-mode", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(
            "setting:${SettingsKeys.HIDE_LAUNCHER_ICON}",
            useUnmergedTree = true,
        ).assertDoesNotExist()
        composeRule.onNodeWithTag("settings:theme-nav").assertIsEnabled()
    }

    @Test
    fun showsOnlyMasterSwitchAndDispatchesChange() {
        var changed: Pair<String, Boolean>? = null
        setScreen(
            state = SettingsUiState(
                isServiceConnected = true,
                isConfigAvailable = true,
                confirmedValues = mapOf(
                    SettingsKeys.ENABLED to false,
                ),
            ),
            onSettingChange = { key, value -> changed = key to value },
        )

        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true).apply {
            assertCountEquals(1)
            get(0).assertIsOff()
        }
        composeRule.onNodeWithTag("setting:${SettingsKeys.ENABLED}", useUnmergedTree = true)
            .performClick()

        assertEquals(SettingsKeys.ENABLED to true, changed)
    }

    @Test
    fun showsThemeNavigationBeforeLayoutTrimAndDispatchesClick() {
        var opened = false
        setScreen(
            state = SettingsUiState(isServiceConnected = false),
            onOpenThemeSettings = { opened = true },
        )

        composeRule.onNodeWithTag("settings:list")
            .performScrollToNode(hasText(resourceString(R.string.settings_layout_trim_title)))
        val themeNode = composeRule.onNodeWithTag("settings:theme-nav").assertIsEnabled()
        val layoutTrimNode = composeRule.onNodeWithTag("settings:layout-trim-nav")
        assertTrue(
            themeNode.fetchSemanticsNode().boundsInRoot.top <
                layoutTrimNode.fetchSemanticsNode().boundsInRoot.top,
        )
        composeRule.onNodeWithText(resourceString(R.string.settings_theme_title)).assertExists()
        composeRule.onNodeWithText(resourceString(R.string.settings_color_mode)).assertDoesNotExist()
        composeRule.onNodeWithText(resourceString(R.string.settings_language)).assertDoesNotExist()
        composeRule.onNodeWithText(resourceString(R.string.settings_hide_launcher_icon))
            .assertDoesNotExist()

        themeNode.performClick()

        assertTrue(opened)
    }

    @Test
    fun showsConnectedStatusCard() {
        setScreen(
            SettingsUiState(
                isServiceConnected = true,
                isConfigAvailable = true,
            ),
        )

        composeRule.onNodeWithTag("settings:service-status").assertExists()
        composeRule.onNodeWithText(resourceString(R.string.settings_status_connected_summary))
            .assertExists()
        composeRule.onNodeWithText(resourceString(R.string.settings_status_connected)).assertExists()
    }

    @Test
    fun showsOnlyLayoutTrimNavigationEntry() {
        var opened = false
        setScreen(
            state = SettingsUiState(),
            onOpenLayoutTrim = { opened = true },
        )

        composeRule.onNodeWithTag("settings:list")
            .performScrollToNode(hasText(resourceString(R.string.settings_layout_trim_title)))
        composeRule.onNodeWithTag("settings:layout-trim-nav").assertExists()
        composeRule.onNodeWithText(resourceString(R.string.settings_layout_trim_title)).assertExists()
        composeRule.onNodeWithText(resourceString(R.string.settings_bottom_nav_title))
            .assertDoesNotExist()
        composeRule.onNodeWithText(resourceString(R.string.settings_health_nav_title))
            .assertDoesNotExist()
        composeRule.onNodeWithText(resourceString(R.string.settings_sport_nav_title))
            .assertDoesNotExist()
        composeRule.onNodeWithText(resourceString(R.string.settings_device_nav_title))
            .assertDoesNotExist()
        composeRule.onNodeWithText(resourceString(R.string.settings_mine_nav_title))
            .assertDoesNotExist()
        composeRule.onNodeWithTag("settings:layout-trim-nav").performClick()
        assert(opened)
    }

    @Test
    fun showsAboutNavigationEntry() {
        var opened = false
        setScreen(
            state = SettingsUiState(),
            onOpenAbout = { opened = true },
        )

        composeRule.onNodeWithTag("settings:list")
            .performScrollToNode(hasText(resourceString(R.string.about_title)))
        composeRule.onNodeWithTag("settings:about-nav").assertExists()
        composeRule.onNodeWithText("模块介绍、项目仓库与开源引用").assertDoesNotExist()
        composeRule.onNodeWithTag("settings:about-nav").performClick()
        assert(opened)
    }

    @Test
    fun showsCategorySettingsContent() {
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                CategorySettingsScreen(
                    category = SettingsCategory.HEALTH,
                    groups = SettingsCatalog.groupsFor(SettingsCategory.HEALTH),
                    state = SettingsUiState(
                        isServiceConnected = true,
                        isConfigAvailable = true,
                        confirmedValues = mapOf(SettingsKeys.ENABLED to true),
                    ),
                    onSettingChange = { _, _ -> },
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(resourceString(R.string.back)).assertExists()
        composeRule.onAllNodesWithText(resourceString(R.string.settings_health_title))
            .assertCountEquals(2)
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            .assertCountEquals(SettingsCatalog.health.size)
    }

    @Test
    fun connectedButUnavailableDoesNotClaimConfigurationIsHealthy() {
        setScreen(
            SettingsUiState(
                isServiceConnected = true,
                isConfigAvailable = false,
                notice = SettingsNotice(SettingsNoticeKind.CONFIG_UNAVAILABLE),
            ),
        )

        composeRule.onNodeWithText(resourceString(R.string.settings_status_connected)).assertExists()
        composeRule.onNodeWithText(
            resourceString(R.string.settings_status_connected_unavailable_summary),
        ).assertExists()
        composeRule.onNodeWithText(resourceString(R.string.settings_status_connected_summary))
            .assertDoesNotExist()
        composeRule.onNodeWithText(resourceString(R.string.settings_status_config_error))
            .assertExists()
    }

    @Test
    fun loadingAndSavingKeepConfirmedValueDisabled() {
        setScreen(
            SettingsUiState(
                isServiceConnected = true,
                isConfigAvailable = true,
                isSaving = true,
                confirmedValues = SettingsCatalog.defaults,
                notice = SettingsNotice(SettingsNoticeKind.SAVING),
            ),
        )

        composeRule.onNodeWithText(resourceString(R.string.settings_status_saving)).assertExists()
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true).get(0)
            .assertIsNotEnabled()
            .assertIsOff()
    }

    @Test
    fun loadingShowsTextAndDisablesMasterSwitch() {
        setScreen(
            SettingsUiState(
                isServiceConnected = true,
                isLoading = true,
                notice = SettingsNotice(SettingsNoticeKind.LOADING),
            ),
        )

        composeRule.onAllNodesWithText(resourceString(R.string.settings_status_loading))
            .assertCountEquals(2)
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true).get(0).assertIsNotEnabled()
    }

    @Test
    fun successfulSaveIsTheOnlyStateThatShowsNewValueAndRestartNotice() {
        setScreen(
            SettingsUiState(
                isServiceConnected = true,
                isConfigAvailable = true,
                confirmedValues = SettingsCatalog.defaults + (SettingsKeys.ENABLED to true),
                notice = SettingsNotice(SettingsNoticeKind.RESTART_REQUIRED),
            ),
        )

        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true).get(0).assertIsOn()
        composeRule.onNodeWithText(resourceString(R.string.settings_restart_notice)).assertExists()
    }

    private fun setScreen(
        state: SettingsUiState,
        onSettingChange: (String, Boolean) -> Unit = { _, _ -> },
        onOpenThemeSettings: () -> Unit = {},
        onOpenLayoutTrim: () -> Unit = {},
        onOpenAbout: () -> Unit = {},
    ) {
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                SettingsScreen(
                    state = state,
                    onSettingChange = onSettingChange,
                    onOpenThemeSettings = onOpenThemeSettings,
                    onOpenLayoutTrim = onOpenLayoutTrim,
                    onOpenAbout = onOpenAbout,
                )
            }
        }
    }
}
