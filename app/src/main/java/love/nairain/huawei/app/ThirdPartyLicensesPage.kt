package love.nairain.huawei.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import love.nairain.huawei.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference

/** 单独展示依赖许可证和项目链接的页面，关于页只保留一个入口。 */
@Composable
internal fun ThirdPartyLicensesPage(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.about_open_source_section),
                largeTitle = stringResource(R.string.about_open_source_section),
                navigationIcon = {
                    PageBackButton(
                        onBack = onBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.testTag("about:libraries:back"),
                    )
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("about:libraries:list"),
            contentPadding = pagePadding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = openSourceLibraries,
                key = { library -> library.name },
            ) { library ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        modifier = Modifier.testTag("about:library:${library.name}"),
                        title = library.name,
                        summary = stringResource(
                            R.string.about_lib_summary,
                            library.license,
                            library.author,
                        ),
                        onClick = { onOpenLink(library.url) },
                    )
                }
            }
        }
    }
}
