#!/usr/bin/env bash
# Compile Kotlin sources with the compiler the project's own Gradle build
# already downloaded, for hosts where no Kotlin CLI is installed. Keeps the
# host suite unblocked by a missing global toolchain. JDK 17 is required:
# newer JDKs are rejected by the embedded compiler's Java version parser.
#
# Caller sets (same shell): SOURCES (array), BUILD (fresh temp dir),
# MAIN_CLASS (the `<file>Kt` entrypoint). Compiles to "$BUILD/classes" and
# runs the entrypoint on the project's runtime jar set.
embedded_kotlin_run() {
  local caches compiler kver stdlib coroutines trove annotations daemon script reflect jdk
  caches="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
  # The project's own Kotlin version first, then whatever else is cached.
  compiler=$(find "$caches" -name 'kotlin-compiler-embeddable-2.0.21.jar' 2>/dev/null | head -1 || true)
  [ -n "$compiler" ] || compiler=$(find "$caches" -name 'kotlin-compiler-embeddable-*.jar' 2>/dev/null | grep -v sources | sort | tail -1 || true)
  [ -n "$compiler" ] || { echo 'BLOCKED: no Kotlin compiler available' >&2; return 2; }
  kver=$(basename "$compiler" | sed -E 's/^kotlin-compiler-embeddable-(.*)\.jar$/\1/')
  # Match stdlib and compiler support jars to the exact compiler version first to avoid metadata version mismatches.
  stdlib=$(find "$caches" -path "*kotlin-stdlib/*" -name "kotlin-stdlib-${kver}.jar" 2>/dev/null | head -1 || true)
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
  jdk=""
  for candidate in "${JAVA_HOME:-}" /usr/lib/jvm/java-17-temurin-jdk /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/temurin-17-jdk-amd64; do
    if [ -x "$candidate/bin/java" ] && "$candidate/bin/java" -version 2>&1 | head -1 | grep -qE '"17\.'; then jdk="$candidate"; break; fi
  done
  if [ -z "$jdk" ]; then
    local java_bin
    java_bin=$(command -v java || true)
    [ -n "$java_bin" ] || { echo 'BLOCKED: java not found' >&2; return 2; }
    jdk="$(dirname "$(dirname "$(readlink -f "$java_bin")")")"
  fi
  local compiler_cp="" runtime_cp="" jar
  for jar in "$compiler" "$stdlib" "$coroutines" "$trove" "$annotations" "$daemon" "$script" "$reflect"; do
    [ -n "$jar" ] && compiler_cp="${compiler_cp:+${compiler_cp}:}$jar"
  done
  for jar in "$stdlib" "$coroutines"; do
    [ -n "$jar" ] && runtime_cp="${runtime_cp:+${runtime_cp}:}$jar"
  done
  "$jdk/bin/java" -cp "$compiler_cp" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "${SOURCES[@]}" -jvm-target 17 \
    -classpath "$runtime_cp" -d "$BUILD/classes"
  "$jdk/bin/java" -cp "$BUILD/classes${runtime_cp:+:${runtime_cp}}" "$MAIN_CLASS"
}
