package com.anderei.yafm.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val tag: String, val displayName: String) {
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    PORTUGUESE("pt", "Português"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("yafm_settings", Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _trashRetentionDays = MutableStateFlow(
        prefs.getInt(KEY_TRASH_DAYS, DEFAULT_TRASH_DAYS).coerceIn(MIN_TRASH_DAYS, MAX_TRASH_DAYS),
    )
    val trashRetentionDays: StateFlow<Int> = _trashRetentionDays.asStateFlow()

    private val _accessMode = MutableStateFlow(loadAccessMode())
    val accessMode: StateFlow<AccessMode> = _accessMode.asStateFlow()

    private val _useRootForAppData = MutableStateFlow(
        prefs.getBoolean(KEY_USE_ROOT_FOR_APP_DATA, false),
    )
    val useRootForAppData: StateFlow<Boolean> = _useRootForAppData.asStateFlow()

    private val _correctSelinuxContext = MutableStateFlow(
        prefs.getBoolean(KEY_CORRECT_SELINUX_CONTEXT, true),
    )
    val correctSelinuxContext: StateFlow<Boolean> = _correctSelinuxContext.asStateFlow()

    /** Stable LocalSend identity; avoids discovering ourselves and keeps device identity. */
    val senderFingerprint: String = prefs.getString(KEY_SENDER_FINGERPRINT, null)
        ?: UUID.randomUUID().toString().also { generated ->
            prefs.edit().putString(KEY_SENDER_FINGERPRINT, generated).apply()
        }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
        _language.value = language
    }

    fun setTrashRetentionDays(days: Int) {
        val clamped = days.coerceIn(MIN_TRASH_DAYS, MAX_TRASH_DAYS)
        prefs.edit().putInt(KEY_TRASH_DAYS, clamped).apply()
        _trashRetentionDays.value = clamped
    }

    fun setAccessMode(mode: AccessMode) {
        prefs.edit().putString(KEY_ACCESS_MODE, mode.name).apply()
        _accessMode.value = mode
    }

    fun setUseRootForAppData(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_ROOT_FOR_APP_DATA, enabled).apply()
        _useRootForAppData.value = enabled
    }

    fun setCorrectSelinuxContext(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CORRECT_SELINUX_CONTEXT, enabled).apply()
        _correctSelinuxContext.value = enabled
    }

    private fun loadAccessMode(): AccessMode {
        val stored = prefs.getString(KEY_ACCESS_MODE, null) ?: return AccessMode.STANDARD
        return runCatching { AccessMode.valueOf(stored) }.getOrDefault(AccessMode.STANDARD)
    }

    private fun loadLanguage(): AppLanguage {
        val stored = prefs.getString(KEY_LANGUAGE, null)
        if (stored != null) return AppLanguage.fromTag(stored)
        return when (Locale.getDefault().language) {
            "es" -> AppLanguage.SPANISH
            "pt" -> AppLanguage.PORTUGUESE
            else -> AppLanguage.ENGLISH
        }
    }

    companion object {
        const val DEFAULT_TRASH_DAYS = 30
        const val MIN_TRASH_DAYS = 1
        const val MAX_TRASH_DAYS = 365
        private const val KEY_LANGUAGE = "language"
        private const val KEY_TRASH_DAYS = "trash_retention_days"
        private const val KEY_ACCESS_MODE = "access_mode"
        private const val KEY_USE_ROOT_FOR_APP_DATA = "use_root_for_app_data"
        private const val KEY_CORRECT_SELINUX_CONTEXT = "correct_selinux_context"
        private const val KEY_SENDER_FINGERPRINT = "sender_fingerprint"
    }
}

/** Creates a configuration context for the selected app language. */
fun Context.withAppLanguage(language: AppLanguage): Context {
    val locale = Locale.forLanguageTag(language.tag)
    Locale.setDefault(locale)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    return createConfigurationContext(configuration)
}
