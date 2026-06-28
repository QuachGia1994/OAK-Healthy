# OAK Healthy

OAK Healthy là app quản lý “stack” thực phẩm bổ sung theo chu kỳ On/Off (uống/nghỉ), hỗ trợ đa học viên (Coach Mode) và đồng bộ đa thiết bị qua Firebase Realtime Database. App hướng tới Trader, Vấn Động Viên và cả Bác Sĩ theo dòi bệnh nhân.

## Tải app (artifacts mới nhất)

- Android (APK): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/28308418554/artifacts/7930168260
- iOS (IPA): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/28308418531/artifacts/7930173339
- iOS (dSYMs): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/28308418531/artifacts/7930173223

Lưu ý: artifacts tải từ GitHub Actions có thể yêu cầu đăng nhập GitHub và hết hạn theo chính sách lưu trữ của GitHub.

## Thay đổi gần đây

- Sync:
  - Realtime sync iOS ↔ Android qua Firebase Realtime Database listeners (không cần poll).
  - Field-level merge: sửa field khác nhau trên 2 máy không bị đè nhau.
  - Manifest cache invalidation khi re-host.
- iOS:
  - Native liquid glass TabView (iOS 26+).
  - Xóa custom OAKBottomTabBar — dùng native tab bar.
- Android:
  - Liquid glass bottom bar (inner glow, sheen, multi-shadow).
  - Swipe giữa 3 tab qua HorizontalPager.
  - AGP 8.5.2 + Kotlin 2.0.21 + Compose BOM 2024.06.00.
  - Room migrate từ kapt sang KSP.
  - Split SettingsScreen.kt → SettingsComponents.kt + MyStackListScreen.kt.
- Cả 2 nền tảng:
  - Xóa ~250 dòng dead code.
  - Onboarding crash fix (Android).
  - Tab bar layout overlap fix (Android).
  - Manifest cache invalidation trên cả 2 nền tảng.

## Hướng dẫn sử dụng nhanh

### Thiết lập ban đầu

1) Mở app → cấp quyền thông báo (nếu muốn nhắc uống).
2) (Tùy chọn) Bật chế độ giao diện theo “Hệ thống” trong Cài đặt.

### Tạo stack và lịch uống

1) Vào tab Stack → thêm thực phẩm bổ sung.
2) Chọn lịch:
   - Uống liên tỡc / Chu kỳ On‑Off / Uống cách N ngày / Theo thứ trong tuần.
3) Quay lại Trang chủ để xem “Cần uống hôm nay”.

### Tick “Đã uống / Bỏ qua”

- Bạn có thể tick trực tiếp trên Trang chủ hoặc tick ngay trên thông báo (Taken/Skip) để thao tác nhanh.

### Đồng bộ 2 thiết bị (Sync Center)

Thiết bị A (máy đang có dữ liệu):
1) Mở Sync Center → Xuất key (chạm vào key để copy).
2) Tạo Link Code.

Thiết bị B (máy mới):
1) Mở Sync Center → Dán key (nửt Dán).
2) Dán Link Code.
3) Bảm Tải về / Đồng bộ.

Gợi ý:
- Auto-Sync dùng Firebase realtime listeners, đồng bộ từng thời khi app đang mở.
- Auto-Sync tự tắt nếu thiếu key; chỉ cần dán key rồi bật lại.

## Guide & Release Notes

- Hướng dẫn up GitHub release: `docs/github-release-guide.md`
- Release notes để dán lên GitHub: `docs/release-notes-v1.0.1.md`

## Tính năng chính

- Dashboard theo mớc giờ: “Cần uống hôm nay”, tick “Đã uống/Bỏ qua” và lưu vào Lịch sử.
- Lịch uống linh hoạt:
  - Uống liên tỡc
  - Chu kỳ On/Off theo ngày bắt đầu (x ngày uống / y ngày nghỉ)
  - Uống cách N ngày (phù hợp lịch uống/tiêm cách ngày)
  - Lập theo thứ trong tuần + cách N tuần (Weekly Recurrence)
  - Tổng thời hạn tính theo ngày (để trống = vô thời hạn)
- Tick ngay trên thông báo (Taken/Skip) để thao tác nhanh.
- Đồng bộ đa thiết bị:
  - Phát dữ liệu (tạo mã liên kết / Bin ID)
  - Tải về (Tải + áp dụng dữ liệu trực tiếp)
  - Tự đồng đồng bộ (Auto‑Sync) (bật/tật trong Cài đặt)
- Bảo mật mã liên kết:
  - Nửt “Con mất” để ẩn/hiện mã khi dùng nơi công công
  - “Thu hồi mã” để xóa ṽnh viễn dữ liệu trên Cloud
- Chẩn đoán thông báo:
  - “Kiểm tra danh sách thông báo” để xem app đã gửi lệnh đặt lịch (phủ thuộc quyền hệ điều hành/chứng chẩ)
- Nâng cấp giao diện/trải nghiệm:
  - 3 tab: Trang chủ / Stack / Lịch sử
  - Lọc nhanh Due/Quá hạn/Đã uống/Bỏ qua + badge Quá hạn
  - Insights 7/30 ngày dạng chart + xem chi tiết bằng nửt mỷi tên

## Ghi chú triển khai (mới)

- Android: tải APK và cài trực tiếp (có thể cần bật “Cài đặt ứng dụng không rõ nguồn gốc”).
- iOS: IPA yêu cầu cài qua TestFlight hoặc tự ký (AltStore/Sideloadly). Nếu bạn chỉ muốn xem demo UI/flow thì vẫn có thể tải IPA để tham khảo build.
- Auto‑Sync:
  - Android chạy theo WorkManager định kỳ (tối thiệu 15 phút theo giới hạn hệ điều hành) + có job one‑off để sync sớm khi bạn thao tác.
  - iOS debounce các trigger sync và giảm polling khi idle để tiết kiệm pin.

## Rules

- Max 30 lines / function.

- iOS:
  - Sau khi “Tải về”, app sẽ tự áp dụng dữ liệu và tự lên lịch lại thông báo (nếu đã bật “Cho phép gửi thông báo”).
  - Safe Mode là cơ chế tự phục hồi khi phát hiện crash loop; không còn nửt bật Safe Mode thủ công ở màn khởi động.
  - Decode `CycleConfig`/`WeeklyRecurrenceConfig` được làm “tolerant” để tránh crash khi dữ liệu lệch schema.
- Android:
  - Cập nhật nội dung “Giới thiệu” (viết hoa Trader/Vấn Động Viên/Bác Sĩ).
  - Tối ưu scroll jank: thêm `LazyListState` và key ổn định cho các danh sách (Home/History).

## Cấu trúc dự án

- `iOS/` — Swift 6.2+, SwiftUI, Strict Concurrency
- `Android/` — Kotlin + Jetpack Compose (Material 3)
- `backup_project.py` — Script backup dự án (zip), có chế độ git sync

## Backup & Git Sync

### Tạo backup

```bash
python backup_project.py --root .
```

### Backup + tự git add/commit/push

```bash
python backup_project.py --root . --git-sync --git-remote origin --git-branch main
```

Tùy chện message:

```bash
python backup_project.py --root . --git-sync --git-message "chore(backup): update"
```

## Lưu ý bảo mật

- Không commit file chừa secrets (ví dụ `iOS/Secrets.xcconfig` đã được loại khỏi backup theo mệnh đệnh).
- Không đưạ API key vào README. Cấu hình key theo cơ chẾf build (CI/xcconfig/BuildConfig).
