package love.nairain.huawei

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                LayoutTrimScreen(
                    onOpenBottomTrim = { opened += "bottom" },
                    onOpenHealth = { opened += "health" },
                    onOpenSport = { opened += "sport" },
                    onOpenDevice = { opened += "device" },
                    onOpenMineTrim = { opened += "mine" },
                    onClose = { closed = true },
                )
            }
        }

        composeRule.onAllNodesWithText("布局精简").assertCountEquals(2)
        composeRule.onNodeWithContentDescription("返回").assertExists()

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

        composeRule.onNodeWithContentDescription("返回").performClick()
        assertTrue(closed)
    }
}
