package love.nairain.huawei

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.app.ServiceBlockScreen
import love.nairain.huawei.app.ServiceBlockUiState
import love.nairain.huawei.app.ServiceCatalog
import love.nairain.huawei.app.ServiceItem
import love.nairain.huawei.app.SettingsScreen
import love.nairain.huawei.app.SettingsUiState
import love.nairain.huawei.config.ServiceBlockConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@RunWith(AndroidJUnit4::class)
class ServiceBlockScreenTest {
    @get:Rule val composeRule = createComposeRule()
    private val component = "com.huawei.health/com.huawei.health.ExampleService"

    @Test fun homeEntryOpensWithoutLayoutSwitch() {
        var opened = false
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                SettingsScreen(SettingsUiState(), { _, _ -> }, onOpenServiceBlock = { opened = true })
            }
        }
        composeRule.onNodeWithTag("settings:list").performScrollToNode(hasTestTag("settings:service-block-nav"))
        composeRule.onNodeWithTag("settings:service-block-nav").performClick()
        assertTrue(opened)
    }

    @Test fun searchesTogglesFiltersAndClearsServices() {
        val state = mutableStateOf(ServiceBlockUiState(
            catalog = ServiceCatalog(listOf(ServiceItem(component, "com.huawei.health:sync")), true),
            loading = false, connected = true,
        ))
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                ServiceBlockScreen(state.value, { state.value = state.value.copy(config = it) }, {}, {})
            }
        }
        composeRule.onNodeWithTag("service-block:enabled").performClick()
        composeRule.onNodeWithTag("service-block:list").performScrollToNode(hasTestTag("service-block:search"))
        composeRule.onNodeWithTag("service-block:search").performTextInput("SYNC")
        composeRule.onNodeWithTag("service-block:list").performScrollToNode(hasTestTag("service-block:$component"))
        composeRule.onNodeWithTag("service-block:$component").performClick()
        composeRule.runOnIdle {
            assertEquals(ServiceBlockConfig(true, setOf(component)), state.value.config)
        }
        composeRule.onNodeWithTag("service-block:list").performScrollToNode(hasTestTag("service-block:filter"))
        composeRule.onNodeWithTag("service-block:filter").performClick()
        composeRule.onNodeWithTag("service-block:clear").performClick()
        composeRule.runOnIdle { assertTrue(state.value.config.components.isEmpty()) }
        composeRule.onNodeWithTag("service-block:list").performScrollToNode(hasTestTag("service-block:search"))
    }

    @Test fun disconnectedAndFailedSaveStateKeepSwitchOff() {
        val state = mutableStateOf(ServiceBlockUiState(
            catalog = ServiceCatalog(supported = true), connected = false, loading = false,
            message = R.string.service_block_disconnected,
        ))
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                ServiceBlockScreen(state.value, {
                    state.value = state.value.copy(config = ServiceBlockConfig(), message = R.string.settings_status_save_error)
                }, {}, {})
            }
        }
        composeRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag("service-block:enabled")), true)
            .assertIsNotEnabled()
        composeRule.runOnIdle { state.value = state.value.copy(connected = true) }
        composeRule.onNodeWithTag("service-block:enabled").performClick()
        composeRule.onNode(isToggleable() and hasAnyAncestor(hasTestTag("service-block:enabled")), true).assertIsOff()
        composeRule.onNodeWithTag("service-block:list").performScrollToNode(
            androidx.compose.ui.test.hasText("保存失败，配置未更改"),
        )
    }
}
