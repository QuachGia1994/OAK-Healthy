import argparse
import fnmatch
import os
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
    return rel_dir in exclude_dirs


def _matches_any_glob(rel_path: str, globs: tuple[str, ...]) -> bool:
    return any(fnmatch.fnmatch(rel_path, pattern) for pattern in globs)


def create_backup(config: BackupConfig) -> tuple[int, int]:
    config.output_zip.parent.mkdir(parents=True, exist_ok=True)
    if config.output_zip.exists():
        config.output_zip.unlink()

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
                if _matches_any_glob(rel_subdir_str, config.exclude_globs):
                    continue
                pruned.append(d)
            dirnames[:] = pruned

            for filename in filenames:
                total_files += 1
                abs_path = current_root_path / filename
                rel_path = _normalize_rel(abs_path.relative_to(config.root))

                if rel_path == _normalize_rel(config.output_zip.relative_to(config.root)):
                    continue
                if _matches_any_glob(rel_path, config.exclude_globs):
                    continue

                zf.write(abs_path, arcname=rel_path)
                zipped_files += 1

    return zipped_files, total_files


def main() -> int:
    parser = argparse.ArgumentParser(prog="backup_project")
    parser.add_argument("--root", default=".", help="Project root folder to backup (default: current directory).")
    parser.add_argument("--out-dir", default="backups", help="Folder to write the zip into (default: backups).")
    parser.add_argument(
        "--name",
        default="",
        help="Optional output file name. If empty, uses <folder>_backup_<timestamp>.zip",
    )
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
    )
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
        "iOS/**/DerivedData/**",
        "iOS/**/build/**",
        "**/node_modules/**",
        "**/__pycache__/**",
        "**/.pytest_cache/**",
    )

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
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

