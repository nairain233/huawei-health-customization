package love.nairain.huawei

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import love.nairain.huawei.app.AboutScreen
import love.nairain.huawei.app.LocalAppDarkTheme
import love.nairain.huawei.app.openSourceLibraries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.theme.darkColorScheme

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsVersionAndDeveloperThenOpensLibraryDetails() {
        setScreen()
        composeRule.onNodeWithText(resourceString(R.string.app_name)).assertExists()
        composeRule.onNodeWithText(resourceString(R.string.about_version, "1.0-test", 42)).assertExists()
        scrollTo("about:author")
        composeRule.onNodeWithText("夜雨 | Nairain").assertExists()
        composeRule.onNodeWithTag("about:avatar", useUnmergedTree = true).assertExists()
        scrollTo("about:libraries")
        composeRule.onNodeWithTag("about:libraries").performClick()
        composeRule.onNodeWithTag("about:libraries:list").assertExists()
        for (library in openSourceLibraries) {
            composeRule.onNodeWithTag("about:libraries:list")
                .performScrollToNode(hasText(library.name))
            composeRule.onNodeWithTag("about:library:" + library.name).assertExists()
        }
        composeRule.onNodeWithTag("about:libraries:back").performClick()
        composeRule.onNodeWithTag("about:list").assertExists()
        composeRule.onNodeWithTag("about:libraries").assertExists()
    }

    @Test
    fun dispatchesExistingExternalLinks() {
        val urls = mutableListOf<String>()
        setScreen(onOpenLink = { urls += it })
        for (tag in listOf("about:author", "about:repository", "about:telegram")) {
            scrollTo(tag)
            composeRule.onNodeWithTag(tag).performClick()
        }
        scrollTo("about:libraries")
        composeRule.onNodeWithTag("about:libraries").performClick()
        composeRule.onNodeWithTag("about:libraries:list").performScrollToNode(hasText("Miuix"))
        composeRule.onNodeWithTag("about:library:Miuix").performClick()
        assertEquals(
            listOf(
                "https://github.com/nairain233",
                "https://github.com/nairain233/huawei-health-customization",
                "https://t.me/Rain_Cl",
                "https://github.com/compose-miuix-ui/miuix",
            ),
            urls,
        )
    }

    @Test
    fun detailBackDoesNotCloseActivity() {
        var closed = false
        setScreen(onClose = { closed = true })
        scrollTo("about:libraries")
        composeRule.onNodeWithTag("about:libraries").performClick()
        composeRule.onNodeWithTag("about:libraries:back").performClick()
        assertTrue(!closed)
        composeRule.onNodeWithTag("about:back").performClick()
        assertTrue(closed)
    }

    @Test
    fun restoresLibraryDestination() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MiuixTheme(colors = lightColorScheme()) {
                AboutScreen("test", 42)
            }
        }
        scrollTo("about:libraries")
        composeRule.onNodeWithTag("about:libraries").performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("about:libraries:list").assertExists()
        composeRule.onNodeWithTag("about:libraries:back").performClick()
        composeRule.onNodeWithTag("about:libraries").assertExists()
    }

    @Test
    fun rendersLightChinese() {
        setScreen(english = false)
        capture("about-light")
    }

    @Test
    fun rendersDarkEnglishWithLargeFont() {
        setScreen(dark = true, fontScale = 1.5f, english = true)
        capture("about-dark-large-en")
        scrollTo("about:libraries")
        composeRule.onNodeWithTag("about:libraries").performClick()
        capture("about-libraries-dark-large-en")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "about-verification").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag("about:list").performScrollToNode(hasTestTag(tag))
    }

    private fun setScreen(
        onClose: () -> Unit = {},
        onOpenLink: (String) -> Unit = {},
        dark: Boolean = false,
        fontScale: Float = 1f,
        english: Boolean? = null,
    ) {
        composeRule.setContent {
            val context = LocalContext.current
            val localized = context.createConfigurationContext(Configuration(LocalConfiguration.current).apply {
                if (english != null) setLocales(LocaleList(Locale.forLanguageTag(if (english) "en" else "zh-CN")))
            })
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalAppDarkTheme provides dark,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                    AboutScreen(versionName = "1.0-test", versionCode = 42, onClose = onClose, onOpenLink = onOpenLink)
                }
            }
        }
    }
}
