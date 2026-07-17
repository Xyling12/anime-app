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
    private val prefs = createEncryptedPrefs(context)

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            createEncryptedPrefsInternal(context)
        } catch (e: Exception) {
            // Файл настроек повреждён или несовместим с текущим ключом шифрования
            // (например, после смены ключа хранилища или битого апдейта) — не должно
            // намертво крашить приложение у существующих пользователей. Удаляем старый
            // файл и создаём хранилище заново.
            when (e) {
                is GeneralSecurityException, is java.io.IOException -> {
                    Log.w(TAG, "Failed to open encrypted prefs, recreating", e)
                    context.deleteSharedPreferences(PREFS_FILE_NAME)
                    createEncryptedPrefsInternal(context)
                }
                else -> throw e
            }
        }
    }

    private fun createEncryptedPrefsInternal(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

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

    /**
     * Режим уведомлений чата: "all" — все сообщения, "mentions" — только @упоминания (дефолт),
     * "off" — тишина. Использует будущий воркер пушей.
     */
    var chatNotifyMode: String
        get() = prefs.getString("chat_notify_mode", "mentions") ?: "mentions"
        set(v) = prefs.edit().putString("chat_notify_mode", v).apply()

    /** Пресет аватара 0–11 (работает и у гостя, у аккаунта синхронизируется с сервером). */
    var avatarId: Int
        get() = prefs.getInt("avatar_id", 0)
        set(v) = prefs.edit().putInt("avatar_id", v).apply()
}
