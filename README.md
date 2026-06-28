# OAK Healthy

OAK Healthy la app quan ly "stack" thuc pham bo sung theo chu ky On/Off (uong/nghi), ho tro da hoc vien (Coach Mode) va dong bo da thiet bi qua Firebase Realtime Database. App huong toi Trader, Van Dong Vien va ca Bac Si theo doi benh nhan.

## Tai app (artifacts moi nhat)

- Android (APK): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/28308418554/artifacts/7930168260
- iOS (IPA): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/28308418531/artifacts/7930173339
- iOS (dSYMs): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/28308418531/artifacts/7930173223

Luu y: artifacts tai tu GitHub Actions co the yeu cau dang nhap GitHub va het han theo chinh sach luu tru cua GitHub.

## Thay doi gan day

- Sync:
  - Realtime sync iOS <-> Android qua Firebase Realtime Database listeners (khong can poll).
  - Field-level merge: sua field khac nhau tren 2 may khong bi de nhau.
  - Manifest cache invalidation khi re-host.
- iOS:
  - Native liquid glass TabView (iOS 26+).
  - Xoa custom OAKBottomTabBar — dung native tab bar.
- Android:
  - Liquid glass bottom bar (inner glow, sheen, multi-shadow).
  - Swipe giua 3 tab qua HorizontalPager.
  - AGP 8.5.2 + Kotlin 2.0.21 + Compose BOM 2024.06.00.
  - Room migrate tu kapt sang KSP.
  - Split SettingsScreen.kt -> SettingsComponents.kt + MyStackListScreen.kt.
- Ca 2 nen tang:
  - Xoa ~250 dong dead code.
  - Onboarding crash fix (Android).
  - Tab bar layout overlap fix (Android).
  - Manifest cache invalidation tren ca 2 nen tang.

## Huong dan su dung nhanh

### Thiet lap ban dau

1) Mo app -> cap quyen thong bao (neu muon nhac uong).
2) (Tu chon) Bat che do giao dien theo "He thong" trong Cai dat.

### Tao stack va lich uong

1) Vao tab Stack -> them thuc pham bo sung.
2) Chon lich:
   - Uong lien tuc / Chu ky On-Off / Uong cach N ngay / Theo thu trong tuan.
3) Quay lai Trang chu de xem "Can uong hom nay".

### Tick "Da uong / Bo qua"

- Ban co the tick truc tiep tren Trang chu hoac tick ngay tren thong bao (Taken/Skip) de thao tac nhanh.

### Dong bo 2 thiet bi (Sync Center)

Thiet bi A (may dang co du lieu):
1) Mo Sync Center -> Xuat key (cham vao key de copy).
2) Tao Link Code.

Thiet bi B (may moi):
1) Mo Sync Center -> Dan key (nut Dan).
2) Dan Link Code.
3) Bam Tai ve / Dong bo.

Goi y:
- Auto-Sync dung Firebase realtime listeners, dong bo tan thoi khi app dang mo.
- Auto-Sync tu tat neu thieu key; chi can dan key roi bat lai.

## Guide & Release Notes

- Huong dan up GitHub release: `docs/github-release-guide.md`
- Release notes de dan len GitHub: `docs/release-notes-v1.0.1.md`

## Tinh nang chinh

- Dashboard theo moc gio: "Can uong hom nay", tick "Da uong/Bo qua" va luu vao Lich su.
- Lich uong linh hoat:
  - Uong lien tuc
  - Chu ky On/Off theo ngay bat dau (x ngay uong / y ngay nghi)
  - Uong cach N ngay (phu hop lich uong/tiem cach ngay)
  - Lap theo thu trong tuan + cach N tuan (Weekly Recurrence)
  - Tong thoi han tinh theo ngay (de trong = vo thoi han)
- Tick ngay tren thong bao (Taken/Skip) de thao tac nhanh.
- Dong bo da thiet bi:
  - Phat du lieu (tao ma lien ket / Bin ID)
  - Tai ve (Tai + ap dung du lieu truc tiep)
  - Tu dong dong bo (Auto-Sync) (bat/tat trong Cai dat)
- Bao mat ma lien ket:
  - Nut "Con mat" de an/ hien ma khi dung noi cong cong.
  - "Thu hoi ma" de xoa vinh vien du lieu tren Cloud.
- Chan doan thong bao:
  - "Kiem tra danh sach thong bao" de xem app da gui lenh dat lich (phu thuoc quyen he dieu hanh/chung chi)
- Nang cap giao dien/trai nghiem:
  - 3 tab: Trang chu / Stack / Lich su
  - Loc nhanh Due/Qua han/Da uong/Bo qua + badge Qua han
  - Insights 7/30 ngay dang chart + xem chi tiet bang nut mui ten

## Ghi chu trien khai (moi)

- Android: tai APK va cai truc tiep (co the can bat "Cai dat ung dung khong ro nguon goc").
- iOS: IPA yeu cau cai qua TestFlight hoac tu ky (AltStore/Sideloadly). Neu ban chi muon xem demo UI/flow thi van co the tai IPA de tham khao build.
- Auto-Sync:
  - Android chay theo WorkManager dinh ky (toi thieu 15 phut theo gioi han he dieu hanh) + co job one-off de sync som khi ban thao tac.
  - iOS debounce cac trigger sync va giam polling khi idle de tiet kiem pin.

## Rules

- Max 30 lines / function.

- iOS:
  - Sau khi "Tai ve", app se tu ap dung du lieu va tu len lich lai thong bao (neu da bat "Cho phep gui thong bao").
  - Safe Mode la co che tu phuc hoi khi phat hien crash loop; khong con nut bat Safe Mode thu man o man khoi dong.
  - Decode CycleConfig/WeeklyRecurrenceConfig duoc lam "tolent" tranh crash khi du lieu lech schema.
- Android:
  - Cap nhat noi dung "Gioi thieu" ( viet hoa Trader/Van Dong Vien/Bac Si).
  - Toi uu scroll jank: them LazyListState va key on dinh cho cac danh sach (Home/History).

## Cau truc du an

- `iOS/` — Swift 6.2+, SwiftUI, Strict Concurrency
- `Android/` — Kotlin + Jetpack Compose (Material 3)
- `backup_project.py` — Script backup du an (zip), co che do git sync

## Backup & Git Sync

### Tao backup

```bash
python backup_project.py --root .
```

### Backup + tu git add/commit/push

```bash
python backup_project.py --root . --git-sync --git-remote origin --git-branch main
```

Tuy chon message:

```bash
python backup_project.py --root . --git-sync --git-message "chore(backup): update"
```

## Luu y bao mat

- Khong commit file chua secrets (vi du `iOS/Secrets.xcconfig` da duoc loai khoi backup theo mac dinh).
- Khong dua API key vao README. Cau hinh key theo co che build (CI/xcconfig/BuildConfig).
