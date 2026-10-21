package love.nairain.huawei.settings

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import love.nairain.huawei.R
import love.nairain.huawei.config.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

internal fun sportPageTitle(id: Int, strings: Resources): String =
    SportFixedItems.pages.firstOrNull { it.id == id }?.title?.let(strings::getString)
        ?: strings.getString(R.string.sport_page_number, id)

internal fun sportRouteTitle(route: String, @Suppress("UNUSED_PARAMETER") model: HostSettingsUiState, strings: Resources): String =
    if (route == "sport.top") strings.getString(R.string.sport_top_title)
    else sportPageTitle(route.substringAfterLast('.').toIntOrNull() ?: 0, strings)

@Composable
internal fun HostSettingsSportHome(@Suppress("UNUSED_PARAMETER") model: HostSettingsUiState, strings: Resources,
    action: (HostSettingsAction) -> Unit) {
    SettingsCard { ArrowPreference(title = strings.getString(R.string.sport_top_title),
        onClick = { action(HostSettingsAction.Navigate("sport.top")) }) }
    SettingsGroup(strings.getString(R.string.sport_page_contents)) {
        SportFixedItems.pages.forEach { page ->
            ArrowPreference(title = strings.getString(page.title), modifier = Modifier.testTag("sport.page.${page.id}"),
                onClick = { action(HostSettingsAction.Navigate("sport.page.${page.id}")) })
        }
    }
}

@Composable
internal fun HostSettingsSportTop(model: HostSettingsUiState, strings: Resources, action: (HostSettingsAction) -> Unit) {
    SettingsGroup(strings.getString(R.string.settings_group_page_top)) {
        SettingsCatalog.sportGroups.first { it.id == "top" }.settings
            .filter { it.key != SettingsKeys.SPORT_BANNER }.forEach {
                LayoutSwitch(it.key, strings.getString(it.title), model, action)
            }
    }
    SettingsGroup(strings.getString(R.string.sport_tabs_title)) {
        SportFixedItems.pages.forEach { page ->
            val identity = model.settings.sportIdentity.orEmpty()
            val config = model.configuration.sport
            val hidden = config.hidesTab(identity, page.id)
            val canHide = page.id !in model.settings.sportTabs || config.canHideTab(identity, page.id, model.settings.sportTabs)
            // 旧身份规则尚未复核时不允许用不完整状态覆盖用户选择。
            val confirmed = config.hiddenTabs.isEmpty() || model.settings.sportIdentity != null
            SwitchPreference(title = strings.getString(page.title), checked = hidden, modifier = Modifier.testTag("sport.tab.${page.id}"),
                enabled = model.writable && confirmed && (hidden || canHide),
                onCheckedChange = { action(HostSettingsAction.SportTab(page.id, it)) })
        }
    }
}

@Composable
internal fun HostSettingsSportContent(pageId: Int, model: HostSettingsUiState, strings: Resources,
    action: (HostSettingsAction) -> Unit) {
    listOf(false, true).forEach { quick ->
        val items = SportFixedItems.items(pageId).filter { (it.kind == SportItemKind.ENTRY) == quick }
        if (items.isNotEmpty()) SettingsGroup(strings.getString(if (quick) R.string.settings_group_quick_entries else R.string.sport_page_contents)) {
            items.forEach { item ->
                val parentHidden = item.parentKey?.let {
                    model.configuration.sport.hidden(SportItem.fixed(pageId, it), model.configuration.layout)
                } == true
                SwitchPreference(title = strings.getString(item.title), modifier = Modifier.testTag("sport.item.${item.id}"),
                    checked = model.configuration.sport.hidden(item, model.configuration.layout),
                    summary = if (parentHidden) strings.getString(R.string.sport_parent_hidden) else null,
                    enabled = model.writable && !parentHidden,
                    onCheckedChange = { action(HostSettingsAction.SportItem(item.id, it)) })
            }
        }
    }
}
