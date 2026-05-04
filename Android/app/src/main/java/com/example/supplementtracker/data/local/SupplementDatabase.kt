package com.example.supplementtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SupplementEntity::class, IntakeRecordEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SupplementDatabase : RoomDatabase() {
    abstract val supplementDao: SupplementDao

    companion object {
        const val DATABASE_NAME = "supplement_db"
    }
}
