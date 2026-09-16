package love.nairain.huawei

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.app.AboutScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsModuleRepositoryAndOpenSourceInformation() {
        setScreen()

        composeRule.onNodeWithText(resourceString(R.string.app_name)).assertExists()
        composeRule.onNodeWithText(resourceString(R.string.about_version, "1.0-test")).assertExists()
        composeRule.onNodeWithTag("about:list")
            .performScrollToNode(hasText(resourceString(R.string.about_project_intro)))
        composeRule.onNodeWithText(resourceString(R.string.about_project_intro)).assertExists()

        composeRule.onNodeWithTag("about:list")
            .performScrollToNode(hasText(resourceString(R.string.about_repository)))
        composeRule.onNodeWithTag("about:repository").assertExists()
        composeRule.onNodeWithText("Huawei-Health-Customization").assertExists()

        composeRule.onNodeWithTag("about:list")
            .performScrollToNode(hasText(resourceString(R.string.about_link_telegram)))
        composeRule.onNodeWithText(resourceString(R.string.about_link_telegram)).assertExists()
        composeRule.onNodeWithText(resourceString(R.string.about_link_author)).assertExists()

        composeRule.onNodeWithTag("about:list")
            .performScrollToNode(hasText("Miuix"))
        composeRule.onNodeWithText("Miuix").assertExists()
        composeRule.onNodeWithTag("about:list")
            .performScrollToNode(hasText("LibXposed Service"))
        composeRule.onNodeWithText("LibXposed Service").assertExists()
        composeRule.onNodeWithTag("about:list")
            .performScrollToNode(hasText("AndroidX"))
        composeRule.onNodeWithText("AndroidX").assertExists()
    }

    @Test
    fun dispatchesBackAction() {
        var closed = false
        setScreen(onClose = { closed = true })

        composeRule.onNodeWithContentDescription(resourceString(R.string.back)).performClick()

        assert(closed)
    }

    private fun setScreen(onClose: () -> Unit = {}) {
        composeRule.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                AboutScreen(
                    versionName = "1.0-test",
                    onClose = onClose,
                )
            }
        }
    }
}
