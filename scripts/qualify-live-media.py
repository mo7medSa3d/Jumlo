#!/usr/bin/env python3
"""Plan Android qualification and run bounded host-only metadata probes.

Uses Python's standard library, the actual bundled extractor, and local JSON inputs.
Never installs tools, runs ADB, downloads media, imports cookies, or marks Android
cases passed. See docs/testing/LIVE_MEDIA_QUALIFICATION.md.
"""
import argparse
import csv
import hashlib
import ipaddress
import json
import math
import os
from pathlib import Path
import platform
import re
import shlex
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
from datetime import datetime, timezone
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/media/assets/media'
PLATFORMS = ('youtube', 'tiktok', 'instagram', 'facebook', 'linkedin', 'google_drive', 'direct')
PLATFORM_CASES = ('video', 'audio', 'quality', 'invalid', 'offline')
WORKFLOW_CASES = ('paste_recreate', 'pause_resume', 'cancel', 'background', 'process_death',
                  'duplicate_names', 'save_folder')
URL_FIELDS = ('video_url', 'audio_url', 'file_url', 'invalid_url')
FIELDS = ('target', 'device_kind', 'api', 'abi', 'device_label', 'apk_sha256', 'platform',
          'case', 'input_ready', 'input_sha256', 'status', 'utc', 'tester', 'selected_formats',
          'observed_tracks', 'output_sha256', 'evidence', 'notes')


def digest(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def template():
    return {'schema': 1, 'platforms': {name: {field: '' for field in URL_FIELDS} for name in PLATFORMS},
            'targets': [
                {'id': 'android9-emulator', 'device_kind': 'emulator', 'api': 28, 'abi': '', 'device_label': '', 'apk_sha256': ''},
                {'id': 'android14plus-emulator', 'device_kind': 'emulator', 'api': 34, 'abi': '', 'device_label': '', 'apk_sha256': ''},
                {'id': 'physical-device', 'device_kind': 'physical', 'api': None, 'abi': '', 'device_label': '', 'apk_sha256': ''}]}


def validate_url(url):
    if not isinstance(url, str) or not url or len(url) > 32768 or any(c.isspace() or ord(c) < 32 for c in url):
        raise ValueError('Input must be one public HTTP(S) URL without whitespace')
    parsed = urlsplit(url)
    host = (parsed.hostname or '').lower().rstrip('.')
    if parsed.scheme not in ('http', 'https') or not host or parsed.username is not None or parsed.password is not None:
        raise ValueError('Only public HTTP(S) inputs without credentials are accepted')
    if parsed.port is not None and not 1 <= parsed.port <= 65535:
        raise ValueError('Invalid URL port')
    if host == 'localhost' or host.endswith(('.localhost', '.local', '.internal')):
        raise ValueError('Local destinations are not qualification inputs')
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        parts = host.split('.')
        if len(parts) < 2 or parts[-1].isdigit() or all(p.isdigit() or p.startswith('0x') for p in parts):
            raise ValueError('Ambiguous or local destination') from None
    else:
        if not address.is_global or address.version == 6:
            raise ValueError('Non-public/unsupported literal destination')
    return url


def load_manifest(path):
    if path.stat().st_size > 256 * 1024:
        raise ValueError('Manifest exceeds 256 KiB')
    raw = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(raw, dict) or raw.get('schema') != 1 or not isinstance(raw.get('platforms'), dict):
        raise ValueError('Expected schema=1 and a platforms object')
    if set(raw) - {'schema', 'platforms', 'targets'} or set(raw['platforms']) - set(PLATFORMS):
        raise ValueError('Unknown manifest field/platform; check spelling')
    result = template()
    for name, inputs in raw['platforms'].items():
        if not isinstance(inputs, dict) or set(inputs) - set(URL_FIELDS):
            raise ValueError(f'Unknown input field for {name}')
        for field, value in inputs.items():
            if not isinstance(value, str):
                raise ValueError(f'{name}.{field} must be text')
            if value:
                validate_url(value)
            result['platforms'][name][field] = value
    if 'targets' in raw:
        targets = raw['targets']
        if not isinstance(targets, list) or not 1 <= len(targets) <= 10:
            raise ValueError('Supply between one and ten target records')
        ids = set()
        for target in targets:
            if not isinstance(target, dict) or set(target) != set(result['targets'][0]):
                raise ValueError('Target fields must match the template')
            if not isinstance(target['id'], str) or not re.fullmatch(r'[a-zA-Z0-9_.-]{1,80}', target['id']) or target['id'] in ids:
                raise ValueError('Target IDs must be unique simple labels')
            ids.add(target['id'])
            if target['device_kind'] not in ('emulator', 'physical'):
                raise ValueError('Target kind must be emulator or physical')
            if target['api'] is not None and (type(target['api']) is not int or target['api'] < 26):
                raise ValueError('Target API must be >=26 or null until known')
            if any(not isinstance(target[key], str) for key in ('abi', 'device_label', 'apk_sha256')):
                raise ValueError('Target labels and hash must be strings')
            if target['apk_sha256'] and not re.fullmatch(r'[a-fA-F0-9]{64}', target['apk_sha256']):
                raise ValueError('apk_sha256 must be an actual 64-character digest')
        result['targets'] = targets
    return result


def input_for_case(inputs, case):
    if case == 'audio':
        return inputs['audio_url'] or inputs['video_url']
    if case == 'invalid':
        return inputs['invalid_url']
    if case == 'file':
        return inputs['file_url']
    return inputs['video_url']


def matrix_rows(manifest):
    rows = []
    for target in manifest['targets']:
        def add(name, case, inputs):
            row = dict.fromkeys(FIELDS, '')
            row.update({key: target[key] if target[key] is not None else '' for key in
                        ('device_kind', 'api', 'abi', 'device_label', 'apk_sha256')})
            row.update(target=target['id'], platform=name, case=case, status='NOT_RUN',
                       input_ready=str(all(inputs)).lower() if inputs else 'n/a',
                       input_sha256=digest('\n'.join(inputs)) if inputs and all(inputs) else '')
            rows.append(row)
        for name in PLATFORMS:
            for case in PLATFORM_CASES:
                add(name, case, [input_for_case(manifest['platforms'][name], case)])
        for name in ('google_drive', 'direct'):
            add(name, 'file', [manifest['platforms'][name]['file_url']])
        # These rows require both engine paths; see the exact checklist in the guide.
        lifecycle = [manifest['platforms']['youtube']['video_url'], manifest['platforms']['direct']['video_url']]
        for case in WORKFLOW_CASES:
            add('both_engines', case, lifecycle)
        add('offline_fixture', 'packaged_runtime', [])
        add('offline_fixture', 'existing_ui_tests', [])
    return rows


def redact(text, input_url=''):
    if input_url:
        text = text.replace(input_url, '<input-url>')
    text = re.sub(r'https?://[^\s<>\"\']+', '<url>', text)
    text = re.sub(r'\x1b\[[0-9;]*m', '', text)
    return text[:8192]


def metadata_summary(stdout):
    lines = stdout.splitlines()
    if lines and re.fullmatch(r'UU_PID:[1-9][0-9]*', lines[0]):
        lines = lines[1:]
    payload = json.loads('\n'.join(lines))
    if not isinstance(payload, dict) or payload.get('_type') in ('playlist', 'multi_video'):
        raise ValueError('Expected one media item')
    formats = payload.get('formats') or [payload]
    if not isinstance(formats, list) or len(formats) > 2000:
        raise ValueError('Unexpected format count')
    allowed = ('format_id', 'ext', 'protocol', 'vcodec', 'acodec', 'width', 'height', 'fps', 'abr', 'tbr', 'filesize', 'filesize_approx')
    clean = []
    for item in formats:
        if not isinstance(item, dict):
            continue
        record = {}
        for key in allowed:
            value = item.get(key)
            if type(value) in (int, float) and math.isfinite(value):
                record[key] = value
            elif isinstance(value, str) and len(value) <= 128 and re.fullmatch(r'[A-Za-z0-9_.+:/ -]*', value) and '://' not in value:
                record[key] = value
        clean.append(record)
    def known(item, key):
        value = item.get(key)
        return isinstance(value, str) and bool(value) and value.lower() not in ('none', 'unknown')
    heights = sorted({min(f['width'], f['height']) for f in clean if known(f, 'vcodec')
                      and type(f.get('width')) is int and type(f.get('height')) is int
                      and f['width'] > 0 and f['height'] > 0}, reverse=True)
    duration = payload.get('duration')
    return {'format_count': len(clean), 'known_video_formats': sum(known(f, 'vcodec') for f in clean),
            'known_audio_formats': sum(known(f, 'acodec') for f in clean),
            'reported_short_edges': heights,
            'duration_seconds': duration if type(duration) in (int, float) and math.isfinite(duration) and duration >= 0 else None,
            'formats': clean}


def probe_command(bootstrap, archive, url):
    return [sys.executable, '-u', str(bootstrap), str(archive), '--ignore-config', '--no-cache-dir',
            '--no-plugin-dirs', '--no-playlist', '--simulate', '--skip-download', '--dump-single-json',
            '--no-check-formats', '--socket-timeout', '10', '--retries', '0', '--extractor-retries', '0',
            '--fragment-retries', '0', '--abort-on-error', '--', validate_url(url)]


def stop_process(process, group_id=None):
    # Group ownership is retained independently of the leader's exit status.
    # The APK launcher calls setsid() before emitting its trusted UU_PID header.
    if group_id is None:
        try:
            if os.getpgid(process.pid) == process.pid:
                group_id = process.pid
        except ProcessLookupError:
            pass
    if group_id is not None:
        if group_id != process.pid or group_id == os.getpgrp():
            raise ValueError('Refusing an unowned process group')
        for sig in (signal.SIGTERM, signal.SIGKILL):
            try:
                os.killpg(group_id, sig)
            except ProcessLookupError:
                break
            if sig == signal.SIGTERM:
                try:
                    process.wait(timeout=0.25)
                except subprocess.TimeoutExpired:
                    pass
                # Even when wait succeeds, a descendant may ignore SIGTERM and
                # keep the group's output pipes open. Always escalate the group.
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=0.25)
        except subprocess.TimeoutExpired:
            process.kill()
    process.wait(timeout=1)


def run_bounded(command, directory, extra_environment, timeout=40, stdout_limit=4 * 1024 * 1024):
    env = {**os.environ, **extra_environment}
    process = subprocess.Popen(command, cwd=directory, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    overflow = threading.Event()
    first_line_ready = threading.Event()
    group_id = None
    def remember_group(header=None):
        nonlocal group_id
        try:
            observed = os.getpgid(process.pid)
        except ProcessLookupError:
            # A fast-exiting owned bootstrap can be reaped before the reader
            # runs. Its exact first line still proves setsid() completed.
            observed = process.pid if header == f'UU_PID:{process.pid}'.encode() else None
        if observed == process.pid:
            group_id = process.pid
    output, errors = bytearray(), bytearray()
    def consume(pipe, buffer, limit, owns_header=False):
        prefix = bytearray()
        try:
            while True:
                chunk = pipe.read1(8192)
                if not chunk:
                    return
                if owns_header and not first_line_ready.is_set():
                    prefix.extend(chunk[:max(0, 64 - len(prefix))])
                    if b'\n' in prefix or len(prefix) >= 64:
                        remember_group(bytes(prefix).split(b'\n', 1)[0])
                        first_line_ready.set()
                remaining = max(0, limit - len(buffer))
                buffer.extend(chunk[:remaining])
                if len(chunk) > remaining:
                    overflow.set()
        finally:
            pipe.close()
            if owns_header:
                first_line_ready.set()
    threads = [threading.Thread(target=consume, args=(process.stdout, output, stdout_limit, True), daemon=True),
               threading.Thread(target=consume, args=(process.stderr, errors, 64 * 1024), daemon=True)]
    for thread in threads:
        thread.start()
    deadline, failure = time.monotonic() + timeout, None
    try:
        while process.poll() is None:
            remember_group()
            if overflow.is_set():
                failure = 'HOST_OUTPUT_LIMIT'
                break
            if time.monotonic() >= deadline:
                failure = 'HOST_TIMEOUT'
                break
            time.sleep(0.025)
    finally:
        # Give the stdout reader the owned bootstrap handshake even when the
        # leader exited between polls and a descendant still holds the pipe.
        first_line_ready.wait(timeout=0.25)
        stop_process(process, group_id)
        for thread in threads:
            thread.join(timeout=2)
        if any(thread.is_alive() for thread in threads):
            # A delayed reader may have learned group ownership during cleanup.
            stop_process(process, group_id)
            for thread in threads:
                thread.join(timeout=1)
    if overflow.is_set():
        failure = 'HOST_OUTPUT_LIMIT'
    return {'exit_code': process.poll(), 'failure': failure,
            'stdout': output.decode('utf-8', errors='replace'), 'stderr': errors.decode('utf-8', errors='replace')}


def extractor_identity():
    props = dict(line.split('=', 1) for line in (ASSETS / 'extractor.properties').read_text().splitlines()
                 if line and not line.startswith('#'))
    actual = hashlib.sha256((ASSETS / 'yt-dlp').read_bytes()).hexdigest()
    if actual != props['sha256']:
        raise ValueError('Bundled extractor hash mismatch; no probe was started')
    return {'version': props['version'], 'archive_sha256': actual,
            'bootstrap_sha256': hashlib.sha256((ASSETS / 'bootstrap.py').read_bytes()).hexdigest()}


def write_json_new(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8') as output:
        json.dump(data, output, indent=2, ensure_ascii=False, allow_nan=False)
        output.write('\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    initialize = commands.add_parser('init', help='Write an empty local URL/device manifest')
    initialize.add_argument('--out', type=Path, required=True)
    plan = commands.add_parser('plan', help='Write an Android matrix; every case starts NOT_RUN')
    plan.add_argument('--manifest', type=Path, required=True)
    plan.add_argument('--out', type=Path, required=True)
    probe = commands.add_parser('probe', help='Run metadata only with the bundled archive on this host')
    probe.add_argument('--manifest', type=Path, required=True)
    probe.add_argument('--platform', choices=PLATFORMS, action='append', dest='platforms')
    probe.add_argument('--field', choices=URL_FIELDS, default='video_url')
    probe.add_argument('--timeout', type=int, default=40)
    probe.add_argument('--out', type=Path, required=True)
    intent = commands.add_parser('intent', help='Print a safely quoted Android share command; never execute ADB')
    intent.add_argument('--manifest', type=Path, required=True)
    intent.add_argument('--platform', choices=PLATFORMS, required=True)
    intent.add_argument('--field', choices=URL_FIELDS, default='video_url')
    intent.add_argument('--serial', required=True)
    intent.add_argument('--package', default='com.jumlo.app.debug')
    args = parser.parse_args()
    try:
        if args.command == 'init':
            write_json_new(args.out, template())
            print(f'Wrote input template: {args.out}')
            return 0
        manifest = load_manifest(args.manifest)
        if args.command == 'plan':
            args.out.parent.mkdir(parents=True, exist_ok=True)
            rows = matrix_rows(manifest)
            with args.out.open('x', newline='', encoding='utf-8') as output:
                writer = csv.DictWriter(output, fieldnames=FIELDS)
                writer.writeheader()
                writer.writerows(rows)
            print(f'Wrote {len(rows)} Android cases, all NOT_RUN: {args.out}')
            return 0
        if args.command == 'intent':
            url = manifest['platforms'][args.platform][args.field]
            validate_url(url)
            if not re.fullmatch(r'[A-Za-z0-9_.:-]{1,128}', args.serial) or not re.fullmatch(r'[A-Za-z][A-Za-z0-9_.]*', args.package):
                raise ValueError('Invalid serial or package')
            remote = shlex.join(['am', 'start', '-W', '-n', f'{args.package}/com.jumlo.app.MainActivity',
                                 '-a', 'android.intent.action.SEND', '-t', 'text/plain',
                                 '--es', 'android.intent.extra.TEXT', url])
            print(shlex.join(['adb', '-s', args.serial, 'shell', remote]))
            return 0
        if not 5 <= args.timeout <= 60:
            raise ValueError('Probe timeout must be between 5 and 60 seconds per URL')
        if args.out.exists():
            raise ValueError('Output already exists; use a new evidence filename')
        identity = extractor_identity()
        result = {'scope': 'HOST_METADATA_ONLY', 'android_matrix_modified': False,
                  'created_utc': datetime.now(timezone.utc).isoformat(), 'extractor': identity,
                  'host_python': platform.python_version(), 'host_os': platform.system(),
                  'host_js_tools_present': {name: bool(shutil.which(name)) for name in ('deno', 'node', 'qjs')},
                  'proxy_environment_present': any(os.environ.get(key) for key in ('HTTPS_PROXY', 'https_proxy', 'ALL_PROXY', 'all_proxy')),
                  'limits': {'seconds_per_url': args.timeout, 'stdout_bytes': 4 * 1024 * 1024, 'stderr_bytes': 64 * 1024,
                             'media_download': False, 'cookies': False}, 'probes': []}
        for name in dict.fromkeys(args.platforms or PLATFORMS):
            url = manifest['platforms'][name][args.field]
            record = {'platform': name, 'field': args.field, 'input_sha256': digest(url) if url else '',
                      'input_host': urlsplit(url).hostname if url else '', 'outcome': 'NOT_RUN_MISSING_URL'}
            if url:
                started = time.monotonic()
                with tempfile.TemporaryDirectory(prefix='jumlo-live-metadata-') as work:
                    environment = {'UU_ALLOW_HTTP': '1', 'PYTHONNOUSERSITE': '1', 'PYTHONDONTWRITEBYTECODE': '1',
                                   'HOME': work, 'XDG_CONFIG_HOME': work, 'XDG_CACHE_HOME': work, 'PYTHONPATH': ''}
                    execution = run_bounded(probe_command(ASSETS / 'bootstrap.py', ASSETS / 'yt-dlp', url),
                                            Path(work), environment, args.timeout)
                record.update(elapsed_seconds=round(time.monotonic() - started, 3), exit_code=execution['exit_code'],
                              outcome=execution['failure'] or ('HOST_EXTRACTOR_ERROR' if execution['exit_code'] else 'HOST_METADATA_RETURNED'),
                              diagnostic=redact(execution['stderr'], url))
                if record['outcome'] == 'HOST_METADATA_RETURNED':
                    try:
                        record['metadata'] = metadata_summary(execution['stdout'])
                    except (ValueError, TypeError, KeyError) as error:
                        record['outcome'] = 'HOST_METADATA_INVALID'
                        record['diagnostic'] = redact(str(error), url)
            result['probes'].append(record)
            print(f'{name}: {record["outcome"]}', flush=True)
        write_json_new(args.out, result)
        print(f'Host-only evidence: {args.out}')
        return 0 if all(row['outcome'] == 'HOST_METADATA_RETURNED' for row in result['probes']) else 1
    except (ValueError, OSError, KeyError) as error:
        parser.error(str(error))


if __name__ == '__main__':
    sys.exit(main())
