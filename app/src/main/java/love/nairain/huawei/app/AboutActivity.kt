package love.nairain.huawei.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import love.nairain.huawei.BuildConfig
import love.nairain.huawei.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 让区块标题与卡片文本对齐。 */
private val SectionTitleMargin = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = 8.dp,
    bottom = 8.dp,
)

private const val TELEGRAM_CHANNEL_URL = "https://t.me/Rain_Cl"
private const val AUTHOR_URL = "https://github.com/nairain233"
private const val REPOSITORY_URL = "https://github.com/nairain233/huawei-health-customization"

/**
 * 模块关于页：展示静态项目信息，不依赖 LSPosed 服务。
 */
class AboutActivity : ComponentActivity() {
    companion object {
        fun intent(context: Context): Intent = Intent(context, AboutActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MiuixTheme(colors = colors) {
                AboutScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
internal fun AboutScreen(
    versionName: String,
    onClose: () -> Unit = {},
) {
    val title = stringResource(R.string.about_title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                largeTitle = title,
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("about:back"),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.back),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("about:list"),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = paddingValues.calculateTopPadding() + 12.dp,
                end = 12.dp,
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AboutHeader(versionName) }
            item { ProjectSection() }
            item { LinkSection() }
            item { DisclaimerSection() }
            item { LibrarySection() }
        }
    }
}

@Composable
private fun AboutHeader(versionName: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about:header"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        setImageDrawable(context.packageManager.getApplicationIcon(context.packageName))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = null
                    }
                },
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.padding(top = 12.dp),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = MiuixTheme.textStyles.title3.fontSize,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.about_header_description),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.about_version, versionName),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                modifier = Modifier.testTag("about:version"),
            )
        }
    }
}

@Composable
private fun ProjectSection() {
    AboutSection(stringResource(R.string.about_section_project)) {
        Text(
            text = stringResource(R.string.about_project_intro),
            modifier = Modifier.padding(16.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.paragraph,
        )
    }
}

@Composable
private fun LinkSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    AboutSection(stringResource(R.string.about_section_links)) {
        ArrowPreference(
            modifier = Modifier.testTag("about:repository"),
            title = stringResource(R.string.about_repository),
            summary = stringResource(R.string.about_repository_url),
            onClick = { openLink(context, REPOSITORY_URL) },
        )
        ArrowPreference(
            modifier = Modifier.testTag("about:telegram"),
            title = stringResource(R.string.about_link_telegram),
            summary = stringResource(R.string.about_link_telegram_summary),
            onClick = { openLink(context, TELEGRAM_CHANNEL_URL) },
        )
        ArrowPreference(
            modifier = Modifier.testTag("about:author"),
            title = stringResource(R.string.about_link_author),
            summary = stringResource(R.string.about_link_author_summary),
            onClick = { openLink(context, AUTHOR_URL) },
        )
    }
}

@Composable
private fun DisclaimerSection() {
    AboutSection(stringResource(R.string.about_section_disclaimer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DisclaimerItem(stringResource(R.string.about_disclaimer_learning))
            DisclaimerItem(stringResource(R.string.about_disclaimer_free))
            DisclaimerItem(stringResource(R.string.about_disclaimer_resale))
            DisclaimerItem(stringResource(R.string.about_disclaimer_liability))
            DisclaimerItem(stringResource(R.string.about_disclaimer_copyright))
        }
    }
}

@Composable
private fun DisclaimerItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.paragraph,
        )
    }
}

@Composable
private fun LibrarySection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    AboutSection(stringResource(R.string.about_open_source_section)) {
        openSourceLibraries.forEach { library ->
            ArrowPreference(
                modifier = Modifier.testTag("about:library:${library.name}"),
                title = library.name,
                summary = stringResource(
                    R.string.about_lib_summary,
                    library.license,
                    library.author,
                ),
                onClick = { openLink(context, library.url) },
            )
        }
    }
}

private fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        .onFailure {
            Toast.makeText(context, R.string.about_link_failed, Toast.LENGTH_SHORT).show()
        }
}

@Composable
private fun AboutSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        SmallTitle(text = title, insideMargin = SectionTitleMargin)
        Card(content = content)
    }
}
