import argparse
import fnmatch
import json
import os
import pathlib
import re
import subprocess
import sys


def iter_source_files(paths: list[str]) -> list[pathlib.Path]:
    roots = [pathlib.Path(p) for p in paths]
    files: list[pathlib.Path] = []
    for root in roots:
        if root.is_file():
            files.append(root)
            continue
        if not root.exists():
            continue
        files.extend(root.rglob("*.swift"))
        files.extend(root.rglob("*.kt"))
    return sorted(set(files))


def detect_language(path: pathlib.Path) -> str:
    suffix = path.suffix.lower()
    if suffix == ".swift":
        return "swift"
    if suffix == ".kt":
        return "kotlin"
    return "unknown"


def is_comment_line(line: str) -> bool:
    trimmed = line.lstrip()
    return trimmed.startswith("//")


def find_function_name(language: str, line: str) -> str | None:
    if language == "swift":
        m = re.search(r"\bfunc\s+([A-Za-z_][A-Za-z0-9_]*)\b", line)
        return m.group(1) if m else None
    if language == "kotlin":
        m = re.search(r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\b", line)
        return m.group(1) if m else None
    return None


def is_expression_bodied_kotlin(line: str) -> bool:
    stripped = line.strip()
    return stripped.startswith("fun ") and "{" not in stripped and "=" in stripped


def strip_block_comments(line: str, in_block: bool) -> tuple[str, bool]:
    out = line
    while True:
        if in_block:
            end = out.find("*/")
            if end < 0:
                return "", True
            out = out[end + 2 :]
            in_block = False
            continue
        start = out.find("/*")
        if start < 0:
            return out, False
        end = out.find("*/", start + 2)
        if end < 0:
            out = out[:start]
            return out, True
        out = out[:start] + out[end + 2 :]


def scan_file(path: pathlib.Path, max_lines: int) -> list[tuple[str, int, str, int]]:
    language = detect_language(path)
    if language == "unknown":
        return []
    try:
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return []

    issues: list[tuple[str, int, str, int]] = []
    brace_depth = 0
    pending: tuple[int, int, str] | None = None
    active: tuple[int, int, str] | None = None
    in_block_comment = False

    for idx, line in enumerate(lines, start=1):
        line, in_block_comment = strip_block_comments(line, in_block_comment)
        if not line.strip():
            continue
        if is_comment_line(line):
            continue

        if active is None and pending is None:
            name = find_function_name(language, line)
            if name:
                if language == "kotlin" and is_expression_bodied_kotlin(line):
                    name = None
                if name:
                    pending = (idx, brace_depth, name)

        open_count = line.count("{")
        close_count = line.count("}")
        next_depth = brace_depth + open_count - close_count

        if pending and active is None:
            start_line, start_depth, name = pending
            if open_count > 0 and next_depth > start_depth:
                active = (start_line, start_depth, name)

        if active:
            start_line, start_depth, name = active
            if idx > start_line and next_depth == start_depth:
                length = idx - start_line + 1
                if length > max_lines:
                    issues.append((str(path).replace("\\", "/"), start_line, name, length))
                active = None
                pending = None

        if pending and active is None:
            start_line, _start_depth, _name = pending
            if next_depth < _start_depth:
                pending = None
                brace_depth = next_depth
                continue
            if idx - start_line > 25:
                pending = None

        brace_depth = next_depth

    return issues


def run_git(args: list[str]) -> str | None:
    try:
        out = subprocess.check_output(["git", *args], stderr=subprocess.DEVNULL)
        return out.decode("utf-8", errors="replace").strip()
    except Exception:
        return None


def read_github_event() -> dict | None:
    event_path = os.environ.get("GITHUB_EVENT_PATH", "").strip()
    if not event_path:
        return None
    try:
        return json.loads(pathlib.Path(event_path).read_text(encoding="utf-8", errors="replace"))
    except Exception:
        return None


def is_sha(value: str | None) -> bool:
    if not value:
        return False
    v = value.strip().lower()
    if len(v) != 40:
        return False
    if v == "0" * 40:
        return False
    return all(c in "0123456789abcdef" for c in v)


def ensure_commit_available(sha: str) -> None:
    if not is_sha(sha):
        return
    ok = run_git(["cat-file", "-e", f"{sha}^{{commit}}"])
    if ok is not None:
        return
    run_git(["fetch", "--no-tags", "--depth=1", "origin", sha])


def detect_base_and_head() -> tuple[str | None, str | None]:
    event_name = os.environ.get("GITHUB_EVENT_NAME", "").strip()
    event = read_github_event()

    if event_name in ("pull_request", "pull_request_target") and isinstance(event, dict):
        pr = event.get("pull_request") if isinstance(event, dict) else None
        base = pr.get("base", {}).get("sha") if isinstance(pr, dict) else None
        head = pr.get("head", {}).get("sha") if isinstance(pr, dict) else None
        return base if is_sha(base) else None, head if is_sha(head) else None

    if event_name == "push" and isinstance(event, dict):
        base = event.get("before")
        head = event.get("after") or os.environ.get("GITHUB_SHA", "").strip()
        return base if is_sha(base) else None, head if is_sha(head) else None

    return None, None


def detect_changed_files(paths: list[str]) -> list[pathlib.Path]:
    root_paths = [pathlib.Path(p).resolve() for p in paths]
    base_ref = os.environ.get("GITHUB_BASE_REF", "").strip()

    base, head = detect_base_and_head()
    if is_sha(base):
        ensure_commit_available(base)
    if is_sha(head):
        ensure_commit_available(head)

    if head is None:
        head = "HEAD"

    if base is not None:
        changed = run_git(["diff", "--name-only", base, head])
        if not changed:
            return []
        return _filter_changed_to_sources(changed, root_paths)

    base = None
    if base_ref:
        base = run_git(["merge-base", "HEAD", f"origin/{base_ref}"])
    if base is None:
        base = run_git(["rev-parse", "HEAD~1"])

    if base is None:
        return []

    if is_sha(base):
        ensure_commit_available(base)
    changed = run_git(["diff", "--name-only", base, head])
    if not changed:
        return []

    return _filter_changed_to_sources(changed, root_paths)


def _filter_changed_to_sources(changed: str, root_paths: list[pathlib.Path]) -> list[pathlib.Path]:
    result: list[pathlib.Path] = []
    for raw in changed.splitlines():
        p = pathlib.Path(raw)
        if p.suffix.lower() not in (".swift", ".kt"):
            continue
        resolved = p.resolve()
        if root_paths and not any(resolved.is_relative_to(rp) for rp in root_paths):
            continue
        result.append(resolved)
    return sorted(set(result))


def apply_path_filters(
    files: list[pathlib.Path],
    includes: list[str],
    excludes: list[str],
) -> list[pathlib.Path]:
    base = pathlib.Path.cwd().resolve()
    include_patterns = [p.strip() for p in includes if p.strip()]
    exclude_patterns = [p.strip() for p in excludes if p.strip()]

    def rel_posix(p: pathlib.Path) -> str:
        try:
            return p.resolve().relative_to(base).as_posix()
        except Exception:
            return p.as_posix()

    def matches_any(path_value: str, patterns: list[str]) -> bool:
        return any(fnmatch.fnmatch(path_value, pat) for pat in patterns)

    out: list[pathlib.Path] = []
    for f in files:
        rp = rel_posix(f)
        if include_patterns and not matches_any(rp, include_patterns):
            continue
        if exclude_patterns and matches_any(rp, exclude_patterns):
            continue
        out.append(f)
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--max", type=int, default=30)
    parser.add_argument("--fail", action="store_true")
    parser.add_argument("--paths", nargs="*", default=["iOS", "Android"])
    parser.add_argument("--changed-only", action="store_true")
    parser.add_argument("--limit", type=int, default=25)
    parser.add_argument("--include", action="append", default=[])
    parser.add_argument("--exclude", action="append", default=[])
    args = parser.parse_args()

    if args.changed_only:
        files = detect_changed_files(args.paths)
        if not files:
            print("OK: no changed source files.")
            return 0
    else:
        files = iter_source_files(args.paths)
    files = apply_path_filters(files, args.include, args.exclude)
    all_issues: list[tuple[str, int, str, int]] = []
    for file in files:
        all_issues.extend(scan_file(file, args.max))

    if all_issues:
        print(f"Found {len(all_issues)} functions over {args.max} lines:")
        shown = all_issues[: max(0, args.limit)]
        for file, start_line, name, length in shown:
            print(f"{file}:{start_line}: {name} ({length} lines)")
        if len(all_issues) > len(shown):
            print(f"... and {len(all_issues) - len(shown)} more")
        return 1 if args.fail else 0

    print(f"OK: no functions over {args.max} lines.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
