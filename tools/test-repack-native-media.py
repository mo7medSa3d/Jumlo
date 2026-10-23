import importlib.util
import io
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('repack',Path(__file__).with_name('repack-native-media.py'))
repack=importlib.util.module_from_spec(spec)
spec.loader.exec_module(repack)


def elf(bits, base=0x400000, needed='libfoo.so'):
    data=bytearray(0x300)
    data[:7]=b'\x7fELF'+bytes((2 if bits==64 else 1,1,1))
    if bits==64:
        struct.pack_into('<Q',data,32,64)
        struct.pack_into('<HH',data,54,56,2)
        struct.pack_into('<IIQQQQQQ',data,64,1,4,0,base,base,len(data),len(data),4096)
        struct.pack_into('<IIQQQQQQ',data,120,2,4,0x180,base+0x180,base+0x180,48,48,8)
        struct.pack_into('<QQQQQQ',data,0x180,5,base+0x240,1,1,0,0)
    else:
        struct.pack_into('<I',data,28,52)
        struct.pack_into('<HH',data,42,32,2)
        struct.pack_into('<IIIIIIII',data,52,1,0,base,base,len(data),len(data),4,4096)
        struct.pack_into('<IIIIIIII',data,84,2,0x180,base+0x180,base+0x180,24,24,4,4)
        struct.pack_into('<IIIIII',data,0x180,5,base+0x240,1,1,0,0)
    names=b'\x00'+needed.encode('ascii')+b'\x00'
    data[0x240:0x240+len(names)]=names
    return bytes(data)


class RepackTests(unittest.TestCase):
    def test_camerax_bitmap_jni_dependency_uses_the_public_android_library(self):
        # CameraX's image helper links this NDK API, available since API 8.
        # Accept only the exact public soname; unresolved names still fail below.
        for bits in (32, 64):
            with self.subTest(bits=bits), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / 'libimage_processing_util_jni.so').write_bytes(elf(bits, needed='libjnigraphics.so'))
                self.assertEqual({'libimage_processing_util_jni.so'}, repack.LinkUniverse(str(root)).closure())

    def test_elf64_maps_virtual_string_table_to_file_offset(self):
        self.assertEqual({'libfoo.so'},repack._needed_of(elf(64)))

    def test_elf32_uses_its_own_header_and_dynamic_word_size(self):
        self.assertEqual({'libfoo.so'},repack._needed_of(elf(32)))

    def test_truncated_elf_cannot_silently_remove_dependencies(self):
        with self.assertRaises(ValueError): repack._needed_of(elf(64)[:80])

    def test_android_compatibility_libraries_and_their_dependencies_survive_repacking(self):
        # These are shipped backports, not Android system libraries. The device
        # linker cannot start Python if the dependency closure removes them.
        for bits in (32, 64):
            for library in ('libandroid-support.so', 'libandroid-glob.so'):
                with self.subTest(bits=bits, library=library), tempfile.TemporaryDirectory() as directory:
                    root=Path(directory)
                    (root/'libpython.so').write_bytes(elf(bits,needed=library))
                    source=io.BytesIO()
                    with zipfile.ZipFile(source,'w',compression=zipfile.ZIP_DEFLATED) as archive:
                        archive.writestr('usr/lib/'+library,elf(bits,needed='libcompat-helper.so'))
                        archive.writestr('usr/lib/libcompat-helper.so',elf(bits,needed='libc.so'))
                    (root/'libpython.zip.so').write_bytes(source.getvalue())
                    closure=repack.LinkUniverse(str(root)).closure()
                    result,dropped=repack.repack_bytes(source.getvalue(),closure)
                    with zipfile.ZipFile(io.BytesIO(result)) as archive:
                        self.assertEqual({'usr/lib/'+library,'usr/lib/libcompat-helper.so'},set(archive.namelist()))
                    self.assertEqual([],dropped)

    def test_versioned_aliases_keep_their_transitive_dependencies(self):
        # Actual upstream shape: the linker requests libglib-2.0.so.0, while
        # the real file has a longer version and needs libpcre2-8.so.
        for bits in (32, 64):
            with self.subTest(bits=bits), tempfile.TemporaryDirectory() as directory:
                root=Path(directory)
                (root/'libffmpeg.so').write_bytes(elf(bits,needed='libglib-2.0.so'))
                source=io.BytesIO()
                with zipfile.ZipFile(source,'w',compression=zipfile.ZIP_DEFLATED) as archive:
                    for name,target in [('libglib-2.0.so','libglib-2.0.so.0'),
                                        ('libglib-2.0.so.0','libglib-2.0.so.0.8600.0')]:
                        entry=zipfile.ZipInfo('usr/lib/'+name)
                        entry.create_system=3
                        entry.external_attr=0o120777 << 16
                        archive.writestr(entry,target)
                    archive.writestr('usr/lib/libglib-2.0.so.0.8600.0',elf(bits,needed='libpcre2-8.so'))
                    archive.writestr('usr/lib/libpcre2-8.so',elf(bits,needed='libc.so'))
                (root/'libffmpeg.zip.so').write_bytes(source.getvalue())
                closure=repack.LinkUniverse(str(root)).closure()
                result,dropped=repack.repack_bytes(source.getvalue(),closure)
                with zipfile.ZipFile(io.BytesIO(result)) as archive:
                    self.assertIn('usr/lib/libpcre2-8.so',archive.namelist())
                    self.assertEqual('libglib-2.0.so.0.8600.0',archive.read('usr/lib/libglib-2.0.so.0').decode())
                self.assertEqual([],dropped)

    def test_needed_names_preserve_exact_loader_versions(self):
        self.assertEqual({'libexample.so.7'},repack._needed_of(elf(64,needed='libexample.so.7')))

    def test_cross_basename_alias_follows_its_relative_target(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/'libpython.so').write_bytes(elf(64,needed='libalias.so'))
            source=io.BytesIO()
            with zipfile.ZipFile(source,'w') as archive:
                entry=zipfile.ZipInfo('usr/lib/libalias.so')
                entry.create_system=3
                entry.external_attr=0o120777 << 16
                archive.writestr(entry,'nested/libimplementation-2.0.so.1')
                archive.writestr('usr/lib/nested/libimplementation-2.0.so.1',elf(64,needed='libhelper.so'))
                archive.writestr('usr/lib/libhelper.so',elf(64,needed='libc.so'))
            (root/'libpython.zip.so').write_bytes(source.getvalue())
            closure=repack.LinkUniverse(str(root)).closure()
            result,dropped=repack.repack_bytes(source.getvalue(),closure)
            self.assertEqual([],dropped)
            with zipfile.ZipFile(io.BytesIO(result)) as archive:
                self.assertIn('usr/lib/libhelper.so',archive.namelist())

    def test_every_retained_versioned_elf_contributes_its_dependencies(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/'libpython.so').write_bytes(elf(64,needed='libc.so'))
            source=io.BytesIO()
            with zipfile.ZipFile(source,'w') as archive:
                archive.writestr('usr/lib/libretained.so.1',elf(64,needed='libhelper.so'))
                archive.writestr('usr/lib/libhelper.so',elf(64,needed='libc.so'))
            (root/'libpython.zip.so').write_bytes(source.getvalue())
            closure=repack.LinkUniverse(str(root)).closure()
            self.assertEqual([],repack.repack_bytes(source.getvalue(),closure)[1])

    def test_unresolved_dependencies_fail_instead_of_assuming_a_system_library(self):
        for missing in ('libmissing.so','libgcc_s.so','libexample.so.7'):
            with self.subTest(missing=missing), tempfile.TemporaryDirectory() as directory:
                root=Path(directory)
                (root/'libpython.so').write_bytes(elf(64,needed=missing))
                # Another version cannot satisfy an exact DT_NEEDED filename.
                (root/'libexample.so.8').write_bytes(elf(64,needed='libc.so'))
                with self.assertRaisesRegex(ValueError,'Unresolved'):
                    repack.LinkUniverse(str(root)).closure()

    def test_invalid_retained_symlink_targets_fail_closed(self):
        for target in ('missing/libtarget.so','../../../outside.so','libalias.so'):
            with self.subTest(target=target), tempfile.TemporaryDirectory() as directory:
                root=Path(directory)
                source=io.BytesIO()
                with zipfile.ZipFile(source,'w') as archive:
                    entry=zipfile.ZipInfo('usr/lib/libalias.so')
                    entry.create_system=3
                    entry.external_attr=0o120777 << 16
                    archive.writestr(entry,target)
                    archive.writestr('usr/lib/libtarget.so',elf(64,needed='libc.so'))
                (root/'libpython.zip.so').write_bytes(source.getvalue())
                with self.assertRaises(ValueError):
                    repack.LinkUniverse(str(root)).closure()

    def test_unused_capture_alias_and_its_unlinked_target_are_removed_together(self):
        # The upstream V4L capture helpers require an absent execinfo backport.
        # Shipped FFmpeg does not link them; an unused alias must not turn them
        # into runtime roots or survive after its target is pruned.
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/'libffmpeg.so').write_bytes(elf(64,needed='libc.so'))
            source=io.BytesIO()
            with zipfile.ZipFile(source,'w') as archive:
                entry=zipfile.ZipInfo('usr/lib/v4l2convert.so')
                entry.create_system=3
                entry.external_attr=0o120777 << 16
                archive.writestr(entry,'libv4l/v4l2convert.so')
                archive.writestr('usr/lib/libv4l/v4l2convert.so',elf(64,needed='libandroid-execinfo.so'))
            (root/'libffmpeg.zip.so').write_bytes(source.getvalue())
            closure=repack.LinkUniverse(str(root)).closure()
            result,dropped=repack.repack_bytes(source.getvalue(),closure)
            with zipfile.ZipFile(io.BytesIO(result)) as archive:
                self.assertEqual([],archive.namelist())
            repack.LinkUniverse(str(root),payloads={'libffmpeg.zip.so':result}).validate()

    def test_verified_platform_libraries_do_not_retain_bundled_link_stubs(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/'libffmpeg.so').write_bytes(elf(64,needed='libandroid.so'))
            source=io.BytesIO()
            with zipfile.ZipFile(source,'w') as archive:
                archive.writestr('usr/lib/libandroid.so',elf(64,needed='libc.so'))
            (root/'libffmpeg.zip.so').write_bytes(source.getvalue())
            closure=repack.LinkUniverse(str(root)).closure()
            result,dropped=repack.repack_bytes(source.getvalue(),closure)
            with zipfile.ZipFile(io.BytesIO(result)) as archive:
                self.assertEqual([],archive.namelist())
            repack.LinkUniverse(str(root),payloads={'libffmpeg.zip.so':result}).validate()

    def test_existing_alias_to_a_retained_runtime_library_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root/'libpython.so').write_bytes(elf(64,needed='libactual.so.1'))
            source=io.BytesIO()
            with zipfile.ZipFile(source,'w') as archive:
                entry=zipfile.ZipInfo('usr/lib/libgeneric.so')
                entry.create_system=3
                entry.external_attr=0o120777 << 16
                archive.writestr(entry,'libactual.so.1')
                archive.writestr('usr/lib/libactual.so.1',elf(64,needed='libc.so'))
            (root/'libpython.zip.so').write_bytes(source.getvalue())
            closure=repack.LinkUniverse(str(root)).closure()
            result,dropped=repack.repack_bytes(source.getvalue(),closure)
            self.assertEqual([],dropped)
            repack.LinkUniverse(str(root),payloads={'libpython.zip.so':result}).validate()

    def test_repack_preserves_source_compressed_bytes_without_using_zlib(self):
        source=io.BytesIO()
        with zipfile.ZipFile(source,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1) as z:
            z.writestr('usr/lib/libfoo.so',b'payload '*10000)
            z.writestr('usr/lib/unused.a',b'not needed')
        with patch('zlib.compressobj',side_effect=AssertionError('must not recompress')):
            result,dropped=repack.repack_bytes(source.getvalue(),{'libfoo.so'})
        with zipfile.ZipFile(io.BytesIO(result)) as z:
            self.assertEqual(['usr/lib/libfoo.so'],z.namelist())
            self.assertEqual(b'payload '*10000,z.read('usr/lib/libfoo.so'))
        self.assertEqual('usr/lib/unused.a',dropped[0][1])
        self.assertEqual(result,repack.repack_bytes(result,{'libfoo.so'})[0])

    def test_python_test_data_is_removed_but_runtime_and_packages_stay(self):
        def kept(name): return repack.decide(name,zipfile.ZipInfo(name),set())[0]
        self.assertFalse(kept('usr/lib/python3.12/test/test_os.py'))
        self.assertTrue(kept('usr/lib/python3.12/asyncio/base_events.py'))
        self.assertTrue(kept('usr/lib/python3.12/site-packages/package/tests/test_api.py'))


class PlatformLzmaCollisionTests(unittest.TestCase):
    def payload(self, bits, *, unversioned_elf=False):
        source = io.BytesIO()
        with zipfile.ZipFile(source, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('usr/lib/liblzma.so.5.8.1', elf(bits, needed='libc.so'))
            archive.writestr('usr/lib/libavcodec.so.61', elf(bits, needed='liblzma.so.5'))
            archive.writestr('usr/lib/python3.12/lib-dynload/_lzma.cpython-312.so', elf(bits, needed='liblzma.so.5'))
            for name in ('liblzma.so', 'liblzma.so.5'):
                if name == 'liblzma.so' and unversioned_elf:
                    archive.writestr('usr/lib/' + name, elf(bits, needed='libc.so'))
                else:
                    entry = zipfile.ZipInfo('usr/lib/' + name)
                    entry.create_system = 3
                    entry.external_attr = 0o120777 << 16
                    archive.writestr(entry, 'liblzma.so.5.8.1')
        return source.getvalue()

    def test_unversioned_platform_alias_is_removed_while_private_lzma_consumers_remain(self):
        # Restoring the generic development alias lets Android's libunwind bind
        # to the app's incompatible XZ implementation. The versioned dependency
        # used by both Python and FFmpeg must still resolve byte-for-byte.
        for bits in (32, 64):
            for archive_name in ('libpython.zip.so', 'libffmpeg.zip.so'):
                with self.subTest(bits=bits, archive=archive_name), tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    (root / 'libffmpeg.so').write_bytes(elf(bits, needed='libavcodec.so.61'))
                    original = self.payload(bits)
                    (root / archive_name).write_bytes(original)
                    closure = repack.LinkUniverse(str(root)).closure()
                    result, dropped = repack.repack_bytes(original, closure)
                    with zipfile.ZipFile(io.BytesIO(original)) as before, zipfile.ZipFile(io.BytesIO(result)) as after:
                        self.assertNotIn('usr/lib/liblzma.so', after.namelist())
                        for name in ('usr/lib/liblzma.so.5', 'usr/lib/liblzma.so.5.8.1',
                                     'usr/lib/libavcodec.so.61',
                                     'usr/lib/python3.12/lib-dynload/_lzma.cpython-312.so'):
                            self.assertEqual(before.read(name), after.read(name))
                    self.assertEqual(['usr/lib/liblzma.so'], [name for _, name in dropped])
                    repack.LinkUniverse(str(root), payloads={archive_name: result}).validate()
                    self.assertEqual(result, repack.repack_bytes(result, closure)[0])

    def test_an_unexpected_unversioned_lzma_binary_is_rejected_instead_of_pruned(self):
        # Only the verified development alias is removable; a different future
        # upstream payload must be reviewed rather than silently losing code.
        with self.assertRaises(ValueError):
            repack.repack_bytes(self.payload(64, unversioned_elf=True), {'liblzma.so.5', 'libavcodec.so.61'})

    def test_a_new_private_consumer_of_the_platform_name_fails_output_closure(self):
        # A future consumer requiring the generic name must stop the build,
        # never acquire a bogus platform exemption or ship unresolved.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'libpython.so').write_bytes(elf(64, needed='liblzma.so'))
            original = self.payload(64)
            (root / 'libpython.zip.so').write_bytes(original)
            closure = repack.LinkUniverse(str(root)).closure()
            with self.assertRaisesRegex(ValueError, 'Unresolved DT_NEEDED'):
                result, _ = repack.repack_bytes(original, closure)
                repack.LinkUniverse(str(root), payloads={'libpython.zip.so': result}).validate()


if __name__=='__main__': unittest.main()
