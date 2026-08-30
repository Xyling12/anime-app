package com.anipulse.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AppVisibility {
    val foreground = MutableStateFlow(false)
}

fun CoroutineScope.launchForegroundPolling(
    intervalMs: Long,
    screenActive: StateFlow<Boolean>? = null,
    block: suspend () -> Unit,
) = launch {
    combine(
        AppVisibility.foreground,
        screenActive ?: flowOf(true),
    ) { appActive, routeActive -> appActive && routeActive }.collectLatest { active ->
        if (!active) return@collectLatest
        while (currentCoroutineContext().isActive) {
            block()
            delay(intervalMs)
        }
    }
}

@Composable
fun ScreenPollingEffect(onActiveChanged: (Boolean) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onActiveChanged(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> onActiveChanged(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onActiveChanged(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onActiveChanged(false)
        }
    }
}
