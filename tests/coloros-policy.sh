#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
test_classes=$(mktemp -d)
trap 'rm -rf "$test_classes"' EXIT HUP INT TERM
if command -v javac >/dev/null 2>&1; then
    javac -d "$test_classes" app/src/main/java/io/github/yixing233/hyperduo/ColorOsPolicy.java tests/ColorOsPolicyTest.java
else
    java -m jdk.compiler/com.sun.tools.javac.Main -d "$test_classes" app/src/main/java/io/github/yixing233/hyperduo/ColorOsPolicy.java tests/ColorOsPolicyTest.java
fi
java -cp "$test_classes" io.github.yixing233.hyperduo.ColorOsPolicyTest
