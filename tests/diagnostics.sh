#!/usr/bin/env sh
# The diagnostics block is plain text a user pastes into a report; its shape is a contract
# with whoever reads the report, so it is checked like one.
set -eu
cd "$(dirname "$0")/.."
test_classes=$(mktemp -d)
trap 'rm -rf "$test_classes"' EXIT HUP INT TERM
sources=$(mktemp)
trap 'rm -rf "$test_classes"; rm -f "$sources"' EXIT HUP INT TERM
find tests/shims -name '*.java' > "$sources"
for name in Prefs TrioSettings ChargeLayout DiagnosticsReport HookReport; do
    printf '%s\n' "app/src/main/java/io/github/yixing233/hyperduo/$name.java" >> "$sources"
done
printf '%s\n' tests/DiagnosticsTest.java tests/HookReportTest.java >> "$sources"
if command -v javac >/dev/null 2>&1; then
    javac -encoding UTF-8 -d "$test_classes" @"$sources"
else
    java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$test_classes" @"$sources"
fi
java -Djava.awt.headless=true -cp "$test_classes" io.github.yixing233.hyperduo.DiagnosticsTest
java -Djava.awt.headless=true -cp "$test_classes" io.github.yixing233.hyperduo.HookReportTest
