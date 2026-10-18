#!/usr/bin/env python3
"""Capture honest, reproducible local checks without downloads or toolchain installation."""
import json
import os
from pathlib import Path
import shutil
import subprocess
from datetime import datetime, timezone

ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'verification/final'
DEST.mkdir(parents=True,exist_ok=True)

def capture(command):
    try:
        result=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=180)
        return result.returncode,result.stdout
    except FileNotFoundError:
        return 127,'BLOCKED: executable unavailable\n'
    except subprocess.TimeoutExpired as error:
        text=error.stdout or b''
        if isinstance(text,bytes): text=text.decode(errors='replace')
        return 124,text+'\nBLOCKED: verification timeout; not a passing check\n'

java=capture(['java','-version'])[1]
kotlin=capture(['kotlinc','-version'])[1]
environment={
    'recorded_at_utc':datetime.now(timezone.utc).isoformat(),
    'java_version_output':java.strip(),
    'kotlin_cli_version_output':kotlin.strip(),
    'gradle_on_path':shutil.which('gradle') is not None,
    'sdkmanager_on_path':shutil.which('sdkmanager') is not None,
    'adb_on_path':shutil.which('adb') is not None,
    'android_sdk_env_configured':bool(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')),
    'official_wrapper_jar_present':(ROOT/'gradle/wrapper/gradle-wrapper.jar').is_file(),
    'android_build_baseline':'JDK 17 / Gradle 8.14 / AGP 8.10.1 / SDK 36 / Build Tools 36.0.0',
    'note':'These observations concern this runner, not the user machine. No toolchains installed.'
}
(DEST/'environment.json').write_text(json.dumps(environment,indent=2)+'\n')
cases=[
 ('core-domain-http',['bash','scripts/test-core.sh'],'Production dependency-free Kotlin/domain sources and real loopback HTTP'),
 ('storage-sql',['python3','scripts/test-storage-sql.py'],'Exact production DAO/migration SQL in host SQLite; not Room'),
 ('source-static',['python3','scripts/test-source-static.py'],'Narrow static consistency checks; not Android lint/compilation'),
 ('offline-preflight',['bash','scripts/tests/test-verify-local.sh'],'Offline/no-download preflight regression'),
 ('launcher-safety',['bash','scripts/tests/test-launcher.sh'],'Missing/unverified JAR cannot execute JVM'),
 ('assemble-debug',['bash','scripts/verify-local.sh',':app:assembleDebug'],'Android build requested through offline preflight'),
 ('gradle-unit-tests',['bash','scripts/verify-local.sh',':app:testDebugUnitTest'],'Gradle-managed JVM unit tests requested through offline preflight'),
 ('android-lint',['bash','scripts/verify-local.sh',':app:lintDebug'],'Android lint requested through offline preflight'),
 ('android-instrumentation',['bash','scripts/verify-local.sh',':app:connectedDebugAndroidTest'],'Device tests requested through offline preflight'),
 ('wrapper-launch',['bash','gradlew','--offline','--version'],'Wrapper launch requested; integrity check precedes execution')
]
# A source ZIP intentionally has no repository metadata. Do not turn its absence
# into a misleading test failure, or accidentally inspect a parent repository.
if (ROOT/'.git').exists():
    cases.append(('source-whitespace',
        ['bash', '-c', 'git diff --check -- app scripts gradlew gradlew.bat gradle build.gradle.kts settings.gradle.kts && git diff --cached --check -- app scripts gradlew gradlew.bat gradle build.gradle.kts settings.gradle.kts'],
        'Staged and unstaged source diff whitespace only; excludes documentation/evidence'))
records=[]
for name,command,scope in cases:
    started=datetime.now(timezone.utc).isoformat()
    code,output=capture(command)
    status='PASS' if code==0 else ('BLOCKED' if 'BLOCKED:' in output else 'FAIL')
    log=f'command: {json.dumps(command)}\nstarted_at_utc: {started}\nscope: {scope}\n\n{output}\nexit_code: {code}\nstatus: {status}\n'
    (DEST/f'{name}.log').write_text(log)
    records.append(dict(name=name,command=command,scope=scope,exit_code=code,status=status,log=f'verification/final/{name}.log'))
    print(f'{name}: {status} (exit {code})',flush=True)
if not (ROOT/'.git').exists():
    message='NOT_APPLICABLE: this source archive contains no Git metadata; no diff check was run.\n'
    (DEST/'source-whitespace.log').write_text(message)
    records.append(dict(name='source-whitespace', command=None,
        scope='Git-only diff check, not applicable to extracted source archives',
        exit_code=None, status='NOT_APPLICABLE', log='verification/final/source-whitespace.log'))
    print('source-whitespace: NOT_APPLICABLE (source archive)', flush=True)
(DEST/'results.json').write_text(json.dumps(records,indent=2)+'\n')
if any(x['status']=='FAIL' for x in records):
    raise SystemExit(1)
if any(x['status']=='BLOCKED' for x in records):
    raise SystemExit(2)
# Exit 0 is reserved for a run with no failures and no blocked checks.
