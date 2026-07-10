package com.example.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("snapscripe_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LANGUAGE = "transcription_language"
        private const val KEY_UI_LANGUAGE = "ui_language"
        private const val KEY_ENCRYPTION = "enable_encryption"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_SHOW_AS_NOTIFICATION = "show_as_notification"
    }

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var uiLanguage: String
        get() = prefs.getString(KEY_UI_LANGUAGE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_UI_LANGUAGE, value).apply()

    var isEncryptionEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENCRYPTION, true)
        set(value) = prefs.edit().putBoolean(KEY_ENCRYPTION, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    var showAsNotification: Boolean
        get() = prefs.getBoolean(KEY_SHOW_AS_NOTIFICATION, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_AS_NOTIFICATION, value).apply()

    fun getTargetLanguageCode(): String {
        val selected = language
        if (selected == "system") {
            val sysLanguage = Locale.getDefault().language
            return if (sysLanguage.startsWith("de")) "de" else "en"
        }
        return selected
    }
}
