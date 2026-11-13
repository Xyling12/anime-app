package com.anipulse.app.ui.player

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.anipulse.app.data.video.WebPlayerPolicy
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.anipulse.app.data.video.Dub
import com.anipulse.app.data.video.VideoCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

internal fun formatPlaybackSpeed(speed: Float): String = when (speed) {
    1f -> "1x"
    2f -> "2x"
    else -> "${speed.toString().trimEnd('0').trimEnd('.')}x"
}

private fun Activity.hidePlayerSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val componentActivity = activity as? ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var isInPipMode by remember(componentActivity) {
        mutableStateOf(componentActivity?.isInPictureInPictureMode == true)
    }

    DisposableEffect(componentActivity) {
        if (componentActivity == null) return@DisposableEffect onDispose {}
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        componentActivity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { componentActivity.removeOnPictureInPictureModeChangedListener(listener) }
    }

    val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600

    // Полноэкранный ландшафт на время плеера только на смартфонах; на планшетах сохраняем свободную ориентацию и multi-window.
    DisposableEffect(lifecycleOwner, activity, isTablet) {
        val window = activity?.window
        val prevOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    activity?.hidePlayerSystemBars()
                }
                Lifecycle.Event.ON_STOP -> window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!isTablet) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.hidePlayerSystemBars()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!isTablet) {
                activity?.requestedOrientation = prevOrientation
            }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(w, true)
                // вернуть авто-яркость
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val stream = state.stream
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            state.error != null -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error ?: "", color = Color.White)
                TextButton(onClick = viewModel::retry) { Text("Повторить", color = Color.White) }
                TextButton(onClick = onBack) { Text("Назад", color = Color.White) }
            }
            stream != null && stream.isNative -> NativePlayer(state, viewModel, isInPipMode, onBack)
            stream != null && stream.embedUrl != null -> {
                WebPlayer(stream.embedUrl!!)
                // Для fallback-плеера оставляем только кнопку «Назад»
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(8.dp).background(Color(0x66000000), RoundedCornerShape(24.dp)),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White) }
            }
        }
        if (!isInPipMode && (stream == null || !stream.isNative)) {
            com.anipulse.app.ui.common.AgeRatingBadge(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                rating = state.rating,
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@EntryPoint
@InstallIn(SingletonComponent::class)
private interface PlayerCacheEntryPoint {
    fun videoCache(): VideoCache
}

private fun buildPipParams(
    sourceRect: android.graphics.Rect?,
    autoEnter: Boolean,
): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
    if (sourceRect != null && !sourceRect.isEmpty) {
        builder.setSourceRectHint(sourceRect)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter)
        builder.setSeamlessResizeEnabled(true)
    }
    return builder.build()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // кастомные thumb/track у Slider и FlowRow для скоростей
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) // DefaultLoadControl/PlayerView
@Composable
private fun NativePlayer(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    isInPipMode: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }

    val videoCache = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, PlayerCacheEntryPoint::class.java)
            .videoCache()
    }
    var videoBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    val exo = remember {
        // Больше буфера → меньше рывков/перебуферизации на нестабильной сети и медленных CDN.
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 2_000,
                /* bufferForPlaybackAfterRebufferMs = */ 4_000,
            )
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = false
                setHandleAudioBecomingNoisy(true)
            }
    }
    DisposableEffect(exo, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isInPictureInPictureMode != true) {
                exo.pause()
                viewModel.saveProgress(exo.currentPosition, exo.duration.coerceAtLeast(0))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentState by rememberUpdatedState(state)
    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var dubMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var settingsMenu by remember { mutableStateOf(false) }
    var commentsMenu by remember { mutableStateOf(false) }
    // Выбранное качество; по умолчанию — до 720p (плавно; 1080p грузит сеть и лагает на старте).
    var selectedQuality by remember(state.stream) { mutableStateOf(state.stream?.defaultQuality()) }
    // Флаги авто-пропуска (сброс на каждой новой серии)
    var recapSkipped by remember(state.episode) { mutableStateOf(false) }
    var openingSkipped by remember(state.episode) { mutableStateOf(false) }

    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            controlsVisible = false
            dubMenu = false
            qualityMenu = false
            speedMenu = false
            settingsMenu = false
            commentsMenu = false
        }
    }

    LaunchedEffect(activity, videoBounds, isPlaying) {
        if (activity != null) {
            runCatching {
                activity.setPictureInPictureParams(buildPipParams(videoBounds, autoEnter = isPlaying))
            }
        }
    }
    DisposableEffect(activity) {
        onDispose {
            if (activity != null) {
                runCatching {
                    activity.setPictureInPictureParams(buildPipParams(null, autoEnter = false))
                }
            }
        }
    }

    LaunchedEffect(exo, state.playbackSpeed) {
        exo.setPlaybackSpeed(state.playbackSpeed)
    }

    // Загрузка/смена потока (качество/источник) без пересоздания плеера.
    LaunchedEffect(state.stream, selectedQuality) {
        val s = state.stream ?: return@LaunchedEffect
        val q = selectedQuality ?: s.bestQuality()
        val url = s.byQuality[q] ?: s.byQuality.values.firstOrNull() ?: return@LaunchedEffect
        // При первом запуске серии — сохранённая позиция из БД; при смене качества — текущая позиция плеера.
        val resumeAt = if (exo.currentMediaItem == null) state.resumePositionMs else exo.currentPosition
        val isLocal = url.startsWith("file://") || url.startsWith("/")
        exo.setMediaSource(videoCache.mediaSourceFactory(isLocal).createMediaSource(MediaItem.fromUri(url)))
        exo.prepare()
        exo.setPlaybackSpeed(currentState.playbackSpeed)
        if (resumeAt > 1000) exo.seekTo(resumeAt)
        if (
            com.anipulse.app.ui.common.AppVisibility.foreground.value &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            exo.play()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveProgress(exo.currentPosition, exo.duration.coerceAtLeast(0))
            exo.release()
        }
    }

    // Индикаторы жестов
    var seekFeedback by remember { mutableIntStateOf(0) }        // -1 / +1 / 0
    var brightnessOverlay by remember { mutableFloatStateOf(-1f) }
    var volumeOverlay by remember { mutableFloatStateOf(-1f) }
    var gestureVolume by remember { mutableFloatStateOf(0f) }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED && currentState.autoNextEpisode && viewModel.hasNext()) {
                    viewModel.markWatched(exo.currentPosition, exo.duration.coerceAtLeast(0))
                    viewModel.nextEpisode()
                }
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // Тик позиции + периодическое сохранение прогресса + авто-пропуск
    LaunchedEffect(Unit) {
        var ticks = 0
        com.anipulse.app.ui.common.AppVisibility.foreground.collectLatest { active ->
            if (!active) return@collectLatest
            while (true) {
                val latestState = currentState
                positionMs = exo.currentPosition
                durationMs = exo.duration.coerceAtLeast(0)
                isPlaying = exo.isPlaying
                if (++ticks % 10 == 0 && exo.isPlaying) viewModel.saveProgress(positionMs, durationMs)
                val sec = positionMs / 1000
                // Авто-пропуск повтора (в самом начале серии). Прыгаем на начало опенинга
                // (если есть таймкоды) или на 80с — иначе при «текущая + 80_000» улетим за опенинг.
                val opForRecap = latestState.stream?.opening
                val openingStartSec = opForRecap?.start?.toLong()
                if (latestState.autoSkipRecap && !recapSkipped && sec in 3..8 && durationMs > 200_000) {
                    exo.seekTo(openingStartSec?.times(1000L) ?: 80_000L); recapSkipped = true
                }
                // Авто-пропуск опенинга. Правая граница — строго, и прыжок в фиксированную позицию.
                if (latestState.autoSkipOpening && !openingSkipped && durationMs > 200_000) {
                    val op = latestState.stream?.opening
                    if (op != null && op.isValidInterval() && sec >= op.start.toLong() && sec < op.stop.toLong()) {
                        exo.seekTo(op.stop * 1000L); openingSkipped = true
                    } else if (op == null && sec in 5..85) {
                        exo.seekTo(90_000L); openingSkipped = true
                    }
                }
                delay(500)
            }
        }
    }
    // Автоскрытие панелей
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) { delay(3500); controlsVisible = false }
    }
    // Кратковременные индикаторы
    LaunchedEffect(seekFeedback) { if (seekFeedback != 0) { delay(600); seekFeedback = 0 } }
    LaunchedEffect(brightnessOverlay) { if (brightnessOverlay >= 0) { delay(800); brightnessOverlay = -1f } }
    LaunchedEffect(volumeOverlay) { if (volumeOverlay >= 0) { delay(800); volumeOverlay = -1f } }

    Box(
        Modifier
            .fillMaxSize()
            // Тапы: одиночный — показать/скрыть панели; двойной — перемотка ±10с
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        if (offset.x < screenW / 2) {
                            exo.seekTo((exo.currentPosition - 10_000).coerceAtLeast(0)); seekFeedback = -1
                        } else {
                            exo.seekTo(exo.currentPosition + 10_000); seekFeedback = 1
                        }
                    },
                )
            }
            // Свайпы по вертикали: слева — яркость, справа — громкость
            .pointerInput(audio, screenH) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (offset.x >= screenW / 2) {
                            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            gestureVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                        }
                    },
                ) { change, dy ->
                    if (change.position.x < screenW / 2) {
                        val win = activity?.window ?: return@detectVerticalDragGestures
                        val attrs = win.attributes
                        var b = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                        b = (b - dy / screenH).coerceIn(0.01f, 1f)
                        attrs.screenBrightness = b; win.attributes = attrs
                        brightnessOverlay = b
                    } else {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        gestureVolume = (gestureVolume - dy / screenH * 1.5f).coerceIn(0f, 1f)
                        val nv = (gestureVolume * max).roundToInt().coerceIn(0, max)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                        volumeOverlay = gestureVolume
                    }
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx).inflate(
                    com.anipulse.app.R.layout.player_view,
                    android.widget.FrameLayout(ctx),
                    false,
                ) as PlayerView).apply {
                    player = exo
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val rect = coords.boundsInWindow()
                    videoBounds = android.graphics.Rect(
                        rect.left.roundToInt(),
                        rect.top.roundToInt(),
                        rect.right.roundToInt(),
                        rect.bottom.roundToInt(),
                    )
                },
        )

        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)

        // Индикатор перемотки
        if (seekFeedback != 0) {
            Row(
                Modifier
                    .align(if (seekFeedback < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (seekFeedback < 0) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp),
                )
            }
        }
        // Индикаторы яркости/громкости
        if (brightnessOverlay >= 0) GestureBadge(Icons.Filled.Brightness6, brightnessOverlay)
        if (volumeOverlay >= 0) GestureBadge(Icons.AutoMirrored.Filled.VolumeUp, volumeOverlay)

        // Панели управления (с автоскрытием)
        AnimatedVisibility(visible = controlsVisible && !isInPipMode, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                // Верхняя строка
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                    // Заголовок ограничен одной строкой с троеточием, чтобы длинное название
                    // не растягивало шапку вниз и не выталкивало правые кнопки (в т.ч. мини-плеер).
                    Row(
                        Modifier.weight(1f).padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.title.isNotBlank()) {
                            Text(
                                state.title,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        Text(
                            if (state.title.isNotBlank()) " · Серия ${state.episode}" else "Серия ${state.episode}",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                        com.anipulse.app.ui.common.AgeRatingBadge(
                            modifier = Modifier.padding(start = 10.dp),
                            rating = state.rating,
                        )
                    }
                    // Выбор качества
                    val qualities = state.stream?.byQuality?.keys?.sortedDescending().orEmpty()
                    if (qualities.isNotEmpty()) {
                        TextButton(
                            onClick = { qualityMenu = !qualityMenu; dubMenu = false; speedMenu = false; settingsMenu = false; commentsMenu = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("${selectedQuality ?: qualities.first()}p", color = Color.White, maxLines = 1, softWrap = false)
                        }
                    }
                    state.selectedDub?.let { dub ->
                        TextButton(
                            onClick = { dubMenu = !dubMenu; qualityMenu = false; speedMenu = false; settingsMenu = false; commentsMenu = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.widthIn(max = 150.dp),
                        ) {
                            Text(
                                dub.title,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { commentsMenu = !commentsMenu; speedMenu = false; settingsMenu = false; dubMenu = false; qualityMenu = false }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Комментарии к серии", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            controlsVisible = false
                            dubMenu = false
                            qualityMenu = false
                            speedMenu = false
                            settingsMenu = false
                            commentsMenu = false
                            runCatching {
                                activity?.enterPictureInPictureMode(
                                    buildPipParams(videoBounds, autoEnter = true),
                                )
                            }.onFailure {
                                Toast.makeText(context, "Мини-плеер недоступен на этом устройстве", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Плавающее окно", tint = Color.White)
                    }
                    IconButton(onClick = { settingsMenu = !settingsMenu; speedMenu = false; dubMenu = false; qualityMenu = false; commentsMenu = false }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = Color.White)
                    }
                }

                // Центр: предыдущая серия / play-pause / следующая серия
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    IconButton(
                        onClick = { viewModel.prevEpisode(); controlsVisible = true },
                        enabled = viewModel.hasPrev(),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious, contentDescription = "Предыдущая серия",
                            tint = if (viewModel.hasPrev()) Color.White else Color(0x55FFFFFF),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (exo.isPlaying) exo.pause() else exo.play()
                            isPlaying = exo.isPlaying
                            controlsVisible = true
                        },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.nextEpisode(); controlsVisible = true },
                        enabled = viewModel.hasNext(),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.SkipNext, contentDescription = "Следующая серия",
                            tint = if (viewModel.hasNext()) Color.White else Color(0x55FFFFFF),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                // Нижняя строка: время, сикбар, следующая серия
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        // Классический вид сикбара: круглый бегунок + сплошная дорожка.
                        // Дефолт Material3 1.3 (вертикальный «столбик» с разрывом дорожки)
                        // на нулевой позиции выглядит оторванным от дорожки — «висит в пустоте».
                        val sliderColors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color(0x55FFFFFF),
                        )
                        Slider(
                            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                            onValueChange = { frac ->
                                controlsVisible = true
                                if (durationMs > 0) { positionMs = (frac * durationMs).toLong() }
                            },
                            onValueChangeFinished = { exo.seekTo(positionMs) },
                            colors = sliderColors,
                            thumb = {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .background(Color.White, androidx.compose.foundation.shape.CircleShape),
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    colors = sliderColors,
                                    thumbTrackGapSize = 0.dp,
                                    trackInsideCornerSize = 0.dp,
                                    drawStopIndicator = null,
                                    modifier = Modifier.height(4.dp),
                                )
                            },
                        )
                        // Метки: конец опенинга (жёлтая), начало эндинга (оранжевая)
                        if (durationMs > 0) {
                            state.stream?.opening?.takeIf { it.isValidInterval() }?.let { op ->
                                SeekMarker(op.stop * 1000L / durationMs.toFloat(), Color(0xFFFFD54F))
                            }
                            state.stream?.ending?.takeIf { it.isValidEnding(durationMs) }?.let { ed ->
                                SeekMarker(ed.start * 1000L / durationMs.toFloat(), Color(0xFFFF8A65))
                            }
                        }
                    }
                    Text(formatTime(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    TextButton(
                        onClick = {
                            speedMenu = !speedMenu
                            qualityMenu = false
                            dubMenu = false
                            settingsMenu = false
                            commentsMenu = false
                            controlsVisible = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            formatPlaybackSpeed(state.playbackSpeed),
                            color = if (state.playbackSpeed != 1f) MaterialTheme.colorScheme.primary else Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }

        if (!isInPipMode) {
            // Кнопка «Пропустить опенинг»: по таймкодам (AniLibria) либо запасной прыжок ~на 90с для любого источника.
            // Правая граница диапазона — строго (until), иначе кнопка не пропадает после seekTo на op.stop.
            // Прыжок делаем в фиксированную позицию (op.stop*1000 или 90_000), а не «текущая + N»:
            // иначе при втором тике после нажатия можно улететь за опенинг.
            run {
                val op = state.stream?.opening?.takeIf { it.isValidInterval() }
                val sec = positionMs / 1000
                val showByTimecode = op != null && sec >= op.start.toLong() && sec < op.stop.toLong()
                val showGeneric = op == null && sec in 5L..80L && durationMs > 200_000
                if (!openingSkipped && (showByTimecode || showGeneric)) {
                    TextButton(
                        onClick = {
                            if (op != null) exo.seekTo(op.stop * 1000L) else exo.seekTo(90_000L)
                            openingSkipped = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 128.dp, end = 16.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(8.dp)),
                    ) { Text("Пропустить опенинг ⏭", color = Color.White) }
                }
            }

            // Кнопка «Следующая серия» на эндинге (по таймкодам AniSkip)
            // Перед переходом помечаем текущую серию просмотренной — иначе при эндинге ~80–95%
            // видео галочка не загорается и тайтл снова предлагают «продолжить».
            state.stream?.ending?.takeIf { it.isValidEnding(durationMs) }?.let { ed ->
                val sec = positionMs / 1000
                if (sec >= ed.start && viewModel.hasNext()) {
                    TextButton(
                        onClick = {
                            viewModel.markWatched(positionMs, durationMs)
                            viewModel.nextEpisode()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 72.dp, end = 16.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                    ) { Text("Следующая серия ⏭", color = Color.White) }
                }
            }
        }

        // Слой-диссмисс: тап мимо закрывает открытое меню
        if (dubMenu || qualityMenu || speedMenu || settingsMenu || commentsMenu) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(dubMenu, qualityMenu, speedMenu, settingsMenu, commentsMenu) {
                        detectTapGestures { dubMenu = false; qualityMenu = false; speedMenu = false; settingsMenu = false; commentsMenu = false }
                    }
            )
        }

        if (!isInPipMode) {
            // Кнопка «Пропустить повтор» (в начале серии)
            // Прыгаем в начало опенинга (если есть таймкоды) либо на фиксированные 80с.
            // Не прибавляем «текущая + 80_000» — иначе улетим за опенинг при втором тике.
            run {
                val sec = positionMs / 1000
                val op = state.stream?.opening?.takeIf { it.isValidInterval() }
                val openingStart = op?.start?.toLong()
                val beforeOpening = openingStart == null || sec < openingStart
                if (!recapSkipped && sec in 3L..15L && beforeOpening && durationMs > 200_000) {
                    TextButton(
                        onClick = {
                            exo.seekTo(openingStart?.times(1000L) ?: 80_000L)
                            recapSkipped = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            // Вертикальный стек: следующая серия 72dp, опенинг 128dp, повтор 184dp.
                            // Раньше повтор (120dp) физически накладывался на опенинг (128dp).
                            .padding(bottom = 184.dp, end = 16.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(8.dp)),
                    ) { Text("Пропустить повтор ⏭", color = Color.White) }
                }
            }

            if (!controlsVisible) {
                com.anipulse.app.ui.common.AgeRatingBadge(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    rating = state.rating,
                )
            }
        }

        // Панель настроек плеера
        // Панель комментариев к текущей серии
        AnimatedVisibility(visible = commentsMenu, modifier = Modifier.align(Alignment.CenterEnd)) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 380.dp)
                    .background(Color(0xF2101018))
                    .imePadding()
                    .padding(12.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Text(
                    "Серия ${state.episode} · обсуждение",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.comments.isEmpty()) {
                        item {
                            Text(
                                "Пока нет комментариев — будь первым!",
                                color = Color(0xFF9D9AB0),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(state.comments, key = { "cm-${it.id}" }) { cm ->
                        Row {
                            com.anipulse.app.ui.common.Avatar(
                                cm.avatar,
                                26.dp,
                                nick = cm.nick,
                                rev = cm.avatarRev,
                                accountId = cm.userId,
                            )
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(
                                    cm.nick,
                                    color = Color(0xFFFF4D8D),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(cm.text, color = Color.White, style = MaterialTheme.typography.bodySmall)
                                if (state.isLoggedIn) {
                                    Text(
                                        "Пожаловаться",
                                        color = Color(0xFFFF8AAF),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.clickable { viewModel.reportComment(cm) }.padding(top = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.isLoggedIn) {
                    var commentInput by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Комментарий…", color = Color(0xFF9D9AB0)) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFFFF4D8D),
                                focusedContainerColor = Color(0xFF15151F),
                                unfocusedContainerColor = Color(0xFF15151F),
                                focusedBorderColor = Color(0xFFFF4D8D),
                                unfocusedBorderColor = Color(0xFF343442),
                            ),
                            maxLines = 2,
                        )
                        IconButton(
                            onClick = { viewModel.postComment(commentInput); commentInput = "" },
                            enabled = !state.commentSending && commentInput.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = Color(0xFFFF4D8D))
                        }
                    }
                } else {
                    Text(
                        "Войдите в Профиле, чтобы писать",
                        color = Color(0xFF9D9AB0),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }

        AnimatedVisibility(visible = settingsMenu, modifier = Modifier.align(Alignment.CenterEnd)) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .background(Color(0xE6000000))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Настройки плеера", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Автопропуск опенинга", color = Color.White, modifier = Modifier.width(200.dp))
                    Switch(checked = state.autoSkipOpening, onCheckedChange = viewModel::setAutoSkipOpening)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Автопропуск повтора", color = Color.White, modifier = Modifier.width(200.dp))
                    Switch(checked = state.autoSkipRecap, onCheckedChange = viewModel::setAutoSkipRecap)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Автопереход к след. серии", color = Color.White, modifier = Modifier.width(200.dp))
                    Switch(checked = state.autoNextEpisode, onCheckedChange = viewModel::setAutoNextEpisode)
                }
                Text("Скорость воспроизведения", color = Color.White, fontWeight = FontWeight.Medium)
                FlowRow(
                    modifier = Modifier.width(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PLAYBACK_SPEEDS.forEach { spd ->
                        FilterChip(
                            selected = spd == state.playbackSpeed,
                            onClick = { viewModel.setPlaybackSpeed(spd) },
                            label = { Text(formatPlaybackSpeed(spd)) },
                        )
                    }
                }
            }
        }

        // Меню выбора скорости воспроизведения
        AnimatedVisibility(visible = speedMenu, modifier = Modifier.align(Alignment.CenterEnd)) {
            Column(
                Modifier.fillMaxHeight().background(Color(0xE6000000)).padding(12.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Скорость", color = Color.White, fontWeight = FontWeight.SemiBold)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PLAYBACK_SPEEDS, key = { it }) { spd ->
                        FilterChip(
                            selected = spd == state.playbackSpeed,
                            onClick = { viewModel.setPlaybackSpeed(spd); speedMenu = false },
                            label = { Text(formatPlaybackSpeed(spd)) },
                        )
                    }
                }
            }
        }

        // Меню выбора озвучки
        AnimatedVisibility(visible = dubMenu, modifier = Modifier.align(Alignment.CenterEnd)) {
            DubMenu(state.dubs, state.selectedDub) { viewModel.selectDub(it); dubMenu = false }
        }

        // Меню выбора качества
        AnimatedVisibility(visible = qualityMenu, modifier = Modifier.align(Alignment.CenterEnd)) {
            val qualities = state.stream?.byQuality?.keys?.sortedDescending().orEmpty()
            Column(
                Modifier.fillMaxHeight().background(Color(0xE6000000)).padding(12.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Качество", color = Color.White, fontWeight = FontWeight.SemiBold)
                qualities.forEach { q ->
                    FilterChip(
                        selected = q == (selectedQuality ?: qualities.firstOrNull()),
                        onClick = { selectedQuality = q; qualityMenu = false },
                        label = { Text("${q}p") },
                    )
                }
            }
        }
    }
}

/** Вертикальная метка на дорожке перемотки в позиции fraction (0..1). */
@Composable
private fun BoxScope.SeekMarker(fraction: Float, color: Color) {
    Box(
        Modifier
            .align(androidx.compose.ui.BiasAlignment(horizontalBias = fraction.coerceIn(0f, 1f) * 2 - 1, verticalBias = 0f))
            .width(3.dp)
            .height(12.dp)
            .background(color, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun GestureBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.background(Color(0xAA000000), RoundedCornerShape(12.dp)).padding(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Spacer(Modifier.size(8.dp))
            LinearProgressIndicator(
                progress = { value },
                modifier = Modifier.width(120.dp),
                color = Color.White,
                trackColor = Color(0x55FFFFFF),
            )
        }
    }
}

@Composable
private fun DubMenu(dubs: List<Dub>, selected: Dub?, onSelect: (Dub) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .background(Color(0xE6000000))
            .padding(12.dp)
            .pointerInput(Unit) { detectTapGestures { } },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Озвучка", color = Color.White, fontWeight = FontWeight.SemiBold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(dubs, key = { it.id }) { dub ->
                FilterChip(
                    selected = dub.id == selected?.id,
                    onClick = { onSelect(dub) },
                    label = { Text(dub.title) },
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPlayer(url: String) {
    if (!WebPlayerPolicy.isAllowedInitialUrl(url)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text("Недопустимый источник плеера", color = Color.White)
        }
        return
    }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                tag = url
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val target = request?.url?.toString().orEmpty()
                        val currentInitial = (view?.tag as? String) ?: url
                        return !WebPlayerPolicy.isAllowedNavigation(target, currentInitial)
                    }
                }
                loadUrl(url)
            }
        },
        update = { view ->
            if (view.tag != url && WebPlayerPolicy.isAllowedInitialUrl(url)) {
                view.tag = url
                view.loadUrl(url)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        },
        modifier = Modifier.fillMaxSize(),
    )
}
