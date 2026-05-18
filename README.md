# OAK Healthy

OAK Healthy là app quản lý “stack” thực phẩm bổ sung theo chu kỳ On/Off (uống/nghỉ), hỗ trợ đa học viên (Coach Mode) và đồng bộ đa thiết bị qua JSONBin. App hướng tới Trader, Vận Động Viên và cả Bác Sĩ theo dõi bệnh nhân.

## Tính năng chính

- Dashboard theo mốc giờ: “Cần uống hôm nay”, tick “Đã uống” và lưu vào Lịch sử.
- Chu kỳ On/Off tự động theo ngày bắt đầu (và cấu hình chu kỳ).
- Đồng bộ đa thiết bị:
  - Phát dữ liệu (tạo mã liên kết / Bin ID)
  - Tải về (tải + áp dụng dữ liệu trực tiếp)
  - Tự động đồng bộ (Auto‑Sync) (bật/tắt trong Cài đặt)
- Bảo mật mã liên kết:
  - Nút “Con mắt” để ẩn/hiện mã khi dùng nơi công cộng
  - “Thu hồi mã” để xóa vĩnh viễn dữ liệu trên Cloud
- Chẩn đoán thông báo:
  - “Kiểm tra danh sách thông báo” để xem app đã gửi lệnh đặt lịch (phụ thuộc quyền hệ điều hành/chứng chỉ)

## Ghi chú triển khai (mới)

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
