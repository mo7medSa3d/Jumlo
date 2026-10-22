#!/usr/bin/env python3
"""Deterministically shrink the packaged junkfood02 media runtime zips.

Upstream packs desktop-shaped archives: static link archives, gobject
introspection data, tool libraries outside every shipped executable's
dynamic-link closure, and CPython's own test modules. Every dropped entry is
provably unreachable at runtime on device:

- it is not reachable from the DT_NEEDED graph of the executables that
  actually ship (libffmpeg.so, libffprobe.so, libpython.so, libqjs.so),
- it is not a symlink entry (the on-device installer recreates symlinks),
- it is not site-packages, OpenSSL engines/providers, TLS data, CPython's
  lib-dynload, or the five libraries the app repairs in place (webp set).

Output is byte-stable: source entry order and ZipInfo metadata preserved,
zlib level 9. Digests land in app/src/media/assets/media/native-overrides/
manifest.properties so the runtime verify gate stays effective.

Standard library only.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import os
import re
import struct
import sys
import zipfile

EXECUTABLES = ('libffmpeg.so', 'libffprobe.so', 'libpython.so', 'libqjs.so')

# Never considered for removal: app-level repair targets, network security
# surface, the interpreter core, and generic small dependencies.
KEEP_RE = re.compile(
    r'(?:^|/)(?:libsharpyuv\.so\.?[\d.]*$|libwebp[A-Za-z0-9_.]*\.so$'
    r'|libcrypto\.so\.?[\d.]*$|libssl\.so\.?[\d.]*$'
    r'|libpython3[A-Za-z0-9_.]*\.so$|libz\.so(?:\.[\d.]+)*$'
    r'|libexpat\.so(?:\.[\d.]+)*$|liblzma\.so(?:\.[\d.]+)*$'
    r'|libc\+\+_shared\.so$)'
)
SYS_LIBS = {'libc.so', 'libm.so', 'libdl.so', 'libstdc++.so', 'libgcc_s.so',
            'liblog.so', 'libOpenSLES.so', 'libEGL.so', 'libGLESv2.so',
            'libvulkan.so', 'libnativewindow.so', 'libsync.so',
            'libandroid.so', 'libandroid-support.so', 'libandroid-glob.so',
            'libmediandk.so'}
_TEST_RE = re.compile(r'_test\w*\.')

def son_key(name: str) -> str:
    m = re.search(r'(lib[A-Za-z0-9_+]+?\.so)', os.path.basename(name))
    return m.group(1) if m else os.path.basename(name)

def _dynamic_entries(data: bytes) -> list[tuple[int, int]] | None:
    if not data.startswith(b'\x7fELF') or data[4] not in (1, 2):
        return None
    order = '<' if data[5] == 1 else '>'
    fmt = 'Q' if data[4] == 2 else 'I'
    phoff = struct.unpack_from(order + fmt, data, 32)[0]
    entsize, count = struct.unpack_from(order + 'HH', data, 54)
    dyn = None
    for i in range(count):
        if data[4] == 2:
            p_type, _, p_off, _, _, filesz, _, _ = struct.unpack(order + 'IIQQQQQQ',
                data[phoff + i * entsize: phoff + i * entsize + 56])
        else:
            p_type, _, p_off, _, _, filesz, _, _ = struct.unpack(order + 'IIIIIIII',
                data[phoff + i * entsize: phoff + i * entsize + 32])
        if p_type == 2:
            dyn = (p_off, filesz)
    if dyn is None:
        return None
    out = []
    pos = 0
    while pos < dyn[1]:
        tag, val = struct.unpack_from(order + 'QQ', data, dyn[0] + pos)
        if tag == 0:
            break
        out.append((tag, val))
        pos += 16
    return out

def elf_needed(path: str) -> set[str]:
    with open(path, 'rb') as fh:
        return _needed_of(fh.read())

def _needed_of(data: bytes) -> set[str]:
    out: set[str] = set()
    entries = _dynamic_entries(data)
    if entries is None:
        return out
    strtab = next((v for t, v in entries if t == 5), None)
    if strtab is None:
        return out
    for tag, val in entries:
        if tag == 1:
            end = data.index(b'\0', strtab + val)
            out.add(son_key(data[strtab + val:end].decode('utf8', 'replace')))
    return out

def is_symlink(info: zipfile.ZipInfo) -> bool:
    if getattr(info, 'create_system', 0) != 3:
        return False
    return ((info.external_attr >> 16) & 0o170000) == 0o120000

class LinkUniverse:
    """DT_NEEDED resolution over the on-device library search space.

    The loader sees (per device): the extracted ffmpeg/python directories and
    the APK's native library directory ($ORIGIN for the executables). The
    merged jni dir bundles both, plus the payload zips which must be opened
    to see every entry the extractor unpacks to packages/*/usr/lib.
    """

    def __init__(self, abi_dir: str):
        self.dir = abi_dir
        self.needed: dict[str, set[str]] = {}
        for root, _, names in os.walk(self.dir):
            for name in names:
                full = os.path.join(root, name)
                if name.endswith('.zip.so'):
                    self._scan_zip(full)
                    continue
                key = son_key(name)
                try:
                    self.needed.setdefault(key, set()).update(elf_needed(full))
                except OSError:
                    continue

    def _scan_zip(self, path: str) -> None:
        with zipfile.ZipFile(path) as z:
            for info in z.infolist():
                name = info.filename
                if info.is_dir() or not name.startswith('usr/lib/'):
                    continue
                base = os.path.basename(name)
                key = son_key(base)
                if is_symlink(info):
                    continue
                data = z.read(info)
                try:
                    self.needed.setdefault(key, set()).update(_needed_of(data))
                except (OSError, ValueError):
                    continue

    def closure(self) -> set[str]:
        closure: set[str] = set()
        for exe in EXECUTABLES:
            path = os.path.join(self.dir, exe)
            if not os.path.isfile(path):
                continue
            closure.update(elf_needed(path) - SYS_LIBS)
        work = list(closure)
        while work:
            key = work.pop()
            for n in set(self.needed.get(key, ())) - SYS_LIBS:
                if n not in closure:
                    closure.add(n)
                    work.append(n)
        return closure

def decide(name: str, info: zipfile.ZipInfo, closure: set[str]) -> tuple[bool, str]:
    """(keep, reason)"""
    basename = os.path.basename(name)
    if is_symlink(info):
        return True, 'symlink'
    if not name.startswith('usr/lib/'):
        return True, 'outside-usr-lib'
    if name.startswith(('usr/lib/engines-3/', 'usr/lib/ossl-modules/')):
        return True, 'openssl-modules'
    if name.startswith(('usr/etc/', 'usr/share/')):
        return True, 'config'
    if insta_keep(name):
        return True, 'keep-list'
    if name.endswith(('.py', '.pyc')):
        return True, 'python-code'
    if name.startswith('usr/lib/python3.12/site-packages/'):
        return True, 'site-packages'
    if name.startswith('usr/lib/python3.12/lib-dynload/'):
        if _TEST_RE.search(basename):
            return False, 'cpython-test-module'
        return True, 'dynload'
    if name == 'usr/lib/quickjs/libquickjs.a':
        return False, 'unused-static-js-archive'
    if name.startswith('usr/lib/python3.12/test/'):
        return False, 'cpython-test-data'
    if name.endswith(('.a', '.typelib')):
        return False, 'link-time-artifact'
    if name == 'usr/lib/glib-2.0/include/glibconfig.h':
        return False, 'build-header'
    if name.endswith('.so') and son_key(name) not in closure:
        return False, 'unreachable-in-link-closure'
    return True, 'in-closure'

def insta_keep(name: str) -> bool:
    return KEEP_RE.search(name) is not None

def repack_bytes(original: bytes, closure: set[str]) -> tuple[bytes, list[tuple[int, str]]]:
    src = zipfile.ZipFile(io.BytesIO(original))
    dropped: list[tuple[int, str]] = []
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9, allowZip64=True) as dst:
        for info in src.infolist():
            if info.is_dir():
                dst.writestr(info, src.read(info))
                continue
            keep, _why = decide(info.filename, info, closure)
            if keep:
                dst.writestr(info, src.read(info))
            else:
                dropped.append((info.compress_size, info.filename))
    return out.getvalue(), dropped

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--dir', action='append', required=True, help='merged jni lib directory (per ABI)')
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--pins', type=str, default=None,
                        help='app/src/media/assets/media/native-overrides/manifest.properties; '
                             'after repacking, every produced digest must match '
                             f'<abi>.{{ffmpeg,python}}Archive.sha256 pinned there')
    args = parser.parse_args()
    pins = {}
    if args.pins:
        if not os.path.isfile(args.pins):
            print(f'repack-native-media: pin manifest missing: {args.pins}', file=sys.stderr)
            return 2
        for line in open(args.pins, encoding='utf8'):
            line = line.strip()
            if '=' in line and not line.startswith('#'):
                k, v = line.split('=', 1)
                pins[k] = v
    reports = []
    for d in args.dir:
        universe = LinkUniverse(d)
        closure = universe.closure()
        abi = os.path.basename(os.path.normpath(d))
        if abi not in ('arm64-v8a', 'x86_64', 'armeabi-v7a', 'x86'):
            print(f'repack-native-media: cannot infer the ABI from {d} (use the per-ABI dir)', file=sys.stderr)
            return 2
        for name in ('libffmpeg.zip.so', 'libpython.zip.so'):
            path = os.path.join(d, name)
            if not os.path.isfile(path):
                print(f'repack-native-media: no {name} in {d}; is extendedMedia disabled?', file=sys.stderr)
                continue
            original = open(path, 'rb').read()
            new, dropped = repack_bytes(original, closure)
            sha = hashlib.sha256(new).hexdigest()
            pin_key = f'{abi}.ffmpegArchive.sha256' if name == 'libffmpeg.zip.so' else f'{abi}.pythonArchive.sha256'
            expected = pins.get(pin_key)
            if args.pins and expected != sha:
                print(f'repack-native-media: {pin_key} pin mismatch: '
                      f'manifest says {expected or "MISSING"}, repack produced {sha}', file=sys.stderr)
                return 3
            if args.write:
                with open(path, 'wb') as fh:
                    fh.write(new)
            print(f'REPACKED_OK {path} bytes_after={len(new)} saved={len(original) - len(new)} sha256={sha}')
            for size, dn in sorted(dropped, reverse=True)[:6]:
                print(f'    -{size/1e6:6.2f} MB {dn}', file=sys.stderr)
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
