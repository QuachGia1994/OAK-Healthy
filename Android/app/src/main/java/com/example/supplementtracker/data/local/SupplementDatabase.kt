package com.example.supplementtracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ClientProfileEntity::class, SupplementEntity::class, IntakeRecordEntity::class],
    version = 6,
    exportSchema = false
)
abstract class SupplementDatabase : RoomDatabase() {
    abstract val supplementDao: SupplementDao

    companion object {
        const val DATABASE_NAME = "supplement_db"
        @Volatile
        private var instance: SupplementDatabase? = null

        private const val DEFAULT_CLIENT_ID = "00000000-0000-0000-0000-000000000000"

        private val CREATE_CLIENT_PROFILES_TABLE_SQL =
            """
            CREATE TABLE IF NOT EXISTS client_profiles (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                avatarColorArgb INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()

        private val INSERT_DEFAULT_CLIENT_PROFILE_SQL =
            """
            INSERT OR IGNORE INTO client_profiles (id, name, avatarColorArgb, createdAt)
            VALUES ('$DEFAULT_CLIENT_ID', 'Client 1', 0, strftime('%s','now') * 1000)
            """.trimIndent()

        private val CREATE_SUPPLEMENTS_NEW_TABLE_SQL =
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

        private val COPY_SUPPLEMENTS_TO_NEW_SQL =
            """
            INSERT INTO supplements_new (id, clientId, name, startDate, daysOn, daysOff, isContinuous, durationMonths, dailyDose, intakeTime)
            SELECT id, '$DEFAULT_CLIENT_ID', name, startDate, daysOn, daysOff, isContinuous, durationMonths, dailyDose, intakeTime
            FROM supplements
            """.trimIndent()

        private val CREATE_INTAKE_RECORDS_NEW_TABLE_SQL =
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

        private val COPY_INTAKE_RECORDS_TO_NEW_SQL =
            """
            INSERT INTO intake_records_new (id, supplementId, date, status)
            SELECT id, supplementId, date, status
            FROM intake_records
            """.trimIndent()

        fun getInstance(context: Context): SupplementDatabase {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                val again = instance
                if (again != null) return@synchronized again
                val created = Room.databaseBuilder(
                    context.applicationContext,
                    SupplementDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                instance = created
                created
            }
        }

        private fun disableForeignKeys(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=OFF")
        }

        private fun enableForeignKeys(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys=ON")
        }

        private fun ensureClientProfiles(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_CLIENT_PROFILES_TABLE_SQL)
            db.execSQL(INSERT_DEFAULT_CLIENT_PROFILE_SQL)
        }

        private fun migrateSupplementsToClientScoped(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_SUPPLEMENTS_NEW_TABLE_SQL)
            db.execSQL(COPY_SUPPLEMENTS_TO_NEW_SQL)
            db.execSQL("DROP TABLE supplements")
            db.execSQL("ALTER TABLE supplements_new RENAME TO supplements")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_supplements_clientId ON supplements(clientId)")
        }

        private fun migrateIntakeRecordsToCascade(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_INTAKE_RECORDS_NEW_TABLE_SQL)
            db.execSQL(COPY_INTAKE_RECORDS_TO_NEW_SQL)
            db.execSQL("DROP TABLE intake_records")
            db.execSQL("ALTER TABLE intake_records_new RENAME TO intake_records")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_intake_records_supplementId ON intake_records(supplementId)")
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                disableForeignKeys(db)
                ensureClientProfiles(db)
                migrateSupplementsToClientScoped(db)
                migrateIntakeRecordsToCascade(db)
                enableForeignKeys(db)
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

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE supplements ADD COLUMN intervalDays INTEGER")
                db.execSQL("ALTER TABLE supplements ADD COLUMN lastTakenLocalDate TEXT")
            }
        }
    }
}
