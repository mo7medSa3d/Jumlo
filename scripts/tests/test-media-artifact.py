#!/usr/bin/env python3
"""Exercise the APK gate with complete, missing and corrupted media payloads."""
import hashlib
import importlib.util
import io
import struct
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('media_artifact', ROOT / 'scripts/inspect-media-artifact.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def elf(abi='arm64-v8a', alignment=16384, incongruent=False, relro_tail=False):
    data = bytearray(0x5000 if relro_tail else 256)
    data[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<H', data, 18, gate.ELF_MACHINES[abi])
    struct.pack_into('<Q', data, 32, 64)
    struct.pack_into('<HH', data, 54, 56, 3 if relro_tail else 1)
    struct.pack_into('<IIQQQQQQ', data, 64, 1, 4, 0, 4096 if incongruent else 0, 0, 256, 256, alignment)
    if relro_tail:
        struct.pack_into('<IIQQQQQQ', data, 120, 1, 6, 0x4000, 0x4000, 0, 0x1000, 0x1000, 16384)
        struct.pack_into('<IIQQQQQQ', data, 176, 0x6474e552, 4, 0x4000, 0x4000, 0, 0x100, 0x100, 1)
    return bytes(data)


def zip_payload(entries):
    result = io.BytesIO()
    with zipfile.ZipFile(result, 'w') as zip_file:
        for name, data in entries.items():
            zip_file.writestr(name, data)
    return result.getvalue()


class MediaArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.apk = Path(self.temp.name) / 'app.apk'
        payload = io.BytesIO()
        with zipfile.ZipFile(payload, 'w') as archive:
            archive.writestr('yt_dlp/version.py', "__version__ = '2026.10.21'\n")
            archive.writestr('yt_dlp_ejs/__init__.py', '')
        self.payload = payload.getvalue()
        self.expected = {'version': '2026.10.21', 'sha256': hashlib.sha256(self.payload).hexdigest()}
        self.entries = {
            'classes.dex': b'dex\n',
            'assets/media/yt-dlp': self.payload,
            'assets/media/extractor.properties': ''.join(f'{k}={v}\n' for k, v in self.expected.items()).encode(),
            'assets/media/bootstrap.py': b'import yt_dlp',
            'assets/media/network_guard.py': b'def install(): pass',
        }
        for name in ('libpython.so', 'libpython.zip.so', 'libqjs.so', 'libffmpeg.so', 'libffmpeg.zip.so', 'libffprobe.so'):
            self.entries['lib/arm64-v8a/' + name] = elf()
        self.entries['lib/arm64-v8a/libpython.zip.so'] = zip_payload({'usr/lib/libpython.so': elf()})
        self.original_libraries = {'usr/lib/' + name: elf(alignment=4096) for name in gate.REPAIRED_LIBRARIES}
        self.native_expected = {'source.version': '1.6.0', 'repair.version': 'fixture'}
        self.update_original_archive()
        self.entries[gate.NATIVE_OVERRIDE_ROOT + 'LICENSE.libwebp.txt'] = b'Synthetic test fixture license'
        for name in gate.REPAIRED_LIBRARIES:
            self.update_overlay(name, elf())

    def update_original_archive(self):
        data = zip_payload(self.original_libraries)
        self.entries['lib/arm64-v8a/libffmpeg.zip.so'] = data
        self.native_expected['arm64-v8a.ffmpegArchive.sha256'] = hashlib.sha256(data).hexdigest()

    def update_overlay(self, name, data):
        self.entries[gate.NATIVE_OVERRIDE_ROOT + 'arm64-v8a/' + name] = data
        self.native_expected['arm64-v8a.' + name + '.sha256'] = hashlib.sha256(data).hexdigest()

    def inspect(self):
        self.entries[gate.NATIVE_OVERRIDE_ROOT + 'manifest.properties'] = ''.join(
            key + '=' + value + '\n' for key, value in self.native_expected.items()).encode()
        with zipfile.ZipFile(self.apk, 'w') as archive:
            for name, data in self.entries.items():
                archive.writestr(name, data)
        return gate.inspect(self.apk, self.expected, self.native_expected)

    def test_complete_engine_passes(self):
        result = self.inspect()
        self.assertEqual(['arm64-v8a'], result['abis'])
        self.assertEqual('2026.10.21', result['extractor_version'])

    def test_missing_javascript_runtime_is_rejected(self):
        del self.entries['lib/arm64-v8a/libqjs.so']
        with self.assertRaisesRegex(ValueError, 'libqjs.so'):
            self.inspect()

    def test_payload_byte_corruption_is_rejected(self):
        self.entries['assets/media/yt-dlp'] += b'corruption'
        with self.assertRaisesRegex(ValueError, 'checksum'):
            self.inspect()

    def test_stale_build_metadata_is_rejected(self):
        self.entries['assets/media/extractor.properties'] = b'version=2025.11.12\nsha256=old\n'
        with self.assertRaisesRegex(ValueError, 'source pin'):
            self.inspect()

    def test_missing_ffmpeg_payload_is_rejected(self):
        self.entries['lib/arm64-v8a/libffmpeg.zip.so'] = b''
        with self.assertRaisesRegex(ValueError, 'libffmpeg.zip.so'):
            self.inspect()

    def test_missing_native_repair_is_rejected(self):
        del self.entries[gate.NATIVE_OVERRIDE_ROOT + 'arm64-v8a/libwebp.so']
        with self.assertRaisesRegex(ValueError, 'Missing native repair'):
            self.inspect()

    def test_corrupted_native_repair_is_rejected(self):
        self.entries[gate.NATIVE_OVERRIDE_ROOT + 'arm64-v8a/libwebp.so'] += b'corruption'
        with self.assertRaisesRegex(ValueError, 'repair checksum'):
            self.inspect()

    def test_native_repair_for_wrong_machine_is_rejected_even_with_a_matching_hash(self):
        self.update_overlay('libwebp.so', elf(abi='x86_64'))
        with self.assertRaisesRegex(ValueError, 'machine'):
            self.inspect()

    def test_original_unaligned_libraries_are_not_accepted_as_replacements(self):
        self.update_overlay('libwebp.so', elf(alignment=4096))
        with self.assertRaisesRegex(ValueError, 'alignment'):
            self.inspect()

    def test_changing_only_the_alignment_header_is_not_sufficient(self):
        self.update_overlay('libwebp.so', elf(incongruent=True))
        with self.assertRaisesRegex(ValueError, 'incongruence'):
            self.inspect()

    def test_relro_overlapping_writable_data_is_rejected(self):
        self.update_overlay('libwebp.so', elf(relro_tail=True))
        with self.assertRaisesRegex(ValueError, 'RELRO'):
            self.inspect()

    def test_unrelated_unaligned_nested_library_is_not_hidden_by_the_repair(self):
        self.original_libraries['usr/lib/libother.so'] = elf(alignment=4096)
        self.update_original_archive()
        with self.assertRaisesRegex(ValueError, 'libother.so.*alignment'):
            self.inspect()

    def test_overlay_contract_rejects_another_upstream_ffmpeg_archive(self):
        self.entries['lib/arm64-v8a/libffmpeg.zip.so'] += b'different dependency release'
        with self.assertRaisesRegex(ValueError, 'different FFmpeg'):
            self.inspect()

    def test_relro_own_load_is_valid_without_trailing_writable_data(self):
        data = bytearray(elf(relro_tail=True))
        # The writable LOAD contains only RELRO; no data shares its final page.
        struct.pack_into('<Q', data, 120 + 32, 0x100)
        struct.pack_into('<Q', data, 120 + 40, 0x100)
        self.update_overlay('libwebp.so', bytes(data))
        self.inspect()


if __name__ == '__main__':
    unittest.main(verbosity=2)
