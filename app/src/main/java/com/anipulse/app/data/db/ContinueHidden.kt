package com.anipulse.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Тайтл, скрытый пользователем из ленты «Продолжить просмотр».
 *
 * Хранится не флагом «скрыт навсегда», а меткой времени: строка прячет тайтл только пока
 * `hiddenAt` свежее последнего прогресса. Стоит снова открыть серию — `updatedAt`
 * в episode_progress обгонит метку, и тайтл вернётся в ленту сам.
 * Иначе «скрыть» означало бы «никогда больше не показывать», и вернуть тайтл было бы нечем.
 */
@Entity(tableName = "continue_hidden")
data class ContinueHidden(
    @PrimaryKey val animeId: Long,
    val hiddenAt: Long = System.currentTimeMillis(),
)

@Dao
interface ContinueHiddenDao {

    @Upsert
    suspend fun hide(row: ContinueHidden)

    @Query("DELETE FROM continue_hidden WHERE animeId = :animeId")
    suspend fun unhide(animeId: Long)

    @Query("SELECT * FROM continue_hidden")
    fun all(): Flow<List<ContinueHidden>>
}
