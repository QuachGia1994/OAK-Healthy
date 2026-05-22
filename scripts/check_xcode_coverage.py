import argparse
import json
import subprocess
import sys


def run(args: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)


def read_xccov_json(xcresult_path: str) -> dict:
    p = run(["xcrun", "xccov", "view", "--report", "--json", xcresult_path])
    if p.returncode != 0:
        raise RuntimeError((p.stderr or p.stdout or "").strip() or "xccov failed")
    return json.loads(p.stdout)


def extract_line_coverage(report: dict) -> float:
    v = report.get("lineCoverage")
    if isinstance(v, (int, float)):
        return float(v)
    raise RuntimeError("lineCoverage not found")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("xcresult_path")
    parser.add_argument("--min", type=float, default=None)
    parser.add_argument("--fail", action="store_true")
    args = parser.parse_args()

    try:
        cov = extract_line_coverage(read_xccov_json(args.xcresult_path))
    except Exception as e:
        msg = str(e).strip() or "xccov failed"
        print(f"iOS coverage unavailable: {msg}")
        return 1 if args.fail else 0

    pct = cov * 100.0
    print(f"iOS coverage: {pct:.2f}%")

    if args.min is None:
        return 0
    if cov + 1e-9 >= args.min:
        return 0
    print(f"iOS coverage below threshold: {pct:.2f}% < {args.min * 100.0:.2f}%")
    return 1 if args.fail else 0


if __name__ == "__main__":
    raise SystemExit(main())
