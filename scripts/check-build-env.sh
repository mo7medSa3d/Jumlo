#!/usr/bin/env bash
# Pre-flight for the Gradle build.
#
# Gradle 8.14 embeds Kotlin 1.9 for the build scripts, and its bundled Java version
# parser rejects JDK 22 and newer. On such a JDK the build dies while compiling
# settings.gradle.kts with the bare message "25.0.4.1" and no hint of the cause.
# This script turns that into one actionable line.
set -euo pipefail

jdk="${JAVA_HOME:-}"
if [ -z "$jdk" ] || [ ! -x "$jdk/bin/java" ]; then
  jdk=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
fi
version=$("$jdk/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)\.([0-9]+).*/\1 \2/')
major=${version%% *}
if [ -n "$major" ] && [ "$major" -gt 21 ] 2>/dev/null; then
  cat >&2 <<MSG
Jumlo must be built with JDK 17 or 21.
Gradle is running on JDK $($jdk/bin/java -version 2>&1 | head -1 | sed -E 's/.*"(.*)".*/\1/').
Gradle 8.14 compiles the build scripts with an embedded Kotlin that cannot parse a
JDK newer than 21, so the build fails before it starts.

Fix: point JAVA_HOME at a JDK 17 or 21 installation, for example
  export JAVA_HOME=/usr/lib/jvm/java-17-temurin-jdk
and run scripts/check-build-env.sh again.
MSG
  exit 1
fi
echo "build environment OK: $($jdk/bin/java -version 2>&1 | head -1 | sed -E 's/.*"(.*)".*/JDK \1/')"
