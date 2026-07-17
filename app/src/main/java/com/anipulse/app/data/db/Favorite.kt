package com.anipulse.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Тайтл в «Моё» (избранное). Постер грузим через шлюз по animeId. */
@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val animeId: Long,
    val title: String = "",
    val score: String? = null,
    /** Статус: none / watching / planned / completed. */
    val status: String = "none",
    val addedAt: Long = System.currentTimeMillis(),
)
