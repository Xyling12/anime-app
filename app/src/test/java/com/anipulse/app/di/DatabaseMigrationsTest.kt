package com.anipulse.app.di

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DatabaseMigrationsTest {

    private fun recordingDb(statements: MutableList<String>): SupportSQLiteDatabase =
        Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty() && args[0] is String) {
                statements += args[0] as String
            }
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                else -> null
            }
        } as SupportSQLiteDatabase

    @Test
    fun `migrations form contiguous chain from version 1 to 4`() {
        assertEquals(1, DatabaseModule.MIGRATION_1_2.startVersion)
        assertEquals(2, DatabaseModule.MIGRATION_1_2.endVersion)
        assertEquals(2, DatabaseModule.MIGRATION_2_3.startVersion)
        assertEquals(3, DatabaseModule.MIGRATION_2_3.endVersion)
        assertEquals(3, DatabaseModule.MIGRATION_3_4.startVersion)
        assertEquals(4, DatabaseModule.MIGRATION_3_4.endVersion)
    }

    @Test
    fun `migrations apply expected non-destructive schema changes`() {
        val sql12 = mutableListOf<String>()
        DatabaseModule.MIGRATION_1_2.migrate(recordingDb(sql12))
        assertEquals(1, sql12.size)
        assertTrue(sql12[0].contains("CREATE TABLE IF NOT EXISTS `favorites`"))
        assertTrue(sql12[0].contains("PRIMARY KEY(`animeId`)"))

        val sql23 = mutableListOf<String>()
        DatabaseModule.MIGRATION_2_3.migrate(recordingDb(sql23))
        assertEquals(1, sql23.size)
        assertTrue(sql23[0].contains("ALTER TABLE `favorites` ADD COLUMN `status`"))
        assertTrue(sql23[0].contains("DEFAULT 'none'"))

        val sql34 = mutableListOf<String>()
        DatabaseModule.MIGRATION_3_4.migrate(recordingDb(sql34))
        assertEquals(1, sql34.size)
        assertTrue(sql34[0].contains("CREATE TABLE IF NOT EXISTS `continue_hidden`"))

        val allSql = sql12 + sql23 + sql34
        assertFalse(allSql.any { it.contains("DROP TABLE", ignoreCase = true) })
    }
}
