# E2E Sync Checklist (2 thiết bị)

Mục tiêu: verify runtime end-to-end sau các thay đổi sync/encryption/manifest/i18n.

## Chuẩn bị
- 2 thiết bị A/B, cài cùng version app (**Android + iOS: 1.0.1**).
- Mỗi thiết bị có ít nhất 1 client, vài supplements và vài intake records.
- Vào Sync Center:
  - Clear log (nếu có).
  - Note lại Link Code đang dùng (nếu có).

## 1) Legacy 1-bin (backward compatibility)
**1.1 Link**
- A: dùng “link code” legacy (nếu bạn có code cũ đã từng host theo dạng 1-bin).
- B: nhập code → pull/sync.

**1.2 Verify**
- Dữ liệu sau sync: supplements/records khớp kỳ vọng.
- Không crash.
- Log có phase DONE.

**1.3 Round-trip**
- B: đổi 1 supplement + thêm 1 record → sync.
- A: sync → verify nhận thay đổi.

## 2) Manifest 2-bins (stack/history)
**2.1 Host mới**
- A: Host để tạo code mới (manifest).
- B: nhập code mới → pull/sync.

**2.2 Stack-only change**
- A: chỉ sửa supplement (stack) → sync.
- B: sync → verify nhận thay đổi.
- Expect: bytes upload/download nhỏ hơn so với full backup, log phase hợp lý.

**2.3 History-only change**
- B: chỉ thêm record (history) → sync.
- A: sync → verify nhận thay đổi.

**2.4 DoseKey/LWW hội tụ (không duplicate record)**
- Chuẩn bị 1 supplement có multi-time: `08:00, 20:00` trên cả A/B (sync trước để 2 bên cùng state).
- Tạo conflict offline:
  - B: tắt mạng.
  - A: đúng giờ `08:00` → mark Taken → sync.
  - B: (offline) đúng giờ `08:00` → mark Skipped.
  - B: bật mạng → sync.
  - A: sync.
- Verify:
  - Chỉ có 1 record cho dose event `08:00` (không được xuất hiện 2 dòng Taken/Skipped cho cùng giờ).
  - Trạng thái cuối cùng theo LWW (record có `updatedAt` mới hơn thắng).
- Lặp lại nhanh cho mốc `20:00` để đảm bảo multi-time không overwrite nhau.

**2.5 Notification reschedule sau edit/delete/sync (không orphan/không giờ cũ)**
- A: với supplement `08:00, 20:00` → edit thành `09:00, 20:00` → save.
- Verify ngay trên A:
  - Android: mở “Notification List / Notification Check” để confirm không còn `08:00`, có `09:00`.
  - iOS: mở “Notification Debug” để confirm không còn `08:00`, có `09:00`.
- B: sync → verify nhận intakeTime mới và list notifications reflect state mới (không còn giờ cũ).
- Delete:
  - A: delete supplement → sync.
  - B: sync → verify không còn pending notifications thuộc supplement đó (không orphan).

## 3) Encryption on/off + import/rotate
**3.1 Enable**
- A: bật encryption → export key.
- B: bật encryption → import key từ A → sync.
- Expect: không lỗi decrypt.

**3.2 Rotate**
- A: rotate key → export key mới.
- B: import key mới → sync.
- Expect: dữ liệu cũ + mới đọc được.

**3.3 Disable**
- A/B: tắt encryption → sync.
- Expect: vẫn sync OK.

## 4) Conflict retry (409/412)
**4.1 Tạo conflict**
- Tắt mạng trên B.
- A: sửa 1 supplement → sync (đẩy lên cloud).
- B: (offline) sửa cùng supplement theo cách khác.
- B: bật mạng → sync.

**4.2 Verify**
- Expect: có phase CONFLICT/RETRY (hoặc tương đương) và cuối cùng DONE.
- Không crash; dữ liệu merge hợp lý (không mất record).

## 5) Offline/retry (độ bền)
- Tắt mạng giữa lúc đang sync (pull/push).
- Expect: app không crash, hiển thị error rõ ràng, retry được khi có mạng.

## Log để gửi khi báo lỗi
- Android: Export log (copy clipboard) → dán gửi.
- iOS: Export log (ShareLink) → gửi text raw JSON.
- Kèm: thiết bị A/B, bước checklist, trạng thái encryption (on/off), có rotate/import không, link code loại nào (legacy/manifest).
