# OAK Healthy

OAK Healthy là app quản lý “stack” thực phẩm bổ sung theo chu kỳ On/Off (uống/nghỉ), hỗ trợ đa học viên (Coach Mode) và đồng bộ đa thiết bị qua Firebase Realtime Database. App hướng tới Trader, Vận Động Viên và cả Bác Sĩ theo dõi bệnh nhân.

## Tải app (artifacts mới nhất)

- Android (APK): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/27621728551/artifacts/7668623507
- iOS (IPA): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/27621728615/artifacts/7668672556
- iOS (dSYMs): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/27621728615/artifacts/7668671910

Lưu ý: artifacts tải từ GitHub Actions có thể yêu cầu đăng nhập GitHub và sẽ hết hạn theo chính sách lưu trữ của GitHub.

## Thay đổi gần đây

- iOS:
  - UI: thay TabView mặc định bằng custom glass tab bar, fix lệch tab “Stack”, tránh bar chồng bar và tăng khoảng trống đáy để không che nút gần cuối.
  - Loading/Launch: đồng bộ giao diện Light/Dark, thêm logo ở launch/loading để nền trắng/chữ đen và nền đen/chữ trắng hiển thị nhất quán hơn.
  - Sync: bỏ delay foreground trên iOS và thêm retry ngắn khi chỉ một phần `stack/history` đổi, giúp Android -> iOS cập nhật gần realtime hơn và giảm lệch dữ liệu.
  - Home: khôi phục hiển thị chip “Chuỗi” trong khu vực “Cần uống hôm nay”.
  - Fix crash khi tick “Đã uống” do SwiftData predicate join trong export/sync (đổi sang fetch đơn giản + filter in-memory).
  - CI: build unsigned IPA và upload thêm dSYMs artifact để symbolicate crash.
- Android:
  - UI: custom glass bottom nav (capsule + pill active) theo style iOS, fix nền “slab” phía sau và tăng độ tương phản text/icon khi dùng dark mode.
  - Theme: lưu lựa chọn giao diện Light/Dark/System để mở lại app không bị trả về sáng ngoài ý muốn.
  - Loading: splash và loading screen đổi màu đúng theo theme hiện tại.
  - Build: sửa lỗi compile liên quan Compose version (weight/offset/align receiver mismatch) khi thay bottom nav.
  - Fix conflict ưu tiên thao tác Home: thao tác Taken/Skip từ Notification không ghi đè record đã có.
  - Release: siết proguard keep rules và bật R8 full mode để giảm size.

## Hướng dẫn sử dụng nhanh

### Thiết lập ban đầu

1) Mở app → cấp quyền thông báo (nếu muốn nhắc uống).
2) (Tuỳ chọn) Bật chế độ giao diện theo “Hệ thống” trong Cài đặt.

### Tạo stack và lịch uống

1) Vào tab Stack → thêm thực phẩm bổ sung.
2) Chọn lịch:
   - Uống liên tục / Chu kỳ On‑Off / Uống cách N ngày / Theo thứ trong tuần.
3) Quay lại Trang chủ để xem “Cần uống hôm nay”.

### Tick “Đã uống / Bỏ qua”

- Bạn có thể tick trực tiếp trên Trang chủ hoặc tick ngay trên thông báo (Taken/Skip) để thao tác nhanh.

### Đồng bộ 2 thiết bị (Sync Center)

Thiết bị A (máy đang có dữ liệu):
1) Mở Sync Center → Xuất key (chạm vào key để copy).
2) Tạo Link Code.

Thiết bị B (máy mới):
1) Mở Sync Center → Dán key (nút Dán).
2) Dán Link Code.
3) Bấm Tải về / Đồng bộ.

Gợi ý:
- Nếu bật Auto‑Sync thì cả 2 máy sẽ tự cập nhật theo định kỳ.
- Khi mở lại app sau khi thao tác trên máy còn lại, bản iOS mới sẽ kéo sync foreground sớm hơn để giảm cảm giác chờ.
- Nếu lỡ dán Link Code mà chưa có key, Auto‑Sync sẽ tự tắt để tránh “đứng vĩnh viễn”; chỉ cần dán key rồi bật lại Auto‑Sync.

## Guide & Release Notes

- Hướng dẫn up GitHub release: `docs/github-release-guide.md`
- Release notes để dán lên GitHub: `docs/release-notes-v1.0.1.md`

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
- Auto‑Sync:
  - Android chạy theo WorkManager định kỳ (tối thiểu 15 phút theo giới hạn hệ điều hành) + có job one‑off để sync sớm khi bạn thao tác.
  - iOS debounce các trigger sync và giảm polling khi idle để tiết kiệm pin.

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
