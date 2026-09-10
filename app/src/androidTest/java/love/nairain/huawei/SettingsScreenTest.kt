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

        composeRule.onNodeWithText("LSPosed 服务未连接").assertExists()
        composeRule.onNodeWithText("请在启用模块后重试").assertExists()
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
            .performScrollToNode(hasText("布局精简"))
        val themeNode = composeRule.onNodeWithTag("settings:theme-nav").assertIsEnabled()
        val layoutTrimNode = composeRule.onNodeWithTag("settings:layout-trim-nav")
        assertTrue(
            themeNode.fetchSemanticsNode().boundsInRoot.top <
                layoutTrimNode.fetchSemanticsNode().boundsInRoot.top,
        )
        composeRule.onNodeWithText("主题设置").assertExists()
        composeRule.onNodeWithText("色彩模式").assertDoesNotExist()
        composeRule.onNodeWithText("隐藏桌面图标").assertDoesNotExist()

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
        composeRule.onNodeWithText("配置读写正常").assertExists()
        composeRule.onNodeWithText("LSPosed 服务已连接").assertExists()
    }

    @Test
    fun showsOnlyLayoutTrimNavigationEntry() {
        var opened = false
        setScreen(
            state = SettingsUiState(),
            onOpenLayoutTrim = { opened = true },
        )

        composeRule.onNodeWithTag("settings:list")
            .performScrollToNode(hasText("布局精简"))
        composeRule.onNodeWithTag("settings:layout-trim-nav").assertExists()
        composeRule.onNodeWithText("布局精简").assertExists()
        composeRule.onNodeWithText("精简底栏").assertDoesNotExist()
        composeRule.onNodeWithText("健康页面精简").assertDoesNotExist()
        composeRule.onNodeWithText("运动页面精简").assertDoesNotExist()
        composeRule.onNodeWithText("设备页面精简").assertDoesNotExist()
        composeRule.onNodeWithText("“我的”页面精简").assertDoesNotExist()
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
            .performScrollToNode(hasText("关于"))
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

        composeRule.onNodeWithContentDescription("返回").assertExists()
        composeRule.onAllNodesWithText("健康页面项目").assertCountEquals(2)
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

        composeRule.onNodeWithText("LSPosed 服务已连接").assertExists()
        composeRule.onNodeWithText("配置暂不可用，请刷新重试").assertExists()
        composeRule.onNodeWithText("配置读写正常").assertDoesNotExist()
        composeRule.onNodeWithText("无法读取配置，设置暂不可用").assertExists()
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

        composeRule.onNodeWithText("正在保存配置，完成前保持当前值").assertExists()
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

        composeRule.onAllNodesWithText("正在读取配置…").assertCountEquals(2)
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
        composeRule.onNodeWithText("修改后需彻底重启华为运动健康两次").assertExists()
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
