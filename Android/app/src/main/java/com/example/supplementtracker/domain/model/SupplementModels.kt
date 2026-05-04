package com.example.supplementtracker.domain.model

import java.util.UUID
import java.time.LocalDate

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
enum class IntakeTime(val label: String) {
    MORNING("Sáng"),
    AFTERNOON("Trưa"),
    EVENING("Chiều"),
    NIGHT("Tối")
}

/**
 * Cấu hình chu kỳ (On/Off).
 */
data class CycleConfig(
    val daysOn: Int,
    val daysOff: Int,
    val isContinuous: Boolean = false,
    val durationMonths: Int? = null
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
    val name: String,
    val startDate: LocalDate,
    val cycleConfig: CycleConfig,
    val dailyDose: String,
    val intakeTime: String // Định dạng HH:mm
)

/**
 * Dữ liệu tham khảo từ từ điển.
 */
data class SupplementReference(
    val name: String,
    val advice: String? = null,
    val preferredTime: String,
    val defaultCycle: CycleConfig
)

/**
 * Trạng thái chu kỳ.
 */
enum class CycleStatus(val label: String) {
    ON("Được uống"),
    OFF("Nghỉ")
}
