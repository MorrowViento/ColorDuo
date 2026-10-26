#!/usr/bin/env python3
"""Guard the shared product behavior against the verified upstream commit."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM = "e9dffbcc8f65a5f3dc379c0b25ccdc0462a8a28d"
SOURCE = "app/src/main/java/io/github/yixing233/hyperduo/"
# These files implement ROM/framework integration or the fork's update address.
PLATFORM_FILES = {"HyperDuoModule.java", "WifiPresence.java", "ui/UpdateController.kt"}
# Files ColorDuo deliberately extends beyond upstream: the per-scene charging arrangement
# (see ChargeLayout.java) needs storage keys and snapshot fields, and the drawing screen
# has to edit them. Everything else - rendering, geometry, zones, appearance - stays
# byte-compared to the pinned upstream commit, so an upstream change there still fails.
DIVERGED_FILES = {"Prefs.java", "TrioSettings.java"}
EXPOSED_HELPERS = (
    "static final class OutTypeLabel",
    "static final class PerfLabel",
    "static void applyOutTypeText",
    "static String perfText",
    "static float perfTextSize",
    "static int perfWidth",
    "static float perfLineSpacing",
    "static int[] outRingDropOrder",
    "static int outGapFor",
    "static int[] outLabelMargins",
)


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def normalize(name, data):
    # Permit identity, hook dispatch/access and ROM input-unit normalization. No rendering, defaults,
    # layout, preview, signal-selection or settings-migration changes are permitted.
    if name == "Prefs.java":
        data = data.replace(b"io.github.chorusfruit_233.colorduo.action.RELOAD",
                            b"io.github.yixing233.hyperduo.action.RELOAD")
    if name == "ui/SettingsScreen.kt":
        data = data.replace(b'MainActivity::class.java.name + "Alias"',
                            b'context.packageName + ".ui.MainActivityAlias"')
    if name == "TrioHooks.java":
        data = data.replace(b'private static final String TAG = "ColorDuo";',
                            b'private static final String TAG = "HyperDuo";')
        data = data.replace(b"    static void useLogger(XposedModule module) { sModule = module; }\n\n", b"")
        data = data.replace(
            b"        if (ColorOsHooks.isInstalled()) {\n"
            b"            ColorOsHooks.invalidateHosts();\n"
            b"            return;\n"
            b"        }\n", b"")
        for signature in EXPOSED_HELPERS:
            data = data.replace(("    " + signature).encode(),
                                ("    private " + signature).encode())
    if name == "BatteryPresence.java":
        data = data.replace(
            b"            int current = currentNow(context);\n"
            b"            int powerVoltage = voltage;\n"
            b"            // Normalize ROM input units and pack voltage before the shared calculation.\n"
            b"            if (ColorOsHooks.isInstalled()) {\n"
            b"                ColorOsBattery.Sample sample = ColorOsHooks.batterySample(current, voltage);\n"
            b"                current = sample.currentMicroAmps;\n"
            b"                powerVoltage = sample.voltageMilliVolts;\n"
            b"            }\n"
            b"            final float watts = wattsOf(current, powerVoltage);\n",
            b"            final int current = currentNow(context);\n"
            b"            final float watts = wattsOf(current, voltage);\n")
    return data


try:
    files = git("ls-tree", "-r", "--name-only", UPSTREAM, "--", SOURCE).decode().splitlines()
except subprocess.CalledProcessError:
    sys.exit(f"Missing upstream baseline {UPSTREAM}; use a checkout with full history.")
checked = 0
failures = []
for path in files:
    name = path.removeprefix(SOURCE)
    if name in PLATFORM_FILES or name in DIVERGED_FILES:
        continue
    upstream = git("show", f"{UPSTREAM}:{path}")
    current = (ROOT / path).read_bytes()
    if normalize(name, current) != upstream:
        failures.append(path)
    checked += 1
if failures:
    sys.exit("Shared behavior differs from upstream:\n" + "\n".join(failures))
print(f"Upstream parity passed: {checked} shared source files match {UPSTREAM[:7]}")
