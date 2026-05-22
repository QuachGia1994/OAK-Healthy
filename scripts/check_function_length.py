import argparse
import pathlib
import re
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

    for idx, line in enumerate(lines, start=1):
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
            if idx - start_line > 25:
                pending = None

        brace_depth = next_depth

    return issues


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--max", type=int, default=30)
    parser.add_argument("--fail", action="store_true")
    parser.add_argument("--paths", nargs="*", default=["iOS", "Android"])
    args = parser.parse_args()

    files = iter_source_files(args.paths)
    all_issues: list[tuple[str, int, str, int]] = []
    for file in files:
        all_issues.extend(scan_file(file, args.max))

    if all_issues:
        print(f"Found {len(all_issues)} functions over {args.max} lines:")
        for file, start_line, name, length in all_issues:
            print(f"{file}:{start_line}: {name} ({length} lines)")
        return 1 if args.fail else 0

    print(f"OK: no functions over {args.max} lines.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

