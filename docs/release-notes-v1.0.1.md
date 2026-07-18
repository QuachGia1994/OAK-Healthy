# OAK Healthy v1.0.1

## Highlights

- iOS doi sang custom glass tab bar de fix tab `Stack` bi lech va loai bo hien tuong bar chong bar.
- Android co bottom bar moi theo cung style, dong thoi bo nen "slab" vuong phia sau.
- Loading/launch tren ca iOS va Android hien thi dung theo Light/Dark theme.
- Dong bo Android -> iOS duoc keo nhanh hon khi mo lai app va giam truong hop chi nhan mot phan du lieu.
- Notification iOS co thao tac `Da uong` / `Bo qua` va xu ly lai an toan khi app duoc mo.
- Frame spike Android giam ro tren Dashboard va Stack khi cuon tren thiet bi that.

## iOS

- Fix lech tab `Stack` bang custom bottom bar va bo `TabView` he thong trong luong dieu huong chinh.
- Them khoang trong day de bottom bar khong che nut o cuoi man hinh.
- Dong bo mau launch/loading theo theme va hien thi logo ro hon.
- Khoi phuc chip `Chuoi` trong khu vuc "Can uong hom nay".
- Bo delay foreground sync va them retry ngan khi chi `stack` hoac `history` thay doi.
- Tiep tuc upload `IPA` unsigned va `dSYMs` de de test va symbolicate crash.
- Luu notification action truoc khi callback ket thuc va replay idempotent khi app active lai.

## Android

- Them custom glass bottom nav dong bo visual voi iOS.
- Fix cac loi build lien quan Compose compatibility (`weight`, `align`, `offset`).
- Tang do tuong phan text/icon trong dark mode.
- Luu theme Light/Dark/System de mo lai app van giu lua chon truoc do.
- Splash/loading doi mau dung theo theme hien tai.
- Giam shadow/gradient ton kem va gom accessibility semantics cho cac danh sach cuon.

## Artifact links

- Android APK: https://github.com/QuachGia1994/OAK-Healthy/actions/runs/29646728542/artifacts/8430268832
- Android workflow: https://github.com/QuachGia1994/OAK-Healthy/actions/runs/29646728542
- iOS IPA: https://github.com/QuachGia1994/OAK-Healthy/actions/runs/29647622068/artifacts/8430535370
- iOS dSYMs: https://github.com/QuachGia1994/OAK-Healthy/actions/runs/29647622068/artifacts/8430535059
- iOS workflow: https://github.com/QuachGia1994/OAK-Healthy/actions/runs/29647622068

## Notes

- Artifact GitHub Actions co the yeu cau dang nhap GitHub va se het han theo retention policy.
- Cac artifact tren het han ngay `2026-10-16` (UTC); neu het han, tai tu workflow thanh cong moi nhat.
- APK hien la Debug build de test, khong phai ban Play Store.
- IPA hien la unsigned build phu hop de test/sideload; ban App Store/TestFlight se di theo workflow `release.yml`.
