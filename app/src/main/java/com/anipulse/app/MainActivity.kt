package com.anipulse.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anipulse.app.data.GatewayApi
import com.anipulse.app.data.SettingsStore
import com.anipulse.app.ui.AnimeLibRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var gateway: GatewayApi

    private val scope = MainScope()
    /** Ссылка `anipulse://auth?token=` может прийти от чужого приложения/страницы —
     * токен сначала проверяем через /auth/me и просим подтверждение, а не сохраняем вслепую. */
    private var pendingLogin by mutableStateOf<Pair<String, String>?>(null) // token to nick

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
        setContent {
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
                            pendingLogin = null
                            Toast.makeText(this, "Добро пожаловать, $nick!", Toast.LENGTH_LONG).show()
                        }) { Text("Войти") }
                    },
                    dismissButton = { TextButton(onClick = { pendingLogin = null }) { Text("Отмена") } },
                )
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

    /** Возврат из OAuth-браузера: anipulse://auth?token=.. или ?linked=vk */
    private fun handleAuthDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "anipulse" || uri.host != "auth") return
        val token = uri.getQueryParameter("token")
        val linked = uri.getQueryParameter("linked")
        when {
            token != null -> validateAndPromptLogin(token)
            linked != null -> Toast.makeText(this, "Сервис привязан: $linked", Toast.LENGTH_LONG).show()
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
}
