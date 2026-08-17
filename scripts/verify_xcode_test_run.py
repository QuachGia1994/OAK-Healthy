import argparse
import re
from pathlib import Path


SELECTED_PASSED = "Test Suite 'Selected tests' passed"
SELECTED_FAILED = "Test Suite 'Selected tests' failed"
SUMMARY_RE = re.compile(r"Executed\s+(\d+)\s+tests?,\s+with\s+(\d+)\s+failures?\s+\((\d+)\s+unexpected\)")


def verify(log_text: str, xcresult_exists: bool) -> tuple[bool, str]:
    if not xcresult_exists:
        return False, "xcresult is missing"
    if SELECTED_FAILED in log_text:
        return False, "selected test suite failed"
    if SELECTED_PASSED not in log_text:
        return False, "selected test suite pass marker is missing"

    matches = list(SUMMARY_RE.finditer(log_text))
    if not matches:
        return False, "test summary is missing"
    executed, failures, unexpected = (int(value) for value in matches[-1].groups())
    if executed <= 0:
        return False, "no tests were executed"
    if failures != 0 or unexpected != 0:
        return False, f"test failures detected: failures={failures}, unexpected={unexpected}"
    return True, f"{executed} tests passed with zero failures"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("log_path")
    parser.add_argument("xcresult_path")
    parser.add_argument("--xcodebuild-exit", type=int, required=True)
    args = parser.parse_args()

    log_path = Path(args.log_path)
    xcresult_path = Path(args.xcresult_path)
    if not log_path.is_file():
        print("iOS test verification failed: test log is missing")
        return 1

    ok, reason = verify(log_path.read_text(encoding="utf-8", errors="replace"), xcresult_path.exists())
    if not ok:
        print(f"iOS test verification failed: {reason}; xcodebuild_exit={args.xcodebuild_exit}")
        return 1

    if args.xcodebuild_exit == 0:
        print(f"iOS tests verified: {reason}")
    else:
        print(f"iOS tests verified despite runner exit {args.xcodebuild_exit}: {reason}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
