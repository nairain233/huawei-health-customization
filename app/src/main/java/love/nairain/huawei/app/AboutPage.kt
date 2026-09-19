package love.nairain.huawei.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import love.nairain.huawei.R

import love.nairain.huawei.app.about.BgEffectBackground
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val AUTHOR_URL = "https://github.com/nairain233"
private const val HEADER_COLLAPSE_DISTANCE_DP = 220

/**
 * 沉浸式关于页：标题随内容滚动收起（不显示应用图标），页面内容使用 Miuix 的标题和卡片分组。
 *
 * 背景效果只负责绘制，不拥有页面状态；链接由页面调用方处理。
 */
@Composable
internal fun AboutPage(
    versionName: String,
    versionCode: Int,
    onBack: () -> Unit,

    modifier: Modifier = Modifier,
    onOpenThirdPartyLicenses: () -> Unit = {},
    onOpenLink: (String) -> Unit,
) {

    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val collapseDistancePx = with(density) { HEADER_COLLAPSE_DISTANCE_DP.dp.toPx() }
    val scrollProgress by remember(listState, collapseDistancePx) {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> 1f
                collapseDistancePx <= 0f -> 1f
                else -> (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
            }
        }
    }
    val collapsed = scrollProgress >= 0.95f
    val surface = MiuixTheme.colorScheme.surface
    val backdrop = if (isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(surface)
            drawContent()
        }
    } else {
        null
    }
    BgEffectBackground(
        dynamicBackground = true,
        isFullSize = true,
        alpha = { 1f - scrollProgress },
        modifier = modifier.fillMaxSize(),
        bgModifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                val titleAlpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                SmallTopAppBar(
                    title = stringResource(R.string.about_title),
                    color = if (collapsed) {
                        MiuixTheme.colorScheme.surface.copy(alpha = 0.94f)
                    } else {
                        Color.Transparent
                    },
                    titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = titleAlpha),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        PageBackButton(
                            onBack = onBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.testTag("about:back"),
                        )
                    },
                )
            },
        ) { contentPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                    .testTag("about:list"),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = contentPadding.calculateTopPadding(),
                    end = 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "about:header") {
                    AboutHeader(
                        versionName = versionName,
                        versionCode = versionCode,
                        scrollProgress = scrollProgress,
                    )
                }
                item(key = "about_developer") {
                    AboutSection(
                        title = stringResource(R.string.about_section_developer),
                        cardTag = "about_developer_card",
                        backdrop = backdrop,
                    ) {
                        ArrowPreference(
                            modifier = Modifier.testTag("about:author"),
                            title = stringResource(R.string.about_developer_name),
                            summary = stringResource(R.string.about_developer_role),
                            startAction = {
                                Image(
                                    painter = painterResource(R.drawable.about_avatar),
                                    contentDescription = stringResource(R.string.about_section_developer),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .testTag("about:avatar").requiredSize(48.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                )
                            },
                            onClick = { onOpenLink(AUTHOR_URL) },
                        )
                    }
                }
                item(key = "about_links") {
                    AboutSection(
                        title = stringResource(R.string.about_section_links),
                        backdrop = backdrop,
                    ) {
                        ArrowPreference(
                            modifier = Modifier.testTag("about:repository"),
                            title = stringResource(R.string.about_repository),
                            endActions = { AboutValueText(stringResource(R.string.about_platform_github)) },
                            onClick = { onOpenLink("https://github.com/nairain233/huawei-health-customization") },
                        )
                        ArrowPreference(
                            modifier = Modifier.testTag("about:telegram"),
                            title = stringResource(R.string.about_link_telegram),
                            endActions = { AboutValueText(stringResource(R.string.about_platform_telegram)) },
                            onClick = { onOpenLink("https://t.me/Rain_Cl") },
                        )
                        ArrowPreference(
                            modifier = Modifier.testTag("about:libraries"),
                            title = stringResource(R.string.about_open_source_section),
                            onClick = onOpenThirdPartyLicenses,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutHeader(
    versionName: String,
    versionCode: Int,
    scrollProgress: Float,
) {

    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val titleBrush = remember(isDark) {
        Brush.horizontalGradient(
            if (isDark) listOf(Color(0xFFC4A7FF), Color(0xFF8ABEFF))
            else listOf(Color(0xFF7956D8), Color(0xFF3478D4)),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 204.dp)
            .graphicsLayer { alpha = 1f - scrollProgress }
            .padding(top = 34.dp, bottom = 34.dp)
            .testTag("about:header"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Miuix Text 会合并纯色；使用主题文字样式的 BasicText 保留渐变画刷。
        BasicText(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            style = MiuixTheme.textStyles.main.copy(
                brush = titleBrush,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            text = stringResource(
                R.string.about_version,
                versionName,
                versionCode,
            ),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .testTag("about:version"),
        )

    }
}

@Composable
private fun AboutSection(
    title: String,
    backdrop: Backdrop?,
    cardTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = if (cardTag == null) Modifier else Modifier.testTag(cardTag)
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    // 与 Miuix 示例关于页的 Overlay_Thin_Light / Pured_Regular_Light 配方一致。
    val blendColors = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
                BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(
            text = title,
            insideMargin = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth().then(cardModifier).then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 60f,
                        colors = BlurDefaults.blurColors(blendColors = blendColors),
                    )
                } else {
                    Modifier
                },
            ),
            colors = CardDefaults.defaultColors(
                if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                Color.Transparent,
            ),
            content = content,
        )
    }
}

@Composable
private fun AboutValueText(text: String) {
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )
}
