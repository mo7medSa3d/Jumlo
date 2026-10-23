#!/usr/bin/env python3
"""Rebuild the pinned APK-only WebP repair. Ordinary Gradle builds do not run this.

Requires Python 3.12, Android NDK r28c and Android SDK CMake 3.22.1.
The default validates a fresh build without editing the repository. --write-assets
is an explicit maintainer update; --check-pins additionally checks byte identity.
Exact pin reproduction uses the canonical toolchain/work paths documented in
docs/media/NATIVE_16KB_REPAIR.md because the original GNU build IDs include them.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/media/assets/media/native-overrides'
DEBUG_ASSETS = ROOT / 'app/src/mediaDebug/assets/media/native-overrides'
DOCS = ROOT / 'docs/media/native'
SOURCE_URL = 'https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-1.6.0.tar.gz'
SOURCE_SHA256 = 'e4ab7009bf0629fd11982d4c2aa83964cf244cffba7347ecd39019a9e38c4564'
ABIS = ('arm64-v8a', 'x86_64')
NAMES = ('libsharpyuv.so', 'libwebp.so', 'libwebpdecoder.so', 'libwebpdemux.so', 'libwebpmux.so')
TARGETS = ('sharpyuv', 'webp', 'webpdecoder', 'webpdemux', 'libwebpmux')


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def dynamic(readelf, path):
    text = subprocess.check_output([str(readelf), '--dynamic', str(path)], text=True)
    return {'soname': re.findall(r'\(SONAME\).*?\[(.*?)\]', text),
            'needed': re.findall(r'\(NEEDED\).*?\[(.*?)\]', text)}


def exports(readelf, path):
    text = subprocess.check_output([str(readelf), '--dyn-syms', '--wide', str(path)], text=True)
    symbols = set()
    for line in text.splitlines():
        fields = line.split()
        if len(fields) >= 8 and fields[4] in ('GLOBAL', 'WEAK') and fields[5] in ('DEFAULT', 'PROTECTED') and fields[6] != 'UND':
            symbols.add(fields[7].split('@')[0])
    return symbols


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ndk', type=Path, required=True)
    parser.add_argument('--cmake', type=Path, required=True)
    parser.add_argument('--work-dir', type=Path, default=Path('/tmp/jumlo-webp-rebuild'))
    parser.add_argument('--source-archive', type=Path, help='Use an already downloaded official source archive')
    parser.add_argument('--jobs', type=int, choices=(1, 2), default=2)
    parser.add_argument('--write-assets', action='store_true')
    parser.add_argument('--check-pins', action='store_true')
    args = parser.parse_args()
    ndk, cmake, work = args.ndk.resolve(), args.cmake.resolve(), args.work_dir.resolve()
    revision = (ndk / 'source.properties').read_text()
    if not re.search(r'^Pkg.Revision\s*=\s*28\.2\.13676358\s*$', revision, re.MULTILINE):
        parser.error('Use pinned NDK r28c (28.2.13676358)')
    if not subprocess.check_output([str(cmake), '--version'], text=True).startswith('cmake version 3.22.1'):
        parser.error('Use pinned CMake 3.22.1')
    tools = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
    readelf, strip = tools / 'llvm-readelf', tools / 'llvm-strip'
    if not readelf.is_file() or not strip.is_file():
        parser.error('This reproducible build targets the official Linux x86_64 NDK toolchain')
    work.mkdir(parents=True, exist_ok=True)
    source_archive = args.source_archive or work / 'libwebp-1.6.0.tar.gz'
    if not source_archive.is_file():
        with urllib.request.urlopen(SOURCE_URL, timeout=60) as response, source_archive.open('wb') as output:
            shutil.copyfileobj(response, output)
    if digest(source_archive) != SOURCE_SHA256:
        raise ValueError('Official libwebp source checksum mismatch')
    source = work / 'libwebp-1.6.0'
    if source.exists():
        shutil.rmtree(source)
    with tarfile.open(source_archive) as archive:
        archive.extractall(work, filter='data')
    spec = importlib.util.spec_from_file_location('media_artifact', ROOT / 'scripts/inspect-media-artifact.py')
    gate = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gate)
    pins = gate.properties((ASSETS / 'manifest.properties').read_text())
    baseline = json.loads((DOCS / 'libwebp-original-abi.json').read_text())
    flags = [
        '-DBUILD_SHARED_LIBS=ON', '-DCMAKE_BUILD_TYPE=Release', '-DCMAKE_POSITION_INDEPENDENT_CODE=ON',
        '-DCMAKE_PLATFORM_NO_VERSIONED_SONAME=ON', '-DCMAKE_SKIP_RPATH=ON',
        '-DANDROID_PLATFORM=android-24', '-DANDROID_STL=none', '-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON',
        f'-DCMAKE_TOOLCHAIN_FILE={ndk}/build/cmake/android.toolchain.cmake',
        f'-DCMAKE_MAKE_PROGRAM={cmake.with_name("ninja")}',
        '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,-z,relro -Wl,-z,now -Wl,--as-needed',
        f'-DCMAKE_C_FLAGS=-ffile-prefix-map={source}=libwebp-1.6.0', '-DWEBP_BUILD_LIBWEBPMUX=ON',
    ]
    flags += [f'-DWEBP_BUILD_{name}=OFF' for name in (
        'CWEBP', 'DWEBP', 'GIF2WEBP', 'IMG2WEBP', 'VWEBP', 'WEBPINFO',
        'ANIM_UTILS', 'EXTRAS', 'WEBPMUX', 'FUZZTEST', 'WEBP_JS')]
    environment = {**os.environ, 'SOURCE_DATE_EPOCH': '0'}
    records = []
    for abi in ABIS:
        build, output = work / ('build-' + abi), work / 'verified' / abi
        if build.exists():
            shutil.rmtree(build)
        output.mkdir(parents=True, exist_ok=True)
        with (work / ('build-' + abi + '.log')).open('w') as log:
            subprocess.run([str(cmake), '-S', str(source), '-B', str(build), '-G', 'Ninja',
                            f'-DANDROID_ABI={abi}', *flags], check=True, stdout=log, stderr=subprocess.STDOUT, env=environment)
            subprocess.run([str(cmake), '--build', str(build), '--target', *TARGETS, '--parallel', str(args.jobs)],
                           check=True, stdout=log, stderr=subprocess.STDOUT, env=environment)
        for name in NAMES:
            library = output / name
            shutil.copyfile(build / name, library)
            subprocess.run([str(strip), '--strip-unneeded', str(library)], check=True)
            old, actual = baseline[abi][name], dynamic(readelf, library)
            before, after = set(old['exports']), exports(readelf, library)
            if actual['soname'] != [old['soname']] or not before <= after or set(old['needed']) != set(actual['needed']):
                raise ValueError(f'Native ABI contract changed: {abi}/{name}')
            layout = gate.inspect_elf64(library.read_bytes(), abi, str(library))
            checksum = digest(library)
            if args.check_pins and checksum != pins[f'{abi}.{name}.sha256']:
                raise ValueError(f'Rebuild differs from checked-in hash: {abi}/{name}: {checksum}')
            records.append({'abi': abi, 'name': name, 'bytes': library.stat().st_size, 'sha256': checksum,
                            'soname': name, 'originalSoname': old['soname'], 'needed': actual['needed'],
                            'originalNeeded': old['needed'], 'originalExportCount': len(before),
                            'newExportCount': len(after), 'exportsIdentical': before == after,
                            'missingOriginalExports': sorted(before - after),
                            'loadAlignments': layout['load_alignments'], 'relroChecked': layout['relro_checked']})
        print(f'Verified {abi}: five libraries, original ABI retained, 16 KiB LOAD and RELRO layout', flush=True)
    report = json.dumps(records, indent=2) + '\n'
    (work / 'verification.json').write_text(report)
    if args.write_assets:
        # Publish only after every ABI and library passed the checks above.
        for record in records:
            abi, name = record['abi'], record['name']
            destination = (DEBUG_ASSETS if abi == 'x86_64' else ASSETS) / abi
            destination.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(work / 'verified' / abi / name, destination / name)
            pins[f'{abi}.{name}.sha256'] = record['sha256']
        (ASSETS / 'manifest.properties').write_text('# Regenerated by scripts/rebuild-media-webp.py\n' +
            ''.join(key + '=' + value + '\n' for key, value in pins.items()))
        (ASSETS / 'LICENSE.libwebp.txt').write_text((source / 'COPYING').read_text() +
            '\n\nPatent grant\n\n' + (source / 'PATENTS').read_text())
        (DOCS / 'libwebp-verification.json').write_text(report)
    print(json.dumps({'report': str(work / 'verification.json'), 'libraries': len(records),
                      'bytes': sum(record['bytes'] for record in records), 'assets_updated': args.write_assets}))


if __name__ == '__main__':
    main()
