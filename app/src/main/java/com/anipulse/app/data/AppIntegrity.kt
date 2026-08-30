package com.anipulse.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.anipulse.app.BuildConfig
import java.security.MessageDigest

private const val TAG = "AppIntegrity"

/**
 * Проверка, что APK подписан нашим ключом и не был перепакован.
 *
 * Типовая атака на бесплатное приложение вне Google Play: злоумышленник берёт APK,
 * дизассемблирует, добавляет код (кража токена, подмена адреса шлюза, реклама),
 * подписывает своим ключом и распространяет под тем же названием на файлопомойках.
 * Пользователь уверен, что ставит AniPulse.
 *
 * Изменить APK, сохранив исходную подпись, нельзя — она перестанет сходиться. Поэтому
 * сверка отпечатка сертификата ловит любую модификацию файла.
 *
 * Чего проверка НЕ даёт: она выполняется внутри самого приложения, поэтому атакующий
 * с достаточной мотивацией просто вырежет её вместе с остальным кодом. Это заградительная
 * мера против массовой перепаковки «на потоке», а не защита от целевого реверса —
 * последней в принципе не существует для кода, исполняемого на устройстве атакующего.
 */
object AppIntegrity {

    /**
     * @return true, если подпись совпадает с ожидаемой либо проверка не настроена
     *         (отладочная сборка или сборка из исходников без keystore.properties).
     */
    fun isSignatureValid(context: Context): Boolean {
        val expected = BuildConfig.RELEASE_CERT_SHA256
            .replace(":", "")
            .trim()
            .uppercase()
        if (expected.isEmpty()) return true

        val actual = runCatching { signingFingerprints(context) }
            .onFailure { Log.w(TAG, "Не удалось прочитать подпись пакета", it) }
            .getOrDefault(emptyList())

        if (actual.isEmpty()) {
            // Прочитать собственную подпись и не смочь — сама по себе аномалия, но ронять
            // приложение из-за неё нельзя: у части прошивок PackageManager ведёт себя странно.
            Log.w(TAG, "Подпись пакета недоступна, проверка пропущена")
            return true
        }

        val valid = actual.any { it == expected }
        if (!valid) Log.e(TAG, "Подпись APK не совпадает с ожидаемой — сборка изменена")
        return valid
    }

    private fun signingFingerprints(context: Context): List<String> {
        val pm = context.packageManager
        val packageName = context.packageName

        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return emptyList()
            // hasMultipleSigners различает две несовместимые схемы: при ротации ключа
            // актуальный сертификат лежит в истории подписи, а не в списке подписантов.
            if (signing.hasMultipleSigners()) signing.apkContentsSigners
            else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        } ?: return emptyList()

        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.filterNotNull().map { signature ->
            digest.reset()
            digest.digest(signature.toByteArray()).joinToString("") { byte ->
                "%02X".format(byte)
            }
        }
    }
}
