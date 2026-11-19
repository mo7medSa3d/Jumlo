#!/usr/bin/env python3
"""Pin a reviewed official yt-dlp release in the APK (build/development time only).

Usage: python3 scripts/update-media-extractor.py 2026.10.21
The native Python/QuickJS/FFmpeg libraries remain pinned separately in Gradle.
"""
import argparse
import ast
import hashlib
import io
import os
from pathlib import Path
import re
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/media/assets/media'


def pin(version):
    if not re.fullmatch(r'20\d{2}\.\d{2}\.\d{2}(?:\.\d+)?', version):
        raise ValueError('Supply an explicit official release version, for example 2026.10.21')
    base = f'https://github.com/yt-dlp/yt-dlp/releases/download/{version}/'
    with urllib.request.urlopen(base + 'SHA2-256SUMS', timeout=60) as response:
        sums = response.read(256 * 1024).decode('ascii')
    expected = next(
        (parts[0] for line in sums.splitlines() if len(parts := line.split()) >= 2 and parts[-1].lstrip('*') == 'yt-dlp'),
        None,
    )
    if not expected:
        raise ValueError('Official SHA2-256SUMS does not contain a yt-dlp entry')
    with urllib.request.urlopen(base + 'yt-dlp', timeout=60) as response:
        payload = response.read(20 * 1024 * 1024 + 1)
    if len(payload) > 20 * 1024 * 1024:
        raise ValueError('Unexpected extractor archive size')
    actual = hashlib.sha256(payload).hexdigest()
    if actual != expected:
        raise ValueError('Official checksum does not match the downloaded archive')
    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        tree = ast.parse(archive.read('yt_dlp/version.py').decode())
        bundled = next(ast.literal_eval(node.value) for node in tree.body
                       if isinstance(node, ast.Assign) and any(
                           isinstance(target, ast.Name) and target.id == '__version__'
                           for target in node.targets))
        if bundled != version:
            raise ValueError('Archive version differs from the requested release')
        if not any(name.startswith('yt_dlp_ejs/') and name.endswith('.js') for name in archive.namelist()):
            raise ValueError('Release does not bundle the required EJS scripts')
    ASSETS.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix='.yt-dlp.', suffix='.tmp', dir=ASSETS)
    temporary = Path(tmp_name)
    try:
        with os.fdopen(fd, 'wb') as handle:
            handle.write(payload)
        temporary.replace(ASSETS / 'yt-dlp')
    finally:
        temporary.unlink(missing_ok=True)
    (ASSETS / 'extractor.properties').write_text(
        '# Official release; regenerated with scripts/update-media-extractor.py\n'
        f'version={version}\nsha256={actual}\nsource={base}yt-dlp\n', encoding='ascii')
    print(f'Pinned yt-dlp {version}: {len(payload):,} bytes, SHA-256 {actual}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version')
    pin(parser.parse_args().version)
