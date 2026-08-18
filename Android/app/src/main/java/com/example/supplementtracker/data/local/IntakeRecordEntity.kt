package com.example.supplementtracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.supplementtracker.domain.model.IntakeStatus
import java.util.UUID

/**
 * Thực thể nhật ký uống thực phẩm bổ sung.
 */
@Entity(
    tableName = "intake_records",
    foreignKeys = [
        ForeignKey(
            entity = SupplementEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplementId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("supplementId")]
)
data class IntakeRecordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val supplementId: String,
    val date: Long, // Epoch millis
    val status: String = IntakeStatus.TAKEN.storageValue,
    val updatedAtEpochMs: Long
)
