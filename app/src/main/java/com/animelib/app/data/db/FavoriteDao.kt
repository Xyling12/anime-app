package com.animelib.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Upsert
    suspend fun upsert(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE animeId = :animeId")
    suspend fun delete(animeId: Long)

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun all(): Flow<List<Favorite>>

    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE animeId = :animeId")
    fun isFavorite(animeId: Long): Flow<Boolean>

    @Query("SELECT * FROM favorites WHERE animeId = :animeId LIMIT 1")
    fun get(animeId: Long): Flow<Favorite?>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    /** Разовый список — для фонового воркера уведомлений о новых сериях. */
    @Query("SELECT * FROM favorites")
    suspend fun allOnce(): List<Favorite>
}
