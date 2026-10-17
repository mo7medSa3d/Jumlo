#!/usr/bin/env python3
"""Read actual Kotlin registry/policy execution. Requires the project's pinned Kotlin CLI/JDK."""
from pathlib import Path
import csv
import io
import json
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CODE = ROOT / "app/src/main/java/com/jumlo/app"

def main():
    with tempfile.TemporaryDirectory(prefix="jumlo-capabilities-") as temporary:
        jar = Path(temporary) / "inventory.jar"
        sources = [CODE / "core/CapabilityPolicy.kt", CODE / "model/Models.kt", CODE / "data/jobs/CapabilityRegistry.kt",
                   ROOT / "scripts/host-tests/CapabilityInventoryMain.kt"]
        subprocess.run(["kotlinc", *map(str, sources), "-jvm-target", "17", "-include-runtime", "-d", str(jar)], check=True)
        raw = subprocess.check_output(["java", "-jar", str(jar)], text=True)
    rows = list(csv.reader(io.StringIO(raw), delimiter="\t"))
    dispatcher = (CODE / "data/jobs/OperationDispatcher.kt").read_text()
    handlers = {}
    for ids, handler in re.findall(r'((?:"[a-z0-9_]+"\s*,?\s*)+)\s*->\s*(\w+)\.execute', dispatcher):
        for tool_id in re.findall(r'"([a-z0-9_]+)"', ids):
            handlers[tool_id] = handler
    kinds = {
        "images": ("Image files", "ImagePixelTest; WorkspaceSettingsTest", "Bitmap buffers <= min(heap/3, 128 MiB); explicit output/geometry limits"),
        "pdf": ("PDF; images for Images to PDF/signature", "PdfStructureCoreTest; ImagePixelTest; WorkspaceSettingsTest", "Bounded bitmap buffers; DPI 36-600 where exposed; validated page ranges; per-tool native limits need device QA"),
        "archives": ("ZIP/files or digest inputs", "CoreTests; ArchiveHandlerTest", "10,000 archive entries; 2 GiB expanded/streamed input; 32 path-depth limit; free-space guard"),
        "downloads": ("1 or <=100 direct URLs", "TransferTests; CoreTests; WorkspaceSettingsTest", "Explicit HTTP consent; resume validation; 2 transfer slots; 2 GiB per-transfer byte cap; free-space guard"),
        "fileExtras": ("Imported files", "CoreTests; PureDomainAlgorithmsTest; WorkspaceSettingsTest", "Copy-based rename/digest; advanced security operations gated"),
        "ocrWeb": ("PDF or HTTPS URL", "No sufficient Android qualification", "Hidden: OCR integrity/WebView lifecycle qualification required"),
        "media": ("Media URL", "MediaPolicyTests; MediaProcessTests; MediaFileTests", "Hidden; optional external-media/licensing/device gate"),
    }
    matrix = []
    for tool_id, title, category, visible, enabled, kind, evidence, reason in rows:
        handler = "navigation" if kind == "NAVIGATION" else handlers.get(tool_id, "none")
        inputs, tests, limits = kinds.get(handler, ("Navigation", "JumloUiTest; AccountHubInteractionTest", "No processing command"))
        matrix.append(dict(id=tool_id,title=title,category=category,visible=visible=="true",enabled=enabled=="true",kind=kind,
                           policyEvidence=evidence,reason=reason or None,handler=handler,inputs=inputs,limits=limits,
                           testSources=tests,deviceProof="NOT EXECUTED",eligibleBuild="standard debug/release" if enabled=="true" else "unavailable in public catalog"))
    count = sum(item["visible"] for item in matrix)
    processing_count = sum(item["visible"] and item["kind"] == "PROCESSING" for item in matrix)
    header = f'''# Feature qualification matrix

Generated from executable `CapabilityRegistry` + `CapabilityPolicy` by `scripts/generate-feature-matrix.py`.
Snapshot date: 2026-10-17. **{len(matrix)} registry definitions; {processing_count} processing tools; {count-processing_count} navigation/viewer definitions; {len(matrix)-count} gated definitions.**

Availability is independent of debuggable mode. It does not certify the Android release.
`policyEvidence` labels a source/host evidence tier; named test sources are coverage locations, not a claim they all ran.
Actual commands, exit codes and native/device distinctions live in `docs/RELEASE.md` and `verification/production-2026-10-17/`.
The Tools screen excludes the three duplicate navigation helpers (`file_02`, `file_trash`, `dl_settings`); their destinations are available through Files/Settings. Its unfiltered count is {count-3} ({processing_count} processors plus the PDF viewer), computed from policy, and changes with search/category filters.
All available entries either lead to real processing or a working navigation destination. Dispatcher and enqueue reject navigation-only and unavailable IDs.
The `pdf_01` viewer is available in Tools and opens a read-only preview workspace; it cannot enqueue a processing command.

| ID / tool | Public UI / build | Inputs / handler | Supported limits | Test sources / policy evidence | Android device proof | Security / licensing gate |
|---|---|---|---|---|---|---|
'''
    lines = [header]
    for row in matrix:
        surface = "Visible PDF viewer" if row["id"] == "pdf_01" else "Navigation destination; omitted from Tools" if row["kind"] == "NAVIGATION" else "Visible tool" if row["visible"] else "Hidden"
        cells = [f'`{row["id"]}` — {row["title"]}', f'{surface}; {row["eligibleBuild"]}',
                 f'{row["inputs"]}; `{row["handler"]}`', row["limits"], f'{row["testSources"]}; {row["policyEvidence"]}',
                 row["deviceProof"], row["reason"] or "Normal release QA still required"]
        lines.append("| " + " | ".join(cell.replace("|", "/") for cell in cells) + " |\n")
    (ROOT / "docs/FEATURE_QUALIFICATION_MATRIX.md").write_text("".join(lines))
    (ROOT / "docs/feature-qualification.json").write_text(json.dumps(matrix,ensure_ascii=False,indent=2)+"\n")
    print(f"Generated {len(matrix)} capabilities: {count} visible, {len(matrix)-count} gated")

if __name__ == "__main__":
    main()
