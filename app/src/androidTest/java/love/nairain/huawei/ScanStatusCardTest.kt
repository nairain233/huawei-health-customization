package love.nairain.huawei

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import love.nairain.huawei.app.ScanStatusCard
import love.nairain.huawei.app.ScanUiState
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.scan.ScanProtocol
import love.nairain.huawei.scan.ScanReport
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

class ScanStatusCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unscannedDoesNotPretendToHaveZeroMatches() {
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(), {}) } }
        compose.onNodeWithTag("scan:matched").assertDoesNotExist()
        compose.onNodeWithTag("scan:card").assertIsNotEnabled()
    }

    @Test fun pendingKeepsPreviousPhaseAndDisablesDuplicateRequest() {
        val report = ScanReport("a".repeat(64), "17.0.7.310", 1700007310, 1, "", "11111111-1111-1111-1111-111111111111", 1,
            phase = "complete", checked = ScanProtocol.keys, matched = setOf(SettingsKeys.MINE_FAMILY))
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(report, pending = true, writable = true), {}) } }
        compose.onNodeWithTag("scan:matched").assertDoesNotExist()
        compose.onNodeWithText("17.0.7.310", substring = true).assertDoesNotExist()
        compose.onNodeWithText("服务屏蔽", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true).assertTextEquals("扫描完成，已发现1/${ScanProtocol.keys.size}，部分功能可能不可用")
        compose.onNodeWithTag("scan:checked", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("scan:pending", useUnmergedTree = true).assertTextEquals("已安排，下次启动运动健康时扫描")
        compose.onNodeWithTag("scan:card").assertIsNotEnabled()
    }

    @Test fun rescanDispatchesAction() {
        var calls = 0
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(writable = true), { calls++ }) } }
        compose.onNodeWithTag("scan:card").performClick()
        assertEquals(1, calls)
    }

    @Test fun completeShowsAllDiscoveredAndHasNoSeparateButton() {
        val report = ScanReport("a".repeat(64), "17.0.7.310", 1700007310, 1, "", "11111111-1111-1111-1111-111111111111", 1,
            phase = "complete", checked = ScanProtocol.keys, matched = ScanProtocol.keys)
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(report, writable = true), {}) } }
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true)
            .assertTextEquals("扫描完成，已发现${ScanProtocol.keys.size}/${ScanProtocol.keys.size}，全部完成")
        compose.onNodeWithTag("scan:rescan").assertDoesNotExist()
        compose.onNodeWithTag("scan:card").assertIsEnabled().assertHasClickAction()
    }

    @Test fun runningShowsProgressAndDisablesRescan() {
        val report = ScanReport("a".repeat(64), "17.0.7.310", 1700007310, 1, "", "11111111-1111-1111-1111-111111111111", 1,
            phase = "running", checked = setOf(SettingsKeys.MINE_FAMILY))
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(report, writable = true), {}) } }
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true).assertTextEquals("扫描中")
        compose.onNodeWithTag("scan:checked", useUnmergedTree = true).assertTextEquals("已检查 1/${ScanProtocol.keys.size} 项")
        compose.onNodeWithTag("scan:card").assertIsNotEnabled()
    }

    @Test fun staleAndFailedStatesRemainVisible() {
        val state = androidx.compose.runtime.mutableStateOf(ScanUiState(expired = true, writable = true))
        compose.setContent { MiuixTheme { ScanStatusCard(state.value, {}) } }
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true).assertTextEquals("上次结果已过期，请重新启动运动健康")
        compose.runOnIdle { state.value = ScanUiState(uncertain = true, writable = true) }
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true).assertTextEquals("扫描状态待确认，请重新启动运动健康")
        compose.runOnIdle {
            state.value = ScanUiState(report = ScanReport("a".repeat(64), "17.0.7.310", 1700007310, 1, "",
                "11111111-1111-1111-1111-111111111111", 1, phase = "failed"), saveFailed = true)
        }
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true).assertTextEquals("扫描失败，下次启动时重试")
        compose.onNodeWithText("预约保存失败，请重试", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("scan:card").assertIsNotEnabled()
    }
}
