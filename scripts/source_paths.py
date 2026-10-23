"""Resolve app and platform-independent Kotlin source files for host contracts."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
def source_path(relative):
    relative = str(relative)
    paths = [ROOT / module / 'src/main/kotlin/com/jumlo/app' / relative for module in ('core-common','downloader-core','document-core','storage-core')] if relative.startswith('core/') else [ROOT / 'app/src/main/java/com/jumlo/app' / relative]
    matches = [p for p in paths if p.is_file()]
    if len(matches) != 1: raise FileNotFoundError(f'Expected one source for {relative}, found {matches}')
    return matches[0]
