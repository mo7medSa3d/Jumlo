#!/usr/bin/env python3
"""Inspect real base APK/AAB ZIP contents for extended media and ELF64 alignment.

This is a targeted packaging gate, not a license audit/SBOM or 16KB device test.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile


def elf_load_alignment(data):
    """Return ELF64 PT_LOAD alignment; this does not qualify device behavior."""
    if len(data) < 64 or data[:4] != b'\x7fELF' or data[4] not in (1, 2) or data[5] not in (1, 2):
        raise ValueError('Native library has an invalid ELF header')
    if data[4] == 1:
        return {'elf_class': 32, 'scope': '16KB ELF gate applies to ELF64 libraries'}
    endian = '<' if data[5] == 1 else '>'
    offset = struct.unpack_from(endian + 'Q', data, 32)[0]
    size, count = struct.unpack_from(endian + 'HH', data, 54)
    if size < 56 or offset + size * count > len(data):
        raise ValueError('Native library has an invalid ELF64 program-header table')
    alignments = []
    for index in range(count):
        header = struct.unpack_from(endian + 'IIQQQQQQ', data, offset + index * size)
        if header[0] == 1:  # PT_LOAD
            alignments.append(header[7])
    return {'elf_class': 64, 'pt_load_alignments': alignments,
            'at_least_16kb': bool(alignments) and all(value >= 16384 for value in alignments)}

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('artifacts', type=Path, nargs='+')
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
forbidden_paths = ('youtubedl', 'yt-dlp', 'yt_dlp', 'ffmpeg', 'ffprobe', 'libpython', 'python3.')
forbidden_dex = (b'Lcom/yausername/youtubedl_android/', b'Lcom/yausername/ffmpeg/', b'Lcom/arthenica/ffmpegkit/')
results = []
for artifact in args.artifacts:
    row = {'path': str(artifact), 'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
           'size_bytes': artifact.stat().st_size, 'forbidden_markers': [],
           'elf64_alignment_failures': [], 'native_entries': [], 'dex_entries': []}
    with zipfile.ZipFile(artifact) as archive:
        bad = archive.testzip()
        if bad is not None:
            raise ValueError(f'Invalid ZIP CRC in {artifact}: {bad}')
        for info in archive.infolist():
            lower = info.filename.lower()
            if any(marker in lower for marker in forbidden_paths):
                row['forbidden_markers'].append({'entry': info.filename, 'kind': 'extended_media_path'})
            if lower.endswith('.so'):
                alignment = elf_load_alignment(archive.read(info))
                row['native_entries'].append({'entry': info.filename, 'bytes': info.file_size, **alignment})
                if alignment['elf_class'] == 64 and not alignment['at_least_16kb']:
                    row['elf64_alignment_failures'].append(info.filename)
            if lower.endswith('.dex'):
                row['dex_entries'].append({'entry': info.filename, 'bytes': info.file_size})
                data = archive.read(info)
                for marker in forbidden_dex:
                    if marker in data:
                        row['forbidden_markers'].append({'entry': info.filename, 'kind': 'extended_media_dex_package', 'marker': marker.decode()})
    row['passed'] = not row['forbidden_markers'] and not row['elf64_alignment_failures']
    results.append(row)
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps({'scope': 'Known extended-media filenames/DEX markers and ELF64 PT_LOAD alignment >=16KB. Pair with dependency graph and APK zipalign check. Not a full license audit or 16KB runtime/RELRO/device qualification.', 'artifacts': results}, indent=2) + '\n')
for row in results:
    print(('PASS' if row['passed'] else 'FAIL'), row['path'], row['sha256'], 'native_entries=' + str(len(row['native_entries'])))
sys.exit(0 if all(row['passed'] for row in results) else 1)
