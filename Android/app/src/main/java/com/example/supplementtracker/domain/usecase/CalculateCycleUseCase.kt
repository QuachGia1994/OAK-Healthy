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
    
    /**
     * Xác định trạng thái dựa trên ngày bắt đầu và cấu hình.
     */
    operator fun invoke(
        startDate: LocalDate,
        config: CycleConfig,
        currentDate: LocalDate = LocalDate.now()
    ): CycleStatus {
        // Early return cho uống liên tục
        if (config.isContinuous) return CycleStatus.ON
        
        // Early return nếu ngày kiểm tra trước ngày bắt đầu
        if (currentDate.isBefore(startDate)) return CycleStatus.ON
        
        val daysElapsed = ChronoUnit.DAYS.between(startDate, currentDate).toInt()
        val totalCycleDays = config.daysOn + config.daysOff
        
        if (totalCycleDays <= 0) return CycleStatus.ON
        
        val dayInCycle = daysElapsed % totalCycleDays
        
        return if (dayInCycle < config.daysOn) CycleStatus.ON else CycleStatus.OFF
    }
}
