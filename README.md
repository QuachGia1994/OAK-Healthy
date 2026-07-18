# OAK Healthy

[![Android Build](https://github.com/QuachGia1994/OAK-Healthy/actions/workflows/android-build.yml/badge.svg)](https://github.com/QuachGia1994/OAK-Healthy/actions/workflows/android-build.yml)
[![iOS Build](https://github.com/QuachGia1994/OAK-Healthy/actions/workflows/ios-build.yml/badge.svg)](https://github.com/QuachGia1994/OAK-Healthy/actions/workflows/ios-build.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-0F6B4F.svg)](LICENSE)
[![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS-173F35.svg)](#build-và-test)

OAK Healthy là ứng dụng theo dõi lịch dùng thực phẩm bổ sung trên Android và iOS. Ứng dụng hỗ trợ nhiều hồ sơ, lịch uống linh hoạt, thông báo, lịch sử Taken/Skipped/Overdue và đồng bộ hai chiều qua Firebase Realtime Database.

## Tải bản build mới nhất

| Nền tảng | Artifact | Workflow đã kiểm tra | Định dạng |
| --- | --- | --- | --- |
| Android | [Tải OAKHealthy-Android-APK](https://github.com/QuachGia1994/OAK-Healthy/actions/runs/29646728542/artifacts/8430268832) | [Android Build #29646728542](https://github.com/QuachGia1994/OAK-Healthy/actions/runs/29646728542) | APK Debug `1.0.1` |
| iOS | [Tải OAKHealthy-iOS-IPA](https://github.com/QuachGia1994/OAK-Healthy/actions/runs/29647622068/artifacts/8430535370) | [iOS Build #29647622068](https://github.com/QuachGia1994/OAK-Healthy/actions/runs/29647622068) | IPA unsigned `1.0.1` |

Hai artifact được tạo ngày 18/07/2026, còn hạn đến 16/10/2026 (UTC) và có thể yêu cầu đăng nhập GitHub. APK dùng để debug/test; IPA chưa ký nên cần ký lại hoặc dùng quy trình sideload phù hợp, không thể cài trực tiếp như bản App Store. Nếu link hết hạn, mở workflow tương ứng và tải artifact từ lần chạy thành công mới nhất.

## Tính năng chính

- Giao diện 3 tab đồng nhất: Dashboard, Stack và History.
- Lịch liên tục, chu kỳ On/Off, cách N ngày và lặp theo tuần.
- Thao tác Đã uống/Bỏ qua từ ứng dụng hoặc thông báo. Trên iOS, nhấn giữ hoặc mở rộng thông báo liều để hiện các nút thao tác.
- Bộ lọc trạng thái 4 màu dễ nhận biết trên Android và iOS.
- Auto-Sync hai chiều với merge theo thời gian cập nhật và retry khi xung đột.
- Mã hóa cloud tùy chọn bằng AES-256-GCM, tương thích chéo Android/iOS.
- Giao diện tiếng Anh và tiếng Việt, hỗ trợ Light/Dark/System theme.

## Đồng bộ hai chiều

Thiết bị Host tạo một Link Code. Thiết bị Link nhập mã này để tải, merge và tiếp tục đồng bộ cùng bộ dữ liệu.

1. Trên Host, chọn chế độ mã hóa trước khi tạo Link Code.
2. Nếu mã hóa được bật, chuyển Sync Key sang thiết bị Link bằng kênh riêng tư.
3. Trên thiết bị Link, import key trước, dán Link Code và bấm Tải về một lần.
4. Bật Auto-Sync trên cả hai thiết bị.

Khi app được mở hoặc quay lại foreground, sync chạy ngay. Thay đổi cục bộ được gom trong khoảng debounce ngắn trước khi upload. Realtime khi app đang hoạt động dùng Firebase revision listeners; tốc độ chạy nền vẫn phụ thuộc mạng, giới hạn pin và cơ chế scheduling của từng hệ điều hành.

Firebase writes dùng transaction nguyên tử và revision tăng đơn điệu để tránh hai thiết bị cùng vượt qua kiểm tra rồi ghi đè dữ liệu của nhau. Payload và revision cũng được đọc trong cùng một snapshot để không ghép nhầm nội dung mới với revision cũ.

## Mã hóa và bảo mật

- Payload mã hóa dùng AES-256-GCM với nonce ngẫu nhiên 12 byte và authentication tag 16 byte.
- Android bọc Sync Key bằng khóa AES trong Android Keystore.
- iOS lưu Sync Key trong Keychain với `WhenUnlockedThisDeviceOnly`.
- Key ID và Link Code được giới hạn bằng mẫu `[A-Za-z0-9_-]{1,64}` trước mọi thao tác Firebase.
- Sync Key được ẩn mặc định. Clipboard iOS tự hết hạn sau 2 phút; Android đánh dấu nội dung là dữ liệu nhạy cảm.
- Link Code là capability khó đoán, không phải mật khẩu. Không đăng Link Code hoặc Sync Key trong ảnh chụp, issue, log hay chat công khai.
- Chế độ mã hóa bị khóa trong thời gian Link Code đang hoạt động. Nếu key bị lộ hoặc cần đổi chế độ, hãy thu hồi Link Code, tạo key/link mới rồi liên kết lại thiết bị.
- Ứng dụng dùng Firebase Anonymous Auth và App Check: Play Integrity trên Android, App Attest trên iOS. Production cần bật enforcement cho Realtime Database trong Firebase Console.
- `GoogleService-Info.plist` và `google-services.json` chứa định danh Firebase client, không thay thế server secret. Không commit service-account key, signing key, `Secrets.xcconfig` hoặc `keystore.properties`.

Firebase Rules trong [`firebase/database.rules.json`](firebase/database.rules.json) giới hạn payload 1 MB, yêu cầu node đầy đủ và chỉ chấp nhận revision tăng. Deploy rules bằng:

```bash
firebase deploy --only database --project oak-healthy
```

## Cấu trúc dự án

- `Android/`: Kotlin, Jetpack Compose, Room, WorkManager và Firebase.
- `iOS/`: Swift, SwiftUI, SwiftData, CryptoKit và Firebase.
- `firebase/`: Realtime Database Rules và script deploy.
- `.github/workflows/`: build/test Android và iOS trên GitHub Actions.
- `DESIGN.md`: quy tắc thiết kế dùng chung cho hai nền tảng.
- `docs/`: review UI/UX và ghi chú dự án.

## Build và test

### Android

Yêu cầu JDK 17 và Android SDK.

```powershell
cd Android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

### iOS

Yêu cầu macOS, Xcode và Swift Package Manager. Dự án dùng `project.yml`/XcodeGen và iOS deployment target 17.0. Trên máy Windows, dùng workflow iOS của GitHub Actions thay cho `xcodebuild` cục bộ.

Các test sync bao gồm codec, manifest, Link Code validation, revision monotonic và fixture AES-GCM dùng chung cho Android/iOS.

Build gần nhất đã vượt qua Android unit test, lint và APK assembly. iOS đã vượt qua `23` unit test, kiểm tra Keychain/AES-GCM, archive unsigned và đóng gói Firebase configuration. Các file debug symbol iOS có tại [OAKHealthy-iOS-dSYMs](https://github.com/QuachGia1994/OAK-Healthy/actions/runs/29647622068/artifacts/8430535059).

## Quyền riêng tư

Dữ liệu sức khỏe được lưu cục bộ và chỉ upload khi người dùng chủ động Host/Link. Mã hóa cloud là tùy chọn nhưng được khuyến nghị. OAK Healthy không thay thế tư vấn, chẩn đoán hoặc điều trị y khoa.

## Tác giả và bản quyền

Tác giả: **Quach Gia (Phong QK)**

Mã nguồn được cấp phép theo [Apache License 2.0](LICENSE). Tên và logo OAK
Healthy không được cấp quyền sử dụng như nhãn hiệu theo giấy phép này.

Quy trình cộng đồng: [Contributing](CONTRIBUTING.md) ·
[Governance](GOVERNANCE.md) · [Roadmap](ROADMAP.md) ·
[Changelog](CHANGELOG.md) · [Security](SECURITY.md)

Copyright © 2026 Quach Gia / OAK Healthy.
