package com.anipulse.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME = "anipulse_settings"
private const val TAG = "SettingsStore"

/** Простые пользовательские настройки (зашифрованные EncryptedSharedPreferences). */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    var autoSkipOpening: Boolean
        get() = prefs.getBoolean("auto_skip_opening", false)
        set(v) = prefs.edit().putBoolean("auto_skip_opening", v).apply()

    var autoSkipRecap: Boolean
        get() = prefs.getBoolean("auto_skip_recap", false)
        set(v) = prefs.edit().putBoolean("auto_skip_recap", v).apply()

    /** Автопереход к следующей серии по окончании текущей (как в Netflix). */
    var autoNextEpisode: Boolean
        get() = prefs.getBoolean("auto_next_episode", true)
        set(v) = prefs.edit().putBoolean("auto_next_episode", v).apply()

    // Аккаунт AniPulse
    var authToken: String?
        get() = prefs.getString("auth_token", null)
        set(v) = prefs.edit().putString("auth_token", v).apply()

    var authNick: String?
        get() = prefs.getString("auth_nick", null)
        set(v) = prefs.edit().putString("auth_nick", v).apply()

    var authEmail: String?
        get() = prefs.getString("auth_email", null)
        set(v) = prefs.edit().putString("auth_email", v).apply()

    /** Админ-роль (кэш из /auth/me; управляет пунктами модерации в UI). */
    var authAdmin: Boolean
        get() = prefs.getBoolean("auth_admin", false)
        set(v) = prefs.edit().putBoolean("auth_admin", v).apply()

    val analyticsInstallId: String
        get() {
            prefs.getString("analytics_install_id", null)?.let { return it }
            val generated = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("analytics_install_id", generated).apply()
            return generated
        }

    /**
     * Режим уведомлений чата: "all" — все сообщения, "mentions" — только @упоминания (дефолт),
     * "off" — тишина. Использует будущий воркер пушей.
     */
    var chatNotifyMode: String
        get() = prefs.getString("chat_notify_mode", "mentions") ?: "mentions"
        set(v) = prefs.edit().putString("chat_notify_mode", v).apply()

    var episodeNotifyIds: Set<String>
        get() = prefs.getStringSet("episode_notify_ids", emptySet())?.toSet() ?: emptySet()
        set(v) = prefs.edit().putStringSet("episode_notify_ids", v.toSet()).apply()

    fun toggleEpisodeNotify(animeId: Long): Boolean {
        val ids = episodeNotifyIds.toMutableSet()
        val enabled = if (animeId.toString() in ids) { ids.remove(animeId.toString()); false }
        else { ids.add(animeId.toString()); true }
        episodeNotifyIds = ids
        return enabled
    }

    /** Пресет аватара 0–11 (работает и у гостя, у аккаунта синхронизируется с сервером). */
    var avatarId: Int
        get() = prefs.getInt("avatar_id", 0)
        set(v) = prefs.edit().putInt("avatar_id", v).apply()

    /** Тёмная тема — фирменная, по умолчанию включена (весь UI построен под тёмный фон). */
    var isDarkTheme: Boolean
        get() = prefs.getBoolean("is_dark_theme", true)
        set(v) = prefs.edit().putBoolean("is_dark_theme", v).apply()
}
