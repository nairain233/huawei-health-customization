package love.nairain.huawei.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import love.nairain.huawei.R
import love.nairain.huawei.scan.ScanProtocol
import love.nairain.huawei.scan.ScanReport
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class ScanUiState(
    val report: ScanReport? = null,
    val pending: Boolean = false,
    val expired: Boolean = false,
    val uncertain: Boolean = false,
    val writable: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val requestUncertain: Boolean = false,
) {
    val scanning: Boolean get() = report?.phase == "running" && !uncertain && !expired
    val canRequest: Boolean get() = writable && !pending && !saving && !scanning && !requestUncertain
}

@Composable
internal fun ScanStatusCard(state: ScanUiState, onRescan: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        BasicComponent(
            modifier = Modifier.testTag("scan:card").semantics(mergeDescendants = true) {
                role = Role.Button
                if (!state.canRequest) disabled()
            },
            enabled = state.canRequest,
            onClickLabel = stringResource(R.string.scan_rescan),
            role = Role.Button,
            onClick = onRescan,
        ) {
            val titleColors = BasicComponentDefaults.titleColor()
            Text(
                stringResource(R.string.scan_title),
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = if (state.canRequest) titleColors.color else titleColors.disabledColor,
            )
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
            ScanSummary(phaseText, state.canRequest, Modifier.testTag("scan:phase"))
            state.report?.let { report ->
                if (state.scanning) ScanSummary(
                    stringResource(R.string.scan_checked, report.checked.size, ScanProtocol.keys.size),
                    state.canRequest,
                    Modifier.testTag("scan:checked"),
                )
            }
            if (state.pending) ScanSummary(
                stringResource(R.string.scan_scheduled), state.canRequest, Modifier.testTag("scan:pending"),
            )
            if (state.saveFailed) ScanSummary(stringResource(R.string.scan_save_failed), state.canRequest)
            if (state.requestUncertain) ScanSummary(
                stringResource(R.string.scan_request_uncertain), state.canRequest,
                Modifier.testTag("scan:request-uncertain"),
            )
            if (state.saving) ScanSummary(stringResource(R.string.scan_saving), state.canRequest)
        }
    }
}

@Composable
private fun ScanSummary(text: String, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = BasicComponentDefaults.summaryColor()
    Text(
        text = text,
        modifier = modifier,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = if (enabled) colors.color else colors.disabledColor,
    )
}
