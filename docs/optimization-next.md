# Kế hoạch tối ưu tiếp theo (1/2/3)

## 1) Pin/Battery (Auto-sync)

### Android (WorkManager – 15 phút, bất kỳ mạng)
- Mục tiêu
  - Giảm wakeups bằng cách bỏ vòng `while(isActive)` trong `HomeViewModel.startAutoSync()` cho background polling.
  - Vẫn giữ “sync ngay” khi user thao tác (dose/delete/edit) bằng debounce/coalesce như hiện tại.
- Thay đổi dự kiến
  - Tạo `CloudSyncWorker` (CoroutineWorker) tự khởi tạo `SupplementDatabase` + `SupplementRepositoryImpl` + `ActiveClientManager`, đọc prefs (`isAutoSyncEnabled`, `cloudSyncHostedBinId/cloudSyncLinkedBinId`), rồi gọi đúng logic sync hiện có (ưu tiên tái sử dụng hàm sync thay vì copy).
  - Schedule:
    - PeriodicWorkRequest 15 phút, constraint NetworkType.CONNECTED.
    - UniquePeriodicWork “oak_cloud_autosync_periodic” với policy KEEP/UPDATE.
  - One-off:
    - Khi có “dirty” (user thao tác) sẽ enqueue OneTimeWorkRequest unique “oak_cloud_autosync_now” (REPLACE) để đẩy nhanh mà vẫn coalesce.
  - Loại bỏ hoặc vô hiệu hóa polling loop trong `HomeViewModel.startAutoSync()` (chỉ giữ cho foreground nếu cần).
- Files liên quan
  - Android/app/src/main/java/.../presentation/home/HomeViewModel.kt
  - Android/app/src/main/java/.../MainActivity.kt
  - Android/app/src/main/java/.../worker/ (thêm worker mới)

### iOS (Flush theo lifecycle + debounce)
- Mục tiêu
  - Giảm gọi sync dồn dập khi user thao tác liên tục, và đảm bảo “flush” khi app sắp background.
- Thay đổi dự kiến
  - Giữ `requestSyncSoon` (đã có), bổ sung:
    - “dirty flag” khi local changed.
    - Trigger flush khi app chuyển `scenePhase` sang inactive/background.
- Files liên quan
  - iOS/Services/CloudSyncManager.swift
  - iOS/SupplementTrackerApp.swift

## 2) Startup & UI

### Baseline Profile (CI – workflow_dispatch)
- Mục tiêu
  - Giảm jank/TTI cho Android release bằng baseline profile thực tế.
- Thay đổi dự kiến
  - Thêm workflow riêng (manual) chạy `:baselineprofile:generateBaselineProfile` bằng emulator trên CI.
  - Upload `app/src/main/baseline-prof.txt` (hoặc output tương đương) làm artifact để kiểm tra/commit khi có thay đổi.
- Files liên quan
  - Android/baselineprofile/**
  - .github/workflows/** (thêm workflow)

### iOS SwiftData fetch
- Mục tiêu
  - Tránh fetch toàn bộ rồi filter trong memory cho các path nóng (backup/build/export).
- Thay đổi dự kiến
  - Refactor `makeStackBackup` / `makeHistoryBackup` sang FetchDescriptor có predicate theo clientId.
  - Rà soát các fetch khác theo pattern tương tự.
- Files liên quan
  - iOS/Services/CloudSyncManager.swift

## 3) Release size

### Android (R8 full mode – có kiểm soát)
- Mục tiêu
  - Shrink thêm mà vẫn an toàn (đã thu gọn keep rules Room).
- Thay đổi dự kiến
  - Bật `android.enableR8.fullMode=true` trong `Android/gradle.properties`.
  - Nếu shrinker báo missing classes, bổ sung keep rules đúng chỗ (ưu tiên keep hẹp).

### iOS (symbol & stripping)
- Mục tiêu
  - Giữ app nhỏ nhưng vẫn debug được crash logs.
- Thay đổi dự kiến
  - Đảm bảo Release dùng `dwarf-with-dsym`, và CI upload dSYM artifact riêng.
  - Giữ `STRIP_SWIFT_SYMBOLS`/`DEPLOYMENT_POSTPROCESSING` như hiện tại.

## Xác minh
- CI xanh (Android + iOS).
- Android: đo crash-free khi sync nền chạy (log/telemetry) và không spam wakeups.
- iOS: xác nhận sync vẫn xảy ra khi app background/foreground và không “sync dồn”.

