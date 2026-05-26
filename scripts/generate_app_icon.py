from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


def hex_rgba(value: str, alpha: int = 255) -> tuple[int, int, int, int]:
    value = value.strip().lstrip("#")
    r = int(value[0:2], 16)
    g = int(value[2:4], 16)
    b = int(value[4:6], 16)
    return (r, g, b, alpha)


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def lerp_color(c1: tuple[int, int, int], c2: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return (
        int(lerp(c1[0], c2[0], t)),
        int(lerp(c1[1], c2[1], t)),
        int(lerp(c1[2], c2[2], t)),
    )


def diagonal_gradient(size: int, c1: tuple[int, int, int], c2: tuple[int, int, int]) -> Image.Image:
    img = Image.new("RGB", (size, size), c1)
    px = img.load()
    denom = float((size - 1) * 2)
    for y in range(size):
        for x in range(size):
            t = (x + y) / denom
            px[x, y] = lerp_color(c1, c2, t)
    return img


def radial_highlight(size: int, center: tuple[int, int], radius: int, alpha: int) -> Image.Image:
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    cx, cy = center
    draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=alpha)
    mask = mask.filter(ImageFilter.GaussianBlur(radius * 0.28))
    layer.putalpha(mask)
    white = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    white.putalpha(mask)
    return white


def rounded_mask(size: int, radius: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, size, size), radius=radius, fill=255)
    return mask


def make_icon(size: int = 1024) -> Image.Image:
    bg1 = (34, 229, 143)
    bg2 = (6, 86, 74)
    base = diagonal_gradient(size, bg1, bg2).convert("RGBA")

    base = Image.alpha_composite(
        base,
        radial_highlight(size=size, center=(int(size * 0.20), int(size * 0.16)), radius=int(size * 0.60), alpha=100),
    )

    base = Image.alpha_composite(
        base,
        radial_highlight(size=size, center=(int(size * 0.90), int(size * 0.92)), radius=int(size * 0.85), alpha=70),
    )

    content = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(content)

    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)

    acorn_center = (int(size * 0.43), int(size * 0.54))
    acorn_r = int(size * 0.28)
    pill_w = int(size * 0.54)
    pill_h = int(size * 0.30)
    pill_left = int(size * 0.50)
    pill_top = int(size * 0.42)
    pill_radius = int(pill_h * 0.48)

    sd.ellipse(
        (
            acorn_center[0] - acorn_r + int(size * 0.02),
            acorn_center[1] - acorn_r + int(size * 0.03),
            acorn_center[0] + acorn_r + int(size * 0.02),
            acorn_center[1] + acorn_r + int(size * 0.03),
        ),
        fill=(0, 0, 0, 60),
    )
    sd.rounded_rectangle(
        (
            pill_left + int(size * 0.02),
            pill_top + int(size * 0.03),
            pill_left + pill_w + int(size * 0.02),
            pill_top + pill_h + int(size * 0.03),
        ),
        radius=pill_radius,
        fill=(0, 0, 0, 50),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=int(size * 0.010)))
    content.alpha_composite(shadow)

    acorn_fill = hex_rgba("#F4FFFB", 252)
    d.ellipse(
        (
            acorn_center[0] - acorn_r,
            acorn_center[1] - acorn_r,
            acorn_center[0] + acorn_r,
            acorn_center[1] + acorn_r,
        ),
        fill=acorn_fill,
    )

    cap_w = int(acorn_r * 0.64)
    cap_h = int(acorn_r * 0.32)
    cap_x = acorn_center[0] - cap_w // 2
    cap_y = acorn_center[1] - acorn_r - int(cap_h * 0.06)
    d.polygon(
        [
            (cap_x, cap_y + cap_h),
            (cap_x + cap_w, cap_y + cap_h),
            (acorn_center[0] + int(cap_w * 0.26), cap_y),
            (acorn_center[0] - int(cap_w * 0.26), cap_y),
        ],
        fill=hex_rgba("#E9FFF6", 220),
    )

    pill_fill = hex_rgba("#BFF7DF", 252)
    d.rounded_rectangle(
        (pill_left, pill_top, pill_left + pill_w, pill_top + pill_h),
        radius=pill_radius,
        fill=pill_fill,
    )

    vein_w = int(size * 0.026)
    vein_h = int(size * 0.62)
    vein_x = int(size * 0.58)
    vein_y = int(size * 0.23)
    d.rounded_rectangle(
        (vein_x, vein_y, vein_x + vein_w, vein_y + vein_h),
        radius=vein_w // 2,
        fill=hex_rgba("#FFFFFF", 78),
    )

    gloss = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    gd = ImageDraw.Draw(gloss)
    gd.ellipse(
        (
            int(size * 0.18),
            int(size * 0.16),
            int(size * 0.84),
            int(size * 0.76),
        ),
        fill=hex_rgba("#FFFFFF", 24),
    )
    gloss = gloss.filter(ImageFilter.GaussianBlur(radius=int(size * 0.02)))
    content.alpha_composite(gloss)

    base = Image.alpha_composite(base, content)

    mask = rounded_mask(size, radius=int(size * 0.22))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="icon.png")
    parser.add_argument("--size", type=int, default=1024)
    args = parser.parse_args()

    out_path = Path(args.out)
    img = make_icon(size=args.size)
    out_path.write_bytes(b"")
    img.save(out_path, format="PNG", optimize=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
