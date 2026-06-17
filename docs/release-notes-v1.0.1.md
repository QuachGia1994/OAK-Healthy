# OAK Healthy v1.0.1

## Highlights

- iOS doi sang custom glass tab bar de fix tab `Stack` bi lech va loai bo hien tuong bar chong bar.
- Android co bottom bar moi theo cung style, dong thoi bo nen "slab" vuong phia sau.
- Loading/launch tren ca iOS va Android hien thi dung theo Light/Dark theme.
- Dong bo Android -> iOS duoc keo nhanh hon khi mo lai app va giam truong hop chi nhan mot phan du lieu.

## iOS

- Fix lech tab `Stack` bang custom bottom bar va bo `TabView` he thong trong luong dieu huong chinh.
- Them khoang trong day de bottom bar khong che nut o cuoi man hinh.
- Dong bo mau launch/loading theo theme va hien thi logo ro hon.
- Khoi phuc chip `Chuoi` trong khu vuc "Can uong hom nay".
- Bo delay foreground sync va them retry ngan khi chi `stack` hoac `history` thay doi.
- Tiep tuc upload `IPA` unsigned va `dSYMs` de de test va symbolicate crash.

## Android

- Them custom glass bottom nav dong bo visual voi iOS.
- Fix cac loi build lien quan Compose compatibility (`weight`, `align`, `offset`).
- Tang do tuong phan text/icon trong dark mode.
- Luu theme Light/Dark/System de mo lai app van giu lua chon truoc do.
- Splash/loading doi mau dung theo theme hien tai.

## Artifact links

- Android APK: https://github.com/QuachGia1994/OAK-Healthy/actions/runs/27621728551/artifacts/7668623507
- iOS IPA: https://github.com/QuachGia1994/OAK-Healthy/actions/runs/27621728615/artifacts/7668672556
- iOS dSYMs: https://github.com/QuachGia1994/OAK-Healthy/actions/runs/27621728615/artifacts/7668671910

## Notes

- Artifact GitHub Actions co the yeu cau dang nhap GitHub va se het han theo retention policy.
- IPA hien la unsigned build phu hop de test/sideload; ban App Store/TestFlight se di theo workflow `release.yml`.
