package love.nairain.huawei

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.app.AppColorMode
import love.nairain.huawei.app.ThemeSettingsScreen
import love.nairain.huawei.config.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@RunWith(AndroidJUnit4::class)
class ThemeSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsThemeControlsInOrderAndDispatchesChanges() {
        var selectedMode: AppColorMode? = null
        var hidden: Boolean? = null
        setScreen(
            onColorModeChange = { selectedMode = it },
            onHideLauncherIconChange = { hidden = it },
        )

        composeRule.onAllNodesWithText("主题设置").assertCountEquals(2)
        composeRule.onNodeWithTag("settings:theme-card", useUnmergedTree = true).assertExists()
        val colorModeNode = composeRule.onNodeWithTag("settings:color-mode").assertIsEnabled()
        val launcherNode = composeRule.onNodeWithTag(
            "setting:${SettingsKeys.HIDE_LAUNCHER_ICON}",
            useUnmergedTree = true,
        ).assertIsEnabled()
        assertTrue(
            colorModeNode.fetchSemanticsNode().boundsInRoot.top <
                launcherNode.fetchSemanticsNode().boundsInRoot.top,
        )

        colorModeNode.performClick()
        composeRule.onNodeWithText("深色模式").performClick()
        launcherNode.performClick()

        assertEquals(AppColorMode.DARK, selectedMode)
        assertEquals(true, hidden)
    }

    @Test
    fun showsLocalErrorAndDispatchesBack() {
        var closed = false
        setScreen(
            statusMessage = "无法更新桌面图标状态",
            onClose = { closed = true },
        )

        composeRule.onNodeWithText("无法更新桌面图标状态").assertExists()
        composeRule.onNodeWithContentDescription("返回").performClick()

        assertTrue(closed)
    }

    private fun setScreen(
        colorMode: AppColorMode = AppColorMode.SYSTEM,
        hideLauncherIcon: Boolean = false,
        statusMessage: String? = null,
        onColorModeChange: (AppColorMode) -> Unit = {},
        onHideLauncherIconChange: (Boolean) -> Unit = {},
        onClose: () -> Unit = {},
    ) {
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                ThemeSettingsScreen(
                    colorMode = colorMode,
                    hideLauncherIcon = hideLauncherIcon,
                    statusMessage = statusMessage,
                    onColorModeChange = onColorModeChange,
                    onHideLauncherIconChange = onHideLauncherIconChange,
                    onClose = onClose,
                )
            }
        }
    }
}
