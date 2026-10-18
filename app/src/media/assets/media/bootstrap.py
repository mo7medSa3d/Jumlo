"""APK-owned yt-dlp launcher. No browser cookies, secrets or arbitrary user options."""
import os
import sys

os.setsid()  # MediaProcessRunner terminates the whole download/FFmpeg process group.
print('UU_PID:' + str(os.getpid()), flush=True)
archive = os.path.abspath(sys.argv[1])
sys.path.insert(0, os.path.dirname(__file__))
sys.path.insert(0, archive)
from network_guard import install
install(os.environ.get('UU_ALLOW_HTTP') == '1')
import yt_dlp

# Every option and the final URL originate from checked Kotlin policy, not webpage text.
yt_dlp.main(sys.argv[2:])
