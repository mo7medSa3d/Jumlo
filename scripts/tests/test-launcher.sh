#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
D=$(mktemp -d)
trap 'rm -rf "$D"' EXIT
mkdir -p "$D/project with spaces/gradle/wrapper" "$D/java/bin"
cp "$ROOT/gradlew" "$D/project with spaces/gradlew"
cat > "$D/java/bin/java" <<'JAVA'
#!/usr/bin/env sh
echo EXECUTED_UNTRUSTED_JAVA
exit 99
JAVA
chmod +x "$D/java/bin/java"
set +e
OUT=$(cd /; JAVA_HOME="$D/java" bash "$D/project with spaces/gradlew" --version 2>&1)
CODE=$?
set -e
if ! { [ "$CODE" -eq 2 ] && [[ "$OUT" == *WRAPPER_JAR_MISSING* ]] && [[ "$OUT" != *EXECUTED_UNTRUSTED_JAVA* ]]; }; then
  echo "FAIL missing wrapper launched Java or returned wrong error: code=$CODE $OUT"; exit 1
fi
echo 'PASS missing wrapper fails before JVM or downloads'
printf 'not an official binary' > "$D/project with spaces/gradle/wrapper/gradle-wrapper.jar"
set +e
OUT=$(cd /; JAVA_HOME="$D/java" bash "$D/project with spaces/gradlew" --version 2>&1)
CODE=$?
set -e
if ! { [ "$CODE" -eq 2 ] && [[ "$OUT" == *WRAPPER_INTEGRITY_FAILED* ]] && [[ "$OUT" != *EXECUTED_UNTRUSTED_JAVA* ]]; }; then
  echo "FAIL invalid wrapper launched Java or returned wrong error: code=$CODE $OUT"; exit 1
fi
echo 'PASS invalid wrapper fails before JVM execution from a spaced path'
