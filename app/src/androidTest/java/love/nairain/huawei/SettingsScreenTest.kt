package love.nairain.huawei

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.app.CategorySettingsScreen
import love.nairain.huawei.app.AppColorMode
import love.nairain.huawei.app.SettingsScreen
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

        composeRule.onNodeWithText("LSPosed 服务未连接").assertExists()
        composeRule.onNodeWithText("请在启用模块后重试").assertExists()
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            .get(0)
            .assertIsNotEnabled()
    }

    @Test
    fun showsOnlyGeneralSwitchesAndDispatchesChanges() {
        var changed: Pair<String, Boolean>? = null
        setScreen(
            state = SettingsUiState(
                isServiceConnected = true,
                values = mapOf(
                    SettingsKeys.ENABLED to false,
                    SettingsKeys.HIDE_LAUNCHER_ICON to false,
                ),
            ),
            onSettingChange = { key, value -> changed = key to value },
        )

        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true).apply {
            assertCountEquals(2)
            get(0).assertIsOff()
            get(1).assertIsOff()
        }
        composeRule.onNodeWithTag("setting:${SettingsKeys.ENABLED}", useUnmergedTree = true)
            .performClick()

        assertEquals(SettingsKeys.ENABLED to true, changed)
    }

    @Test
    fun showsConnectedStatusCard() {
        setScreen(SettingsUiState(isServiceConnected = true))

        composeRule.onNodeWithTag("settings:service-status").assertExists()
        composeRule.onNodeWithText("正常运行中").assertExists()
        composeRule.onNodeWithText("LSPosed 服务已连接").assertExists()
    }

    @Test
    fun showsUsableColorModeAboveLayoutTrimAndDispatchesSelection() {
        var selectedMode: AppColorMode? = null
        setScreen(
            state = SettingsUiState(isServiceConnected = false),
            colorMode = AppColorMode.SYSTEM,
            onColorModeChange = { selectedMode = it },
        )

        composeRule.onNodeWithTag("settings:list")
            .performScrollToNode(hasText("布局精简"))
        val colorModeNode = composeRule.onNodeWithTag("settings:color-mode")
            .assertIsEnabled()
        val layoutTrimNode = composeRule.onNodeWithTag("settings:layout-trim-nav")
        assertTrue(
            colorModeNode.fetchSemanticsNode().boundsInRoot.top <
                layoutTrimNode.fetchSemanticsNode().boundsInRoot.top,
        )
        composeRule.onNodeWithText("跟随系统").assertExists()

        colorModeNode.performClick()
        composeRule.onNodeWithText("深色模式").performClick()

        assertEquals(AppColorMode.DARK, selectedMode)
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
                    settings = SettingsCatalog.settingsFor(SettingsCategory.HEALTH),
                    state = SettingsUiState(
                        isServiceConnected = true,
                        values = mapOf(SettingsKeys.ENABLED to true),
                    ),
                    onSettingChange = { _, _ -> },
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("返回").assertExists()
        composeRule.onNodeWithText("健康页面项目").assertExists()
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
            .assertCountEquals(SettingsCatalog.health.size)
    }

    private fun setScreen(
        state: SettingsUiState,
        colorMode: AppColorMode = AppColorMode.SYSTEM,
        onSettingChange: (String, Boolean) -> Unit = { _, _ -> },
        onColorModeChange: (AppColorMode) -> Unit = {},
        onOpenLayoutTrim: () -> Unit = {},
        onOpenAbout: () -> Unit = {},
    ) {
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                SettingsScreen(
                    state = state,
                    colorMode = colorMode,
                    onSettingChange = onSettingChange,
                    onColorModeChange = onColorModeChange,
                    onOpenLayoutTrim = onOpenLayoutTrim,
                    onOpenAbout = onOpenAbout,
                )
            }
        }
    }
}
