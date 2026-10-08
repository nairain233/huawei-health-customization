package love.nairain.huawei

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.app.ExperimentalFeaturesScreen
import love.nairain.huawei.app.SettingsUiState
import love.nairain.huawei.config.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 实验性功能页保留独立开关与确认写入行为。 */
@RunWith(AndroidJUnit4::class)
class LocalWatchFaceScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun localImportCanBeChangedWhileLayoutIsOffWithoutOptimisticUpdate() {
        var request: Pair<String, Boolean>? = null
        composeRule.setContent {
            MiuixTheme {
                ExperimentalFeaturesScreen(
                    state = SettingsUiState(isServiceConnected = true, isConfigAvailable = true),
                    onSettingChange = { key, value -> request = key to value },
                    onClose = {},
                )
            }
        }
        val tag = "setting:${SettingsKeys.LOCAL_WATCH_FACE}"
        composeRule.onNodeWithTag("settings:experimental").performScrollToNode(hasTestTag(tag))
        val toggle = composeRule.onAllNodes(isToggleable() and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)[0]
        toggle.assertIsEnabled().assertIsOff().performClick()
        composeRule.runOnIdle { assertEquals(SettingsKeys.LOCAL_WATCH_FACE to true, request) }
        toggle.assertIsOff()
    }
}
