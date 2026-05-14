import argparse
import subprocess
import sys
from pathlib import Path


SVG_SOURCE = """<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#00C853"/>
    <stop offset="0.55" stop-color="#007A4B"/>
    <stop offset="1" stop-color="#004D40"/>
  </linearGradient>
  <radialGradient id="shine" cx="0.32" cy="0.28" r="0.78">
    <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.22"/>
    <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
  </radialGradient>
  <filter id="outerShadow" x="-20%" y="-20%" width="140%" height="140%">
    <feDropShadow dx="0" dy="26" stdDeviation="22" flood-color="#000000" flood-opacity="0.40"/>
  </filter>
  <filter id="innerSoft" x="-20%" y="-20%" width="140%" height="140%">
    <feGaussianBlur in="SourceAlpha" stdDeviation="10" result="blur"/>
    <feOffset dx="-10" dy="-10" result="off"/>
    <feComposite in="off" in2="SourceAlpha" operator="arithmetic" k2="-1" k3="1" result="inner"/>
    <feColorMatrix in="inner" type="matrix"
      values="1 0 0 0 1  0 1 0 0 1  0 0 1 0 1  0 0 0 0.20 0" result="innerColor"/>
    <feComposite in="innerColor" in2="SourceGraphic" operator="over"/>
  </filter>
  <linearGradient id="leaf" x1="0.2" y1="0.1" x2="0.9" y2="0.95">
    <stop offset="0" stop-color="#E9FFF2"/>
    <stop offset="0.35" stop-color="#BDEFD4"/>
    <stop offset="1" stop-color="#006D48"/>
  </linearGradient>
  <linearGradient id="pill" x1="0.15" y1="0.15" x2="0.85" y2="0.85">
    <stop offset="0" stop-color="#FFFFFF"/>
    <stop offset="0.45" stop-color="#CFF7E0"/>
    <stop offset="1" stop-color="#00A35E"/>
  </linearGradient>
</defs>
<rect width="1024" height="1024" rx="220" fill="url(#bg)"/>
<rect width="1024" height="1024" rx="220" fill="url(#shine)"/>
<g filter="url(#outerShadow)">
  <g filter="url(#innerSoft)">
    <path d="M330 520c0-200 140-340 340-340 88 0 170 30 240 88 14 12 14 33 0 45l-46 46c-12 12-31 13-44 2-41-31-89-48-150-48-133 0-226 93-226 207s93 207 226 207c60 0 109-17 150-48 13-11 32-10 44 2l46 46c14 12 14 33 0 45-70 58-152 88-240 88-200 0-340-140-340-340z" fill="url(#leaf)"/>
    <path d="M430 530c0-104 84-188 188-188h126c84 0 152 68 152 152s-68 152-152 152H618c-104 0-188-84-188-188z" fill="url(#pill)"/>
    <path d="M468 482c28-62 90-96 154-96h110c56 0 104 28 128 70 7 13 3 29-9 36-13 7-29 3-36-9-17-28-49-45-83-45H622c-47 0-90 24-111 66-6 14-22 19-35 13-14-6-19-22-13-35z" fill="#FFFFFF" opacity="0.20"/>
    <path d="M460 622c34 46 88 74 158 74h126c58 0 114-26 150-72 9-12 27-14 39-5 12 9 14 27 5 39-47 60-119 96-194 96H618c-96 0-170-38-219-104-10-12-7-31 5-40 12-10 31-7 41 5z" fill="#000000" opacity="0.18"/>
    <path d="M646 276v560" stroke="#FFFFFF" stroke-width="28" stroke-linecap="round" opacity="0.18"/>
  </g>
</g>
</svg>"""

ICON_SPECS: tuple[tuple[str, int], ...] = (
    ("icon_20@1x.png", 20),
    ("icon_20@2x.png", 40),
    ("icon_20@3x.png", 60),
    ("icon_29@1x.png", 29),
    ("icon_29@2x.png", 58),
    ("icon_29@3x.png", 87),
    ("icon_40@1x.png", 40),
    ("icon_40@2x.png", 80),
    ("icon_40@3x.png", 120),
    ("icon_60@2x.png", 120),
    ("icon_60@3x.png", 180),
    ("icon_76@1x.png", 76),
    ("icon_76@2x.png", 152),
    ("icon_83.5@2x.png", 167),
    ("icon_20_ipad@2x.png", 40),
    ("icon_29_ipad@2x.png", 58),
    ("icon_40_ipad@2x.png", 80),
    ("icon_1024.png", 1024),
)


def find_imagemagick() -> list[str] | None:
    for cmd in (["magick"], ["convert"]):
        try:
            subprocess.run(cmd + ["-version"], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return cmd
        except (subprocess.CalledProcessError, FileNotFoundError):
            continue
    return None


def write_svg(temp_dir: Path) -> Path:
    temp_dir.mkdir(parents=True, exist_ok=True)
    svg_path = temp_dir / "oak_app_icon.svg"
    svg_path.write_text(SVG_SOURCE, encoding="utf-8")
    return svg_path


def render_png(tool: list[str], svg_path: Path, out_path: Path, size: int) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    args = tool + [
        "-background", "none",
        str(svg_path),
        "-resize", f"{size}x{size}",
        str(out_path),
    ]
    subprocess.run(args, check=True)


def generate(appiconset_dir: Path) -> int:
    tool = find_imagemagick()
    if tool is None:
        print("Missing ImageMagick. Install it and ensure `magick` is on PATH.", file=sys.stderr)
        return 1

    temp_dir = appiconset_dir / ".tmp_icon_gen"
    svg_path = write_svg(temp_dir)
    for name, px in ICON_SPECS:
        render_png(tool, svg_path, appiconset_dir / name, px)
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--appiconset",
        default=str(Path("iOS") / "Assets.xcassets" / "AppIcon.appiconset"),
    )
    args = parser.parse_args()
    return generate(Path(args.appiconset))


if __name__ == "__main__":
    raise SystemExit(main())
