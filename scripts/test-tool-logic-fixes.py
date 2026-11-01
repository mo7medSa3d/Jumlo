#!/usr/bin/env python3
"""Gate for the 2026-11-01 tool-logic review fixes.

Checks, by source inspection (the Android handlers cannot compile in this VM):
1. Neither download handler discards the user's submitted network policy.
2. Annotate and image-overlay fail on an out-of-range page instead of
   silently stamping the last page.
3. parsePageRangeSyntax KDoc no longer claims sorted output.
4. The pdf_annotate dead branch is gone.
5. formatBytes covers TB/PB instead of stopping at GB.

Usage: python3 scripts/test-tool-logic-fixes.py [repo-root]
Exit 0 only when every check passes.
"""
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/jumlo/app"

CHECKS = []


def check(name, ok, detail=""):
    CHECKS.append((name, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'}: {name}" + (f" -- {detail}" if detail and not ok else ""))


def read(rel):
    return (APP / rel).read_text(encoding="utf-8")


# 1. Download network policy must be honored, not forced off.
for rel, label in [
    ("data/media/MediaOperationHandler.kt", "dl_media"),
    ("data/processing/DownloadOperationHandler.kt", "dl_02/dl_03"),
]:
    src = read(rel)
    check(
        f"{label}: no forced wifiOnly=false/unmeteredOnly=false/cleartextConsent=true override",
        "wifiOnly=false, unmeteredOnly=false, cleartextConsent=true" not in src,
        "override still present",
    )
    check(
        f"{label}: submitted DownloadSettings are decoded and used directly",
        "Json.decodeFromString<DownloadSettings>(request.settingsJson)" in src,
    )

# The enforcement code the override had neutered must still exist.
media = read("data/media/MediaOperationHandler.kt")
check("dl_media: checkNetwork still enforces wifiOnly", "if(settings.wifiOnly" in media)
check("dl_media: checkNetwork still enforces unmeteredOnly", "if(settings.unmeteredOnly" in media)
dl = read("data/processing/DownloadOperationHandler.kt")
check("dl direct: networkPolicy still enforces wifiOnly", "if(settings.wifiOnly" in dl)
check("dl direct: networkPolicy still enforces unmeteredOnly", "if(settings.unmeteredOnly" in dl)

# 2. Annotate / overlayImage must fail on out-of-range pages.
pdf = read("data/processing/PdfOperationHandler.kt")
check(
    "annotate: no silent page clamp",
    '.coerceIn(1,doc.numberOfPages' not in pdf,
)
check(
    "annotate: out-of-range page fails with INVALID_PAGE_INDEX",
    'require(page in 1..doc.numberOfPages) { "INVALID_PAGE_INDEX" }' in pdf,
)
check(
    "overlayImage: no silent target clamp",
    ".coerceIn(0,(doc.numberOfPages-1)" not in pdf,
)
check(
    "overlayImage: out-of-range target fails with INVALID_PAGE_INDEX",
    'require(target in 0 until doc.numberOfPages) { "INVALID_PAGE_INDEX" }' in pdf,
)
check(
    "no other numberOfPages/pageCount coerceIn clamps remain in PdfOperationHandler",
    "coerceIn(1,doc.numberOfPages" not in pdf and "coerceIn(0,(doc.numberOfPages" not in pdf,
)

# 3. KDoc must describe insertion order, not sorted output.
pure = read("data/engine/PureDomainAlgorithms.kt")
check(
    "parsePageRangeSyntax KDoc no longer claims sorted output",
    "returns a sorted list" not in pure and "NOT sorted" in pure,
)

# 4. Dead branch removed.
draft = read("model/WorkspaceDraft.kt")
check(
    "pdf_annotate dead branch removed",
    "if(kind.carriesText) text1 else text1" not in draft and "val body=text1" in draft,
)

# 5. formatBytes covers TB and PB.
check("formatBytes has a TB tier", '%.2f TB' in pure)
check("formatBytes has a PB tier", '%.2f PB' in pure)
check("formatBytes keeps B/KB/MB/GB tiers", all(
    t in pure for t in ['"$bytes B"', '%.0f KB', '%.1f MB', '%.2f GB']
))

failed = [c for c in CHECKS if not c[1]]
print(f"\n{len(CHECKS) - len(failed)}/{len(CHECKS)} checks passed.")
sys.exit(1 if failed else 0)
