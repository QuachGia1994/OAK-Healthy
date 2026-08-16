package com.example.supplementtracker.persistence

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.example.supplementtracker.data.local.SupplementDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {
    private val databaseName = "migration-${UUID.randomUUID()}.db"
    private val supplementId = "11111111-1111-1111-1111-111111111111"
    private val recordId = "22222222-2222-2222-2222-222222222222"
    private val defaultClientId = "00000000-0000-0000-0000-000000000000"
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrateVersion2To6_preservesRowsAndBackfillsCurrentColumns() {
        createVersion2Database()
        val database = Room.databaseBuilder(context, SupplementDatabase::class.java, databaseName)
            .addMigrations(
                SupplementDatabase.MIGRATION_2_3,
                SupplementDatabase.MIGRATION_3_4,
                SupplementDatabase.MIGRATION_4_5,
                SupplementDatabase.MIGRATION_5_6
            )
            .allowMainThreadQueries()
            .build()

        val sqlite = database.openHelper.writableDatabase
        assertEquals(6, sqlite.version)
        sqlite.query("SELECT id, clientId, name, weeklyWeekdaysMask, intervalDays, lastTakenLocalDate, updatedAtEpochMs, deletedAtEpochMs FROM supplements WHERE id = ?", arrayOf(supplementId)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(supplementId, cursor.getString(0))
            assertEquals(defaultClientId, cursor.getString(1))
            assertEquals("Vitamin C", cursor.getString(2))
            assertNull(cursor.getString(3))
            assertNull(cursor.getString(4))
            assertNull(cursor.getString(5))
            assertEquals(true, cursor.getLong(6) > 0L)
            assertNull(cursor.getString(7))
        }
        sqlite.query("SELECT id, name FROM client_profiles WHERE id = ?", arrayOf(defaultClientId)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(defaultClientId, cursor.getString(0))
            assertEquals("Client 1", cursor.getString(1))
        }
        sqlite.query("SELECT id, supplementId, date, status, updatedAtEpochMs FROM intake_records WHERE id = ?", arrayOf(recordId)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(recordId, cursor.getString(0))
            assertEquals(supplementId, cursor.getString(1))
            assertEquals(1_700_000_000_000L, cursor.getLong(2))
            assertEquals("Taken", cursor.getString(3))
            assertEquals(1_700_000_000_000L, cursor.getLong(4))
        }
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
        database.close()
    }

    private fun createVersion2Database() {
        val path = context.getDatabasePath(databaseName)
        path.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(path, null)
        database.execSQL(
            """
            CREATE TABLE supplements (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                startDate TEXT NOT NULL,
                daysOn INTEGER NOT NULL,
                daysOff INTEGER NOT NULL,
                isContinuous INTEGER NOT NULL,
                durationMonths INTEGER,
                dailyDose TEXT NOT NULL,
                intakeTime TEXT NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE intake_records (
                id TEXT NOT NULL,
                supplementId TEXT NOT NULL,
                date INTEGER NOT NULL,
                status TEXT NOT NULL,
                PRIMARY KEY(id),
                FOREIGN KEY(supplementId) REFERENCES supplements(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX index_intake_records_supplementId ON intake_records(supplementId)")
        database.execSQL(
            "INSERT INTO supplements (id, name, startDate, daysOn, daysOff, isContinuous, durationMonths, dailyDose, intakeTime) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(supplementId, "Vitamin C", "2026-01-02", 5, 2, 0, 3, "500 mg", "08:00")
        )
        database.execSQL(
            "INSERT INTO intake_records (id, supplementId, date, status) VALUES (?, ?, ?, ?)",
            arrayOf(recordId, supplementId, 1_700_000_000_000L, "Taken")
        )
        database.version = 2
        database.close()
    }
}
