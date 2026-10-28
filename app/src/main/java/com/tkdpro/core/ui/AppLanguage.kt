package com.tkdpro.core.ui

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tkdpro.R

/**
 * Per-app language. The platform persists the choice (Android 13+ natively, older versions through
 * AppCompat's autoStoreLocales service). SYSTEM follows the device language when it is TR or EN.
 */
enum class AppLanguage(val tag: String, @StringRes val label: Int) {
    SYSTEM("", R.string.language_system),
    ENGLISH("en", R.string.language_english),
    TURKISH("tr", R.string.language_turkish);

    companion object {
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            return entries.firstOrNull { it.tag.isNotEmpty() && tag.startsWith(it.tag) } ?: SYSTEM
        }

        fun apply(language: AppLanguage) {
            val list = if (language == SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language.tag)
            AppCompatDelegate.setApplicationLocales(list)
        }
    }
}
