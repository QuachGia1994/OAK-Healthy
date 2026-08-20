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
    fun migrateEverySupportedVersionTo6_preservesRowsAndBackfillsCurrentColumns() {
        for (sourceVersion in 2..5) {
            context.deleteDatabase(databaseName)
            createSourceDatabase(sourceVersion)
            val database = openCurrentDatabase()
            assertMigratedRows(database, sourceVersion)
            database.close()
        }
    }

    private fun openCurrentDatabase(): SupplementDatabase =
        Room.databaseBuilder(context, SupplementDatabase::class.java, databaseName)
            .addMigrations(
                SupplementDatabase.MIGRATION_2_3,
                SupplementDatabase.MIGRATION_3_4,
                SupplementDatabase.MIGRATION_4_5,
                SupplementDatabase.MIGRATION_5_6
            )
            .allowMainThreadQueries()
            .build()

    private fun assertMigratedRows(database: SupplementDatabase, sourceVersion: Int) {
        val sqlite = database.openHelper.writableDatabase
        assertEquals("source v$sourceVersion", 6, sqlite.version)
        sqlite.query(
            "SELECT id, clientId, name, weeklyWeekdaysMask, intervalDays, lastTakenLocalDate, " +
                "updatedAtEpochMs, deletedAtEpochMs FROM supplements WHERE id = ?",
            arrayOf(supplementId)
        ).use { cursor ->
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
        sqlite.query(
            "SELECT id, supplementId, date, status, updatedAtEpochMs FROM intake_records WHERE id = ?",
            arrayOf(recordId)
        ).use { cursor ->
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
    }

    private fun createSourceDatabase(sourceVersion: Int) {
        val path = context.getDatabasePath(databaseName)
        path.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(path, null)
        createVersion2Schema(database)
        seedVersion2Rows(database)
        if (sourceVersion >= 3) migrateRaw2To3(database)
        if (sourceVersion >= 4) migrateRaw3To4(database)
        if (sourceVersion >= 5) migrateRaw4To5(database)
        database.version = sourceVersion
        database.close()
    }

    private fun createVersion2Schema(database: SQLiteDatabase) {
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
    }

    private fun seedVersion2Rows(database: SQLiteDatabase) {
        database.execSQL(
            "INSERT INTO supplements (id, name, startDate, daysOn, daysOff, isContinuous, " +
                "durationMonths, dailyDose, intakeTime) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(supplementId, "Vitamin C", "2026-01-02", 5, 2, 0, 3, "500 mg", "08:00")
        )
        database.execSQL(
            "INSERT INTO intake_records (id, supplementId, date, status) VALUES (?, ?, ?, ?)",
            arrayOf(recordId, supplementId, 1_700_000_000_000L, "Taken")
        )
    }

    private fun migrateRaw2To3(database: SQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE intake_records_staging (" +
                "id TEXT NOT NULL, supplementId TEXT NOT NULL, date INTEGER NOT NULL, " +
                "status TEXT NOT NULL, PRIMARY KEY(id))"
        )
        database.execSQL(
            "INSERT INTO intake_records_staging (id, supplementId, date, status) " +
                "SELECT id, supplementId, date, status FROM intake_records"
        )
        database.execSQL("DROP TABLE intake_records")
        database.execSQL(
            "CREATE TABLE client_profiles (" +
                "id TEXT NOT NULL, name TEXT NOT NULL, avatarColorArgb INTEGER NOT NULL, " +
                "createdAt INTEGER NOT NULL, PRIMARY KEY(id))"
        )
        database.execSQL(
            "INSERT INTO client_profiles (id, name, avatarColorArgb, createdAt) VALUES (?, ?, ?, ?)",
            arrayOf(defaultClientId, "Client 1", 0, 1_700_000_000_000L)
        )
        database.execSQL(
            "CREATE TABLE supplements_new (" +
                "id TEXT NOT NULL, clientId TEXT NOT NULL, name TEXT NOT NULL, startDate TEXT NOT NULL, " +
                "daysOn INTEGER NOT NULL, daysOff INTEGER NOT NULL, isContinuous INTEGER NOT NULL, " +
                "durationMonths INTEGER, dailyDose TEXT NOT NULL, intakeTime TEXT NOT NULL, PRIMARY KEY(id), " +
                "FOREIGN KEY(clientId) REFERENCES client_profiles(id) ON DELETE CASCADE)"
        )
        database.execSQL(
            "INSERT INTO supplements_new (id, clientId, name, startDate, daysOn, daysOff, isContinuous, " +
                "durationMonths, dailyDose, intakeTime) SELECT id, ?, name, startDate, daysOn, daysOff, " +
                "isContinuous, durationMonths, dailyDose, intakeTime FROM supplements",
            arrayOf(defaultClientId)
        )
        database.execSQL("DROP TABLE supplements")
        database.execSQL("ALTER TABLE supplements_new RENAME TO supplements")
        database.execSQL("CREATE INDEX index_supplements_clientId ON supplements(clientId)")
        database.execSQL(
            "CREATE TABLE intake_records_new (" +
                "id TEXT NOT NULL, supplementId TEXT NOT NULL, date INTEGER NOT NULL, status TEXT NOT NULL, " +
                "PRIMARY KEY(id), FOREIGN KEY(supplementId) REFERENCES supplements(id) ON DELETE CASCADE)"
        )
        database.execSQL(
            "INSERT INTO intake_records_new (id, supplementId, date, status) " +
                "SELECT id, supplementId, date, status FROM intake_records_staging"
        )
        database.execSQL("DROP TABLE intake_records_staging")
        database.execSQL("ALTER TABLE intake_records_new RENAME TO intake_records")
        database.execSQL("CREATE INDEX index_intake_records_supplementId ON intake_records(supplementId)")
    }

    private fun migrateRaw3To4(database: SQLiteDatabase) {
        database.execSQL("ALTER TABLE supplements ADD COLUMN weeklyWeekdaysMask INTEGER")
        database.execSQL("ALTER TABLE supplements ADD COLUMN weeklyIntervalWeeks INTEGER")
        database.execSQL("ALTER TABLE supplements ADD COLUMN weeklyAnchorDate TEXT")
    }

    private fun migrateRaw4To5(database: SQLiteDatabase) {
        database.execSQL("ALTER TABLE supplements ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE supplements ADD COLUMN deletedAtEpochMs INTEGER")
        database.execSQL("UPDATE supplements SET updatedAtEpochMs = 1700000000000")
        database.execSQL("ALTER TABLE intake_records ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
        database.execSQL("UPDATE intake_records SET updatedAtEpochMs = date")
    }
}
