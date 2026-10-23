#!/usr/bin/env python3
"""Deterministically shrink the packaged junkfood02 media runtime zips.

Upstream includes static link archives, introspection/build data, CPython tests,
and libraries outside the retained runtime's dynamic dependency graph. Keep the
runtime modules and repair targets, follow exact DT_NEEDED names and physical
symlink targets, and include dependencies of every ELF the retention policy keeps.
Reopen the proposed payloads and reject unresolved dependencies or dangling aliases
before publishing any archive. This checks static linker closure; it does not
substitute for executing the packaged runtime on Android.

Output is byte-stable across Python/zlib versions: retained compressed records
and metadata are copied verbatim; only central-directory offsets are updated.
Digests land in app/src/media/assets/media/native-overrides/
manifest.properties so the runtime verify gate stays effective.

Standard library only.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import os
import posixpath
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
# Verified subset of Android 8.0/API 26 public libraries:
# https://android.googlesource.com/platform/system/core/+/refs/tags/android-8.0.0_r1/rootdir/etc/public.libraries.android.txt
# libandroid-support, libandroid-glob and libgcc_s are NOT platform exemptions.
SYS_LIBS = {'libc.so', 'libm.so', 'libdl.so', 'libstdc++.so',
            'liblog.so', 'libOpenSLES.so', 'libEGL.so', 'libGLESv2.so',
            'libvulkan.so', 'libnativewindow.so', 'libsync.so',
            # Public bitmap JNI API since API 8; used by CameraX's image helper.
            # https://developer.android.com/ndk/guides/stable_apis#bitmaps
            'libandroid.so', 'libjnigraphics.so',
            'libmediandk.so'}
_TEST_RE = re.compile(r'_test\w*\.')

def son_key(name: str) -> str:
    # The loader requests exact filenames; version suffixes are not interchangeable.
    return posixpath.basename(name)

def _elf_layout(data: bytes):
    if not data.startswith(b'\x7fELF'):
        return None
    if len(data) < 52 or data[4] not in (1, 2) or data[5] not in (1, 2):
        raise ValueError('Invalid ELF header')
    wide = data[4] == 2
    order = '<' if data[5] == 1 else '>'
    try:
        phoff = struct.unpack_from(order + ('Q' if wide else 'I'), data, 32 if wide else 28)[0]
        entsize, count = struct.unpack_from(order + 'HH', data, 54 if wide else 42)
        minimum = 56 if wide else 32
        if entsize < minimum or count == 0xffff or phoff + entsize * count > len(data):
            raise ValueError('Invalid ELF program table')
        segments = []
        for i in range(count):
            values = struct.unpack_from(order + ('IIQQQQQQ' if wide else 'IIIIIIII'), data, phoff + i * entsize)
            p_type, p_off, p_vaddr, filesz = (values[0], values[2], values[3], values[5]) if wide else (values[0], values[1], values[2], values[4])
            if p_off + filesz > len(data):
                raise ValueError('Truncated ELF segment')
            segments.append((p_type, p_off, p_vaddr, filesz))
        return wide, order, segments
    except struct.error as error:
        raise ValueError('Truncated ELF header') from error

def elf_needed(path: str) -> set[str]:
    with open(path, 'rb') as fh:
        return _needed_of(fh.read())

def _needed_of(data: bytes) -> set[str]:
    layout = _elf_layout(data)
    if layout is None:
        return set()
    wide, order, segments = layout
    entries = []
    for kind, offset, _, size in segments:
        if kind != 2:
            continue
        step = 16 if wide else 8
        if size % step:
            raise ValueError('Invalid ELF dynamic table')
        for position in range(offset, offset + size, step):
            tag, value = struct.unpack_from(order + ('QQ' if wide else 'II'), data, position)
            if tag == 0:
                break
            entries.append((tag, value))
    strtab = next((v for t, v in entries if t == 5), None)
    if strtab is None:
        if any(t == 1 for t, _ in entries):
            raise ValueError('ELF dependency names have no string table')
        return set()
    mapped = next(((off + strtab - address, off + size) for kind, off, address, size in segments
                   if kind == 1 and address <= strtab < address + size), None)
    if mapped is None:
        raise ValueError('ELF string table is outside file-backed load segments')
    strings, limit = mapped
    out: set[str] = set()
    for tag, val in entries:
        if tag == 1:
            if strings + val >= limit:
                raise ValueError('ELF dependency name is outside string table')
            end = data.index(b'\0', strings + val, limit)
            out.add(data[strings + val:end].decode('utf8', 'strict'))
    return out

def is_symlink(info: zipfile.ZipInfo) -> bool:
    if getattr(info, 'create_system', 0) != 3:
        return False
    return ((info.external_attr >> 16) & 0o170000) == 0o120000

class LinkUniverse:
    """Exact linker names and archive-local aliases in the on-device search space.

    Nodes identify physical entries, not a normalized SONAME. Only the native
    directory and top-level usr/lib entries supply DT_NEEDED names; dynamically
    loaded modules can be roots without masquerading as global library providers.
    Multiple providers of a name are all checked and retained conservatively.
    """

    def __init__(self, abi_dir: str, payloads: dict[str, bytes] | None = None):
        self.dir = abi_dir
        self.needed: dict[str, set[str]] = {}
        self.aliases: dict[str, str] = {}
        self.providers: dict[str, set[str]] = {}
        self.entries: dict[str, zipfile.ZipInfo] = {}
        self.files: set[str] = set()
        self.module_roots: set[str] = set()
        self._payloads = payloads or {}
        for root, dirs, names in os.walk(self.dir):
            dirs.sort()
            for name in sorted(names):
                full = os.path.join(root, name)
                relative = os.path.relpath(full, self.dir).replace(os.sep, '/')
                if name.endswith('.zip.so'):
                    self._scan_zip(full, relative)
                    continue
                with open(full, 'rb') as stream:
                    data = stream.read()
                self.files.add(relative)
                if data.startswith(b'\x7fELF'):
                    self.needed[relative] = _needed_of(data)
                    self.module_roots.add(relative)  # Native files are never pruned.
                    if '/' not in relative:
                        self.providers.setdefault(name, set()).add(relative)
        for node in self.aliases:
            self.resolve_alias(node)  # Validate actual paths, cycles and targets.

    def _scan_zip(self, path: str, relative: str) -> None:
        source = io.BytesIO(self._payloads[relative]) if relative in self._payloads else path
        with zipfile.ZipFile(source) as archive:
            infos = archive.infolist()
            if len({info.filename for info in infos}) != len(infos):
                raise ValueError(f'Ambiguous ZIP directory: {relative}')
            for info in infos:
                name = info.filename
                node = relative + '!' + name
                self.files.add(node)
                self.entries[node] = info
                if info.is_dir():
                    continue
                data = archive.read(info)
                if is_symlink(info):
                    target = data.decode('utf8', 'strict')
                    if not target or '\x00' in target or posixpath.isabs(target):
                        raise ValueError(f'Invalid symlink target: {node}')
                    resolved = posixpath.normpath(posixpath.join(posixpath.dirname(name), target))
                    if resolved == '..' or resolved.startswith('../'):
                        raise ValueError(f'Symlink escapes archive: {node}')
                    self.aliases[node] = relative + '!' + resolved
                    if decide(name, info, set())[0]:
                        self.module_roots.add(node)
                elif data.startswith(b'\x7fELF'):
                    self.needed[node] = _needed_of(data)
                    # Includes versioned files that the retention policy keeps,
                    # not just linked executables and dynamically loaded modules.
                    if decide(name, info, set())[0]:
                        self.module_roots.add(node)
                if posixpath.dirname(name) == 'usr/lib' and (node in self.needed or node in self.aliases):
                    self.providers.setdefault(son_key(name), set()).add(node)

    def resolve_alias(self, node: str) -> str:
        seen = set()
        while node in self.aliases:
            if node in seen:
                raise ValueError(f'Cyclic archive symlink: {node}')
            seen.add(node)
            node = self.aliases[node]
        if node not in self.files:
            raise ValueError(f'Dangling archive symlink: {node}')
        return node

    def _walk(self, roots: set[str], visited: set[str]) -> None:
        work = list(roots)
        while work:
            node = work.pop()
            if node in visited:
                continue
            visited.add(node)
            if node in self.aliases:
                work.append(self.aliases[node])
            for name in self.needed.get(node, ()):
                # Resolve the verified API 26 platform set on Android itself;
                # do not retain upstream build stubs that shadow those names.
                if name in SYS_LIBS:
                    continue
                providers = self.providers.get(name, set())
                if providers:
                    for provider in providers:
                        if self.resolve_alias(provider) not in self.needed:
                            raise ValueError(f'Dependency is not an ELF: {node} -> {name}')
                    work.extend(providers)
                else:
                    raise ValueError(f'Unresolved DT_NEEDED: {node} -> {name}')

    def closure(self) -> set[str]:
        visited: set[str] = set()
        roots = set(self.module_roots)
        while True:
            self._walk(roots, visited)
            names = {son_key(node.split('!', 1)[-1]) for node in visited}
            # A retained basename can also keep another physical entry. Include
            # that entry's dependencies before deciding the graph is complete.
            roots = {node for node, info in self.entries.items()
                     if node not in visited and (node in self.needed or node in self.aliases)
                     and decide(info.filename, info, names)[0]}
            # Preserve alternate names of retained runtime files, including
            # aliases used by dynamic loading rather than DT_NEEDED. Do not root
            # an otherwise unused tool solely because an alias exists for it.
            roots.update(node for node in self.aliases if node not in visited
                         and self.resolve_alias(node) in visited)
            if not roots:
                return names

    def validate(self) -> None:
        """Check EVERY ELF and alias physically present in the proposed payloads."""
        self._walk(set(self.needed) | set(self.aliases), set())

def decide(name: str, info: zipfile.ZipInfo, closure: set[str]) -> tuple[bool, str]:
    """(keep, reason)"""
    basename = os.path.basename(name)
    if not name.startswith('usr/lib/'):
        return True, 'outside-usr-lib'
    if name == 'usr/lib/liblzma.so':
        # Android's libunwind needs the platform liblzma.so XzUnpacker API.
        # Both pinned private runtimes use liblzma.so.5 instead. Their unused
        # development alias shadows the incompatible platform implementation.
        # Keep every ELF byte and the .so.5 alias; reject an unexpected binary.
        if not is_symlink(info):
            raise ValueError('Platform liblzma.so collision is not a development alias')
        return False, 'platform-lzma-development-alias'
    if name.startswith(('usr/lib/engines-3/', 'usr/lib/ossl-modules/')):
        return True, 'openssl-modules'
    if name.startswith(('usr/etc/', 'usr/share/')):
        return True, 'config'
    if insta_keep(name):
        return True, 'keep-list'
    if name.startswith('usr/lib/python3.12/site-packages/'):
        return True, 'site-packages'
    if name.startswith('usr/lib/python3.12/test/'):
        return False, 'cpython-test-data'
    if name.endswith(('.py', '.pyc')):
        return True, 'python-code'
    if name.startswith('usr/lib/python3.12/lib-dynload/'):
        if _TEST_RE.search(basename):
            return False, 'cpython-test-module'
        return True, 'dynload'
    if is_symlink(info):
        # Keep referenced aliases with their physical targets. An unused alias
        # must not survive after its unlinked library has been removed.
        return son_key(name) in closure, 'alias-in-closure'
    if name == 'usr/lib/quickjs/libquickjs.a':
        return False, 'unused-static-js-archive'
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
    """Prune a checked single-disk ZIP without ever recompressing its contents.

    The upstream payloads are ordinary ZIP32 archives. Reject other layouts
    instead of silently rewriting an unreviewed variant of the native payload.
    """
    src = zipfile.ZipFile(io.BytesIO(original))
    eocd = original.rfind(b'PK\x05\x06', max(0, len(original) - 65557))
    if eocd < 0:
        raise ValueError('ZIP end record missing')
    disk, cd_disk, count_disk, count, cd_size, cd_start, comment_size = struct.unpack_from('<HHHHIIH', original, eocd + 4)
    if disk or cd_disk or count_disk != count or count == 65535 or cd_start + cd_size != eocd or eocd + 22 + comment_size != len(original):
        raise ValueError('Unsupported ZIP layout')
    infos = src.infolist()
    if len(infos) != count or len({i.filename for i in infos}) != count:
        raise ValueError('Ambiguous ZIP directory')
    by_offset = sorted(infos, key=lambda i: i.header_offset)
    dropped: list[tuple[int, str]] = []
    out = bytearray()
    offsets = {}
    for index, info in enumerate(by_offset):
        end = by_offset[index + 1].header_offset if index + 1 < count else cd_start
        if original[info.header_offset:info.header_offset + 4] != b'PK\x03\x04' or end <= info.header_offset:
            raise ValueError('Invalid ZIP local record')
        keep, _why = decide(info.filename, info, closure)
        if keep or info.is_dir():
            src.read(info)  # CRC and decompression validation before retaining bytes.
            offsets[info.header_offset] = len(out)
            out.extend(original[info.header_offset:end])
        else:
            dropped.append((info.compress_size, info.filename))
    new_cd_start = len(out)
    position = cd_start
    retained = 0
    for _ in range(count):
        if original[position:position + 4] != b'PK\x01\x02':
            raise ValueError('Invalid ZIP central record')
        name_size, extra_size, entry_comment = struct.unpack_from('<HHH', original, position + 28)
        length = 46 + name_size + extra_size + entry_comment
        old_offset = struct.unpack_from('<I', original, position + 42)[0]
        if old_offset in offsets:
            record = bytearray(original[position:position + length])
            struct.pack_into('<I', record, 42, offsets[old_offset])
            out.extend(record)
            retained += 1
        position += length
    if position != eocd:
        raise ValueError('Invalid ZIP directory length')
    out.extend(struct.pack('<4sHHHHIIH', b'PK\x05\x06', 0, 0, retained, retained,
                           len(out) - new_cd_start, new_cd_start, comment_size))
    out.extend(original[eocd + 22:])
    return bytes(out), dropped

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
        abi = os.path.basename(os.path.normpath(d))
        if abi not in ('arm64-v8a', 'x86_64', 'armeabi-v7a', 'x86'):
            print(f'repack-native-media: cannot infer the ABI from {d} (use the per-ABI dir)', file=sys.stderr)
            return 2
        if args.pins:
            for name, source_key, output_key in (
                ('libffmpeg.zip.so', 'upstreamArchive', 'ffmpegArchive'),
                ('libpython.zip.so', 'upstreamPythonArchive', 'pythonArchive'),
            ):
                path = os.path.join(d, name)
                if not os.path.isfile(path):
                    continue
                source_pin = pins.get(f'{abi}.{source_key}.sha256')
                output_pin = pins.get(f'{abi}.{output_key}.sha256')
                actual = hashlib.sha256(open(path, 'rb').read()).hexdigest()
                if not source_pin or not output_pin or actual not in (source_pin, output_pin):
                    print(f'repack-native-media: {abi}/{name} does not match pinned upstream or repacked bytes', file=sys.stderr)
                    return 3
        universe = LinkUniverse(d)
        closure = universe.closure()
        outputs = {}
        messages = []
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
            outputs[name] = new
            messages.append((path, len(new), len(original) - len(new), sha, dropped))
        LinkUniverse(d, payloads=outputs).validate()
        if args.write:
            for name, data in outputs.items():
                with open(os.path.join(d, name), 'wb') as stream:
                    stream.write(data)
        for path, length, saved, sha, dropped in messages:
            print(f'REPACKED_OK {path} bytes_after={length} saved={saved} sha256={sha}')
            for size, name in sorted(dropped, reverse=True)[:6]:
                print(f'    -{size/1e6:6.2f} MB {name}', file=sys.stderr)
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
