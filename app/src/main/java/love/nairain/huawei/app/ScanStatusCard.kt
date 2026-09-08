package love.nairain.huawei.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import love.nairain.huawei.R
import love.nairain.huawei.scan.ScanProtocol
import love.nairain.huawei.scan.ScanReport
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import java.text.DateFormat
import java.util.Date

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
    Card(modifier = modifier.fillMaxWidth().testTag("scan:card"), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.scan_title))
            Text(stringResource(when {
                state.expired -> R.string.scan_expired
                state.uncertain -> R.string.scan_uncertain
                state.report == null -> R.string.scan_not_started
                state.scanning -> R.string.scan_running
                state.report.phase == "failed" -> R.string.scan_failed
                else -> R.string.scan_complete
            }), modifier = Modifier.testTag("scan:phase"))
            state.report?.let { report ->
                Text(stringResource(R.string.scan_matched, report.matched.size, ScanProtocol.keys.size), Modifier.testTag("scan:matched"))
                if (state.scanning) Text(stringResource(R.string.scan_checked, report.checked.size, ScanProtocol.keys.size))
                Text(stringResource(R.string.scan_version, report.version))
                Text(stringResource(R.string.scan_time, DateFormat.getDateTimeInstance().format(Date(report.time))))
                Text(stringResource(when (report.service) {
                    "installed" -> R.string.scan_service_installed
                    "unsupported" -> R.string.scan_service_unsupported
                    else -> R.string.scan_service_disabled
                }))
            }
            if (state.pending) Text(stringResource(R.string.scan_scheduled), Modifier.testTag("scan:pending"))
            if (state.saveFailed) Text(stringResource(R.string.scan_save_failed))
            Text(stringResource(R.string.scan_description))
            TextButton(
                text = stringResource(if (state.saving) R.string.scan_saving else R.string.scan_rescan),
                enabled = state.canRequest,
                onClick = onRescan,
                modifier = Modifier.testTag("scan:rescan"),
            )
        }
    }
}
