from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


try:
    from PIL import Image
except ModuleNotFoundError:
    Image = None


IOS_SPECS: tuple[dict, ...] = (
    {"idiom": "iphone", "size": "20x20", "scale": "2x", "filename": "icon_20@2x.png", "px": 40},
    {"idiom": "iphone", "size": "20x20", "scale": "3x", "filename": "icon_20@3x.png", "px": 60},
    {"idiom": "iphone", "size": "29x29", "scale": "2x", "filename": "icon_29@2x.png", "px": 58},
    {"idiom": "iphone", "size": "29x29", "scale": "3x", "filename": "icon_29@3x.png", "px": 87},
    {"idiom": "iphone", "size": "40x40", "scale": "2x", "filename": "icon_40@2x.png", "px": 80},
    {"idiom": "iphone", "size": "40x40", "scale": "3x", "filename": "icon_40@3x.png", "px": 120},
    {"idiom": "iphone", "size": "60x60", "scale": "2x", "filename": "icon_60@2x.png", "px": 120},
    {"idiom": "iphone", "size": "60x60", "scale": "3x", "filename": "icon_60@3x.png", "px": 180},
    {"idiom": "ipad", "size": "20x20", "scale": "1x", "filename": "icon_20@1x.png", "px": 20},
    {"idiom": "ipad", "size": "20x20", "scale": "2x", "filename": "icon_20_ipad@2x.png", "px": 40},
    {"idiom": "ipad", "size": "29x29", "scale": "1x", "filename": "icon_29@1x.png", "px": 29},
    {"idiom": "ipad", "size": "29x29", "scale": "2x", "filename": "icon_29_ipad@2x.png", "px": 58},
    {"idiom": "ipad", "size": "40x40", "scale": "1x", "filename": "icon_40@1x.png", "px": 40},
    {"idiom": "ipad", "size": "40x40", "scale": "2x", "filename": "icon_40_ipad@2x.png", "px": 80},
    {"idiom": "ipad", "size": "76x76", "scale": "1x", "filename": "icon_76@1x.png", "px": 76},
    {"idiom": "ipad", "size": "76x76", "scale": "2x", "filename": "icon_76@2x.png", "px": 152},
    {"idiom": "ipad", "size": "83.5x83.5", "scale": "2x", "filename": "icon_83.5@2x.png", "px": 167},
    {"idiom": "ios-marketing", "size": "1024x1024", "scale": "1x", "filename": "icon_1024.png", "px": 1024},
)

ANDROID_DPI: tuple[tuple[str, int], ...] = (
    ("mipmap-mdpi", 48),
    ("mipmap-hdpi", 72),
    ("mipmap-xhdpi", 96),
    ("mipmap-xxhdpi", 144),
    ("mipmap-xxxhdpi", 192),
)


def ensure_pillow() -> bool:
    if Image is not None:
        return True
    print("Missing Pillow. Install it: py -m pip install pillow", file=sys.stderr)
    return False


def resample_lanczos() -> int:
    resampling = getattr(Image, "Resampling", None)
    return resampling.LANCZOS if resampling is not None else Image.LANCZOS


def center_crop_square(img: Image.Image) -> Image.Image:
    if img.width == img.height:
        return img
    side = min(img.width, img.height)
    left = (img.width - side) // 2
    top = (img.height - side) // 2
    return img.crop((left, top, left + side, top + side))


def open_base_image(path: Path) -> Image.Image:
    img = Image.open(path).convert("RGBA")
    return center_crop_square(img)


def find_default_source(input_path: Path, appiconset_dir: Path) -> Path | None:
    if input_path.exists():
        return input_path
    ios_1024 = appiconset_dir / "icon_1024.png"
    if ios_1024.exists():
        return ios_1024
    android_legacy = Path("Android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")
    if android_legacy.exists():
        return android_legacy
    return None


def write_ios_contents_json(appiconset_dir: Path) -> None:
    contents = {"images": [], "info": {"version": 1, "author": "xcode"}}
    for s in IOS_SPECS:
        contents["images"].append(
            {"idiom": s["idiom"], "size": s["size"], "scale": s["scale"], "filename": s["filename"]}
        )
    (appiconset_dir / "Contents.json").write_text(json.dumps(contents, indent=2), encoding="utf-8")


def save_png(img: Image.Image, out_path: Path, px: int) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    resized = img.resize((px, px), resample=resample_lanczos())
    resized.save(out_path, format="PNG", optimize=True)


def generate_ios(appiconset_dir: Path, base: Image.Image) -> None:
    appiconset_dir.mkdir(parents=True, exist_ok=True)
    write_ios_contents_json(appiconset_dir)
    for s in IOS_SPECS:
        save_png(base, appiconset_dir / s["filename"], int(s["px"]))


def generate_android(res_dir: Path, base: Image.Image) -> None:
    for folder, px in ANDROID_DPI:
        out_dir = res_dir / folder
        save_png(base, out_dir / "ic_launcher.png", px)
        save_png(base, out_dir / "ic_launcher_round.png", px)
        save_png(base, out_dir / "ic_launcher_foreground.png", px)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default="icon.png")
    parser.add_argument("--generate-android", action="store_true")
    parser.add_argument(
        "--appiconset",
        default=str(Path("iOS/Assets.xcassets/AppIcon.appiconset")),
    )
    parser.add_argument("--android-res", default=str(Path("Android/app/src/main/res")))
    args = parser.parse_args()

    if not ensure_pillow():
        return 1
    appiconset_dir = Path(args.appiconset)
    input_path = Path(args.input)
    source = find_default_source(input_path, appiconset_dir)
    if source is None:
        print("Missing base image. Provide icon.png or ensure iOS icon_1024.png exists.", file=sys.stderr)
        return 1

    base = open_base_image(source).resize((1024, 1024), resample=resample_lanczos())
    if not input_path.exists():
        base.save(input_path, format="PNG", optimize=True)
    generate_ios(appiconset_dir, base)
    if args.generate_android:
        generate_android(Path(args.android_res), base)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
