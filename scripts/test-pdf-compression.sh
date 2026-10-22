#!/usr/bin/env bash
set -euo pipefail
# The PDF compression path, proven on a full JDK.
#
# The Android unit-test source set replaces the JDK with android.jar, which has
# no javax.imageio, so BitmapFactory returns null there and the JPEG path could
# not be tested at all. This suite runs the same compressor - the byte-level
# codec seam - with an ImageIO codec on a real 300 DPI scan, with no device.
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT

SOURCES=(
  "$ROOT/app/src/main/java/com/jumlo/app/core/PdfPageGeometry.kt"
  "$ROOT/app/src/main/java/com/jumlo/app/data/pdf/PdfImageCodec.kt"
  "$ROOT/app/src/main/java/com/jumlo/app/data/pdf/PdfImageCompressor.kt"
  "$ROOT/app/src/pdfCompressionTest/kotlin/PdfCompressionHostTests.kt"
  "$ROOT/app/src/pdfCompressionTest/kotlin/PdfCompressionMeasure.kt"
)

caches="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
pdfbox=$(find "$caches" -name 'pdfbox-android-*-runtime.jar' 2>/dev/null | sort | tail -1)
commons=$(find "$caches" -name 'commons-logging-1.2.jar' 2>/dev/null | sort | tail -1)
if [ -z "$pdfbox" ]; then
  echo 'BLOCKED: the pdfbox-android runtime jar is not in the Gradle cache' >&2
  exit 2
fi
CLASSPATH="$pdfbox${commons:+:$commons}"

jdk=""
for candidate in "${JAVA_HOME:-}" /usr/lib/jvm/java-17-temurin-jdk /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/temurin-17-jdk-amd64; do
  if [ -x "$candidate/bin/java" ] && "$candidate/bin/java" -version 2>&1 | head -1 | grep -qE '"17\.'; then jdk="$candidate"; break; fi
done
[ -n "$jdk" ] || jdk="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"

compiler=$(find "$caches" -name 'kotlin-compiler-embeddable-2.0.21.jar' 2>/dev/null | head -1)
[ -n "$compiler" ] || compiler=$(find "$caches" -name 'kotlin-compiler-embeddable-*.jar' 2>/dev/null | grep -v sources | sort | tail -1)
stdlib=$(find "$caches" -path '*kotlin-stdlib/*' -name 'kotlin-stdlib-*.jar' 2>/dev/null | grep -v sources | grep -v -- '-jdk' | sort | tail -1)
coroutines=$(find "$caches" -name 'kotlinx-coroutines-core-jvm-*.jar' 2>/dev/null | grep -v sources | sort | tail -1)
trove=$(find "$caches" -iname '*trove4j*.jar' 2>/dev/null | grep -v sources | sort | tail -1)
annotations=$(find "$caches" -name 'annotations-13.0.jar' 2>/dev/null | sort | tail -1)
daemon=$(find "$caches" -name 'kotlin-daemon-embeddable-*.jar' 2>/dev/null | sort | tail -1)
script=$(find "$caches" -name 'kotlin-script-runtime-*.jar' 2>/dev/null | sort | tail -1)
reflect=$(find "$caches" -name 'kotlin-reflect-*.jar' 2>/dev/null | grep -v sources | sort | tail -1)
[ -n "$compiler" ] || { echo 'BLOCKED: no Kotlin compiler available' >&2; exit 2; }

"$jdk/bin/java" -cp "$compiler:$stdlib:$coroutines:$trove:$annotations:$daemon:$script:$reflect" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "${SOURCES[@]}" -jvm-target 17 \
  -classpath "$CLASSPATH:$stdlib:$coroutines" -d "$BUILD/pdf-compression-tests"
if [ "${1:-}" = "--measure" ]; then
  ( cd "$ROOT" && "$jdk/bin/java" -cp "$BUILD/pdf-compression-tests:$CLASSPATH:$stdlib:$coroutines" com.jumlo.app.hosttest.PdfCompressionMeasureKt )
else
  "$jdk/bin/java" -cp "$BUILD/pdf-compression-tests:$CLASSPATH:$stdlib:$coroutines" com.jumlo.app.hosttest.PdfCompressionHostTestsKt
fi
