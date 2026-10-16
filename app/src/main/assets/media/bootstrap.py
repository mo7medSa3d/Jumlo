"""Owned launcher; arguments are argv entries, never interpreted by a shell."""
import os
import sys

os.setsid()
print('UU_PID:' + str(os.getpid()), flush=True)
# The library's immutable APK-pinned zipapp is the sole Python package source.
archive = os.path.abspath(sys.argv[1])
sys.path.insert(0, archive)
from network_guard import install
install(os.environ.get('UU_ALLOW_HTTP') == '1')
import yt_dlp
# Ignore user/global configuration and disable runtime plugins and updates.
yt_dlp.main(sys.argv[2:])
