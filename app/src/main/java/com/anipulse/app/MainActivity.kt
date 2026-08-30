package com.anipulse.app

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import com.anipulse.app.data.GatewayApi
import com.anipulse.app.data.SettingsStore
import com.anipulse.app.ui.AnimeLibRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var gateway: GatewayApi
    @Inject lateinit var syncRepository: com.anipulse.app.data.SyncRepository

    private var analyticsJob: Job? = null
    /** После обмена одноразового App Link-кода токен дополнительно проверяется через /auth/me
     * и сохраняется только после явного подтверждения пользователя. */
    private var pendingLogin by mutableStateOf<Pair<String, String>?>(null) // token to nick

    override fun onStart() {
        super.onStart()
        com.anipulse.app.ui.common.AppVisibility.foreground.value = true
        analyticsJob?.cancel()
        analyticsJob = lifecycleScope.launch {
            sendAnalytics("start")
            while (isActive) {
                delay(60_000)
                sendAnalytics("heartbeat")
            }
        }
    }

    override fun onStop() {
        com.anipulse.app.ui.common.AppVisibility.foreground.value = false
        analyticsJob?.cancel()
        analyticsJob = null
        lifecycleScope.launch { sendAnalytics("stop") }
        super.onStop()
    }

    private suspend fun sendAnalytics(event: String) {
        val bearer = settings.authToken?.let { "Bearer $it" }
        runCatching {
            gateway.analyticsHeartbeat(
                bearer,
                com.anipulse.app.data.AnalyticsHeartbeatRequest(
                    installId = settings.analyticsInstallId,
                    version = BuildConfig.VERSION_NAME,
                    event = event,
                ),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)

        // Подпись сверяется до всего остального: если APK перепакован, дальше идти незачем.
        // Токен при этом стирается — в изменённой сборке ему верить нельзя, а именно за ним
        // такие пересборки обычно и делают.
        if (!com.anipulse.app.data.AppIntegrity.isSignatureValid(this)) {
            settings.clearAuth()
            Toast.makeText(
                this,
                "Эта сборка изменена и не является официальной. Скачайте AniPulse с anipulsetv.ru",
                Toast.LENGTH_LONG,
            ).show()
            // finish() закрывает только экран, процесс остаётся жить и может доработать
            // уже запущенные корутины. В изменённой сборке этого допускать нельзя,
            // поэтому после показа сообщения процесс завершается целиком.
            finishAndRemoveTask()
            // Именно Handler главного лупера, а не View.postDelayed: из onCreate выходим
            // до setContent, decorView ни к чему не присоединён, и его очередь никогда
            // не выполнится — процесс тогда остаётся жить (проверено на эмуляторе).
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ kotlin.system.exitProcess(0) }, 3_000)
            return
        }

        enableEdgeToEdge()
        // Android 13+: разрешение на пуши (каналы: ЛС, @упоминания, друзья, новые серии)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        handleAuthDeepLink(intent)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { syncRepository.syncAll() } }
        setContent {
            Box {
                AnimeLibRoot()
                pendingLogin?.let { (token, nick) ->
                    AlertDialog(
                        onDismissRequest = { pendingLogin = null },
                        title = { Text("Подтвердите вход") },
                        text = { Text("Войти как $nick?") },
                        confirmButton = {
                            TextButton(onClick = {
                                settings.authToken = token
                                settings.authNick = nick
                                settings.authEmail = null
                                lifecycleScope.launch { runCatching { syncRepository.syncAll() } }
                                sendBroadcast(Intent(ACTION_AUTH_CHANGED).setPackage(packageName))
                                pendingLogin = null
                                Toast.makeText(this@MainActivity, "Добро пожаловать, $nick!", Toast.LENGTH_LONG).show()
                            }) { Text("Войти") }
                        },
                        dismissButton = { TextButton(onClick = { pendingLogin = null }) { Text("Отмена") } },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthDeepLink(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        analyticsJob?.cancel()
    }

    /** Возврат только по проверенной Android App Link. В URL находится одноразовый код, не bearer-токен. */
    private fun handleAuthDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "https" || uri.host != "anipulsetv.ru" || uri.path != "/auth/android-callback") return
        val code = uri.getQueryParameter("code")
        val linked = uri.getQueryParameter("linked")
        when {
            !code.isNullOrBlank() -> exchangeAndPromptLogin(code)
            linked != null -> Toast.makeText(this, "Сервис привязан: $linked", Toast.LENGTH_LONG).show()
        }
    }

    private fun exchangeAndPromptLogin(code: String) {
        if (code.length !in 32..128) return
        lifecycleScope.launch {
            val response = runCatching {
                gateway.exchangeOAuthCode(com.anipulse.app.data.OAuthCodeRequest(code))
            }.getOrNull()
            val token = response?.token
            if (token != null) validateAndPromptLogin(token)
            else Toast.makeText(this@MainActivity, "Ссылка входа уже использована или устарела", Toast.LENGTH_LONG).show()
        }
    }

    /** Токен подтверждается сервером (не доверяем query-параметру nick) до показа диалога входа. */
    private fun validateAndPromptLogin(token: String) {
        lifecycleScope.launch {
            val nick = runCatching { gateway.me("Bearer $token") }.getOrNull()?.nick
            if (nick != null) {
                pendingLogin = token to nick
            } else {
                Toast.makeText(this@MainActivity, "Не удалось войти — ссылка недействительна", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION_AUTH_CHANGED = "com.anipulse.app.AUTH_CHANGED"
    }
}
