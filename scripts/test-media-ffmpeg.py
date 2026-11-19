#!/usr/bin/env python3
"""Host FFmpeg + ffprobe verification of Jumlo media merge, audio transcode, and container guards."""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


@unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "ffmpeg/ffprobe required")
class MediaFfmpegHostTests(unittest.TestCase):
    @classmethod
    def _run_ffmpeg_cmd(cls, args):
        cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y"] + args
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=30)
        if res.returncode != 0:
            raise AssertionError(f"FFmpeg failed: {res.stderr}")

    @classmethod
    def setUpClass(cls):
        # Read-only synthetic fixtures are shared across tests to avoid re-encoding libx264/aac per test method.
        cls._fixtures_dir = Path(tempfile.mkdtemp(prefix="jumlo-ffmpeg-fixtures-"))
        cls.addClassCleanup(shutil.rmtree, cls._fixtures_dir, ignore_errors=True)
        cls.video = cls._fixtures_dir / "video.mp4"
        cls.audio = cls._fixtures_dir / "audio.m4a"
        cls._run_ffmpeg_cmd([
            "-f", "lavfi", "-i", "testsrc=size=1280x720:rate=15", "-t", "1",
            "-an", "-c:v", "libx264", "-pix_fmt", "yuv420p", str(cls.video)
        ])
        cls._run_ffmpeg_cmd([
            "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100", "-t", "1",
            "-vn", "-c:a", "aac", str(cls.audio)
        ])

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="jumlo-ffmpeg-"))

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _run_ffmpeg(self, args):
        self._run_ffmpeg_cmd(args)

    def _probe_streams(self, path: Path) -> str:
        cmd = [
            "ffprobe", "-v", "error",
            "-show_entries", "stream=codec_name,codec_type,width,height",
            "-of", "csv=p=0", str(path)
        ]
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=15)
        self.assertEqual(0, res.returncode, f"ffprobe failed: {res.stderr}")
        return res.stdout.strip()

    def test_merge_video_and_audio_preserves_dimensions_and_streams(self):
        out = self.tmp / "media.mp4"
        self._run_ffmpeg([
            "-i", str(self.video), "-i", str(self.audio),
            "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", str(out)
        ])
        self.assertTrue(out.is_file() and out.stat().st_size > 0)
        head = out.read_bytes()[:16]
        self.assertEqual(b"ftyp", head[4:8])
        streams = self._probe_streams(out)
        self.assertIn("h264,video,1280,720", streams)
        self.assertIn("aac,audio", streams)

    def test_extract_audio_mp3_contains_only_audio(self):
        out = self.tmp / "media.mp3"
        self._run_ffmpeg([
            "-i", str(self.audio), "-vn", "-c:a", "libmp3lame", "-b:a", "192k", str(out)
        ])
        self.assertTrue(out.is_file() and out.stat().st_size > 0)
        streams = self._probe_streams(out)
        self.assertIn("mp3,audio", streams)
        self.assertNotIn("video", streams)

    def test_extract_audio_m4a_source_codec_copy(self):
        out = self.tmp / "media.m4a"
        self._run_ffmpeg([
            "-i", str(self.audio), "-vn", "-c:a", "copy", str(out)
        ])
        self.assertTrue(out.is_file() and out.stat().st_size > 0)
        self.assertEqual(b"ftyp", out.read_bytes()[4:8])
        streams = self._probe_streams(out)
        self.assertEqual("aac,audio", streams)

    def test_extract_audio_opus_in_ogg_container(self):
        out = self.tmp / "media.opus"
        self._run_ffmpeg([
            "-i", str(self.audio), "-vn", "-c:a", "libopus", "-b:a", "128k", str(out)
        ])
        self.assertTrue(out.is_file() and out.stat().st_size > 0)
        self.assertEqual(b"OggS", out.read_bytes()[:4])
        streams = self._probe_streams(out)
        self.assertEqual("opus,audio", streams)

    def test_malformed_input_fails_without_valid_output(self):
        bad = self.tmp / "corrupt.bin"
        bad.write_text("not a media container", encoding="utf-8")
        out = self.tmp / "rejected.mp4"
        res = subprocess.run(
            ["ffmpeg", "-v", "error", "-nostdin", "-i", str(bad), str(out)],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=15
        )
        self.assertNotEqual(0, res.returncode)
        self.assertFalse(out.exists() and out.stat().st_size > 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
