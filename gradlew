#!/usr/bin/env bash
# Project launcher: verifies the official pinned wrapper before executing it.
# JAVA_TOOL_OPTIONS is supported by the JVM; shell option strings are never eval'd.
set -euo pipefail
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
EXPECTED=7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172
fail() { printf 'BLOCKED: %s\n' "$*" >&2; exit 2; }
[ -f "$JAR" ] || fail 'WRAPPER_JAR_MISSING. Run scripts/restore-wrapper.sh --download explicitly on a network-enabled machine, or use a trusted local Gradle via scripts/verify-local.sh.'
if command -v sha256sum >/dev/null; then
  ACTUAL=$(sha256sum "$JAR" | cut -d ' ' -f 1)
elif command -v shasum >/dev/null; then
  ACTUAL=$(shasum -a 256 "$JAR" | cut -d ' ' -f 1)
else
  fail 'SHA256_TOOL_MISSING. Cannot execute an unverified wrapper.'
fi
[ "$ACTUAL" = "$EXPECTED" ] || fail 'WRAPPER_INTEGRITY_FAILED. Refusing to execute this JAR.'
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/java}
JAVA=${JAVA:-java}
command -v "$JAVA" >/dev/null || fail 'JAVA_NOT_FOUND. Set JAVA_HOME.'
exec "$JAVA" -Xmx64m -Xms64m "-Dorg.gradle.appname=gradlew" -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
