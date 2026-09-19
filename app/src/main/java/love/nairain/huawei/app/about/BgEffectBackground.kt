// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package love.nairain.huawei.app.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页使用的 Miuix OS2 全屏背景。
 *
 * RuntimeShader 仅从 API 33 开始可用，旧系统直接绘制主题 surface，确保页面仍然可用。
 */
@Composable
internal fun BgEffectBackground(
    dynamicBackground: Boolean,
    modifier: Modifier = Modifier,
    isFullSize: Boolean = false,
    effectBackground: Boolean = true,
    alpha: () -> Float = { 1f },
    bgModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    val shaderSupported = remember { isRuntimeShaderSupported() }
    if (!shaderSupported) {
        Box(modifier = modifier.background(surface), content = content)
        return
    }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val widthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val deviceType = if (widthDp >= 600.dp) DeviceType.PAD else DeviceType.PHONE
    val isDarkTheme = surface.luminance() < 0.5f
    val painter = remember { BgEffectPainter() }
    val preset = remember(deviceType, isDarkTheme) {
        BgEffectConfig.get(deviceType, isDarkTheme)
    }

    Box(modifier = modifier) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(bgModifier)
                .bgEffectDraw(
                    painter = painter,
                    preset = preset,
                    deviceType = deviceType,
                    isDarkTheme = isDarkTheme,
                    surface = surface,
                    effectBackground = effectBackground,
                    isFullSize = isFullSize,
                    playing = dynamicBackground,
                    alpha = alpha,
                ),
        )
        content()
    }
}
