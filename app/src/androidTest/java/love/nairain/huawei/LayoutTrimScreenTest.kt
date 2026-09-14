package love.nairain.huawei

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import love.nairain.huawei.app.ScanUiState
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.app.LayoutTrimScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@RunWith(AndroidJUnit4::class)
class LayoutTrimScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsEntriesInOrderAndDispatchesClicks() {
        val opened = mutableListOf<String>()
        var closed = false
        var rescans = 0
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                LayoutTrimScreen(
                    onOpenBottomTrim = { opened += "bottom" },
                    onOpenHealth = { opened += "health" },
                    onOpenSport = { opened += "sport" },
                    onOpenDevice = { opened += "device" },
                    onOpenMineTrim = { opened += "mine" },
                    onClose = { closed = true },
                    scanState = ScanUiState(writable = true),
                    onRescan = { rescans++ },
                )
            }
        }

        composeRule.onAllNodesWithText(resourceString(R.string.settings_layout_trim_title))
            .assertCountEquals(2)
        composeRule.onNodeWithContentDescription(resourceString(R.string.back)).assertExists()

        val tags = listOf(
            "layout-trim:bottom-nav",
            "layout-trim:health-nav",
            "layout-trim:sport-nav",
            "layout-trim:device-nav",
            "layout-trim:mine-nav",
        )
        val topPositions = tags.map { tag ->
            composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue(topPositions.zipWithNext().all { (current, next) -> current < next })

        tags.forEach { tag -> composeRule.onNodeWithTag(tag).performClick() }
        assertEquals(listOf("bottom", "health", "sport", "device", "mine"), opened)

        composeRule.onNodeWithTag("settings:layout-trim").performScrollToNode(hasTestTag("scan:card"))
        assertTrue(composeRule.onNodeWithTag("scan:card").fetchSemanticsNode().boundsInRoot.top >
            composeRule.onNodeWithTag("layout-trim:mine-nav").fetchSemanticsNode().boundsInRoot.top)
        composeRule.onNodeWithTag("scan:card").performClick()
        assertEquals(1, rescans)

        composeRule.onNodeWithContentDescription(resourceString(R.string.back)).performClick()
        assertTrue(closed)
    }
}
