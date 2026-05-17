package com.example.supplementtracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.supplementtracker.domain.model.IntakeTime
import java.time.LocalDate
import java.util.UUID

/**
 * Thực thể lưu trữ trong Room Database.
 */
@Entity(
    tableName = "supplements",
    foreignKeys = [
        ForeignKey(
            entity = ClientProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clientId")]
)
data class SupplementEntity(
    @PrimaryKey
    val id: String,
    val clientId: String,
    val name: String,
    val startDate: String, // Lưu dạng ISO-8601 String
    val daysOn: Int,
    val daysOff: Int,
    val isContinuous: Boolean,
    val durationMonths: Int?, // Tổng thời hạn (tháng)
    val dailyDose: String,
    val intakeTime: String, // Lưu dạng String enum
    val weeklyWeekdaysMask: Int?,
    val weeklyIntervalWeeks: Int?,
    val weeklyAnchorDate: String?
)
