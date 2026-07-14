package com.animelib.app.data.shikimori

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ShikimoriApi {

    @GET("api/animes")
    suspend fun animes(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        @Query("order") order: String = "popularity",
        @Query("kind") kind: String? = null,
        @Query("status") status: String? = null,
        @Query("season") season: String? = null,
        @Query("genre") genre: String? = null,
        @Query("search") search: String? = null,
        @Query("censored") censored: Boolean = true,
    ): List<ShikiAnime>

    @GET("api/animes/{id}")
    suspend fun animeDetails(@Path("id") id: Long): ShikiAnimeDetails

    @GET("api/genres")
    suspend fun genres(): List<ShikiGenre>

    @GET("api/animes/{id}/similar")
    suspend fun similar(@Path("id") id: Long): List<ShikiAnime>

    @GET("api/calendar")
    suspend fun calendar(@Query("censored") censored: Boolean = false): List<ShikiCalendarEntry>
}
