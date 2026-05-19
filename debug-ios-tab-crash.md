# debug-ios-tab-crash.md

[OPEN]

## Symptoms (reported)
- iOS lần đầu vào gặp lỗi “không thể khởi tạo dữ liệu” → vào Safe Mode.
- Thoát Safe Mode xong bị crash khi vào tab Lịch sử.
- Crash khi vào Sync Center.

## Expected
- App khởi tạo SwiftData bình thường (hoặc fallback an toàn) và không crash khi vào History/Sync Center.

## Hypotheses (falsifiable)
- **H1**: SwiftData `ModelContainer`/store init fail hoặc store bị corrupt → một số view fetch crash khi context/container chưa ổn định.
- **H2**: Flow Safe Mode exit để lại state không nhất quán (activeClientId/binId/pending import) khiến History/Sync Center task chạy với dữ liệu rỗng/invalid và crash.
- **H3**: Auto-sync chạy khi app vừa active, race với bootstrap/activeClientManager/modelContext → gây crash khi chuyển tab.
- **H4**: Decode/decompress/decrypt payload (CloudSyncPayloadCodec / CloudSyncCrypto) ném lỗi không được xử lý trong một task path nào đó khi vào Sync Center.
- **H5**: Crash đến từ SwiftUI lifecycle callback mismatch (onChange/task) trên device build configuration khác với diagnostics local.

## Evidence to collect (pre-fix)
- Boot stage timeline: start → containerReady → uiReady/uiStable.
- Store init error details (nếu có) và store URL.
- Safe mode state + pending import keys + activeClientId khi exit safe mode.
- HistoryView reload start/end + record counts + errors.
- SyncCenterView tasks start/end + activeBinId + errors.
- Auto-sync start/stop + syncIfEnabled enter/exit + exceptions.

## Instrumentation plan
- Start Debug Server (remote) để iPhone gửi log qua LAN.
- Thêm debug reporter (POST JSON event) vào iOS:
  - App bootstrap + makeModelContainer catch
  - SafeModeView actions
  - HistoryView reload
  - SyncCenterView tasks + action buttons
  - AutoSync loop entry/exit

## How to run
1. Start debug server: `python3 ... --remote --session ios-tab-crash --outdir .dbg --clean --idle 1200`
2. Lấy `DEBUG_SERVER_URL` in ra và paste vào UI debug field trong Safe Mode (mình sẽ thêm).
3. Reproduce:
   - Launch lần đầu → nếu vào Safe Mode: thử exit.
   - Tap History → tap Sync Center.
4. Gửi lại logs (`GET /logs?runId=pre`).

