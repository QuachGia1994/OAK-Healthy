# GitHub Release Guide

Muc tieu: chuan bi noi dung va link can thiet de dang GitHub release cho `OAK Healthy` ma khong bi sai artifact.

## Ban hien tai

- Version app: `1.0.1`
- Android APK artifact: https://github.com/QuachGia1994/OAK-Healthy/actions/runs/27621728551/artifacts/7668623507
- iOS IPA artifact: https://github.com/QuachGia1994/OAK-Healthy/actions/runs/27621728615/artifacts/7668672556
- iOS dSYMs artifact: https://github.com/QuachGia1994/OAK-Healthy/actions/runs/27621728615/artifacts/7668671910

## Cach dang GitHub release

1. Push tag theo format `v*`, vi du `v1.0.1`.
2. Mo tab Releases tren GitHub va tao release moi theo tag vua push.
3. Dat title gon ro, vi du `OAK Healthy v1.0.1`.
4. Copy noi dung tu `docs/release-notes-v1.0.1.md` vao phan description.
5. Dat link artifact Android/iOS o dau release note hoac pin them trong comment/mo ta neu can.
6. Publish release sau khi kiem tra lai artifact, version va changelog.

## Luu y workflow

- Workflow `release.yml` ho tro 2 cach:
  - `push tags v*`: chay release tu dong theo tag.
  - `workflow_dispatch`: chay tay va chon `ios_lane` / `android_track`.
- Neu thieu secrets store release, workflow se skip job upload App Store / Play Console thay vi fail toan bo.
- APK/IPA trong README la artifact build de test nhanh, khong phai file store release da ky.

## Checklist truoc khi publish

1. README da tro dung artifact moi nhat.
2. Release notes da cap nhat cac fix iOS/Android gan nhat.
3. Artifact van con han va mo duoc khi dang nhap GitHub.
4. Tag release khop version trong Android va iOS (`1.0.1`).
