#!/usr/bin/env bash
set -euo pipefail
# The Unicode filename tests create real filesystem entries. Some CI shells
# start in a POSIX/ASCII locale, where the JVM cannot encode those paths.
if locale -a 2>/dev/null | grep -qiE '^C\.(utf8|UTF-8)$'; then
  export LANG=C.UTF-8 LC_ALL=C.UTF-8
fi
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT
mapfile -t SOURCES < <(find "$ROOT/core-common/src/main/kotlin" "$ROOT/downloader-core/src/main/kotlin" "$ROOT/document-core/src/main/kotlin" "$ROOT/storage-core/src/main/kotlin" "$ROOT/app/src/hostTest/kotlin" -name '*.kt' | sort)
SOURCES+=("$ROOT/app/src/main/java/com/jumlo/app/model/Models.kt" "$ROOT/app/src/main/java/com/jumlo/app/data/engine/PureDomainAlgorithms.kt")

# Prefer the Kotlin CLI. When it is not installed, fall back to the compiler the
# project's own Gradle build already downloaded, so the host suite is not blocked by
# a missing global toolchain. JDK 17 is required: newer JDKs are rejected by the
# embedded compiler's Java version parser.
run_with_cli() {
  kotlinc "${SOURCES[@]}" -jvm-target 17 -include-runtime -d "$BUILD/core-tests.jar"
  java -jar "$BUILD/core-tests.jar"
}
run_with_embedded() {
  local caches compiler kver stdlib coroutines trove annotations daemon script reflect jdk
  caches="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
  # The project's own Kotlin version first, then whatever else is cached.
  compiler=$(find "$caches" -name 'kotlin-compiler-embeddable-2.0.21.jar' 2>/dev/null | head -1 || true)
  [ -n "$compiler" ] || compiler=$(find "$caches" -name 'kotlin-compiler-embeddable-*.jar' 2>/dev/null | grep -v sources | sort | tail -1 || true)
  [ -n "$compiler" ] || { echo 'BLOCKED: no Kotlin compiler available' >&2; return 2; }
  kver=$(basename "$compiler" | sed -E 's/^kotlin-compiler-embeddable-(.+)\.jar$/\1/')
  # Match stdlib and companion Kotlin JARs to the selected compiler version to avoid metadata mismatches.
  stdlib=$(find "$caches" -path '*kotlin-stdlib/*' -name "kotlin-stdlib-${kver}.jar" 2>/dev/null | head -1 || true)
  [ -n "$stdlib" ] || stdlib=$(find "$caches" -path '*kotlin-stdlib/*' -name 'kotlin-stdlib-*.jar' 2>/dev/null | grep -v sources | grep -v -- '-jdk' | sort | tail -1 || true)
  coroutines=$(find "$caches" -name 'kotlinx-coroutines-core-jvm-*.jar' 2>/dev/null | grep -v sources | sort | tail -1 || true)
  trove=$(find "$caches" -iname '*trove4j*.jar' 2>/dev/null | grep -v sources | sort | tail -1 || true)
  annotations=$(find "$caches" -name 'annotations-13.0.jar' 2>/dev/null | sort | tail -1 || true)
  daemon=$(find "$caches" -name "kotlin-daemon-embeddable-${kver}.jar" 2>/dev/null | head -1 || true)
  [ -n "$daemon" ] || daemon=$(find "$caches" -name 'kotlin-daemon-embeddable-*.jar' 2>/dev/null | sort | tail -1 || true)
  script=$(find "$caches" -name "kotlin-script-runtime-${kver}.jar" 2>/dev/null | head -1 || true)
  [ -n "$script" ] || script=$(find "$caches" -name 'kotlin-script-runtime-*.jar' 2>/dev/null | sort | tail -1 || true)
  reflect=$(find "$caches" -name "kotlin-reflect-${kver}.jar" 2>/dev/null | head -1 || true)
  [ -n "$reflect" ] || reflect=$(find "$caches" -name 'kotlin-reflect-*.jar' 2>/dev/null | grep -v sources | sort | tail -1 || true)
  # JDK 17 is preferred: the embedded compiler rejects newer Java version strings.
  jdk=""
  for candidate in "${JAVA_HOME:-}" /usr/lib/jvm/java-17-temurin-jdk /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/temurin-17-jdk-amd64; do
    if [ -x "$candidate/bin/java" ] && "$candidate/bin/java" -version 2>&1 | head -1 | grep -qE '"17\.'; then jdk="$candidate"; break; fi
  done
  [ -n "$jdk" ] || jdk="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  "$jdk/bin/java" -cp "$compiler:$stdlib:$coroutines:$trove:$annotations:$daemon:$script:$reflect" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "${SOURCES[@]}" -jvm-target 17 \
    -classpath "$stdlib:$coroutines" -d "$BUILD/core-tests"
  "$jdk/bin/java" -cp "$BUILD/core-tests:$stdlib:$coroutines" com.jumlo.app.hosttest.CoreTestsKt
}

if command -v kotlinc >/dev/null; then
  run_with_cli
else
  run_with_embedded
fi
