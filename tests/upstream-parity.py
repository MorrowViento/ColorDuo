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
            b"            // Normalize ROM input units before the shared upstream calculation.\n"
            b"            if (ColorOsHooks.isInstalled()) current = ColorOsHooks.batteryCurrentMicroAmps(current, voltage);\n",
            b"            final int current = currentNow(context);\n")
    return data


try:
    files = git("ls-tree", "-r", "--name-only", UPSTREAM, "--", SOURCE).decode().splitlines()
except subprocess.CalledProcessError:
    sys.exit(f"Missing upstream baseline {UPSTREAM}; use a checkout with full history.")
checked = 0
failures = []
for path in files:
    name = path.removeprefix(SOURCE)
    if name in PLATFORM_FILES:
        continue
    upstream = git("show", f"{UPSTREAM}:{path}")
    current = (ROOT / path).read_bytes()
    if normalize(name, current) != upstream:
        failures.append(path)
    checked += 1
if failures:
    sys.exit("Shared behavior differs from upstream:\n" + "\n".join(failures))
print(f"Upstream parity passed: {checked} shared source files match {UPSTREAM[:7]}")
