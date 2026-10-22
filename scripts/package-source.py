#!/usr/bin/env python3
"""Create and verify a deterministic Jumlo source ZIP without generated/private files.

Build exclusions are repository-relative prefixes, deliberately NOT a generic
"build" directory rule: verification/production-2026-10-17/build logs are retained.
All included symlinks/special files and unsafe portable ZIP member names fail closed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import stat
import sys
import tempfile
import zipfile

MANIFEST_NAME = "CONTENTS_SHA256.json"
FIXED_ZIP_TIME = (1980, 1, 1, 0, 0, 0)
EXCLUDED_PREFIXES = (
    ".git", ".gradle", ".kotlin", "build", "app/build", "benchmark/build",
)
CACHE_COMPONENTS = frozenset({
    "__pycache__", ".cache", ".pytest_cache", ".mypy_cache", ".ruff_cache",
    ".cxx", ".externalNativeBuild", "node_modules",
})
LOCAL_NAMES = frozenset({
    "local.properties", "keystore.properties", ".DS_Store", "Thumbs.db",
    "desktop.ini", ".env", ".env.local",
})
PRIVATE_SUFFIXES = frozenset({".jks", ".keystore", ".p12", ".pfx", ".key", ".pk8"})
CACHE_SUFFIXES = frozenset({".pyc", ".pyo"})
CHUNK_BYTES = 1024 * 1024


def excluded(name: str) -> bool:
    parts = PurePosixPath(name).parts
    return (
        any(name == prefix or name.startswith(prefix + "/") for prefix in EXCLUDED_PREFIXES)
        or any(part in CACHE_COMPONENTS for part in parts)
        or parts[-1] in LOCAL_NAMES
        or (parts[-1].startswith(".env.") and parts[-1].endswith(".local"))
        or Path(parts[-1]).suffix.lower() in PRIVATE_SUFFIXES | CACHE_SUFFIXES
    )


def safe_member(name: str) -> None:
    parts = name.split("/")
    if (
        not name or name.startswith("/") or "\\" in name or ":" in name
        or any(part in {"", ".", ".."} or part.endswith((".", " ")) for part in parts)
        or any(ord(char) < 32 or ord(char) == 127 for char in name)
    ):
        raise ValueError(f"Unsafe ZIP member path: {name!r}")


def signature(info: os.stat_result) -> tuple[int]:
    return (info.st_size,)


def scan(source: Path) -> dict[str, tuple[Path, os.stat_result]]:
    files: dict[str, tuple[Path, os.stat_result]] = {}

    def visit(directory: Path) -> None:
        for path in sorted(directory.iterdir(), key=lambda item: item.name):
            name = path.relative_to(source).as_posix()
            safe_member(name)
            if excluded(name):
                continue
            info = path.lstat()
            if stat.S_ISLNK(info.st_mode):
                raise ValueError(f"Unexpected symlink in source: {name}")
            if stat.S_ISDIR(info.st_mode):
                visit(path)
            elif stat.S_ISREG(info.st_mode):
                if name == MANIFEST_NAME:
                    # A supplied source ZIP may already contain the manifest of
                    # a previous handoff. Regenerate it from THIS output's files.
                    continue
                files[name] = path, info
            else:
                raise ValueError(f"Unexpected special file in source: {name}")

    visit(source)
    return dict(sorted(files.items()))


def read_file(path: Path, expected: os.stat_result, consume) -> tuple[str, int]:
    """Do not follow leaf symlinks; refuse a source file changed during packaging."""
    with os.fdopen(os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)), "rb") as stream:
        if signature(os.fstat(stream.fileno())) != signature(expected):
            raise ValueError(f"Source changed before packaging: {path.name}")
        digest = hashlib.sha256()
        size = 0
        while chunk := stream.read(CHUNK_BYTES):
            digest.update(chunk)
            size += len(chunk)
            consume(chunk)
        if signature(os.fstat(stream.fileno())) != signature(expected) or size != expected.st_size:
            raise ValueError(f"Source changed during packaging: {path.name}")
    if signature(path.lstat()) != signature(expected) or path.is_symlink():
        raise ValueError(f"Source changed after packaging: {path.name}")
    return digest.hexdigest(), size


def zip_info(name: str, mode: int) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, FIXED_ZIP_TIME)
    info.create_system = 3
    info.external_attr = (stat.S_IFREG | mode) << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    return info


def verify(archive_path: Path, expected_manifest: dict) -> int:
    with zipfile.ZipFile(archive_path, "r") as archive:
        names = archive.namelist()
        expected_names = sorted([MANIFEST_NAME, *expected_manifest["files"]])
        if names != expected_names or len(names) != len(set(names)):
            raise ValueError("Archive entry names/count/order do not match manifest")
        bad_member = archive.testzip()
        if bad_member is not None:
            raise ValueError(f"Archive CRC verification failed: {bad_member}")
        actual_manifest = json.loads(archive.read(MANIFEST_NAME))
        if actual_manifest != expected_manifest:
            raise ValueError("Archive manifest changed")
        for name, record in actual_manifest["files"].items():
            safe_member(name)
            info = archive.getinfo(name)
            if info.file_size != record["bytes"] or (info.external_attr >> 16 & 0o777) != record["mode"]:
                raise ValueError(f"Archive size/mode mismatch: {name}")
            digest = hashlib.sha256()
            with archive.open(name) as stream:
                while chunk := stream.read(CHUNK_BYTES):
                    digest.update(chunk)
            if digest.hexdigest() != record["sha256"]:
                raise ValueError(f"Archive SHA-256 mismatch: {name}")
        return len(names)


def package(source: Path, output: Path, original_archive: Path | None = None) -> dict:
    if not output.is_absolute():
        raise ValueError("--output must be an absolute path")
    if source.is_symlink():
        raise ValueError("Unexpected symlink source root")
    source = source.resolve(strict=True)
    if not source.is_dir():
        raise ValueError("--source must be a directory")
    # A self-including destination and symlinked output paths are never accepted.
    for component in (output, *output.parents):
        if component.is_symlink():
            raise ValueError(f"Unexpected symlink output path: {component}")
    output = output.resolve()
    if output.is_relative_to(source):
        raise ValueError("--output must be outside the source tree")
    if output.exists() and not output.is_file():
        raise ValueError("--output must name a regular file")
    files = scan(source)
    manifest = {"formatVersion": 1, "files": {}}
    if original_archive is not None:
        with original_archive.open("rb") as stream:
            manifest["originalUserZipSha256"] = hashlib.file_digest(stream, "sha256").hexdigest()
    for name, (path, info) in files.items():
        digest, size = read_file(path, info, lambda _: None)
        manifest["files"][name] = {"sha256": digest, "bytes": size, "mode": stat.S_IMODE(info.st_mode) & 0o777}
    manifest_bytes = (json.dumps(manifest, sort_keys=True, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{output.name}.", suffix=".tmp", dir=output.parent)
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9, allowZip64=True) as archive:
            for name in sorted([MANIFEST_NAME, *files]):
                if name == MANIFEST_NAME:
                    archive.writestr(zip_info(name, 0o644), manifest_bytes, compresslevel=9)
                    continue
                path, info = files[name]
                entry = zip_info(name, manifest["files"][name]["mode"])
                with archive.open(entry, "w", force_zip64=True) as sink:
                    digest, size = read_file(path, info, sink.write)
                if digest != manifest["files"][name]["sha256"] or size != manifest["files"][name]["bytes"]:
                    raise ValueError(f"Source content changed between hash and ZIP write: {name}")
        entry_count = verify(temporary, manifest)
        with temporary.open("rb") as stream:
            pass
            archive_hash = hashlib.file_digest(stream, "sha256").hexdigest()
        archive_size = temporary.stat().st_size
        os.replace(temporary, output)
        return {"archivepath": str(output), "size": archive_size, "sha256": archive_hash, "entrycount": entry_count}
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path(__file__).resolve().parents[1], help="Source root; defaults to repository containing this script")
    parser.add_argument("--output", type=Path, required=True, help="Absolute output ZIP path outside the source tree")
    parser.add_argument("--original-archive", type=Path, help="Optional original attachment; record its actual SHA-256 for provenance")
    args = parser.parse_args()
    try:
        result = package(args.source, args.output, args.original_archive)
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as error:
        print(json.dumps({"error": str(error)}), file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
