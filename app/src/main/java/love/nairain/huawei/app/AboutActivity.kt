package love.nairain.huawei.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import love.nairain.huawei.BuildConfig
import love.nairain.huawei.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

class AboutActivity : AppCompatActivity() {
    companion object {
        fun intent(context: Context): Intent = Intent(context, AboutActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val moduleApplication = application as ModuleApplication
        setContent {
            HuaweiTrimTheme(colorMode = moduleApplication.colorMode, window = window) {
                AboutScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    onClose = { finish() },
                )
            }
        }
    }
}

@Serializable
internal sealed interface AboutDestination : NavKey {
    @Serializable data object Main : AboutDestination
    @Serializable data object Libraries : AboutDestination
}

@Composable
internal fun AboutScreen(
    versionName: String,
    versionCode: Int,
    onClose: () -> Unit = {},
    onOpenLink: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val openUrl: (String) -> Unit = onOpenLink ?: { openLink(context, it) }
    val backStack = rememberNavBackStack<AboutDestination>(AboutDestination.Main)
    val back: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onClose()
    }
    val swipeDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
        NavSwipeDirection.RightToLeft
    } else NavSwipeDirection.LeftToRight
    CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onBackground) {
        NavDisplay(
            backStack = backStack,
            onBack = back,
            transition = NavTransitions.MiuixDefault,
            effects = remember { NavDisplayEffects(blockInputDuringTransition = false) },
            modifier = Modifier.fillMaxSize(),
        ) {
            entry<AboutDestination.Main> {
                AboutPage(
                    versionName = versionName,
                    versionCode = versionCode,
                    onBack = onClose,
                    onOpenLink = openUrl,
                    onOpenThirdPartyLicenses = {
                        if (backStack.last() != AboutDestination.Libraries) backStack.add(AboutDestination.Libraries)
                    },
                )
            }
            entry<AboutDestination.Libraries>(swipeDismiss = swipeDirection) {
                ThirdPartyLicensesPage(onBack = back, onOpenLink = openUrl)
            }
        }
    }
}

@Composable
internal fun PageBackButton(onBack: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier.padding(start = 12.dp)) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

internal fun pagePadding(padding: PaddingValues): PaddingValues = PaddingValues(
    start = 12.dp,
    top = padding.calculateTopPadding() + 12.dp,
    end = 12.dp,
    bottom = padding.calculateBottomPadding() + 12.dp,
)

private fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        .onFailure { Toast.makeText(context, R.string.about_link_failed, Toast.LENGTH_SHORT).show() }
}
