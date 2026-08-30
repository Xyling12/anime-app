package com.anipulse.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

const val DATABASE_NAME = "anipulse.db"

@Database(
    entities = [EpisodeProgress::class, Favorite::class, ContinueHidden::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun continueHiddenDao(): ContinueHiddenDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Единственная точка открытия базы: и Hilt, и виджет ходят сюда.
         *
         * Раньше их было две. Hilt открывал `anipulse.db`, а виджет — `getInstance()`
         * с именем `animelib.db` (имя до переименования приложения) и
         * `fallbackToDestructiveMigration`. То есть виджет «Продолжить просмотр» читал
         * пустую параллельную базу и не показывал реальный прогресс. Второй путь удалён,
         * а не исправлен: с шифрованием две независимые точки открытия разъехались бы
         * снова, но уже с ключами.
         */
        fun build(
            context: Context,
            passphrase: String,
            migrations: Array<Migration>,
        ): AppDatabase {
            System.loadLibrary("sqlcipher")
            DatabaseMigration.migratePlaintextIfNeeded(context, DATABASE_NAME, passphrase)
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8)))
                .addMigrations(*migrations)
                .build()
        }

        /**
         * Для компонентов без внедрения зависимостей (BroadcastReceiver виджета).
         * Возвращает тот же экземпляр, что получает остальное приложение.
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: com.anipulse.app.data.db.entryPointDatabase(context).also { INSTANCE = it }
            }
    }
}

/** Достаёт уже собранную Hilt базу — чтобы виджет не открывал её вторым путём. */
private fun entryPointDatabase(context: Context): AppDatabase =
    dagger.hilt.android.EntryPointAccessors.fromApplication(
        context.applicationContext,
        DatabaseEntryPoint::class.java,
    ).database()

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface DatabaseEntryPoint {
    fun database(): AppDatabase
}
