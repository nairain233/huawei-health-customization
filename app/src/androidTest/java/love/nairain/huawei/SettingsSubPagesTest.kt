package love.nairain.huawei

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.app.CategorySettingsScreen
import love.nairain.huawei.app.SettingsNotice
import love.nairain.huawei.app.SettingsNoticeKind
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
                isConfigAvailable = true,
                confirmedValues = mapOf(SettingsKeys.ENABLED to false),
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
                isConfigAvailable = true,
                confirmedValues = mapOf(SettingsKeys.ENABLED to true),
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
                isConfigAvailable = true,
                confirmedValues = mapOf(SettingsKeys.ENABLED to true),
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
                isConfigAvailable = true,
                confirmedValues = mapOf(SettingsKeys.ENABLED to false),
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
                isConfigAvailable = true,
                confirmedValues = mapOf(SettingsKeys.ENABLED to true),
            ),
        )

        val switches = composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)
        switches.assertCountEquals(SettingsCatalog.bottom.size)
        for (index in SettingsCatalog.bottom.indices) {
            switches.get(index).assertIsEnabled()
            switches.get(index).assertIsOff()
        }
    }

    @Test
    fun categoryShowsFailureNoticesAsTextAndUncertainStateIsDisabled() {
        val notices = listOf(
            SettingsNoticeKind.DEFAULTS_NOT_PERSISTED to "默认配置未持久化，现有设置仍可编辑",
            SettingsNoticeKind.SAVE_FAILED to "保存失败，配置未更改",
            SettingsNoticeKind.STATE_UNCERTAIN to
                "保存失败且无法确认旧配置已恢复，已暂停编辑；请等待服务重新连接后核对",
        )
        var state by mutableStateOf(SettingsUiState(
            isServiceConnected = true,
            isConfigAvailable = true,
            confirmedValues = SettingsCatalog.defaults + (SettingsKeys.ENABLED to true),
        ))
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                CategorySettingsScreen(
                    category = SettingsCategory.HEALTH,
                    groups = SettingsCatalog.groupsFor(SettingsCategory.HEALTH),
                    state = state,
                    onSettingChange = { _, _ -> },
                    onClose = {},
                )
            }
        }

        notices.forEach { (kind, text) ->
            composeRule.runOnUiThread {
                state = state.copy(
                    isConfigAvailable = kind != SettingsNoticeKind.STATE_UNCERTAIN,
                    notice = SettingsNotice(kind),
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(text).assertExists()
            if (kind == SettingsNoticeKind.DEFAULTS_NOT_PERSISTED) {
                composeRule.onAllNodes(isToggleable(), useUnmergedTree = true).get(0).assertIsEnabled()
            }
        }
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true).get(0).assertIsNotEnabled()
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
