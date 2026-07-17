package com.anipulse.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EpisodeProgress::class, Favorite::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun favoriteDao(): FavoriteDao
}
