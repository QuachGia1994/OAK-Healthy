import argparse
import pathlib
import sys
import xml.etree.ElementTree as ET


def read_line_coverage(xml_path: pathlib.Path) -> float:
    root = ET.fromstring(xml_path.read_text(encoding="utf-8", errors="replace"))
    for c in root.findall("counter"):
        if c.attrib.get("type") != "LINE":
            continue
        missed = int(c.attrib.get("missed", "0"))
        covered = int(c.attrib.get("covered", "0"))
        total = missed + covered
        if total <= 0:
            return 0.0
        return covered / total
    raise RuntimeError("LINE counter not found")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("jacoco_xml_path")
    parser.add_argument("--min", type=float, default=None)
    parser.add_argument("--fail", action="store_true")
    args = parser.parse_args()

    xml_path = pathlib.Path(args.jacoco_xml_path)
    if not xml_path.exists():
        print(f"JaCoCo XML not found: {xml_path}")
        return 1 if args.fail else 0

    cov = read_line_coverage(xml_path)
    pct = cov * 100.0
    print(f"Android coverage: {pct:.2f}%")

    if args.min is None:
        return 0
    if cov + 1e-9 >= args.min:
        return 0
    print(f"Android coverage below threshold: {pct:.2f}% < {args.min * 100.0:.2f}%")
    return 1 if args.fail else 0


if __name__ == "__main__":
    raise SystemExit(main())
