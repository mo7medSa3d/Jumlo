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

# Defend against CVE-2026-26331 class .netrc / --netrc-cmd loading.
# yt-dlp has no --no-netrc flag (netrc is opt-in); block netrc/config flags and disable config files.
args = sys.argv[2:]
for arg in args:
    if arg == '--':
        break
    if arg in ('-n', '--netrc', '--netrc-location', '--netrc-cmd', '--config-location', '--config-locations') or arg.startswith(
        ('--netrc-location=', '--netrc-cmd=', '--config-location=', '--config-locations=')
    ):
        raise SystemExit('MEDIA_FORBIDDEN_NETRC_OPTION')

# Every option and the final URL originate from checked Kotlin policy, not webpage text.
yt_dlp.main(['--ignore-config', '--no-config-locations', *args])
