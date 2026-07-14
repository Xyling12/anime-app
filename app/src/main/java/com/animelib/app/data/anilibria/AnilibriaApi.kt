package com.animelib.app.data.anilibria

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class AlRelease(
    val id: Long,
    val name: AlName? = null,
    @SerialName("episodes_total") val episodesTotal: Int = 0,
    val episodes: List<AlEpisode> = emptyList(),
)

@Serializable
data class AlName(val main: String? = null, val english: String? = null)

@Serializable
data class AlEpisode(
    val id: String,
    val ordinal: Float = 0f,
    val name: String? = null,
    @SerialName("hls_480") val hls480: String? = null,
    @SerialName("hls_720") val hls720: String? = null,
    @SerialName("hls_1080") val hls1080: String? = null,
    val opening: AlSkip? = null,
    val ending: AlSkip? = null,
)

@Serializable
data class AlSkip(val start: Int? = null, val stop: Int? = null)

interface AnilibriaApi {
    /** Поиск релизов по названию. Ответ — голый массив релизов. */
    @GET("api/v1/app/search/releases")
    suspend fun search(@Query("query") query: String): List<AlRelease>

    /** Полный релиз со списком эпизодов и HLS-ссылками. */
    @GET("api/v1/anime/releases/{id}")
    suspend fun release(@Path("id") id: Long): AlRelease
}
