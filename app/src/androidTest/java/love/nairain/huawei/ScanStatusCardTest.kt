package love.nairain.huawei

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import love.nairain.huawei.app.ScanStatusCard
import love.nairain.huawei.app.ScanUiState
import love.nairain.huawei.config.SettingsKeys
import love.nairain.huawei.scan.ScanProtocol
import love.nairain.huawei.scan.ScanReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class ScanStatusCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowCardWrapsLongMessagesAcrossLanguagesThemesAndFontScales() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val variant = mutableStateOf(Triple("zh-CN", false, 1f))
        compose.setContent {
            val (language, dark, fontScale) = variant.value
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }
            val localizedContext = context.createConfigurationContext(configuration)
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                    Box(Modifier.width(320.dp)) {
                        ScanStatusCard(ScanUiState(writable = true, requestUncertain = true), {})
                    }
                }
            }
        }
        for (language in listOf("zh-CN", "en")) {
            for (dark in listOf(false, true)) {
                for (scale in listOf(1f, 2f)) {
                    compose.runOnIdle { variant.value = Triple(language, dark, scale) }
                    compose.onNodeWithTag("scan:card").assertIsNotEnabled()
                    val bitmap = compose.onNodeWithTag("scan:card").captureToImage()
                    val file = File(context.getExternalFilesDir(null), "scan-$language-$dark-$scale.png")
                    FileOutputStream(file).use {
                        bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    for (tag in listOf("scan:phase", "scan:request-uncertain")) {
                        val results = mutableListOf<TextLayoutResult>()
                        compose.onNodeWithTag(tag, useUnmergedTree = true)
                            .assertIsDisplayed()
                            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                                it(results)
                            }
                        assertTrue(results.isNotEmpty())
                        assertFalse(
                            "$language dark=$dark scale=$scale tag=$tag: " + results.joinToString {
                                "size=${it.size} paragraph=${it.multiParagraph.width}x${it.multiParagraph.height} end=${it.getLineEnd(it.lineCount - 1)} lines=${it.lineCount} text=${it.layoutInput.text}"
                            },
                            results.any {
                                it.isLineEllipsized(it.lineCount - 1) ||
                                    it.getLineEnd(it.lineCount - 1) != it.layoutInput.text.length
                            },
                        )
                    }
                }
            }
        }
    }

    @Test fun unscannedDoesNotPretendToHaveZeroMatches() {
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(), {}) } }
        compose.onNodeWithTag("scan:matched").assertDoesNotExist()
        compose.onNodeWithTag("scan:card").assertIsNotEnabled()
    }

    @Test fun pendingKeepsPreviousPhaseAndDisablesDuplicateRequest() {
        val report = ScanReport("a".repeat(64), "17.0.7.310", 1700007310, 1, "", "11111111-1111-1111-1111-111111111111", 1,
            phase = "complete", checked = ScanProtocol.keys, matched = setOf(SettingsKeys.MINE_FAMILY))
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(report, pending = true, writable = true), {}) } }
        compose.onNodeWithTag("scan:matched").assertDoesNotExist()
        compose.onNodeWithText("17.0.7.310", substring = true).assertDoesNotExist()
        compose.onNodeWithText("服务屏蔽", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true).assertTextEquals(
            resourceString(R.string.scan_complete_partial, 1, ScanProtocol.keys.size),
        )
        compose.onNodeWithTag("scan:checked", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("scan:pending", useUnmergedTree = true).assertTextEquals(
            resourceString(R.string.scan_scheduled),
        )
        compose.onNodeWithTag("scan:card").assertIsNotEnabled()
    }

    @Test fun rescanDispatchesAction() {
        var calls = 0
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(writable = true), { calls++ }) } }
        compose.onNodeWithTag("scan:card").performClick()
        assertEquals(1, calls)
    }

    @Test fun savingAndRestoredFailureHaveCorrectActions() {
        val state = mutableStateOf(ScanUiState(writable = true, saving = true))
        compose.setContent { MiuixTheme { ScanStatusCard(state.value, {}) } }
        compose.onNodeWithTag("scan:card").assertIsNotEnabled()
        compose.onNodeWithText(resourceString(R.string.scan_saving), useUnmergedTree = true).assertExists()
        compose.runOnIdle { state.value = ScanUiState(writable = true, saveFailed = true) }
        compose.onNodeWithTag("scan:card").assertIsEnabled()
        compose.onNodeWithText(resourceString(R.string.scan_save_failed), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("scan:pending", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun uncertainRequestDisablesActionUntilConfirmedReconnectState() {
        val state = mutableStateOf(ScanUiState(writable = true, requestUncertain = true))
        compose.setContent { MiuixTheme { ScanStatusCard(state.value, {}) } }
        compose.onNodeWithTag("scan:card").assertIsNotEnabled()
        compose.onNodeWithTag("scan:request-uncertain", useUnmergedTree = true)
            .assertTextEquals(resourceString(R.string.scan_request_uncertain))
        compose.onNodeWithTag("scan:pending", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { state.value = ScanUiState(writable = false) }
        compose.onNodeWithTag("scan:card").assertIsNotEnabled()
        compose.runOnIdle { state.value = ScanUiState(writable = true) }
        compose.onNodeWithTag("scan:card").assertIsEnabled()
        compose.onNodeWithTag("scan:request-uncertain", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun completeShowsAllDiscoveredAndHasNoSeparateButton() {
        val report = ScanReport("a".repeat(64), "17.0.7.310", 1700007310, 1, "", "11111111-1111-1111-1111-111111111111", 1,
            phase = "complete", checked = ScanProtocol.keys, matched = ScanProtocol.keys)
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(report, writable = true), {}) } }
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true)
            .assertTextEquals(
                resourceString(
                    R.string.scan_complete_all,
                    ScanProtocol.keys.size,
                    ScanProtocol.keys.size,
                ),
            )
        compose.onNodeWithTag("scan:rescan").assertDoesNotExist()
        compose.onNodeWithTag("scan:card").assertIsEnabled().assertHasClickAction()
    }

    @Test fun runningShowsProgressAndDisablesRescan() {
        val report = ScanReport("a".repeat(64), "17.0.7.310", 1700007310, 1, "", "11111111-1111-1111-1111-111111111111", 1,
            phase = "running", checked = setOf(SettingsKeys.MINE_FAMILY))
        compose.setContent { MiuixTheme { ScanStatusCard(ScanUiState(report, writable = true), {}) } }
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true)
            .assertTextEquals(resourceString(R.string.scan_running))
        compose.onNodeWithTag("scan:checked", useUnmergedTree = true)
            .assertTextEquals(resourceString(R.string.scan_checked, 1, ScanProtocol.keys.size))
        compose.onNodeWithTag("scan:card").assertIsNotEnabled()
    }

    @Test fun staleAndFailedStatesRemainVisible() {
        val state = mutableStateOf(ScanUiState(expired = true, writable = true))
        compose.setContent { MiuixTheme { ScanStatusCard(state.value, {}) } }
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true)
            .assertTextEquals(resourceString(R.string.scan_expired))
        compose.runOnIdle { state.value = ScanUiState(uncertain = true, writable = true) }
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true)
            .assertTextEquals(resourceString(R.string.scan_uncertain))
        compose.runOnIdle {
            state.value = ScanUiState(report = ScanReport("a".repeat(64), "17.0.7.310", 1700007310, 1, "",
                "11111111-1111-1111-1111-111111111111", 1, phase = "failed"), saveFailed = true)
        }
        compose.onNodeWithTag("scan:phase", useUnmergedTree = true)
            .assertTextEquals(resourceString(R.string.scan_failed))
        compose.onNodeWithText(resourceString(R.string.scan_save_failed), useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithTag("scan:card").assertIsNotEnabled()
    }
}
