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
jdk=""
for candidate in "${JAVA_HOME:-}" /usr/lib/jvm/java-17-temurin-jdk /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/temurin-17-jdk-amd64; do
  if [ -x "$candidate/bin/java" ] && "$candidate/bin/java" -version 2>&1 | head -1 | grep -qE '"17\.'; then jdk="$candidate"; break; fi
done
[ -n "$jdk" ] || jdk="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"

STDLIB=$(find "$KOTLIN_LIB" -name 'kotlin-stdlib-*.jar' | head -1)
"$jdk/bin/java" -cp "$KOTLIN_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  "$ROOT/document-core/src/main/kotlin/com/jumlo/app/core/office/OfficeDocument.kt" \
  "$ROOT/document-core/src/main/kotlin/com/jumlo/app/core/office/OfficePackageWriter.kt" \
  "$ROOT/document-core/src/main/kotlin/com/jumlo/app/core/office/DocumentRenderPolicy.kt" \
  "$ROOT/document-core/src/main/kotlin/com/jumlo/app/core/office/OfficeTextLayout.kt" \
  "$ROOT/app/src/officeTest/kotlin/OfficePackageHostTests.kt" \
  -no-stdlib -no-reflect -jvm-target 17 -classpath "$STDLIB" -d "$BUILD/classes"
"$jdk/bin/java" -cp "$BUILD/classes:$STDLIB" com.jumlo.app.officetest.OfficePackageHostTestsKt "$@"
