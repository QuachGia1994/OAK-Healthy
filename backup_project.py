import argparse
import fnmatch
import os
import subprocess
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


@dataclass(frozen=True)
class BackupConfig:
    root: Path
    output_zip: Path
    exclude_dirs: tuple[str, ...]
    exclude_globs: tuple[str, ...]


def _normalize_rel(path: Path) -> str:
    return path.as_posix().lstrip("./")


def _is_excluded_dir(rel_dir: str, exclude_dirs: tuple[str, ...]) -> bool:
    rel_dir = rel_dir.strip("/")
    return any(rel_dir == ex or rel_dir.startswith(f"{ex}/") for ex in exclude_dirs)


def _matches_any_glob(rel_path: str, globs: tuple[str, ...]) -> bool:
    return any(fnmatch.fnmatch(rel_path, pattern) for pattern in globs)


def create_backup(config: BackupConfig) -> tuple[int, int]:
    config.output_zip.parent.mkdir(parents=True, exist_ok=True)
    if config.output_zip.exists():
        config.output_zip.unlink()

    try:
        output_zip_rel = _normalize_rel(config.output_zip.relative_to(config.root))
    except ValueError:
        output_zip_rel = ""

    total_files = 0
    zipped_files = 0

    with ZipFile(config.output_zip, "w", compression=ZIP_DEFLATED) as zf:
        for current_root, dirnames, filenames in os.walk(config.root):
            current_root_path = Path(current_root)
            rel_dir = _normalize_rel(current_root_path.relative_to(config.root))

            pruned = []
            for d in list(dirnames):
                rel_subdir = (Path(rel_dir) / d) if rel_dir else Path(d)
                rel_subdir_str = _normalize_rel(rel_subdir)
                if _is_excluded_dir(rel_subdir_str, config.exclude_dirs):
                    continue
                if _matches_any_glob(rel_subdir_str, config.exclude_globs) or _matches_any_glob(f"{rel_subdir_str}/", config.exclude_globs):
                    continue
                pruned.append(d)
            dirnames[:] = pruned

            for filename in filenames:
                total_files += 1
                abs_path = current_root_path / filename
                rel_path = _normalize_rel(abs_path.relative_to(config.root))

                if output_zip_rel and rel_path == output_zip_rel:
                    continue
                if _matches_any_glob(rel_path, config.exclude_globs):
                    continue

                zf.write(abs_path, arcname=rel_path)
                zipped_files += 1

    return zipped_files, total_files


def _run_git(cwd: Path, args: list[str]) -> tuple[int, str]:
    proc = subprocess.run(
        ["git", *args],
        cwd=str(cwd),
        text=True,
        capture_output=True,
    )
    output = (proc.stdout or "") + (proc.stderr or "")
    return proc.returncode, output.strip()


def git_commit_and_push(root: Path, message: str, remote: str, branch: str) -> None:
    if not (root / ".git").exists():
        print("Skip Git sync: .git folder not found.")
        return

    code, out = _run_git(root, ["status", "--porcelain"])
    if code != 0:
        print("Git status failed.")
        print(out)
        return

    if not out.strip():
        print("Git sync: no changes to commit.")
        return

    for step in (["add", "-A"], ["commit", "-m", message], ["push", remote, branch]):
        code, out = _run_git(root, step)
        if code != 0:
            print(f"Git command failed: git {' '.join(step)}")
            print(out)
            return
    print(f"Git sync done: pushed to {remote}/{branch}")


def main() -> int:
    parser = argparse.ArgumentParser(prog="backup_project")
    parser.add_argument("--root", default=".", help="Project root folder to backup (default: current directory).")
    parser.add_argument("--out-dir", default="backups", help="Folder to write the zip into (default: backups).")
    parser.add_argument(
        "--name",
        default="",
        help="Optional output file name. If empty, uses <folder>_backup_<timestamp>.zip",
    )
    parser.add_argument("--exclude-dir", action="append", default=[], help="Additional excluded directories (relative to root). Can be repeated.")
    parser.add_argument("--exclude-glob", action="append", default=[], help="Additional excluded glob patterns (posix-style). Can be repeated.")
    parser.add_argument("--git-sync", action="store_true", help="After creating backup, auto git add/commit/push.")
    parser.add_argument("--git-remote", default="origin", help="Git remote name (default: origin).")
    parser.add_argument("--git-branch", default="main", help="Git branch name (default: main).")
    parser.add_argument("--git-message", default="", help="Git commit message. If empty, uses a timestamped message.")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    if not root.exists() or not root.is_dir():
        raise SystemExit(f"Invalid --root: {root}")

    out_dir = (root / args.out_dir).resolve()
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    default_name = f"{root.name}_backup_{stamp}.zip"
    output_zip = out_dir / (args.name.strip() or default_name)

    exclude_dirs = (
        ".git",
        ".idea",
        ".vscode",
        "__pycache__",
        ".pytest_cache",
        "node_modules",
        "backups",
        "Android/.gradle",
        "Android/.kotlin",
        "Android/build",
        "Android/app/build",
        "Android/.idea",
        "iOS/build",
        "iOS/DerivedData",
    ) + tuple(args.exclude_dir)
    exclude_globs = (
        "*.zip",
        "*.pyc",
        "*.log",
        ".DS_Store",
        "Android/local.properties",
        "Android/**/build/**",
        "Android/**/.gradle/**",
        "Android/**/.kotlin/**",
        "Android/**/.idea/**",
        "iOS/Secrets.xcconfig",
        "iOS/**/Secrets.xcconfig",
        "iOS/**/DerivedData/**",
        "iOS/**/build/**",
        "**/node_modules/**",
        "**/__pycache__/**",
        "**/.pytest_cache/**",
    ) + tuple(args.exclude_glob)

    zipped, total = create_backup(
        BackupConfig(
            root=root,
            output_zip=output_zip,
            exclude_dirs=exclude_dirs,
            exclude_globs=exclude_globs,
        )
    )

    print(f"Backup created: {output_zip}")
    print(f"Files zipped: {zipped}/{total}")
    
    if args.git_sync:
        msg = args.git_message.strip() or f"chore(backup): {stamp}"
        git_commit_and_push(root=root, message=msg, remote=args.git_remote, branch=args.git_branch)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
