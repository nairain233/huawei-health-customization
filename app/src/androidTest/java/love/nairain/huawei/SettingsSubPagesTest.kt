package love.nairain.huawei

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.app.CategorySettingsScreen
import love.nairain.huawei.app.SettingsUiState
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsCategory
import love.nairain.huawei.config.SettingsKeys
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@RunWith(AndroidJUnit4::class)
class SettingsSubPagesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disablesMineSwitchesUntilMasterSwitchIsOn() {
        setCategoryScreen(
            SettingsCategory.MINE,
            SettingsUiState(
                isServiceConnected = true,
                values = mapOf(SettingsKeys.ENABLED to false),
            ),
        )

        val switches = composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
        switches.assertCountEquals(SettingsCatalog.mine.size)
        for (index in SettingsCatalog.mine.indices) {
            switches.get(index).assertIsNotEnabled()
            switches.get(index).assertIsOff()
        }
    }

    @Test
    fun enablesMineSwitchesAndDefaultsThemOffWhenMasterSwitchIsOn() {
        setCategoryScreen(
            SettingsCategory.MINE,
            SettingsUiState(
                isServiceConnected = true,
                values = mapOf(SettingsKeys.ENABLED to true),
            ),
        )

        val switches = composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
        switches.assertCountEquals(SettingsCatalog.mine.size)
        for (index in SettingsCatalog.mine.indices) {
            switches.get(index).assertIsEnabled()
            switches.get(index).assertIsOff()
        }
    }

    @Test
    fun showsMineOtherGroup() {
        setCategoryScreen(
            SettingsCategory.MINE,
            SettingsUiState(
                isServiceConnected = true,
                values = mapOf(SettingsKeys.ENABLED to true),
            ),
        )

        composeRule.onNodeWithTag("settings-group:mine:other").assertExists()
        composeRule.onNodeWithText("其他").assertExists()
    }

    @Test
    fun disablesBottomSwitchesUntilMasterSwitchIsOn() {
        setCategoryScreen(
            SettingsCategory.BOTTOM,
            SettingsUiState(
                isServiceConnected = true,
                values = mapOf(SettingsKeys.ENABLED to false),
            ),
        )

        val switches = composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
        switches.assertCountEquals(SettingsCatalog.bottom.size)
        for (index in SettingsCatalog.bottom.indices) {
            switches.get(index).assertIsNotEnabled()
            switches.get(index).assertIsOff()
        }
    }

    @Test
    fun enablesBottomSwitchesAndDefaultsThemOffWhenMasterSwitchIsOn() {
        setCategoryScreen(
            SettingsCategory.BOTTOM,
            SettingsUiState(
                isServiceConnected = true,
                values = mapOf(SettingsKeys.ENABLED to true),
            ),
        )

        val switches = composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
        switches.assertCountEquals(SettingsCatalog.bottom.size)
        for (index in SettingsCatalog.bottom.indices) {
            switches.get(index).assertIsEnabled()
            switches.get(index).assertIsOff()
        }
    }

    private fun setCategoryScreen(category: SettingsCategory, state: SettingsUiState) {
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                CategorySettingsScreen(
                    category = category,
                    groups = SettingsCatalog.groupsFor(category),
                    state = state,
                    onSettingChange = { _, _ -> },
                    onClose = {},
                )
            }
        }
    }
}
