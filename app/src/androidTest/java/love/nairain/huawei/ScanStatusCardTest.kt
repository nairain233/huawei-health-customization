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
        compose.onNodeWithTag("scan:rescan").assertIsNotEnabled()
    }

    @Test fun pendingKeepsPreviousRatioAndDisablesDuplicateRequest() {
        val report = ScanReport("a".repeat(64), "17.0.7.310", 1700007310, 1, "", "11111111-1111-1111-1111-111111111111", 1,
            phase = "complete", checked = ScanProtocol.keys, matched = setOf(SettingsKeys.MINE_FAMILY))
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(report, pending = true, writable = true), {}) } }
        compose.onNodeWithTag("scan:matched").assertTextEquals("已匹配 1/78 项")
        compose.onNodeWithTag("scan:pending").assertExists()
        compose.onNodeWithTag("scan:rescan").assertIsNotEnabled()
    }

    @Test fun rescanDispatchesAction() {
        var calls = 0
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(writable = true), { calls++ }) } }
        compose.onNodeWithTag("scan:rescan").performClick()
        assertEquals(1, calls)
    }
}
