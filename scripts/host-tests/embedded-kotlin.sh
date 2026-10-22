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
  local caches compiler stdlib coroutines trove annotations daemon script reflect jdk
  caches="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
  # The project's own Kotlin version first, then whatever else is cached.
  compiler=$(find "$caches" -name 'kotlin-compiler-embeddable-2.0.21.jar' 2>/dev/null | head -1)
  [ -n "$compiler" ] || compiler=$(find "$caches" -name 'kotlin-compiler-embeddable-*.jar' 2>/dev/null | grep -v sources | sort | tail -1)
  # The real stdlib, not the empty -jdk8/-jdk7 wrappers.
  stdlib=$(find "$caches" -path '*kotlin-stdlib/*' -name 'kotlin-stdlib-*.jar' 2>/dev/null | grep -v sources | grep -v -- '-jdk' | sort | tail -1)
  coroutines=$(find "$caches" -name 'kotlinx-coroutines-core-jvm-*.jar' 2>/dev/null | grep -v sources | sort | tail -1)
  trove=$(find "$caches" -iname '*trove4j*.jar' 2>/dev/null | grep -v sources | sort | tail -1)
  annotations=$(find "$caches" -name 'annotations-13.0.jar' 2>/dev/null | sort | tail -1)
  daemon=$(find "$caches" -name 'kotlin-daemon-embeddable-*.jar' 2>/dev/null | sort | tail -1)
  script=$(find "$caches" -name 'kotlin-script-runtime-*.jar' 2>/dev/null | sort | tail -1)
  reflect=$(find "$caches" -name 'kotlin-reflect-*.jar' 2>/dev/null | grep -v sources | sort | tail -1)
  [ -n "$compiler" ] || { echo 'BLOCKED: no Kotlin compiler available' >&2; return 2; }
  jdk=""
  for candidate in "${JAVA_HOME:-}" /usr/lib/jvm/java-17-temurin-jdk /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/temurin-17-jdk-amd64; do
    if [ -x "$candidate/bin/java" ] && "$candidate/bin/java" -version 2>&1 | head -1 | grep -qE '"17\.'; then jdk="$candidate"; break; fi
  done
  [ -n "$jdk" ] || jdk="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  "$jdk/bin/java" -cp "$compiler:$stdlib:$coroutines:$trove:$annotations:$daemon:$script:$reflect" \
    org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "${SOURCES[@]}" -jvm-target 17 \
    -classpath "$stdlib:$coroutines" -d "$BUILD/classes"
  "$jdk/bin/java" -cp "$BUILD/classes:$stdlib:$coroutines" "$MAIN_CLASS"
}
