package love.nairain.huawei.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import love.nairain.huawei.R
import love.nairain.huawei.app.visibleServices
import love.nairain.huawei.config.ServicePreset
import love.nairain.huawei.config.ServiceSection
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun HostSettingsServices(model: HostSettingsUiState, strings: Resources, action: (HostSettingsAction) -> Unit) {
    SettingsNotice(strings.getString(R.string.service_block_notice), strings)
    TextButton(text = strings.getString(R.string.service_block_refresh), onClick = { action(HostSettingsAction.Refresh) })
    SettingsCard {
        SwitchPreference(title = strings.getString(R.string.service_block_enabled), checked = model.services.enabled,
            enabled = model.writable, onCheckedChange = { action(HostSettingsAction.ServiceEnabled(it)) })
    }
    val declared = model.declared
    ServiceSection.entries.forEach { section ->
        SettingsGroup(strings.getString(if (section == ServiceSection.CORE) R.string.service_block_core_title else R.string.service_block_low_title)) {
            ServicePreset.visible(section).forEach { group ->
                val available = group.components.any(declared::contains)
                val summary = buildList {
                    add(strings.getString(group.impact))
                    if (!available) add(strings.getString(R.string.service_group_missing))
                }.joinToString("\n")
                SwitchPreference(title = strings.getString(group.title), summary = summary, checked = group.id in model.services.presets,
                    enabled = model.writable && (available || group.id in model.services.presets), onCheckedChange = { action(HostSettingsAction.Preset(group.id, it)) })
                if (ServicePreset.hasLegacySelection(model.services.presets, group)) {
                    SettingsNote(strings.getString(R.string.service_legacy_partial))
                    TextButton(text = strings.getString(R.string.service_legacy_clear), enabled = model.writable, onClick = { action(HostSettingsAction.ClearLegacy(group.id)) })
                }
            }
        }
    }
    SettingsCard {
        SwitchPreference(title = strings.getString(R.string.service_block_debug_mode), summary = strings.getString(R.string.service_block_debug_mode_summary),
            checked = model.services.debugMode, enabled = model.writable, onCheckedChange = { action(HostSettingsAction.DebugMode(it)) })
        ArrowPreference(title = strings.getString(R.string.service_custom_title), enabled = model.services.debugMode,
            summary = if (!model.services.debugMode && model.services.components.isNotEmpty()) strings.getString(R.string.service_block_custom_paused) else null,
            onClick = { action(HostSettingsAction.Navigate("custom")) })
    }
    SettingsNote(strings.getString(R.string.service_effective_count, model.services.effectiveComponents(declared).size))
}

@Composable
internal fun HostSettingsCustomServices(model: HostSettingsUiState, strings: Resources, list: LazyListState,
    action: (HostSettingsAction) -> Unit, modifier: Modifier = Modifier) {
    val groupSelected = remember(model.services.presets) { ServicePreset.selectedComponents(model.services.presets) }
    val selected = remember(model.services.components, groupSelected) { model.services.components + groupSelected }
    val rows = remember(model.settings.catalog, selected, model.query, model.selectedOnly) {
        visibleServices(model.settings.catalog.services, selected, model.query, model.selectedOnly)
    }
    val declared = remember(model.settings.catalog) { model.declared }
    LazyColumn(modifier = modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "search") {
            TextField(value = model.draftQuery, onValueChange = { action(HostSettingsAction.DraftQuery(it)) },
                label = strings.getString(R.string.service_block_search), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item(key = "filter") { TextButton(text = strings.getString(R.string.host_settings_apply_filter), onClick = { action(HostSettingsAction.ApplyFilter) }) }
        item(key = "selected") {
            SettingsCard { SwitchPreference(title = strings.getString(R.string.service_block_only_selected), checked = model.selectedOnly,
                onCheckedChange = { action(HostSettingsAction.SelectedOnly(it)) }) }
        }
        item(key = "clear") {
            TextButton(text = strings.getString(R.string.service_custom_clear), enabled = model.writable && model.services.debugMode && model.services.components.isNotEmpty(),
                onClick = { action(HostSettingsAction.ClearCustom) })
        }
        if (rows.isEmpty()) item(key = "empty") { SettingsNote(strings.getString(R.string.service_block_empty)) }
        items(rows, key = { it.component }) { item ->
            val details = if (item.missing) strings.getString(R.string.service_block_missing) else strings.getString(R.string.service_block_details,
                item.process, strings.getString(if (item.exported) R.string.service_block_yes else R.string.service_block_no),
                strings.getString(if (item.enabled) R.string.service_block_yes else R.string.service_block_no))
            val summary = details + if (item.component in groupSelected) "\n" + strings.getString(R.string.service_group_selected) else ""
            SettingsCard { SwitchPreference(title = item.className, summary = summary, checked = item.component in model.services.components,
                enabled = model.writable && model.services.debugMode && (item.component in declared || item.component in model.services.components),
                onCheckedChange = { action(HostSettingsAction.Component(item.component, it)) }) }
        }
    }
}
