from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


try:
    from PIL import Image, ImageChops, ImageDraw, ImageFilter
except ModuleNotFoundError:
    Image = None


def icon_specs() -> list[dict]:
    return [
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
    ]


def hex_to_rgba(value: str, alpha: int = 255) -> tuple[int, int, int, int]:
    v = value.lstrip("#")
    return int(v[0:2], 16), int(v[2:4], 16), int(v[4:6], 16), alpha


def lerp(a: int, b: int, t: float) -> int:
    return int(a + (b - a) * t)


def linear_gradient(size: int, start: tuple[int, int, int], end: tuple[int, int, int]) -> Image.Image:
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        t = y / max(size - 1, 1)
        r = lerp(start[0], end[0], t)
        g = lerp(start[1], end[1], t)
        b = lerp(start[2], end[2], t)
        for x in range(size):
            px[x, y] = (r, g, b, 255)
    return img


def add_radial_shine(base: Image.Image, center: tuple[float, float], strength: float) -> Image.Image:
    size = base.size[0]
    overlay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    max_r = int(size * 0.85)
    cx, cy = int(size * center[0]), int(size * center[1])
    for i in range(max_r, 0, -1):
        t = i / max_r
        a = int(255 * strength * (t * t) * 0.22)
        draw.ellipse((cx - i, cy - i, cx + i, cy + i), fill=(255, 255, 255, a))
    return Image.alpha_composite(base, overlay)


def shadow_from_mask(mask: Image.Image, offset: tuple[int, int], blur: int, color: tuple[int, int, int, int]) -> Image.Image:
    shaded = Image.new("RGBA", mask.size, color)
    shadow = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    shadow.paste(shaded, (0, 0), mask)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    out = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    out.paste(shadow, offset, shadow)
    return out


def leaf_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    leaf_box = (int(size * 0.18), int(size * 0.18), int(size * 0.76), int(size * 0.86))
    d.ellipse(leaf_box, fill=255)
    tip = [(int(size * 0.42), int(size * 0.12)), (int(size * 0.56), int(size * 0.12)), (int(size * 0.50), int(size * 0.22))]
    d.polygon(tip, fill=255)
    return mask


def pill_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    box = (int(size * 0.36), int(size * 0.36), int(size * 0.86), int(size * 0.64))
    d.rounded_rectangle(box, radius=int(size * 0.14), fill=255)
    return mask


def seam_layer(size: int) -> Image.Image:
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    x = int(size * 0.58)
    d.line((x, int(size * 0.26), x, int(size * 0.82)), fill=(255, 255, 255, int(255 * 0.18)), width=max(1, int(size * 0.03)))
    return layer


def paste_with_mask(src: Image.Image, fill: Image.Image, mask: Image.Image) -> Image.Image:
    dst = Image.new("RGBA", fill.size, (0, 0, 0, 0))
    dst.paste(fill, (0, 0), mask)
    return Image.alpha_composite(src, dst)


def draw_leaf_pill(size: int) -> Image.Image:
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    leaf = leaf_mask(size)
    pill = pill_mask(size)
    combined = ImageChops.lighter(leaf, pill)
    outer = shadow_from_mask(combined, (int(size * 0.02), int(size * 0.03)), int(size * 0.03), (0, 0, 0, int(255 * 0.40)))
    layer = Image.alpha_composite(layer, outer)

    leaf_grad = linear_gradient(size, (233, 255, 242), (0, 109, 72))
    pill_grad = linear_gradient(size, (255, 255, 255), (0, 163, 94))
    layer = paste_with_mask(layer, leaf_grad, leaf)
    layer = paste_with_mask(layer, pill_grad, pill)

    highlight = shadow_from_mask(combined, (-int(size * 0.02), -int(size * 0.02)), int(size * 0.02), (255, 255, 255, int(255 * 0.20)))
    layer = Image.alpha_composite(layer, highlight)
    layer = Image.alpha_composite(layer, seam_layer(size))
    return layer


def generate_base_icon(size: int) -> Image.Image:
    start = hex_to_rgba("#00C853")[0:3]
    end = hex_to_rgba("#004D40")[0:3]
    base = linear_gradient(size, start, end)
    base = add_radial_shine(base, (0.32, 0.28), 1.0)
    motif = draw_leaf_pill(size)
    return Image.alpha_composite(base, motif)


def load_or_create_base(path: Path) -> Image.Image | None:
    if Image is None:
        print("Missing Pillow. Install it: py -m pip install pillow", file=sys.stderr)
        return None
    if path.exists():
        img = Image.open(path).convert("RGBA")
        if img.width != img.height:
            side = min(img.width, img.height)
            left = (img.width - side) // 2
            top = (img.height - side) // 2
            img = img.crop((left, top, left + side, top + side))
        return img
    img = generate_base_icon(1024)
    img.save(path, format="PNG")
    return img


def write_contents_json(appiconset_dir: Path, specs: list[dict]) -> None:
    contents = {"images": [], "info": {"version": 1, "author": "xcode"}}
    for s in specs:
        contents["images"].append(
            {"idiom": s["idiom"], "size": s["size"], "scale": s["scale"], "filename": s["filename"]}
        )
    (appiconset_dir / "Contents.json").write_text(json.dumps(contents, indent=2), encoding="utf-8")


def resize_and_save(base: Image.Image, out_path: Path, px: int) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    resampling = getattr(Image, "Resampling", None)
    lanczos = resampling.LANCZOS if resampling is not None else Image.LANCZOS
    img = base.resize((px, px), resample=lanczos)
    img.save(out_path, format="PNG", optimize=True)


def generate_icons(base_path: Path, appiconset_dir: Path) -> int:
    specs = icon_specs()
    base = load_or_create_base(base_path)
    if base is None:
        return 1
    appiconset_dir.mkdir(parents=True, exist_ok=True)
    write_contents_json(appiconset_dir, specs)
    for s in specs:
        resize_and_save(base, appiconset_dir / s["filename"], int(s["px"]))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--appiconset",
        default=str(Path("iOS") / "Assets.xcassets" / "AppIcon.appiconset"),
    )
    parser.add_argument("--input", default="icon.png")
    args = parser.parse_args()
    return generate_icons(Path(args.input), Path(args.appiconset))


if __name__ == "__main__":
    raise SystemExit(main())
