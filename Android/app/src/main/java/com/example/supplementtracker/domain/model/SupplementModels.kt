package com.example.supplementtracker.domain.model

import java.util.UUID
import java.time.LocalDate

/**
 * Hồ sơ học viên/khách hàng (Coach Mode).
 */
data class ClientProfile(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val avatarColorArgb: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Wrapper cho kết quả xử lý dữ liệu.
 */
sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
    data object Loading : Resource<Nothing>()
}

/**
 * Thời điểm uống trong ngày.
 */
enum class IntakeTime {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT
}

/**
 * Cấu hình chu kỳ (On/Off).
 */
data class WeeklyRecurrenceConfig(
    val weekdaysMask: Int,
    val intervalWeeks: Int,
    val anchorDate: LocalDate
)

data class CycleConfig(
    val daysOn: Int,
    val daysOff: Int,
    val isContinuous: Boolean = false,
    val durationMonths: Int? = null,
    val weeklyRecurrence: WeeklyRecurrenceConfig? = null,
    val intervalDays: Int? = null
) {
    companion object {
        val Continuous = CycleConfig(1, 0, true)
    }
}

/**
 * Thực phẩm bổ sung của người dùng.
 */
data class UserSupplement(
    val id: UUID = UUID.randomUUID(),
    val clientId: UUID,
    val name: String,
    val startDate: LocalDate,
    val cycleConfig: CycleConfig,
    val dailyDose: String,
    val intakeTime: String, // Định dạng HH:mm
    val lastTakenLocalDate: LocalDate? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val deletedAtEpochMs: Long? = null
)

/**
 * Wrapper cho trạng thái "đã uống hôm nay" của một chất bổ sung.
 */
data class UserSupplementTakenToday(
    val supplement: UserSupplement,
    val todayStatus: String?,
    val isTakenToday: Boolean
)

/**
 * Dữ liệu tham khảo từ từ điển.
 */
data class SupplementReference(
    val name: String,
    val advice: String? = null,
    val preferredTime: String,
    val preferredDose: String? = null,
    val defaultCycle: CycleConfig
)

/**
 * Trạng thái chu kỳ.
 */
enum class CycleStatus {
    ON,
    OFF
}
