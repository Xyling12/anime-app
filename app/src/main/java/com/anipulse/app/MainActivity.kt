package com.anipulse.app

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    private val scope = MainScope()
    private var analyticsJob: Job? = null
    /** После обмена одноразового App Link-кода токен дополнительно проверяется через /auth/me
     * и сохраняется только после явного подтверждения пользователя. */
    private var pendingLogin by mutableStateOf<Pair<String, String>?>(null) // token to nick

    override fun onStart() {
        super.onStart()
        com.anipulse.app.ui.common.AppVisibility.foreground.value = true
        analyticsJob?.cancel()
        analyticsJob = scope.launch {
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
        scope.launch { sendAnalytics("stop") }
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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android 13+: разрешение на пуши (каналы: ЛС, @упоминания, друзья, новые серии)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        handleAuthDeepLink(intent)
        scope.launch { runCatching { syncRepository.syncAll() } }
        if (savedInstanceState == null) {
            com.anipulse.app.notify.SoundPlayer.playMessageSound(this)
        }
        setContent {
            var showLaunch by remember { mutableStateOf(savedInstanceState == null) }
            val betaNoticePrefs = remember {
                getSharedPreferences("anipulse_onboarding", Context.MODE_PRIVATE)
            }
            var showBetaNotice by remember {
                mutableStateOf(
                    savedInstanceState == null &&
                        !betaNoticePrefs.getBoolean("beta_notice_0_4_seen", false),
                )
            }
            LaunchedEffect(showLaunch) {
                if (showLaunch) {
                    delay(1150)
                    showLaunch = false
                }
            }
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
                            scope.launch { runCatching { syncRepository.syncAll() } }
                            sendBroadcast(Intent(ACTION_AUTH_CHANGED).setPackage(packageName))
                            pendingLogin = null
                            Toast.makeText(this@MainActivity, "Добро пожаловать, $nick!", Toast.LENGTH_LONG).show()
                        }) { Text("Войти") }
                    },
                    dismissButton = { TextButton(onClick = { pendingLogin = null }) { Text("Отмена") } },
                )
            }
            if (showLaunch) com.anipulse.app.ui.common.LaunchPulseOverlay()
            if (!showLaunch && showBetaNotice) {
                AlertDialog(
                    onDismissRequest = {},
                    icon = {
                        androidx.compose.foundation.layout.Box(
                            androidx.compose.ui.Modifier
                                .background(androidx.compose.ui.graphics.Color(0x33FF4D8D), androidx.compose.foundation.shape.CircleShape)
                                .padding(14.dp),
                        ) { Icon(Icons.Filled.Info, null, tint = androidx.compose.ui.graphics.Color(0xFFFF4D8D)) }
                    },
                    title = { Text("AniPulse в бета-режиме", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                    text = {
                        Text(
                            "В приложении ещё могут встречаться ошибки и недоработки. " +
                                "Спасибо за понимание и помощь в развитии AniPulse!\n\n" +
                                "Если захотите сообщить о проблеме, отправьте баг-репорт через профиль.",
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                betaNoticePrefs.edit()
                                    .putBoolean("beta_notice_0_4_seen", true)
                                    .apply()
                                showBetaNotice = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFF4D8D)),
                        ) {
                            Text("Понятно")
                        }
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    containerColor = androidx.compose.ui.graphics.Color(0xFF15151F),
                    tonalElevation = 0.dp,
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
        scope.cancel()
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
        scope.launch {
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
        scope.launch {
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
