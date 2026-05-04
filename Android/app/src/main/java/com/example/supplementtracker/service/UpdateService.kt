package com.example.supplementtracker.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thông tin phiên bản ứng dụng từ xa.
 */
data class AppUpdateInfo(
    val version: String,
    val updateUrl: String,
    val forceUpdate: Boolean
)

/**
 * Dịch vụ kiểm tra cập nhật phiên bản (Android).
 */
class UpdateService {
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable = _isUpdateAvailable.asStateFlow()

    /**
     * Kiểm tra phiên bản mới bất đồng bộ.
     */
    suspend fun checkForUpdates(currentVersionName: String) {
        // Giả lập gọi API kiểm tra version
        delay(1000)
        
        val remoteVersion = "1.1.0" // Giả sử version mới
        if (remoteVersion > currentVersionName) {
            _updateInfo.value = AppUpdateInfo(
                version = remoteVersion,
                updateUrl = "https://github.com/your-repo/OAK-Healthy/releases",
                forceUpdate = false
            )
            _isUpdateAvailable.value = true
        }
    }
}
