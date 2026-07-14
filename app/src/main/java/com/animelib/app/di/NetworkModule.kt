package com.animelib.app.di

import com.animelib.app.data.Api
import com.animelib.app.data.anilibria.AnilibriaApi
import com.animelib.app.data.shikimori.ShikimoriApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "AnimeLib/0.1 (Android)")
                    .build()
            )
        }
        .build()

    private fun retrofit(client: OkHttpClient, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    // Всё через наш шлюз (обход блокировок РФ + ddos-guard). Адреса — в Api.
    @Provides
    @Singleton
    fun shikimori(client: OkHttpClient): ShikimoriApi =
        retrofit(client, Api.SHIKIMORI).create(ShikimoriApi::class.java)

    @Provides
    @Singleton
    fun anilibria(client: OkHttpClient): AnilibriaApi =
        retrofit(client, Api.ANILIBRIA).create(AnilibriaApi::class.java)

    @Provides
    @Singleton
    fun gateway(client: OkHttpClient): com.animelib.app.data.GatewayApi =
        retrofit(client, Api.GATEWAY).create(com.animelib.app.data.GatewayApi::class.java)
}
