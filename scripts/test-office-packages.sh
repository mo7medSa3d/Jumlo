#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT
KOTLIN_LIB="${JUMLO_KOTLIN_LIB:-}"
if [ -z "$KOTLIN_LIB" ]; then
  KOTLIN_COMPILER=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}" -name 'kotlin-compiler-embeddable-*.jar' 2>/dev/null | head -1)
  if [ -n "$KOTLIN_COMPILER" ]; then KOTLIN_LIB=$(dirname "$KOTLIN_COMPILER"); fi
fi
if [ -z "$KOTLIN_LIB" ]; then
  echo 'BLOCKED: set JUMLO_KOTLIN_LIB to a Kotlin compiler/Gradle lib directory' >&2
  exit 2
fi
STDLIB=$(find "$KOTLIN_LIB" -name 'kotlin-stdlib-*.jar' | head -1)
java -cp "$KOTLIN_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  "$ROOT/document-core/src/main/kotlin/com/jumlo/app/core/office/OfficePackageWriter.kt" \
  "$ROOT/document-core/src/main/kotlin/com/jumlo/app/core/office/DocumentRenderPolicy.kt" \
  "$ROOT/document-core/src/main/kotlin/com/jumlo/app/core/office/OfficeTextLayout.kt" \
  "$ROOT/app/src/officeTest/kotlin/OfficePackageHostTests.kt" \
  -no-stdlib -no-reflect -jvm-target 17 -classpath "$STDLIB" -d "$BUILD/classes"
java -cp "$BUILD/classes:$STDLIB" com.jumlo.app.officetest.OfficePackageHostTestsKt "$@"
