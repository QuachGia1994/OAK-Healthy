# OAK Healthy

OAK Healthy là app quản lý “stack” thực phẩm bổ sung theo chu kỳ On/Off (uống/nghỉ), hỗ trợ đa học viên (Coach Mode) và đồng bộ đa thiết bị qua JSONBin. App hướng tới Trader, Vận Động Viên và cả Bác Sĩ theo dõi bệnh nhân.

## Tải app (artifacts mới nhất)

- Android (APK): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/26348847785/artifacts/7181335521
- iOS (IPA): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/26349223925/artifacts/7181456459

Lưu ý: artifacts tải từ GitHub Actions có thể yêu cầu đăng nhập GitHub và sẽ hết hạn theo chính sách lưu trữ của GitHub.

## Tính năng chính

- Dashboard theo mốc giờ: “Cần uống hôm nay”, tick “Đã uống/Bỏ qua” và lưu vào Lịch sử.
- Lịch uống linh hoạt:
  - Uống liên tục
  - Chu kỳ On/Off theo ngày bắt đầu (x ngày uống / y ngày nghỉ)
  - Uống cách N ngày (phù hợp lịch uống/tiêm cách ngày)
  - Lặp theo thứ trong tuần + cách N tuần (Weekly Recurrence)
  - Tổng thời hạn tính theo ngày (để trống = vô thời hạn)
- Tick ngay trên thông báo (Taken/Skip) để thao tác nhanh.
- Đồng bộ đa thiết bị:
  - Phát dữ liệu (tạo mã liên kết / Bin ID)
  - Tải về (tải + áp dụng dữ liệu trực tiếp)
  - Tự động đồng bộ (Auto‑Sync) (bật/tắt trong Cài đặt)
- Bảo mật mã liên kết:
  - Nút “Con mắt” để ẩn/hiện mã khi dùng nơi công cộng
  - “Thu hồi mã” để xóa vĩnh viễn dữ liệu trên Cloud
- Chẩn đoán thông báo:
  - “Kiểm tra danh sách thông báo” để xem app đã gửi lệnh đặt lịch (phụ thuộc quyền hệ điều hành/chứng chỉ)
- Nâng cấp giao diện/trải nghiệm:
  - 3 tab: Trang chủ / Stack / Lịch sử
  - Lọc nhanh Due/Quá hạn/Đã uống/Bỏ qua + badge Quá hạn
  - Insights 7/30 ngày dạng chart + xem chi tiết bằng nút mũi tên

## Ghi chú triển khai (mới)

- Android: tải APK và cài trực tiếp (có thể cần bật “Cài đặt ứng dụng không rõ nguồn gốc”).
- iOS: IPA yêu cầu cài qua TestFlight hoặc tự ký (AltStore/Sideloadly). Nếu bạn chỉ muốn xem demo UI/flow thì vẫn có thể tải IPA để tham khảo build.

## Rules

- Max 30 lines / function.

- iOS:
  - Sau khi “Tải về”, app sẽ tự áp dụng dữ liệu và tự lên lịch lại thông báo (nếu đã bật “Cho phép gửi thông báo”).
  - Safe Mode là cơ chế tự phục hồi khi phát hiện crash loop; không còn nút bật Safe Mode thủ công ở màn khởi động.
  - Decode `CycleConfig`/`WeeklyRecurrenceConfig` được làm “tolerant” để tránh crash khi dữ liệu lệch schema.
- Android:
  - Cập nhật nội dung “Giới thiệu” (viết hoa Trader/Vận Động Viên/Bác Sĩ).
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

Tuỳ chọn message:

```bash
python backup_project.py --root . --git-sync --git-message "chore(backup): update"
```

## Lưu ý bảo mật

- Không commit file chứa secrets (ví dụ `iOS/Secrets.xcconfig` đã được loại khỏi backup theo mặc định).
- Không đưa API key vào README. Cấu hình key theo cơ chế build (CI/xcconfig/BuildConfig).
