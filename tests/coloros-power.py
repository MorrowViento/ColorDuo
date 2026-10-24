#!/usr/bin/env python3
"""Run the device-unit regression through the real upstream calculation and formatter."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "app/src/main/java/io/github/yixing233/hyperduo"
battery = (SOURCE / "BatteryPresence.java").read_text()
hooks = (SOURCE / "TrioHooks.java").read_text()


def method(source, name):
    pattern = rf"^    (?:private )?static [\w\[\]]+ {re.escape(name)}\([^\n]*\) \{{.*?^    \}}"
    match = re.search(pattern, source, re.M | re.S)
    if not match:
        raise RuntimeError(f"Production method missing: {name}")
    return match.group()


separator = re.search(r"static final char PERF_BREAK = ('[^']+');", hooks)
if not separator:
    raise RuntimeError("Production separator missing")
methods = "\n".join((method(battery, "wattsOf"), method(hooks, "powerText"), method(hooks, "perfText")))
harness = r'''
package io.github.yixing233.hyperduo;

public final class ColorOsPowerTest {
    static class TrioState {
        static final int TEMP_UNKNOWN = Integer.MIN_VALUE;
        static final float POWER_UNKNOWN = Float.NaN;
        static boolean sChargingAttached;
        static float sChargePowerW;
        static int sBatteryTempC = TEMP_UNKNOWN;
    }
    static class TrioAppearance {
        boolean perfPower = true;
        boolean perfTemp;
        boolean perfTempChargingOnly;
        boolean perfAnywhere() { return perfPower || perfTemp; }
    }
    static final char PERF_BREAK = @SEPARATOR@;

@METHODS@

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String firmware = "OnePlus/PJZ110/OP5D0DL1:17/CP2A.260605.016/V.487427a-2227f32-224bc4c:user/release-keys";
        // User's raw Binder result: low word fffffe10, high word ffffffff = -496 mA.
        int current = ColorOsPolicy.batteryCurrentMicroAmps(firmware, -496);
        check(current == -496000, "verified mA input must become uA exactly once");
        float watts = wattsOf(current, 4465);
        check(Math.abs(watts - 2.21464f) < 0.00001f, "real sample must produce about 2.2 W");
        check(powerText(watts).equals("2.2W"), "fixed sample uses upstream decimal formatting");
        check(powerText(wattsOf(-496, 4465)).equals("0.0W"), "fixture reproduces alpha3's unit error");

        TrioAppearance appearance = new TrioAppearance();
        TrioState.sChargingAttached = true;
        TrioState.sChargePowerW = watts;
        check(perfText(appearance).equals("2.2W"), "FULL with an attached source retains the real reading");
        TrioState.sBatteryTempC = 32;
        appearance.perfTemp = true;
        check(perfText(appearance).equals("2.2W\n32\u00B0C"), "power and temperature retain upstream two-line formatting");
        TrioState.sChargingAttached = false;
        check(perfText(appearance).equals("32\u00B0C"), "unplugging removes the power segment");
        TrioState.sChargingAttached = true;
        appearance.perfTemp = false;
        TrioState.sChargePowerW = wattsOf(ColorOsPolicy.batteryCurrentMicroAmps(firmware, 0), 4465);
        check(perfText(appearance).equals("0.0W"), "a real zero must remain a real zero");

        int unknown = ColorOsPolicy.batteryCurrentMicroAmps(firmware, Integer.MIN_VALUE);
        check(unknown == Integer.MIN_VALUE, "unsupported current keeps its sentinel");
        TrioState.sChargePowerW = wattsOf(unknown, 4465);
        check(Float.isNaN(TrioState.sChargePowerW) && perfText(appearance).isEmpty(),
                "unsupported current must not display a false zero");
        check(Float.isNaN(wattsOf(current, 0)), "unknown voltage stays unknown");
        check(ColorOsPolicy.batteryCurrentMicroAmps("different firmware", -496000) == -496000,
                "unverified builds retain the Android uA contract");
        check(ColorOsPolicy.batteryCurrentMicroAmps(firmware.replace("V.487427a", "V.new"), -496000) == -496000,
                "future firmware must not inherit an unverified multiplier");
        check(ColorOsPolicy.batteryCurrentMicroAmps(firmware, Integer.MAX_VALUE) == Integer.MIN_VALUE,
                "overflow is unavailable rather than a fabricated power reading");
        check(wattsOf(ColorOsPolicy.batteryCurrentMicroAmps(firmware, 496), 4465) == watts,
                "the upstream formula handles either current sign");
        System.out.println("ColorOS measured-current normalization and upstream power rendering passed");
    }
}
'''
harness = harness.replace("@SEPARATOR@", separator.group(1)).replace("@METHODS@", methods)
with tempfile.TemporaryDirectory(prefix="colorduo-power-") as directory:
    test_source = Path(directory) / "ColorOsPowerTest.java"
    test_source.write_text(harness)
    compiler = [shutil.which("javac")] if shutil.which("javac") else ["java", "-m", "jdk.compiler/com.sun.tools.javac.Main"]
    subprocess.run(compiler + ["-encoding", "UTF-8", "-d", directory,
                              str(SOURCE / "Refl.java"), str(SOURCE / "ColorOsPolicy.java"),
                              str(test_source)], check=True)
    subprocess.run(["java", "-cp", directory, "io.github.yixing233.hyperduo.ColorOsPowerTest"], check=True)
