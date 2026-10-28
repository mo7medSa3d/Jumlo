#!/usr/bin/env python3
"""Reproducible read-only source inventory for Jumlo's Phase 0 release audit.

Findings require manual review. Regex source matches are NOT evidence that a UI
control is dead, that a color has invalid contrast or that device tests passed.
The scan covers app and Android-free production source, not generated build files.
"""
from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import asdict, dataclass
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOTS = (
    "app/src/main/java",
    "app/src/media/java",
    "app/src/base/java",
    "core-common/src/main",
    "downloader-core/src/main",
    "document-core/src/main",
    "storage-core/src/main",
)

PATTERNS = {
    "todo_marker": re.compile(
        r"\b(?:TODO|FIXME|HACK|XXX)\b|NotImplementedError|TODO\s*\(|"
        r"UnsupportedOperationException\s*\(",
    ),
    "empty_callback": re.compile(
        r"\bon(?:Click|CheckedChange|ValueChange|DismissRequest|LongClick)"
        r"\s*=\s*\{\s*\}",
    ),
    "hardcoded_color": re.compile(r"\bColor\s*\(\s*0x[0-9a-fA-F]{6,8}\s*\)"),
    "android_import_in_core": re.compile(r"(?m)^\\s*import\\s+(?:android|androidx)\\."),
    "hardcoded_ui_text": re.compile(
        r"\b(?:Text|BasicText)\s*\(\s*\"[^\"]{1,120}\"",
    ),
}


@dataclass(frozen=True)
class Finding:
    kind: str
    path: str
    line: int
    snippet: str


def scan_source(path: str, content: str) -> list[Finding]:
    """Return all candidate source patterns with exact 1-based line references."""
    if not path.endswith((".kt", ".java")):
        return []
    lines = content.splitlines()
    found: list[Finding] = []
    for kind, pattern in PATTERNS.items():
        if kind == "hardcoded_color" and "/ui/theme/" in f"/{path}":
            continue  # Literal token *definitions* are centralized, not UI bypasses.
        if kind == "android_import_in_core" and not path.startswith((
            "core-common/", "downloader-core/", "document-core/", "storage-core/"
        )):
            continue  # Android belongs in app adapters, never these pure JVM modules.
        for match in pattern.finditer(content):
            number = content.count("\n", 0, match.start()) + 1
            snippet = lines[number - 1].strip()[:180] if number <= len(lines) else ""
            found.append(Finding(kind, path, number, snippet))
    return sorted(found, key=lambda f: (f.path, f.line, f.kind))


def source_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for relative in SOURCE_ROOTS:
        location = root / relative
        if location.is_dir():
            files.extend(p for p in location.rglob("*") if p.is_file() and p.suffix in {".kt", ".java"})
    return sorted(set(files))


def commit_sha(root: Path) -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=root, text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "UNKNOWN"


def inventory(root: Path) -> dict:
    files = source_files(root)
    findings: list[Finding] = []
    for file in files:
        relative = file.relative_to(root).as_posix()
        findings.extend(scan_source(relative, file.read_text(encoding="utf-8")))
    findings.sort(key=lambda f: (f.path, f.line, f.kind))
    return {
        "commit": commit_sha(root),
        "source_files_scanned": len(files),
        "total_candidates": len(findings),
        "by_kind": dict(sorted(Counter(f.kind for f in findings).items())),
        "findings": [asdict(x) for x in findings],
        "caveat": (
            "Static candidates, not confirmed bugs. Manual interaction/device audit "
            "is still required; passing this scan does not prove feature correctness."
        ),
    }


def write_report(report: dict, json_path: Path, markdown_path: Path) -> None:
    json_path.parent.mkdir(parents=True, exist_ok=True)
    markdown_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    lines = [
        "# Jumlo static source candidate inventory",
        "",
        f"- Commit: \\x60{report['commit']}\\x60".replace("\\x60", "`"),
        f"- Files scanned: {report['source_files_scanned']}",
        f"- Source candidates: {report['total_candidates']}",
        "- Classification: **REVIEW REQUIRED**, never a runtime PASS or a proven defect",
        "",
        "| Category | Count |",
        "|---|---:|",
    ]
    lines += [f"| {name} | {count} |" for name, count in report["by_kind"].items()]
    lines += [
        "",
        "| Category | File:line | Source excerpt (candidate) |",
        "|---|---|---|",
    ]
    for row in report["findings"]:
        excerpt = row["snippet"].replace("|", "\\|").replace("`", "'")
        lines.append(f"| {row['kind']} | \\x60{row['path']}:{row['line']}\\x60 | \\x60{excerpt}\\x60 |".replace("\\x60", "`"))
    lines += ["", report["caveat"], ""]
    markdown_path.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Inspect production Kotlin/Java source")
    parser.add_argument("--output", default="build/reports/source-audit.json")
    parser.add_argument("--markdown", default="build/reports/source-audit.md")
    arguments = parser.parse_args()
    report = inventory(ROOT)
    write_report(report, ROOT / arguments.output, ROOT / arguments.markdown)
    print(
        "Source inventory: {source_files_scanned} files, {total_candidates} candidates; "
        "manual review required".format(**report)
    )
    # Deliberately print the exact candidates to the job log as well as the artifact.
    # These are source-review leads, not verified failures or secrets.
    for finding in report["findings"]:
        print(f"SOURCE_CANDIDATE | {finding['kind']} | {finding['path']}:{finding['line']} | {finding['snippet']}")


if __name__ == "__main__":
    main()
