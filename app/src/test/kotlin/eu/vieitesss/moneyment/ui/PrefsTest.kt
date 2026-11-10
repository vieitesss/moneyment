package eu.vieitesss.moneyment.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PrefsTest {
    @Test
    fun systemThemeFollowsThePhone() {
        assertTrue(isDark(ThemeChoice.SYSTEM, systemDark = true))
        assertFalse(isDark(ThemeChoice.SYSTEM, systemDark = false))
    }

    @Test
    fun explicitThemeIgnoresThePhone() {
        assertFalse(isDark(ThemeChoice.LIGHT, systemDark = true))
        assertTrue(isDark(ThemeChoice.DARK, systemDark = false))
        assertFalse(isDark(ThemeChoice.LIGHT, systemDark = false))
        assertTrue(isDark(ThemeChoice.DARK, systemDark = true))
    }

    @Test
    fun storedThemeValueFallsBackToTheSystemChoice() {
        assertEquals(ThemeChoice.SYSTEM, themeChoice(null))
        assertEquals(ThemeChoice.SYSTEM, themeChoice(""))
        assertEquals(ThemeChoice.SYSTEM, themeChoice("SEPIA"))
        assertEquals(ThemeChoice.LIGHT, themeChoice("LIGHT"))
        assertEquals(ThemeChoice.DARK, themeChoice("DARK"))
    }

    @Test
    fun storedLanguageValueFallsBackToTheSystemChoice() {
        assertEquals(LanguageChoice.SYSTEM, languageChoice(null))
        assertEquals(LanguageChoice.SYSTEM, languageChoice("KLINGON"))
        assertEquals(LanguageChoice.ENGLISH, languageChoice("ENGLISH"))
        assertEquals(LanguageChoice.SPANISH, languageChoice("SPANISH"))
    }

    @Test
    fun onlyAnExplicitLanguageAsksForALocale() {
        assertNull(languageLocale(LanguageChoice.SYSTEM))
        assertEquals(Locale.ENGLISH, languageLocale(LanguageChoice.ENGLISH))
        assertEquals("es", languageLocale(LanguageChoice.SPANISH)?.language)
    }
}
