package love.nairain.huawei.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import love.nairain.huawei.R
import love.nairain.huawei.config.ServiceBlockConfig
import love.nairain.huawei.config.ServicePreset
import love.nairain.huawei.config.ServiceSection
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ServiceBlockScreen(
    state: ServiceBlockUiState,
    onConfigChange: (ServiceBlockConfig) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    var customRequested by rememberSaveable { mutableStateOf(false) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var blockedOnly by rememberSaveable { mutableStateOf(false) }
    val custom = customRequested && state.config.debugMode
    LaunchedEffect(state.config.debugMode, state.writable) {
        if (!state.config.debugMode) customRequested = false
        if (!state.writable) pendingId = null
    }
    BackHandler(enabled = custom) { customRequested = false }
    val groupsSelected = ServicePreset.selectedComponents(state.config.presets)
    val rows = visibleServices(state.catalog.services, state.config.components + groupsSelected, query, blockedOnly)
    val declared = state.catalog.services.map { it.component }.toSet()
    val scrollBehavior = MiuixScrollBehavior()
    val direction = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val card = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)
    val text = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
    val summaryStyle = MiuixTheme.textStyles.body2.copy(
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    val detailStyle = MiuixTheme.textStyles.body2.copy(
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )

    Scaffold(topBar = {
        TopAppBar(
            title = stringResource(if (custom) R.string.service_custom_title else R.string.service_block_title),
            color = MiuixTheme.colorScheme.surface,
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(onClick = { if (custom) customRequested = false else onClose() }) {
                    Icon(MiuixIcons.Regular.Back, contentDescription = stringResource(R.string.back))
                }
            },
        )
    }) { padding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize().imePadding().testTag("service-block:list")
                .scrollEndHaptic().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = safeInsets.calculateLeftPadding(direction),
                top = padding.calculateTopPadding() + 8.dp,
                end = safeInsets.calculateRightPadding(direction),
                bottom = padding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            if (!custom) {
                item("master") {
                    Card(
                        modifier = card.testTag("service-block:master"),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.service_block_enabled),
                            checked = state.config.enabled,
                            enabled = state.writable && (state.catalog.supported || state.config.enabled),
                            onCheckedChange = { onConfigChange(state.config.copy(enabled = it)) },
                            modifier = Modifier.testTag("service-block:enabled"),
                        )
                        Text(
                            text = stringResource(R.string.service_block_notice).lineSequence().first(),
                            modifier = text,
                            style = summaryStyle,
                        )
                    }
                }
                ServiceSection.entries.forEach { section ->
                    item(section.name) {
                        Card(modifier = card.testTag("service-block:section:${section.name}"), insideMargin = PaddingValues(0.dp)) {
                            Text(
                                text = stringResource(if (section == ServiceSection.LOW_COUPLING)
                                    R.string.service_block_low_title else R.string.service_block_core_title),
                                modifier = text,
                                style = MiuixTheme.textStyles.body1,
                            )
                            Text(
                                text = stringResource(if (section == ServiceSection.CORE)
                                    R.string.service_core_notice else R.string.service_low_notice),
                                modifier = text,
                                style = if (section == ServiceSection.CORE) {
                                    summaryStyle.copy(color = MiuixTheme.colorScheme.error)
                                } else summaryStyle,
                            )
                            ServicePreset.visible(section).forEach { group ->
                                val selected = group.id in state.config.presets
                                val legacy = ServicePreset.hasLegacySelection(state.config.presets, group)
                                val available = group.components.any(declared::contains)
                                SwitchPreference(
                                    title = stringResource(group.title),
                                    checked = selected,
                                    enabled = state.writable && (selected || legacy || (available && state.catalog.supported)),
                                    onCheckedChange = { enabled ->
                                        if (enabled && section == ServiceSection.CORE) pendingId = group.id
                                        else onConfigChange(state.config.copy(presets =
                                            ServicePreset.changeSelection(state.config.presets, group, enabled)))
                                    },
                                    modifier = Modifier.testTag("service-block:preset:${group.id}"),
                                )
                                Text(stringResource(group.impact), modifier = text, style = summaryStyle)
                                if (!group.evidenceConfirmed || !available) {
                                    Text(
                                        stringResource(if (!group.evidenceConfirmed) R.string.service_evidence_pending
                                            else R.string.service_group_missing),
                                        modifier = text,
                                        style = summaryStyle.copy(color = MiuixTheme.colorScheme.error),
                                    )
                                }
                                if (legacy && !selected) {
                                    Text(stringResource(R.string.service_legacy_partial), modifier = text, style = summaryStyle)
                                    ArrowPreference(
                                        title = stringResource(R.string.service_legacy_clear),
                                        enabled = state.writable,
                                        onClick = { onConfigChange(state.config.copy(presets =
                                            ServicePreset.changeSelection(state.config.presets, group, false))) },
                                        modifier = Modifier.testTag("service-block:legacy-clear:${group.id}"),
                                    )
                                }
                            }
                        }
                    }
                }
                item("debug") {
                    Card(modifier = card.testTag("service-block:section:DEBUG"), insideMargin = PaddingValues(0.dp)) {
                        SwitchPreference(
                            title = stringResource(R.string.service_block_debug_mode),
                            checked = state.config.debugMode,
                            enabled = state.writable,
                            onCheckedChange = { onConfigChange(state.config.copy(debugMode = it)) },
                            modifier = Modifier.testTag("service-block:debug"),
                        )
                        Text(stringResource(R.string.service_block_debug_mode_summary), modifier = text, style = summaryStyle)
                        if (state.config.debugMode) {
                            ArrowPreference(
                                title = stringResource(R.string.service_custom_title),
                                onClick = { customRequested = true },
                                modifier = Modifier.testTag("service-block:custom-nav"),
                            )
                        } else if (state.config.components.isNotEmpty()) {
                            Text(stringResource(R.string.service_block_custom_paused), modifier = text, style = summaryStyle)
                        }
                    }
                }
            } else {
                item("custom-controls") {
                    SwitchPreference(
                        title = stringResource(R.string.service_block_only_selected),
                        checked = blockedOnly,
                        onCheckedChange = { blockedOnly = it },
                        modifier = Modifier.testTag("service-block:filter"),
                    )
                    ArrowPreference(
                        title = stringResource(R.string.service_custom_clear),
                        enabled = state.writable && state.config.components.isNotEmpty(),
                        onClick = { onConfigChange(state.config.copy(components = emptySet())) },
                        modifier = Modifier.testTag("service-block:clear"),
                    )
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        label = stringResource(R.string.service_block_search),
                        singleLine = true,
                        modifier = card.testTag("service-block:search"),
                    )
                }
                if (!state.loading && rows.isEmpty()) {
                    item("empty") { Text(stringResource(R.string.service_block_empty), modifier = text, style = summaryStyle) }
                }
                items(rows, key = { it.component }) { row ->
                    val selected = row.component in state.config.components
                    val owner = ServicePreset.entries.firstOrNull { row.component in it.components }
                    Card(modifier = card, insideMargin = PaddingValues(0.dp)) {
                        Text(row.className, modifier = text, style = MiuixTheme.textStyles.body1)
                        Text(
                            if (owner != null) stringResource(owner.title) else stringResource(R.string.service_unclassified),
                            modifier = text,
                            style = summaryStyle,
                        )
                        Text(
                            if (row.missing) stringResource(R.string.service_block_missing) else stringResource(
                                R.string.service_block_details, row.process,
                                stringResource(if (row.exported) R.string.service_block_yes else R.string.service_block_no),
                                stringResource(if (row.enabled) R.string.service_block_yes else R.string.service_block_no),
                            ),
                            modifier = text,
                            style = detailStyle,
                        )
                        if (row.component in groupsSelected) {
                            Text(stringResource(R.string.service_group_selected), modifier = text, style = summaryStyle)
                        }
                        SwitchPreference(
                            title = stringResource(R.string.service_custom_toggle),
                            checked = selected,
                            enabled = state.writable && (selected || (state.catalog.supported && !row.missing)),
                            onCheckedChange = { enabled ->
                                onConfigChange(state.config.copy(components = if (enabled)
                                    state.config.components + row.component else state.config.components - row.component))
                            },
                            modifier = Modifier.testTag("service-block:${row.component}"),
                        )
                    }
                }
            }
            item("status") {
                if (state.loading) Text(stringResource(R.string.service_block_loading), modifier = text, style = summaryStyle)
                state.catalog.status?.let { Text(stringResource(it), modifier = text, style = summaryStyle) }
                state.message?.let { Text(stringResource(it), modifier = text, style = summaryStyle) }
                Text(stringResource(R.string.service_effective_count, state.config.effectiveComponents(declared).size), modifier = text, style = summaryStyle)
                ArrowPreference(
                    title = stringResource(R.string.service_block_refresh),
                    enabled = !state.loading && !state.saving,
                    onClick = onRefresh,
                )
                Text(
                    stringResource(R.string.service_block_notice),
                    modifier = text,
                    style = summaryStyle,
                )
            }
            item("bottom") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }

    val pending = pendingId?.let(ServicePreset::fromId)
    WindowDialog(
        show = pending != null && state.writable && !custom,
        title = pending?.let { stringResource(it.title) },
        summary = pending?.let { stringResource(it.impact) },
        onDismissRequest = { pendingId = null },
    ) {
        ArrowPreference(
            title = stringResource(R.string.service_confirm_block),
            enabled = state.writable,
            onClick = {
                if (pending != null && state.writable) {
                    onConfigChange(state.config.copy(presets =
                        ServicePreset.changeSelection(state.config.presets, pending, true)))
                }
                pendingId = null
            },
            modifier = Modifier.testTag("service-block:confirm"),
        )
        ArrowPreference(title = stringResource(R.string.service_cancel), onClick = { pendingId = null },
            modifier = Modifier.testTag("service-block:cancel"))
    }
}
