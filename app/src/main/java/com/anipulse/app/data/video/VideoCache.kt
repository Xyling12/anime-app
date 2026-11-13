package com.anipulse.app.data.video

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.anipulse.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единый LRU-кэш видеопотоков Media3 (256 МБ) для мгновенной перемотки и экономии трафика.
 * Экземпляр [SimpleCache] обязан быть единственным на каталог `video_cache` в рамках процесса.
 */
@UnstableApi
@Singleton
class VideoCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cacheDir = File(context.cacheDir, "video_cache")
    private val dbProvider by lazy { StandaloneDatabaseProvider(context) }

    val cache: SimpleCache by lazy {
        SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            dbProvider,
        )
    }

    fun mediaSourceFactory(isLocal: Boolean): MediaSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("AniPulse/${BuildConfig.VERSION_NAME} (Android)")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(false)
        val upstream = DefaultDataSource.Factory(context, httpFactory)
        if (isLocal) return DefaultMediaSourceFactory(upstream)

        val cached = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DefaultMediaSourceFactory(cached)
    }

    private companion object {
        const val MAX_CACHE_BYTES = 256L * 1024L * 1024L
    }
}
