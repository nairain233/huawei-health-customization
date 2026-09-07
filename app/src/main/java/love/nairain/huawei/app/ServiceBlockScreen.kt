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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import love.nairain.huawei.R
import love.nairain.huawei.config.ServiceBlockConfig
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

@Composable
internal fun ServiceBlockScreen(
    state: ServiceBlockUiState,
    onConfigChange: (ServiceBlockConfig) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var blockedOnly by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val direction = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val rows = visibleServices(state.catalog.services, state.config.components, query, blockedOnly)
    val cardModifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)

    Scaffold(topBar = {
        TopAppBar(
            title = stringResource(R.string.service_block_title),
            color = MiuixTheme.colorScheme.surface,
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(onClick = onClose) {
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
            item("controls") {
                Card(modifier = cardModifier, insideMargin = PaddingValues(0.dp)) {
                    SwitchPreference(
                        title = stringResource(R.string.service_block_enabled),
                        checked = state.config.enabled,
                        enabled = state.writable && (state.catalog.supported || state.config.enabled),
                        onCheckedChange = { onConfigChange(state.config.copy(enabled = it)) },
                        modifier = Modifier.testTag("service-block:enabled"),
                    )
                    SwitchPreference(
                        title = stringResource(R.string.service_block_only_selected),
                        checked = blockedOnly,
                        onCheckedChange = { blockedOnly = it },
                        modifier = Modifier.testTag("service-block:filter"),
                    )
                    ArrowPreference(
                        title = stringResource(R.string.service_block_clear),
                        enabled = state.writable && state.config.components.isNotEmpty(),
                        onClick = { onConfigChange(state.config.copy(components = emptySet())) },
                        modifier = Modifier.testTag("service-block:clear"),
                    )
                    ArrowPreference(
                        title = stringResource(R.string.service_block_refresh),
                        enabled = !state.loading && !state.saving,
                        onClick = onRefresh,
                    )
                }
            }
            item("search") {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.service_block_search),
                    singleLine = true,
                    modifier = cardModifier.testTag("service-block:search"),
                )
            }
            item("notice") {
                Card(modifier = cardModifier, insideMargin = PaddingValues(16.dp)) {
                    Text(stringResource(R.string.service_block_notice))
                }
            }
            item("status") {
                Card(modifier = cardModifier, insideMargin = PaddingValues(16.dp)) {
                    if (state.loading) Text(stringResource(R.string.service_block_loading))
                    state.catalog.status?.let { Text(stringResource(it)) }
                    state.message?.let { Text(stringResource(it)) }
                    Text(stringResource(R.string.service_block_count, state.config.components.size, rows.size))
                }
            }
            if (!state.loading && rows.isEmpty()) {
                item("empty") {
                    Card(modifier = cardModifier, insideMargin = PaddingValues(16.dp)) {
                        Text(stringResource(R.string.service_block_empty))
                    }
                }
            }
            items(rows, key = { it.component }) { row ->
                val checked = row.component in state.config.components
                Card(modifier = cardModifier, insideMargin = PaddingValues(0.dp)) {
                    Text(row.className, modifier = Modifier.padding(16.dp))
                    Text(
                        text = if (row.missing) stringResource(R.string.service_block_missing)
                        else stringResource(
                            R.string.service_block_details, row.process,
                            stringResource(if (row.exported) R.string.service_block_yes else R.string.service_block_no),
                            stringResource(if (row.enabled) R.string.service_block_yes else R.string.service_block_no),
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MiuixTheme.textStyles.body2,
                    )
                    SwitchPreference(
                        title = stringResource(R.string.service_block_item_toggle),
                        checked = checked,
                        enabled = state.writable && (checked || (state.catalog.supported && !row.missing)),
                        onCheckedChange = { selected ->
                            onConfigChange(state.config.copy(components = if (selected) {
                                state.config.components + row.component
                            } else state.config.components - row.component))
                        },
                        modifier = Modifier.testTag("service-block:${row.component}"),
                    )
                }
            }
            item("bottom") { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}
