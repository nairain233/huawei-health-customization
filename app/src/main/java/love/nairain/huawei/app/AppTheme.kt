package love.nairain.huawei.app

import android.content.Context
import android.content.SharedPreferences
import android.view.Window
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import love.nairain.huawei.R
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 模块配置应用自身的色彩模式，不参与目标应用 Hook 配置。 */
internal enum class AppColorMode(
    val preferenceValue: String,
    val colorSchemeMode: ColorSchemeMode,
    @StringRes val label: Int,
) {
    SYSTEM("system", ColorSchemeMode.System, R.string.settings_color_mode_system),
    LIGHT("light", ColorSchemeMode.Light, R.string.settings_color_mode_light),
    DARK("dark", ColorSchemeMode.Dark, R.string.settings_color_mode_dark),
    ;

    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromPreferenceValue(value: String?): AppColorMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
    }
}

/** 模块配置应用自身的界面语言，不参与目标应用 Hook 配置。 */
internal enum class AppLanguage(
    val languageTag: String,
    @StringRes val label: Int,
) {
    SYSTEM("", R.string.settings_language_system),
    SIMPLIFIED_CHINESE("zh-CN", R.string.settings_language_simplified_chinese),
    ENGLISH("en", R.string.settings_language_english),
    ;

    fun toLocaleList(): LocaleListCompat = LocaleListCompat.forLanguageTags(languageTag)

    companion object {
        fun fromLocaleList(locales: LocaleListCompat): AppLanguage {
            val languageTags = locales.toLanguageTags()
            return entries.firstOrNull { it.languageTag == languageTags } ?: SYSTEM
        }
    }
}

/** 色彩模式使用应用私有偏好，避免依赖 LSPosed 服务。 */
internal object AppAppearancePreferences {
    private const val PREFERENCES_NAME = "app_appearance"
    private const val COLOR_MODE_KEY = "color_mode"

    fun read(context: Context): AppColorMode = read(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    fun write(context: Context, colorMode: AppColorMode) {
        write(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            colorMode,
        )
    }

    internal fun read(preferences: SharedPreferences): AppColorMode =
        AppColorMode.fromPreferenceValue(
            runCatching { preferences.getString(COLOR_MODE_KEY, null) }.getOrNull(),
        )

    internal fun write(preferences: SharedPreferences, colorMode: AppColorMode) {
        preferences.edit { putString(COLOR_MODE_KEY, colorMode.preferenceValue) }
    }
}

internal val LocalAppDarkTheme = staticCompositionLocalOf { false }

/** 为所有模块页面提供一致的 Miuix 主题与系统栏图标明暗。 */
@Composable
internal fun HuaweiTrimTheme(
    colorMode: AppColorMode,
    window: Window,
    content: @Composable () -> Unit,
) {
    val darkTheme = colorMode.isDark(isSystemInDarkTheme())
    val controller = remember(colorMode) {
        ThemeController(colorSchemeMode = colorMode.colorSchemeMode)
    }

    SideEffect {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MiuixTheme(controller = controller, content = content)
    }
}
