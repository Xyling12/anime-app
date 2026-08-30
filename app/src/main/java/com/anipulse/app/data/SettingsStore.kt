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
private const val ENCRYPTED_PREFS_FILE_NAME = "anipulse_settings_secure"
private const val TAG = "SettingsStore"

/**
 * Пользовательские настройки в EncryptedSharedPreferences: значения шифруются AES-256-GCM
 * ключом из Android Keystore, который не покидает аппаратное хранилище устройства.
 *
 * Здесь лежит токен авторизации, поэтому обычного MODE_PRIVATE мало: он защищает только
 * от других приложений, но не от чтения при root, кастомном recovery или физическом
 * доступе к разблокированному устройству.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = openPreferences(context)

    /**
     * Keystore иногда становится нечитаемым: сброс биометрии, восстановление образа прошивки,
     * баги вендорских реализаций. Падать с крашем на старте из-за настроек нельзя, поэтому
     * при неудаче шифрованное хранилище пересоздаётся с нуля, а если не помогло и это —
     * приложение продолжает работать на обычном хранилище.
     *
     * Ухудшение молчаливое, и это осознанный компромисс: неработающее приложение хуже,
     * чем работающее с менее защищённым токеном. Токен при этом сбрасывается (см. ниже),
     * так что в незашифрованное хранилище он не попадает — потребуется повторный вход.
     */
    private fun openPreferences(context: Context): SharedPreferences {
        runCatching { createEncrypted(context) }
            .onSuccess { encrypted ->
                migratePlaintextPreferences(context, encrypted)
                return encrypted
            }
            .onFailure { Log.w(TAG, "Не удалось открыть шифрованное хранилище, пересоздаю", it) }

        // Вторая попытка: удаляем повреждённый файл и создаём заново.
        runCatching {
            context.deleteSharedPreferences(ENCRYPTED_PREFS_FILE_NAME)
            createEncrypted(context)
        }.onSuccess { return it }
            .onFailure { Log.e(TAG, "Шифрованное хранилище недоступно, работаю без него", it) }

        val fallback = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        // Токен в незашифрованном виде не храним ни при каких условиях.
        fallback.edit().remove("auth_token").apply()
        return fallback
    }

    @Throws(GeneralSecurityException::class)
    private fun createEncrypted(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Перенос настроек тех, кто обновился с версии на обычном SharedPreferences.
     * Токен намеренно НЕ переносится: он уже лежал открытым текстом и мог быть скомпрометирован,
     * поэтому дешевле попросить войти заново, чем тащить потенциально утёкшее значение дальше.
     */
    private fun migratePlaintextPreferences(context: Context, target: SharedPreferences) {
        val legacy = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        runCatching {
            val editor = target.edit()
            for ((key, value) in legacy.all) {
                if (key == "auth_token") continue
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.apply()
            context.deleteSharedPreferences(PREFS_FILE_NAME)
            Log.i(TAG, "Настройки перенесены в шифрованное хранилище")
        }.onFailure { Log.w(TAG, "Перенос настроек не удался", it) }
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

    /** Полный сброс данных аккаунта на устройстве. Настройки просмотра не трогаем. */
    fun clearAuth() {
        prefs.edit()
            .remove("auth_token")
            .remove("auth_nick")
            .remove("auth_email")
            .remove("auth_admin")
            .apply()
    }

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
