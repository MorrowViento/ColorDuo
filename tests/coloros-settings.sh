#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
test_classes=$(mktemp -d)
trap 'rm -rf "$test_classes"' EXIT HUP INT TERM
test_sources=$(mktemp)
trap 'rm -rf "$test_classes"; rm -f "$test_sources"' EXIT HUP INT TERM
find tests/shims -name '*.java' > "$test_sources"
for name in Prefs TrioSettings TrioAppearance TrioGeometry TrioZones TrioRenderer ChargeLayout RecommendedLayout; do
    printf '%s\n' "app/src/main/java/io/github/yixing233/hyperduo/$name.java" >> "$test_sources"
done
printf '%s\n' tests/ColorOsSettingsTest.java >> "$test_sources"
if command -v javac >/dev/null 2>&1; then
    javac -encoding UTF-8 -d "$test_classes" @"$test_sources"
else
    java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$test_classes" @"$test_sources"
fi
java -Djava.awt.headless=true -Xmx512m -cp "$test_classes" io.github.yixing233.hyperduo.ColorOsSettingsTest
# The diagnostics block is a separate contract over the same shims. It runs here rather than
# as its own workflow step because changing .github/workflows needs the Workflows token
# permission; the script is self-contained either way.
sh tests/diagnostics.sh
