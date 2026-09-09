package love.nairain.huawei.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import love.nairain.huawei.R
import love.nairain.huawei.scan.ScanProtocol
import love.nairain.huawei.scan.ScanReport
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text

internal data class ScanUiState(
    val report: ScanReport? = null,
    val pending: Boolean = false,
    val expired: Boolean = false,
    val uncertain: Boolean = false,
    val writable: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val scanning: Boolean get() = report?.phase == "running" && !uncertain && !expired
    val canRequest: Boolean get() = writable && !pending && !saving && !scanning
}

@Composable
internal fun ScanStatusCard(state: ScanUiState, onRescan: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("scan:card").clickable(
            enabled = state.canRequest,
            onClickLabel = stringResource(R.string.scan_rescan),
            role = Role.Button,
            onClick = onRescan,
        ),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.scan_title))
            val report = state.report
            val phaseText = when {
                report?.phase == "complete" && !state.expired && !state.uncertain -> stringResource(
                    if (report.matched.size == ScanProtocol.keys.size) R.string.scan_complete_all
                    else R.string.scan_complete_partial,
                    report.matched.size, ScanProtocol.keys.size,
                )
                else -> stringResource(when {
                    state.expired -> R.string.scan_expired
                    state.uncertain -> R.string.scan_uncertain
                    state.report == null -> R.string.scan_not_started
                    state.scanning -> R.string.scan_running
                    state.report.phase == "failed" -> R.string.scan_failed
                    else -> R.string.scan_complete
                })
            }
            Text(phaseText, modifier = Modifier.testTag("scan:phase"))
            state.report?.let { report ->
                if (state.scanning) Text(
                    stringResource(R.string.scan_checked, report.checked.size, ScanProtocol.keys.size),
                    Modifier.testTag("scan:checked"),
                )
            }
            if (state.pending) Text(stringResource(R.string.scan_scheduled), Modifier.testTag("scan:pending"))
            if (state.saveFailed) Text(stringResource(R.string.scan_save_failed))
            if (state.saving) Text(stringResource(R.string.scan_saving))
        }
    }
}
