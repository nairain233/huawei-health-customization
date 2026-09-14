package love.nairain.huawei

import androidx.core.os.LocaleListCompat
import love.nairain.huawei.app.AppAppearancePreferences
import love.nairain.huawei.app.AppColorMode
import love.nairain.huawei.app.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class AppThemeTest {
    @Test
    fun defaultsToSystemForMissingOrUnknownPreference() {
        assertEquals(AppColorMode.SYSTEM, AppAppearancePreferences.read(InMemoryPreferences()))
        assertEquals(
            AppColorMode.SYSTEM,
            AppAppearancePreferences.read(
                InMemoryPreferences(mapOf("color_mode" to "unknown")),
            ),
        )
        assertEquals(
            AppColorMode.SYSTEM,
            AppAppearancePreferences.read(
                InMemoryPreferences(mapOf("color_mode" to 1)),
            ),
        )
    }

    @Test
    fun persistsAndReadsEveryColorMode() {
        AppColorMode.entries.forEach { colorMode ->
            val preferences = InMemoryPreferences()

            AppAppearancePreferences.write(preferences, colorMode)

            assertEquals(colorMode, AppAppearancePreferences.read(preferences))
            assertEquals(
                colorMode.preferenceValue,
                preferences.getString("color_mode", null),
            )
        }
    }

    @Test
    fun mapsToMiuixModesAndResolvesDarkAppearance() {
        assertEquals(ColorSchemeMode.System, AppColorMode.SYSTEM.colorSchemeMode)
        assertEquals(ColorSchemeMode.Light, AppColorMode.LIGHT.colorSchemeMode)
        assertEquals(ColorSchemeMode.Dark, AppColorMode.DARK.colorSchemeMode)

        assertFalse(AppColorMode.SYSTEM.isDark(systemInDarkTheme = false))
        assertTrue(AppColorMode.SYSTEM.isDark(systemInDarkTheme = true))
        assertFalse(AppColorMode.LIGHT.isDark(systemInDarkTheme = true))
        assertTrue(AppColorMode.DARK.isDark(systemInDarkTheme = false))
    }

    @Test
    fun mapsSupportedApplicationLocalesAndFallsBackToSystem() {
        AppLanguage.entries.forEach { language ->
            assertEquals(
                language,
                AppLanguage.fromLocaleList(language.toLocaleList()),
            )
        }

        assertEquals(
            AppLanguage.SYSTEM,
            AppLanguage.fromLocaleList(LocaleListCompat.forLanguageTags("fr")),
        )
        assertEquals(
            AppLanguage.SYSTEM,
            AppLanguage.fromLocaleList(LocaleListCompat.forLanguageTags("en,zh-CN")),
        )
    }
}
