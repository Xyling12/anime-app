package com.anipulse.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anipulse.app.data.db.AppDatabase
import com.anipulse.app.data.db.FavoriteDao
import com.anipulse.app.data.db.ProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // v1→v2: добавлена таблица избранного. Прогресс просмотра не трогаем.
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `favorites` (
                    `animeId` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `score` TEXT,
                    `addedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`animeId`)
                )
                """.trimIndent()
            )
        }
    }

    // v2→v3: статус тайтла в «Моё» (смотрю/в планах/просмотрено).
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `favorites` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'none'")
        }
    }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "anipulse.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun progressDao(db: AppDatabase): ProgressDao = db.progressDao()

    @Provides
    fun favoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()
}
