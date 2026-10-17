"""Owned launcher; injects platform headers and user session cookies before invoking yt-dlp."""
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

raw_args = list(sys.argv[2:])
injected = []

cookies_file = os.environ.get('UU_COOKIES_FILE', '').strip()
if cookies_file and os.path.isfile(cookies_file) and os.path.getsize(cookies_file) > 0:
    injected += ['--cookies', cookies_file]

user_agent = os.environ.get('UU_USER_AGENT', '').strip()
if user_agent:
    injected += ['--user-agent', user_agent]

referer = os.environ.get('UU_REFERER', '').strip()
if referer:
    injected += ['--referer', referer]

extra_headers = os.environ.get('UU_EXTRA_HEADERS', '').strip()
if extra_headers:
    for line in extra_headers.splitlines():
        line = line.strip()
        if ':' in line:
            injected += ['--add-header', line]

if '--' in raw_args:
    sep_idx = raw_args.index('--')
    final_args = injected + raw_args[:sep_idx] + raw_args[sep_idx:]
else:
    final_args = injected + raw_args

yt_dlp.main(final_args)
