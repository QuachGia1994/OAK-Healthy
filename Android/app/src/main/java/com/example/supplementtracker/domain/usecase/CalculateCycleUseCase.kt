package com.example.supplementtracker.domain.usecase

import com.example.supplementtracker.domain.model.CycleConfig
import com.example.supplementtracker.domain.model.CycleStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * UseCase tính toán trạng thái chu kỳ (On/Off).
 * Logic khớp 100% với bản iOS.
 */
class CalculateCycleUseCase {
    fun isExpired(
        startDate: LocalDate,
        config: CycleConfig,
        currentDate: LocalDate = LocalDate.now()
    ): Boolean {
        val endDate = endDateIfNeeded(startDate, config) ?: return false
        return !currentDate.isBefore(endDate)
    }
    
    /**
     * Xác định trạng thái dựa trên ngày bắt đầu và cấu hình.
     */
    operator fun invoke(
        startDate: LocalDate,
        config: CycleConfig,
        currentDate: LocalDate = LocalDate.now()
    ): CycleStatus {
        // Chưa tới ngày bắt đầu thì routine chưa hoạt động.
        if (currentDate.isBefore(startDate)) return CycleStatus.OFF

        // Kiểm tra thời hạn (Duration).
        if (isExpired(startDate, config, currentDate)) return CycleStatus.OFF

        // Continuous chỉ ON từ startDate trở đi.
        if (config.isContinuous) return CycleStatus.ON
        
        val daysElapsed = ChronoUnit.DAYS.between(startDate, currentDate).toInt()
        val totalCycleDays = config.daysOn + config.daysOff
        
        if (totalCycleDays <= 0) return CycleStatus.ON
        
        val dayInCycle = daysElapsed % totalCycleDays
        
        return if (dayInCycle < config.daysOn) CycleStatus.ON else CycleStatus.OFF
    }

    private fun endDateIfNeeded(startDate: LocalDate, config: CycleConfig): LocalDate? {
        val days = config.durationMonths ?: return null
        if (days <= 0) return null
        return startDate.plusDays(days.toLong())
    }
}
