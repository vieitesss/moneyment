package eu.vieitesss.moneyment.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

/** What the person picked for the theme; [SYSTEM] follows the phone. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** What the person picked for the language; [SYSTEM] follows the phone. */
enum class LanguageChoice { SYSTEM, ENGLISH, SPANISH }

/** True when the app should draw dark, which depends on the choice and, for [ThemeChoice.SYSTEM], the phone. */
fun isDark(choice: ThemeChoice, systemDark: Boolean): Boolean = when (choice) {
    ThemeChoice.SYSTEM -> systemDark
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}

/** The locale a choice asks for, or null to follow the phone. */
fun languageLocale(choice: LanguageChoice): Locale? = when (choice) {
    LanguageChoice.SYSTEM -> null
    LanguageChoice.ENGLISH -> Locale.ENGLISH
    LanguageChoice.SPANISH -> Locale.forLanguageTag("es-ES")
}

/** The stored theme; an unknown or missing value means the phone decides. */
fun themeChoice(stored: String?): ThemeChoice =
    ThemeChoice.entries.firstOrNull { it.name == stored } ?: ThemeChoice.SYSTEM

/** The stored language; an unknown or missing value means the phone decides. */
fun languageChoice(stored: String?): LanguageChoice =
    LanguageChoice.entries.firstOrNull { it.name == stored } ?: LanguageChoice.SYSTEM

/**
 * The environment set up for [choice]: the localized resources plus the default locale, so
 * date and currency formatting follows the choice as well. Returning the Context unchanged
 * for [LanguageChoice.SYSTEM] leaves the phone's own language alone.
 */
fun localizedContext(context: Context, choice: LanguageChoice): Context {
    val locale = languageLocale(choice)
    // Follow the phone, undoing a language picked earlier in this process.
    Locale.setDefault(locale ?: context.resources.configuration.locales[0])
    if (locale == null) return context
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocale(locale)
    return context.createConfigurationContext(configuration)
}

/** The phone settings kept in one small file next to the data, but not part of the backup. */
@SuppressLint("ApplySharedPref") // These tiny writes must survive an immediate process kill.
class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun theme(): ThemeChoice = themeChoice(prefs.getString(KEY_THEME, null))

    fun language(): LanguageChoice = languageChoice(prefs.getString(KEY_LANGUAGE, null))

    // Finish writing before returning: a reinstall can kill the process immediately after a choice.
    fun theme(choice: ThemeChoice) {
        prefs.edit().putString(KEY_THEME, choice.name).commit()
    }

    fun language(choice: LanguageChoice) {
        prefs.edit().putString(KEY_LANGUAGE, choice.name).commit()
    }

    // Updates. Kept here with the other phone settings, and out of the JSON backup.

    fun autoUpdates(): Boolean = prefs.getBoolean(KEY_AUTO_UPDATES, true)

    fun autoUpdates(on: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATES, on).commit()
    }

    /** When the last automatic check happened, or 0 for never. */
    fun lastCheck(): Long = prefs.getLong(KEY_LAST_CHECK, 0L)

    fun checked(at: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK, at).commit()
    }

    /** The version "Later" put aside, if any, and the moment it wakes up again. */
    fun snoozedVersion(): String? = prefs.getString(KEY_SNOOZED_VERSION, null)

    fun snoozedUntil(): Long = prefs.getLong(KEY_SNOOZED_UNTIL, 0L)

    fun snooze(version: String, until: Long) {
        prefs.edit().putString(KEY_SNOOZED_VERSION, version).putLong(KEY_SNOOZED_UNTIL, until).commit()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_LANGUAGE = "language"
        const val KEY_AUTO_UPDATES = "auto_updates"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_SNOOZED_VERSION = "snoozed_version"
        const val KEY_SNOOZED_UNTIL = "snoozed_until"
    }
}
