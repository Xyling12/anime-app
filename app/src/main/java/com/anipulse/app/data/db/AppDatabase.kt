package com.anipulse.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [EpisodeProgress::class, Favorite::class, ContinueHidden::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun continueHiddenDao(): ContinueHiddenDao
}
