#!/usr/bin/env python3
"""Verify the built APK actually contains the pinned local media engine.

This is a packaging gate. It does not execute Android binaries or certify live
provider support. Inspect every packaged ABI so a missing FFmpeg/JS component
cannot silently turn the normal app into a direct-file-only downloader.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_LIBRARIES = (
    'libpython.so', 'libpython.zip.so', 'libqjs.so',
    'libffmpeg.so', 'libffmpeg.zip.so', 'libffprobe.so',
)
NATIVE_OVERRIDE_ROOT = 'assets/media/native-overrides/'
REPAIRED_LIBRARIES = ('libsharpyuv.so', 'libwebp.so', 'libwebpdecoder.so', 'libwebpdemux.so', 'libwebpmux.so')
ELF_MACHINES = {'arm64-v8a': 183, 'x86_64': 62}


def properties(text):
    return dict(line.split('=', 1) for line in text.splitlines()
                if line.strip() and not line.lstrip().startswith('#') and '=' in line)


def inspect_elf64(data, abi, label='ELF'):
    """Check loadable layout, including RELRO; a p_align-only edit is insufficient."""
    def require(condition, message):
        if not condition:
            raise ValueError(f'{label}: {message}')
    require(len(data) >= 64 and data[:6] == b'\x7fELF\x02\x01', 'invalid 64-bit ELF')
    require(struct.unpack_from('<H', data, 18)[0] == ELF_MACHINES[abi], 'ELF machine differs from ABI')
    offset = struct.unpack_from('<Q', data, 32)[0]
    entry_size, count = struct.unpack_from('<HH', data, 54)
    require(offset >= 64 and 56 <= entry_size <= 128 and 0 < count <= 128 and
            offset + count * entry_size <= len(data), 'invalid program headers')
    headers = [struct.unpack_from('<IIQQQQQQ', data, offset + i * entry_size) for i in range(count)]
    loads = [p for p in headers if p[0] == 1]
    require(bool(loads), 'no LOAD segments')
    for segment in loads:
        _, _, at, address, _, file_size, memory_size, alignment = segment
        require(file_size <= memory_size and at + file_size <= len(data), 'invalid LOAD bounds')
        require(alignment >= 16384 and alignment & (alignment - 1) == 0, 'LOAD alignment below 16 KiB')
        require(at % alignment == address % alignment, 'LOAD offset/address incongruence')
    for relro in (p for p in headers if p[0] == 0x6474e552):
        end = relro[3] + relro[6]
        protected_end = (end + 16383) // 16384 * 16384
        require(not any(p[1] & 2 and max(p[3], end) < min(p[3] + p[6], protected_end) for p in loads),
                'GNU_RELRO overlaps trailing writable data on a 16 KiB page')
    return {'load_alignments': [p[7] for p in loads], 'relro_checked': True}


def inspect(path, expected=None, native_expected=None):
    path = Path(path)
    if expected is None:
        expected = properties((ROOT / 'app/src/media/assets/media/extractor.properties').read_text())
    with zipfile.ZipFile(path) as apk:
        members = {entry.filename: entry for entry in apk.infolist()}
        if len(members) != len(apk.infolist()):
            raise ValueError('Duplicate APK members')
        bad = apk.testzip()
        if bad:
            raise ValueError(f'Invalid APK member CRC: {bad}')
        required_assets = ('yt-dlp', 'extractor.properties', 'bootstrap.py', 'network_guard.py')
        for name in required_assets:
            key = 'assets/media/' + name
            if key not in members or members[key].file_size == 0:
                raise ValueError(f'Missing media asset: {key}')
        packaged = properties(apk.read('assets/media/extractor.properties').decode())
        if any(packaged.get(key) != expected.get(key) for key in ('version', 'sha256')):
            raise ValueError('Packaged extractor metadata differs from source pin')
        payload = apk.read('assets/media/yt-dlp')
        digest = hashlib.sha256(payload).hexdigest()
        if digest != expected['sha256']:
            raise ValueError('Packaged extractor checksum mismatch')
        with zipfile.ZipFile(io.BytesIO(payload)) as extractor:
            version = extractor.read('yt_dlp/version.py').decode()
            if not re.search(r"__version__\s*=\s*['\"]" + re.escape(expected['version']) + r"['\"]", version):
                raise ValueError('Packaged extractor version mismatch')
            if not any(name.startswith('yt_dlp_ejs/') for name in extractor.namelist()):
                raise ValueError('Missing bundled JavaScript challenge solver')
        abis = sorted({name.split('/')[1] for name in members
                       if name.startswith('lib/') and len(name.split('/')) == 3})
        if not abis:
            raise ValueError('No packaged native media runtime')
        repair_abis = {name[len(NATIVE_OVERRIDE_ROOT):].split('/', 1)[0]
                       for name in members if name.startswith(NATIVE_OVERRIDE_ROOT)
                       and name.endswith('.so') and '/' in name[len(NATIVE_OVERRIDE_ROOT):]}
        unused_repair_abis = repair_abis.difference(abis)
        if unused_repair_abis:
            raise ValueError('Native repair assets without a packaged runtime ABI: ' +
                             ', '.join(sorted(unused_repair_abis)))
        for abi in abis:
            for library in REQUIRED_LIBRARIES:
                key = f'lib/{abi}/{library}'
                if key not in members or members[key].file_size == 0:
                    raise ValueError(f'Missing native runtime component: {key}')
        native_count = 0
        overrides = {}
        if any(abi in ELF_MACHINES for abi in abis):
            if native_expected is None:
                native_expected = properties((ROOT / 'app/src/media/assets/media/native-overrides/manifest.properties').read_text())
            native_manifest = properties(apk.read(NATIVE_OVERRIDE_ROOT + 'manifest.properties').decode())
            if native_manifest != native_expected:
                raise ValueError('Packaged native repair metadata differs from source pin')
            if not apk.read(NATIVE_OVERRIDE_ROOT + 'LICENSE.libwebp.txt').strip():
                raise ValueError('Missing native repair license')
            for abi in (item for item in abis if item in ELF_MACHINES):
                runtime_zip = apk.read(f'lib/{abi}/libffmpeg.zip.so')
                if hashlib.sha256(runtime_zip).hexdigest() != native_manifest.get(f'{abi}.ffmpegArchive.sha256'):
                    raise ValueError(f'{abi}: native repair targets a different FFmpeg payload')
                for library in REPAIRED_LIBRARIES:
                    key = NATIVE_OVERRIDE_ROOT + f'{abi}/{library}'
                    if key not in members:
                        raise ValueError(f'Missing native repair: {key}')
                    data = apk.read(key)
                    if hashlib.sha256(data).hexdigest() != native_manifest.get(f'{abi}.{library}.sha256'):
                        raise ValueError(f'Native repair checksum mismatch: {key}')
                    inspect_elf64(data, abi, key)
                    native_count += 1
                overrides[abi] = list(REPAIRED_LIBRARIES)
                for key in members:
                    if not key.startswith(f'lib/{abi}/') or key.endswith('/'):
                        continue
                    data = apk.read(key)
                    if data.startswith(b'\x7fELF'):
                        inspect_elf64(data, abi, key)
                        native_count += 1
                    elif key.endswith(('libpython.zip.so', 'libffmpeg.zip.so')):
                        with zipfile.ZipFile(io.BytesIO(data)) as nested:
                            for entry in nested.infolist():
                                if entry.is_dir():
                                    continue
                                payload = nested.read(entry)
                                if not payload.startswith(b'\x7fELF'):
                                    continue
                                # These original bytes are retained in the upstream archive.
                                # Production initialization installs the verified APK overlays
                                # before launching any child executable; device tests verify it.
                                if key.endswith('libffmpeg.zip.so') and entry.filename in {
                                        'usr/lib/' + name for name in REPAIRED_LIBRARIES}:
                                    continue
                                inspect_elf64(payload, abi, key + '!' + entry.filename)
                                native_count += 1
                    elif key.endswith(('libpython.so', 'libqjs.so', 'libffmpeg.so', 'libffprobe.so')):
                        raise ValueError(f'Invalid native executable: {key}')
        if not any(re.fullmatch(r'classes\d*\.dex', name) for name in members):
            raise ValueError('APK contains no compiled application')
    return {'artifact': str(path), 'bytes': path.stat().st_size, 'abis': abis,
            'extractor_version': expected['version'], 'extractor_sha256': digest,
            'verified_native_elfs': native_count, 'native_overrides': overrides}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path, nargs='+')
    args = parser.parse_args()
    try:
        for path in args.artifacts:
            print(json.dumps(inspect(path), sort_keys=True))
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        print(f'FAIL: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
