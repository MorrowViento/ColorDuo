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
    private static final class Nodes implements ColorOsBattery.Reader {
        String cells;
        String pack;
        int cellReads;
        int packReads;
        boolean denied;

        public String read(String path) throws java.io.IOException {
            if (path.equals(ColorOsBattery.CELLS_PATH)) cellReads++;
            else if (path.equals(ColorOsBattery.PACK_VOLTAGE_PATH)) packReads++;
            else throw new AssertionError("Unexpected battery path: " + path);
            if (denied) throw new java.io.IOException("permission denied");
            return path.equals(ColorOsBattery.CELLS_PATH) ? cells : pack;
        }
    }

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

        Nodes nodes = new Nodes();
        nodes.cells = "4,2,0,0,654,4450,4470\n";
        ColorOsBattery sampler = new ColorOsBattery(nodes);
        ColorOsBattery.Sample dual = sampler.sample(firmware, -496, 4465, 0);
        check(dual.seriesCells == 2 && dual.cellsSource.equals("aging_ffc_data"),
                "kernel gauge metadata detects a two-cell series pack");
        check(dual.currentMicroAmps == -496000 && dual.voltageMilliVolts == 8930,
                "mA conversion and series-cell voltage correction happen independently, once each");
        check(dual.voltageSource.equals("single_cell_x2"), "the fallback records its voltage basis");
        check(Math.abs(wattsOf(dual.currentMicroAmps, dual.voltageMilliVolts) - 4.42928f) < 0.00001f,
                "the dual-cell sample represents total battery-side power");
        check(powerText(wattsOf(dual.currentMicroAmps, dual.voltageMilliVolts)).equals("4.4W"),
                "dual-cell input retains the original power formatter");

        Nodes actual = new Nodes();
        actual.cells = "0,2,0,0,655,4530,4470,4500,4455\n";
        // Device voltage_now: 4434000 uV = the framework's 4434 mV input.
        // bms/voltage_now was unavailable. Current and voltage were separate probes.
        // Three bracketed CURRENT_NOW calls matched the -753 mA gauge reading.
        ColorOsBattery.Sample device = new ColorOsBattery(actual).sample(firmware, -753, 4434, 0);
        check(device.seriesCells == 2 && device.cellsSource.equals("aging_ffc_data"),
                "the actual Android 17 aging_ffc_data record detects two series cells");
        check(device.voltageMilliVolts == 8868 && device.voltageSource.equals("single_cell_x2"),
                "the actual single-cell voltage is doubled once when BMS voltage is unavailable");
        check(device.currentMicroAmps == -753000, "the matched API/gauge current only changes units");
        check(Math.abs(wattsOf(device.currentMicroAmps, device.voltageMilliVolts) - 6.677604f) < 0.00001f,
                "verified device input fixtures account for the entire series pack");
        check(powerText(wattsOf(device.currentMicroAmps, device.voltageMilliVolts)).equals("6.7W"),
                "verified inputs keep the shared decimal format");
        check(powerText(wattsOf(device.currentMicroAmps, 4434)).equals("3.3W"),
                "fixture reproduces the previous single-cell undercount");

        ColorOsBattery.Sample alreadyPack = sampler.sample(firmware, -496, 8930, 3000);
        check(alreadyPack.voltageMilliVolts == 8930 && nodes.packReads == 1,
                "an existing pack voltage is not doubled or queried again");
        check(alreadyPack.voltageSource.equals("broadcast_pack"), "pack basis is logged");

        nodes.pack = "8920000\n";
        ColorOsBattery.Sample measured = sampler.sample(firmware, -496, 4465, 60000);
        check(measured.voltageMilliVolts == 8920 && measured.voltageSource.equals("bms_pack"),
                "a readable real pack voltage takes precedence over twice the maximum cell voltage");
        check(nodes.cellReads == 1, "validated hardware topology is cached");

        Nodes one = new Nodes();
        one.cells = "0,1,0,-1,654";
        ColorOsBattery.Sample single = new ColorOsBattery(one).sample(firmware, -496, 4465, 0);
        check(single.seriesCells == 1 && single.voltageMilliVolts == 4465 && one.packReads == 0,
                "one-cell gauge metadata overrides firmware fallback without changing the voltage");
        ColorOsBattery.Sample parallel = new ColorOsBattery(one).sample("other firmware", -992000, 4465, 0);
        check(powerText(wattsOf(parallel.currentMicroAmps, parallel.voltageMilliVolts)).equals("4.4W"),
                "a parallel pack with summed current is not multiplied as two series cells");

        Nodes singleBms = new Nodes();
        singleBms.cells = "1,2,0,0,654";
        singleBms.pack = "4463000";
        check(new ColorOsBattery(singleBms).sample(firmware, -496, 4465, 0).voltageMilliVolts == 8930,
                "a BMS reporting only one cell is not mistaken for total pack voltage");

        Nodes denied = new Nodes();
        denied.denied = true;
        ColorOsBattery fallback = new ColorOsBattery(denied);
        ColorOsBattery.Sample known = fallback.sample(firmware, -496, 4465, 0);
        check(known.seriesCells == 2 && known.cellsSource.equals("firmware")
                && known.voltageMilliVolts == 8930, "known OnePlus firmware survives inaccessible sysfs");
        fallback.sample(firmware, -496, 4465, 3000);
        check(denied.cellReads == 1 && denied.packReads == 1,
                "denied nodes are rate-limited instead of retried on every battery sample");
        fallback.sample(firmware, -496, 4465, 60000);
        check(denied.cellReads == 2 && denied.packReads == 2, "unavailable nodes can recover after a minute");
        ColorOsBattery.Sample unverified = new ColorOsBattery(denied).sample("other firmware", -496000, 4465, 0);
        check(unverified.seriesCells == 0 && unverified.currentMicroAmps == -496000
                && unverified.voltageMilliVolts == 4465, "unknown hardware keeps Android input semantics");

        Nodes recovering = new Nodes();
        ColorOsBattery late = new ColorOsBattery(recovering);
        check(late.sample("other firmware", -496000, 4465, 0).seriesCells == 0, "missing topology is unknown");
        recovering.cells = "1,2,0,0,654";
        check(late.sample("other firmware", -496000, 4465, 3000).seriesCells == 0, "probe cooldown is respected");
        check(late.sample("other firmware", -496000, 4465, 60000).voltageMilliVolts == 8930,
                "a late hardware node can activate dual-cell correction");

        Nodes initializing = new Nodes();
        initializing.cells = "0,1,0,0,0";
        ColorOsBattery boot = new ColorOsBattery(initializing);
        check(boot.sample("other firmware", -496000, 4465, 0).seriesCells == 1,
                "the initial driver fallback is accepted conservatively");
        initializing.cells = "0,2,0,0,0";
        check(boot.sample("other firmware", -496000, 4465, 60000).seriesCells == 2,
                "the driver's startup one-cell fallback cannot stay cached forever");

        for (String invalid : new String[] {"", "1,2", "1,2,0,0", "1,3,0,0,654", "1,0,0,0,654",
                "1,2,1,0,654", "1,2,0,0,654,", "1,2,0,0,nope", "1,2,0,0,2147483648"}) {
            check(ColorOsPolicy.batterySeriesCells(invalid) == 0, "invalid topology is not guessed: " + invalid);
        }
        ColorOsBattery.Sample unavailable = sampler.sample(firmware, Integer.MIN_VALUE, 4465, 63000);
        check(Float.isNaN(wattsOf(unavailable.currentMicroAmps, unavailable.voltageMilliVolts)),
                "dual-cell correction preserves unavailable current");
        ColorOsBattery.Sample noVoltage = sampler.sample(firmware, -496, 0, 66000);
        check(Float.isNaN(wattsOf(noVoltage.currentMicroAmps, noVoltage.voltageMilliVolts)),
                "dual-cell correction does not fabricate missing voltage");
        check(ColorOsPolicy.batteryPackVoltageMilliVolts(8930, 2, 9000) == 8930,
                "reported total voltage remains authoritative");
        check(ColorOsPolicy.batteryPackVoltageMilliVolts(4465, 0, 8930) == 4465,
                "a pack node alone cannot establish hardware topology");
        System.out.println("ColorOS current units, series-cell detection, pack-voltage selection and upstream rendering passed");
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
                              str(SOURCE / "ColorOsBattery.java"),
                              str(test_source)], check=True)
    subprocess.run(["java", "-cp", directory, "io.github.yixing233.hyperduo.ColorOsPowerTest"], check=True)
