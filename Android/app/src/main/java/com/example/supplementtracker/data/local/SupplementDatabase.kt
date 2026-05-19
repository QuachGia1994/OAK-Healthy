package com.example.supplementtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ClientProfileEntity::class, SupplementEntity::class, IntakeRecordEntity::class],
    version = 5,
    exportSchema = false
)
abstract class SupplementDatabase : RoomDatabase() {
    abstract val supplementDao: SupplementDao

    companion object {
        const val DATABASE_NAME = "supplement_db"

        private const val DEFAULT_CLIENT_ID = "00000000-0000-0000-0000-000000000000"

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS client_profiles (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        avatarColorArgb INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO client_profiles (id, name, avatarColorArgb, createdAt)
                    VALUES ('$DEFAULT_CLIENT_ID', 'Client 1', 0, strftime('%s','now') * 1000)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS supplements_new (
                        id TEXT NOT NULL,
                        clientId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        daysOn INTEGER NOT NULL,
                        daysOff INTEGER NOT NULL,
                        isContinuous INTEGER NOT NULL,
                        durationMonths INTEGER,
                        dailyDose TEXT NOT NULL,
                        intakeTime TEXT NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(clientId) REFERENCES client_profiles(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO supplements_new (id, clientId, name, startDate, daysOn, daysOff, isContinuous, durationMonths, dailyDose, intakeTime)
                    SELECT id, '$DEFAULT_CLIENT_ID', name, startDate, daysOn, daysOff, isContinuous, durationMonths, dailyDose, intakeTime
                    FROM supplements
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE supplements")
                db.execSQL("ALTER TABLE supplements_new RENAME TO supplements")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_supplements_clientId ON supplements(clientId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS intake_records_new (
                        id TEXT NOT NULL,
                        supplementId TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(supplementId) REFERENCES supplements(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO intake_records_new (id, supplementId, date, status)
                    SELECT id, supplementId, date, status
                    FROM intake_records
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE intake_records")
                db.execSQL("ALTER TABLE intake_records_new RENAME TO intake_records")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_intake_records_supplementId ON intake_records(supplementId)")

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }
        
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE supplements ADD COLUMN weeklyWeekdaysMask INTEGER")
                db.execSQL("ALTER TABLE supplements ADD COLUMN weeklyIntervalWeeks INTEGER")
                db.execSQL("ALTER TABLE supplements ADD COLUMN weeklyAnchorDate TEXT")
            }
        }
        
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE supplements ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE supplements ADD COLUMN deletedAtEpochMs INTEGER")
                db.execSQL("UPDATE supplements SET updatedAtEpochMs = strftime('%s','now') * 1000 WHERE updatedAtEpochMs = 0")
                
                db.execSQL("ALTER TABLE intake_records ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE intake_records SET updatedAtEpochMs = date WHERE updatedAtEpochMs = 0")
            }
        }
    }
}
