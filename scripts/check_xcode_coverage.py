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


def iter_files(report: dict):
    targets = report.get("targets")
    if not isinstance(targets, list):
        return
    for t in targets:
        if not isinstance(t, dict):
            continue
        files = t.get("files")
        if not isinstance(files, list):
            continue
        for f in files:
            if isinstance(f, dict):
                yield f


def matches_any(path: str, patterns: list[str]) -> bool:
    if not patterns:
        return True
    for p in patterns:
        if p and p in path:
            return True
    return False


def compute_filtered_coverage(report: dict, includes: list[str], excludes: list[str]) -> float | None:
    total_exec = 0.0
    total_cov = 0.0
    for f in iter_files(report):
        path = str(f.get("path") or "")
        if not matches_any(path, includes):
            continue
        if excludes and matches_any(path, excludes):
            continue
        exec_lines = f.get("executableLines")
        if not isinstance(exec_lines, int) or exec_lines <= 0:
            continue
        covered_lines = f.get("coveredLines")
        if isinstance(covered_lines, int):
            total_exec += float(exec_lines)
            total_cov += float(covered_lines)
            continue
        lc = f.get("lineCoverage")
        if isinstance(lc, (int, float)):
            total_exec += float(exec_lines)
            total_cov += float(exec_lines) * float(lc)
            continue
    if total_exec <= 0.0:
        return None
    return total_cov / total_exec


def extract_line_coverage(report: dict) -> float:
    v = report.get("lineCoverage")
    if isinstance(v, (int, float)):
        return float(v)
    raise RuntimeError("lineCoverage not found")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("xcresult_path")
    parser.add_argument("--include", action="append", default=[])
    parser.add_argument("--exclude", action="append", default=[])
    parser.add_argument("--min", type=float, default=None)
    parser.add_argument("--fail", action="store_true")
    args = parser.parse_args()

    try:
        report = read_xccov_json(args.xcresult_path)
        cov = compute_filtered_coverage(report, args.include, args.exclude)
        if cov is None:
            cov = extract_line_coverage(report)
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
