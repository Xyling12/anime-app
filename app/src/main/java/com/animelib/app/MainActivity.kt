package com.animelib.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.animelib.app.data.SettingsStore
import com.animelib.app.ui.AnimeLibRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
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
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthDeepLink(intent)
    }

    /** Возврат из OAuth-браузера: anipulse://auth?token=..&nick=.. или ?linked=vk */
    private fun handleAuthDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "anipulse" || uri.host != "auth") return
        val token = uri.getQueryParameter("token")
        val nick = uri.getQueryParameter("nick")
        val linked = uri.getQueryParameter("linked")
        when {
            token != null -> {
                settings.authToken = token
                settings.authNick = nick
                settings.authEmail = null
                Toast.makeText(this, "Добро пожаловать, ${nick ?: "друг"}!", Toast.LENGTH_LONG).show()
            }
            linked != null -> {
                Toast.makeText(this, "Сервис привязан: $linked", Toast.LENGTH_LONG).show()
            }
        }
    }
}
