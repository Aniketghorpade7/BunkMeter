#!/usr/bin/env bash
#
# run-logic-tests.sh — Run BunkMeter's Android-free unit tests on the plain JVM,
# without Gradle.
#
# WHY THIS EXISTS
#   The Android Gradle Plugin's build can fail under a too-new JDK: its
#   JdkImageTransform/`jlink` step chokes on `core-for-system-modules.jar`
#   (seen with JDK 24+/26). That blocks `./gradlew testDebugUnitTest` before any
#   source compiles. But the pure decision logic — AttendanceLogic, DateUtils —
#   has NO Android dependencies, so we can compile and run its JUnit tests
#   directly. Any JDK works here; the jlink issue is specific to AGP, not to
#   plain `javac`/`java`.
#
# USAGE
#   ./scripts/run-logic-tests.sh
#
# ADDING A TEST
#   Add its fully-qualified class name to TEST_CLASSES below. Only tests whose
#   code (and the classes they touch) use NO Android APIs can run here — anything
#   needing Android/Robolectric must go through Gradle on a JDK 17/21.
#
# OVERRIDES (optional env vars)
#   JUNIT_JAR, HAMCREST_JAR   explicit jar paths (skip the Gradle-cache search)
#   GRADLE_USER_HOME          where to look for the cached jars (default ~/.gradle)

set -euo pipefail

# --- locate the repo (works from any cwd) ----------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

MAIN_SRC="app/src/main/java"
TEST_SRC="app/src/test/java"

# --- host-side test classes (Android-free only) ----------------------------
TEST_CLASSES=(
  com.bunkmeter.app.AttendanceLogicTest
  com.bunkmeter.app.DateUtilsTest
  com.bunkmeter.app.ExampleUnitTest
)

# --- find JUnit 4 + Hamcrest (from the Gradle cache, unless overridden) -----
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
JUNIT_JAR="${JUNIT_JAR:-$(find "$GRADLE_HOME/caches" -name 'junit-4*.jar' 2>/dev/null | grep -Ev 'sources|javadoc' | head -n1 || true)}"
HAMCREST_JAR="${HAMCREST_JAR:-$(find "$GRADLE_HOME/caches" -name 'hamcrest-core-*.jar' 2>/dev/null | grep -Ev 'sources|javadoc' | head -n1 || true)}"

if [[ -z "$JUNIT_JAR" || -z "$HAMCREST_JAR" ]]; then
  echo "ERROR: could not find JUnit 4 / Hamcrest jars under $GRADLE_HOME/caches" >&2
  echo "  Run a Gradle sync once to populate the cache, or set JUNIT_JAR / HAMCREST_JAR." >&2
  exit 1
fi

CP="$JUNIT_JAR:$HAMCREST_JAR"

# --- build dir (temp, auto-cleaned) ----------------------------------------
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT

# --- map class names -> test source files ----------------------------------
JAVA_FILES=()
for cls in "${TEST_CLASSES[@]}"; do
  JAVA_FILES+=("$TEST_SRC/${cls//.//}.java")
done

echo "JDK:      $(java -version 2>&1 | head -n1)"
echo "JUnit:    $JUNIT_JAR"
echo "Hamcrest: $HAMCREST_JAR"
echo

# Compile the tests; -sourcepath lets javac pull in the referenced main sources
# (AttendanceLogic, DateUtils, ...) on demand — no need to list them here.
echo "== compiling =="
javac -d "$BUILD_DIR" -cp "$CP" -sourcepath "$MAIN_SRC:$TEST_SRC" "${JAVA_FILES[@]}"

echo
echo "== running =="
java -cp "$BUILD_DIR:$CP" org.junit.runner.JUnitCore "${TEST_CLASSES[@]}"
