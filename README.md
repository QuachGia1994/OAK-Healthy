# OAK Healthy

OAK Healthy l\u00e0 app qu\u1ea3n l\u00fd \u201cstack\u201d th\u1ef1c ph\u1ea9m b\u1ed5 sung theo chu k\u1ef3 On/Off (u\u1ed1ng/ngh\u1ec9), h\u1ed7 tr\u1ee3 \u0111a h\u1ecdc vi\u00ean (Coach Mode) v\u00e0 \u0111\u1ed3ng b\u1ed9 \u0111a thi\u1ebft b\u1ecb qua Firebase Realtime Database. App h\u01b0\u1edbng t\u1edbi Trader, V\u1ea5n \u0110\u1ed9ng Vi\u00ean v\u00e0 c\u1ea3 B\u00e1c S\u0129 theo d\u00f2i b\u1ec7nh nh\u00e2n.

## T\u1ea3i app (artifacts m\u1edbi nh\u1ea5t)

- Android (APK): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/28308418554/artifacts/7930168260
- iOS (IPA): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/28308418531/artifacts/7930173339
- iOS (dSYMs): https://github.com/QuachGia1994/OAK-Healthy/actions/runs/28308418531/artifacts/7930173223

L\u01b0u \u00fd: artifacts t\u1ea3i t\u1eeb GitHub Actions c\u00f3 th\u1ec3 y\u00eau c\u1ea7u \u0111\u0103ng nh\u1eadp GitHub v\u00e0 h\u1ebft h\u1ea1n theo ch\u00ednh s\u00e1ch l\u01b0u tr\u1eef c\u1ee7a GitHub.

## Thay \u0111\u1ed5i g\u1ea7n \u0111\u00e2y

- Sync:
  - Realtime sync iOS \u2194 Android qua Firebase Realtime Database listeners (kh\u00f4ng c\u1ea7n poll).
  - Field-level merge: s\u1eeda field kh\u00e1c nhau tr\u00ean 2 m\u00e1y kh\u00f4ng b\u1ecb \u0111\u00e8 nhau.
  - Manifest cache invalidation khi re-host.
- iOS:
  - Native liquid glass TabView (iOS 26+).
  - X\u00f3a custom OAKBottomTabBar \u2014 d\u00f9ng native tab bar.
- Android:
  - Liquid glass bottom bar (inner glow, sheen, multi-shadow).
  - Swipe gi\u1eefa 3 tab qua HorizontalPager.
  - AGP 8.5.2 + Kotlin 2.0.21 + Compose BOM 2024.06.00.
  - Room migrate t\u1eeb kapt sang KSP.
  - Split SettingsScreen.kt \u2192 SettingsComponents.kt + MyStackListScreen.kt.
- C\u1ea3 2 n\u1ec1n t\u1ea3ng:
  - X\u00f3a ~250 d\u00f2ng dead code.
  - Onboarding crash fix (Android).
  - Tab bar layout overlap fix (Android).
  - Manifest cache invalidation tr\u00ean c\u1ea3 2 n\u1ec1n t\u1ea3ng.

## H\u01b0\u1edbng d\u1eabn s\u1eed d\u1ee5ng nhanh

### Thi\u1ebft l\u1eadp ban \u0111\u1ea7u

1) M\u1edf app \u2192 c\u1ea5p quy\u1ec1n th\u00f4ng b\u00e1o (n\u1ebfu mu\u1ed1n nh\u1eafc u\u1ed1ng).
2) (T\u00f9y ch\u1ecdn) B\u1eadt ch\u1ebf \u0111\u1ed9 giao di\u1ec7n theo \u201cH\u1ec7 th\u1ed1ng\u201d trong C\u00e0i \u0111\u1eb7t.

### T\u1ea1o stack v\u00e0 l\u1ecbch u\u1ed1ng

1) V\u00e0o tab Stack \u2192 th\u00eam th\u1ef1c ph\u1ea9m b\u1ed5 sung.
2) Ch\u1ecdn l\u1ecbch:
   - U\u1ed1ng li\u00ean t\u1ee1c / Chu k\u1ef3 On\u2011Off / U\u1ed1ng c\u00e1ch N ng\u00e0y / Theo th\u1ee9 trong tu\u1ea7n.
3) Quay l\u1ea1i Trang ch\u1ee7 \u0111\u1ec3 xem \u201cC\u1ea7n u\u1ed1ng h\u00f4m nay\u201d.

### Tick \u201c\u0110\u00e3 u\u1ed1ng / B\u1ecf qua\u201d

- B\u1ea1n c\u00f3 th\u1ec3 tick tr\u1ef1c ti\u1ebfp tr\u00ean Trang ch\u1ee7 ho\u1eb7c tick ngay tr\u00ean th\u00f4ng b\u00e1o (Taken/Skip) \u0111\u1ec3 thao t\u00e1c nhanh.

### \u0110\u1ed3ng b\u1ed9 2 thi\u1ebft b\u1ecb (Sync Center)

Thi\u1ebft b\u1ecb A (m\u00e1y \u0111ang c\u00f3 d\u1eef li\u1ec7u):
1) M\u1edf Sync Center \u2192 Xu\u1ea5t key (ch\u1ea1m v\u00e0o key \u0111\u1ec3 copy).
2) T\u1ea1o Link Code.

Thi\u1ebft b\u1ecb B (m\u00e1y m\u1edbi):
1) M\u1edf Sync Center \u2192 D\u00e1n key (n\u1eedt D\u00e1n).
2) D\u00e1n Link Code.
3) B\u1ea3m T\u1ea3i v\u1ec1 / \u0110\u1ed3ng b\u1ed9.

G\u1ee3i \u00fd:
- Auto-Sync d\u00f9ng Firebase realtime listeners, \u0111\u1ed3ng b\u1ed9 t\u1eebng th\u1eddi khi app \u0111ang m\u1edf.
- Auto-Sync t\u1ef1 t\u1eaft n\u1ebfu thi\u1ebfu key; ch\u1ec9 c\u1ea7n d\u00e1n key r\u1ed3i b\u1eadt l\u1ea1i.

## Guide & Release Notes

- H\u01b0\u1edbng d\u1eabn up GitHub release: `docs/github-release-guide.md`
- Release notes \u0111\u1ec3 d\u00e1n l\u00ean GitHub: `docs/release-notes-v1.0.1.md`

## T\u00ednh n\u0103ng ch\u00ednh

- Dashboard theo m\u1edbc gi\u1edd: \u201cC\u1ea7n u\u1ed1ng h\u00f4m nay\u201d, tick \u201c\u0110\u00e3 u\u1ed1ng/B\u1ecf qua\u201d v\u00e0 l\u01b0u v\u00e0o L\u1ecbch s\u1eed.
- L\u1ecbch u\u1ed1ng linh ho\u1ea1t:
  - U\u1ed1ng li\u00ean t\u1ee1c
  - Chu k\u1ef3 On/Off theo ng\u00e0y b\u1eaft \u0111\u1ea7u (x ng\u00e0y u\u1ed1ng / y ng\u00e0y ngh\u1ec9)
  - U\u1ed1ng c\u00e1ch N ng\u00e0y (ph\u00f9 h\u1ee3p l\u1ecbch u\u1ed1ng/ti\u00eam c\u00e1ch ng\u00e0y)
  - L\u1eadp theo th\u1ee9 trong tu\u1ea7n + c\u00e1ch N tu\u1ea7n (Weekly Recurrence)
  - T\u1ed5ng th\u1eddi h\u1ea1n t\u00ednh theo ng\u00e0y (\u0111\u1ec3 tr\u1ed1ng = v\u00f4 th\u1eddi h\u1ea1n)
- Tick ngay tr\u00ean th\u00f4ng b\u00e1o (Taken/Skip) \u0111\u1ec3 thao t\u00e1c nhanh.
- \u0110\u1ed3ng b\u1ed9 \u0111a thi\u1ebft b\u1ecb:
  - Ph\u00e1t d\u1eef li\u1ec7u (t\u1ea1o m\u00e3 li\u00ean k\u1ebft / Bin ID)
  - T\u1ea3i v\u1ec1 (T\u1ea3i + \u00e1p d\u1ee5ng d\u1eef li\u1ec7u tr\u1ef1c ti\u1ebfp)
  - T\u1ef1 \u0111\u1ed3ng \u0111\u1ed3ng b\u1ed9 (Auto\u2011Sync) (b\u1eadt/t\u1eadt trong C\u00e0i \u0111\u1eb7t)
- B\u1ea3o m\u1eadt m\u00e3 li\u00ean k\u1ebft:
  - N\u1eedt \u201cCon m\u1ea5t\u201d \u0111\u1ec3 \u1ea9n/hi\u1ec7n m\u00e3 khi d\u00f9ng n\u01a1i c\u00f4ng c\u00f4ng
  - \u201cThu h\u1ed3i m\u00e3\u201d \u0111\u1ec3 x\u00f3a v\u0303nh vi\u1ec5n d\u1eef li\u1ec7u tr\u00ean Cloud
- Ch\u1ea9n \u0111o\u00e1n th\u00f4ng b\u00e1o:
  - \u201cKi\u1ec3m tra danh s\u00e1ch th\u00f4ng b\u00e1o\u201d \u0111\u1ec3 xem app \u0111\u00e3 g\u1eedi l\u1ec7nh \u0111\u1eb7t l\u1ecbch (ph\u1ee7 thu\u1ed9c quy\u1ec1n h\u1ec7 \u0111i\u1ec1u h\u00e0nh/ch\u1ee9ng ch\u1ea9)
- N\u00e2ng c\u1ea5p giao di\u1ec7n/tr\u1ea3i nghi\u1ec7m:
  - 3 tab: Trang ch\u1ee7 / Stack / L\u1ecbch s\u1eed
  - L\u1ecdc nhanh Due/Qu\u00e1 h\u1ea1n/\u0110\u00e3 u\u1ed1ng/B\u1ecf qua + badge Qu\u00e1 h\u1ea1n
  - Insights 7/30 ng\u00e0y d\u1ea1ng chart + xem chi ti\u1ebft b\u1eb1ng n\u1eedt m\u1ef7i t\u00ean

## Ghi ch\u00fa tri\u1ec3n khai (m\u1edbi)

- Android: t\u1ea3i APK v\u00e0 c\u00e0i tr\u1ef1c ti\u1ebfp (c\u00f3 th\u1ec3 c\u1ea7n b\u1eadt \u201cC\u00e0i \u0111\u1eb7t \u1ee9ng d\u1ee5ng kh\u00f4ng r\u00f5 ngu\u1ed3n g\u1ed1c\u201d).
- iOS: IPA y\u00eau c\u1ea7u c\u00e0i qua TestFlight ho\u1eb7c t\u1ef1 k\u00fd (AltStore/Sideloadly). N\u1ebfu b\u1ea1n ch\u1ec9 mu\u1ed1n xem demo UI/flow th\u00ec v\u1eabn c\u00f3 th\u1ec3 t\u1ea3i IPA \u0111\u1ec3 tham kh\u1ea3o build.
- Auto\u2011Sync:
  - Android ch\u1ea1y theo WorkManager \u0111\u1ecbnh k\u1ef3 (t\u1ed1i thi\u1ec7u 15 ph\u00fat theo gi\u1edbi h\u1ea1n h\u1ec7 \u0111i\u1ec1u h\u00e0nh) + c\u00f3 job one\u2011off \u0111\u1ec3 sync s\u1edbm khi b\u1ea1n thao t\u00e1c.
  - iOS debounce c\u00e1c trigger sync v\u00e0 gi\u1ea3m polling khi idle \u0111\u1ec3 ti\u1ebft ki\u1ec7m pin.

## Rules

- Max 30 lines / function.

- iOS:
  - Sau khi \u201cT\u1ea3i v\u1ec1\u201d, app s\u1ebd t\u1ef1 \u00e1p d\u1ee5ng d\u1eef li\u1ec7u v\u00e0 t\u1ef1 l\u00ean l\u1ecbch l\u1ea1i th\u00f4ng b\u00e1o (n\u1ebfu \u0111\u00e3 b\u1eadt \u201cCho ph\u00e9p g\u1eedi th\u00f4ng b\u00e1o\u201d).
  - Safe Mode l\u00e0 c\u01a1 ch\u1ebf t\u1ef1 ph\u1ee5c h\u1ed3i khi ph\u00e1t hi\u1ec7n crash loop; kh\u00f4ng c\u00f2n n\u1eedt b\u1eadt Safe Mode th\u1ee7 c\u00f4ng \u1edf m\u00e0n kh\u1edfi \u0111\u1ed9ng.
  - Decode `CycleConfig`/`WeeklyRecurrenceConfig` \u0111\u01b0\u1ee3c l\u00e0m \u201ctolerant\u201d \u0111\u1ec3 tr\u00e1nh crash khi d\u1eef li\u1ec7u l\u1ec7ch schema.
- Android:
  - C\u1eadp nh\u1eadt n\u1ed9i dung \u201cGi\u1edbi thi\u1ec7u\u201d (vi\u1ebft hoa Trader/V\u1ea5n \u0110\u1ed9ng Vi\u00ean/B\u00e1c S\u0129).
  - T\u1ed1i \u01b0u scroll jank: th\u00eam `LazyListState` v\u00e0 key \u1ed5n \u0111\u1ecbnh cho c\u00e1c danh s\u00e1ch (Home/History).

## C\u1ea5u tr\u00fac d\u1ef1 \u00e1n

- `iOS/` \u2014 Swift 6.2+, SwiftUI, Strict Concurrency
- `Android/` \u2014 Kotlin + Jetpack Compose (Material 3)
- `backup_project.py` \u2014 Script backup d\u1ef1 \u00e1n (zip), c\u00f3 ch\u1ebf \u0111\u1ed9 git sync

## Backup & Git Sync

### T\u1ea1o backup

```bash
python backup_project.py --root .
```

### Backup + t\u1ef1 git add/commit/push

```bash
python backup_project.py --root . --git-sync --git-remote origin --git-branch main
```

T\u00f9y ch\u1ec7n message:

```bash
python backup_project.py --root . --git-sync --git-message "chore(backup): update"
```

## L\u01b0u \u00fd b\u1ea3o m\u1eadt

- Kh\u00f4ng commit file ch\u1eeba secrets (v\u00ed d\u1ee5 `iOS/Secrets.xcconfig` \u0111\u00e3 \u0111\u01b0\u1ee3c lo\u1ea1i kh\u1ecfi backup theo m\u1ec7nh \u0111\u1ec7nh).
- Kh\u00f4ng \u0111\u01b0\u1ea1 API key v\u00e0o README. C\u1ea5u h\u00ecnh key theo c\u01a1 ch\u1ebef build (CI/xcconfig/BuildConfig).
