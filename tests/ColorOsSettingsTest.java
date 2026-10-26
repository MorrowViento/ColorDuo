package io.github.yixing233.hyperduo;

import android.graphics.Canvas;
import android.os.Bundle;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Executes real settings transport, ownership and rendering with the existing desktop shims. */
public final class ColorOsSettingsTest {
    public static void main(String[] args) {
        TrioSettings fresh = TrioSettings.defaults();
        for (int[] zone : fresh.ringZones) check(zone.length == 0, "upstream starts with empty component zones");
        check(!fresh.showSim1 && !fresh.showSim2, "per-card rows are opt-in upstream");
        check(!TrioAppearance.of(fresh).foldsMobile(), "unplaced signal remains native");
        check(!TrioAppearance.of(fresh).ownsWifi(), "unplaced Wi-Fi remains native");
        check(TrioSettings.fromBundle(fresh.toBundle()).equals(fresh), "full settings transport");
        check(Arrays.deepEquals(TrioSettings.fromBundle(new Bundle()).ringZones, fresh.ringZones),
                "first connection without stored settings uses the same layout");

        Bundle cleared = new Bundle();
        cleared.putString(Prefs.KEY_RING_LAYOUT, Prefs.EMPTY_LAYOUT);
        TrioSettings empty = TrioSettings.fromBundle(cleared);
        for (int[] zone : empty.ringZones) check(zone.length == 0, "explicitly cleared layout survives");
        check(!TrioAppearance.of(empty).ownsWifi() && !TrioAppearance.of(empty).foldsMobile(),
                "clearing components restores native signal ownership");
        check(Arrays.deepEquals(TrioSettings.fromBundle(empty.toBundle()).ringZones, empty.ringZones),
                "empty layout survives a second transport");

        Bundle legacy = new Bundle();
        legacy.putInt(Prefs.KEY_RING_SLOTS_V2, Prefs.packRingParts(Prefs.PART_VALUE, Prefs.PART_WIFI, Prefs.PART_SIM_1));
        TrioSettings migrated = TrioSettings.fromBundle(legacy);
        check(Arrays.equals(migrated.ringZones[0], new int[] {Prefs.PART_VALUE}),
                "existing packed layouts retain upstream migration");

        TrioSettings dual = fresh.copy();
        dual.showSim1 = true;
        dual.showSim2 = true;
        dual.ringZones = Prefs.decodeLayout("7|1|8");
        TrioAppearance.Ring layout = TrioAppearance.of(dual).ring(65, false, 3, "5G", 2);
        check(layout.parts[0] == Prefs.PART_SIM_1 && layout.parts[2] == Prefs.PART_SIM_2,
                "separately placed cards display simultaneously");
        check(Arrays.deepEquals(TrioSettings.fromBundle(dual.toBundle()).ringZones, dual.ringZones),
                "explicit dual-card placement is preserved");
        int[] reading = new int[2];
        TrioRenderer.outSignalLevels(false, 4, new int[] {4, -1}, 0, reading);
        check(Arrays.equals(reading, new int[] {4, -1}), "single SIM uses one full reading");
        TrioRenderer.outSignalLevels(false, 2, new int[] {-1, 2}, 1, reading);
        check(Arrays.equals(reading, new int[] {2, -1}), "a lone SIM in slot 2 is not duplicated");
        TrioRenderer.outSignalLevels(false, 4, new int[] {4, 2}, 0, reading);
        check(Arrays.equals(reading, new int[] {4, 2}), "dual readings retain each card's level");
        TrioRenderer.outSignalLevels(true, 2, new int[] {4, 2}, 1, reading);
        check(Arrays.equals(reading, new int[] {2, -1}), "data-SIM-only selects the actual data slot");

        BufferedImage image = new BufferedImage(240, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            TrioRenderer.drawInto(new Canvas(graphics), 240, 200, 65, false, false,
                    false, false, 4, 4, new int[] {4, 2}, "5G", 0xFFFFFFFF, dual, true, true);
        } finally { graphics.dispose(); }
        int ink = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, y) >>> 24) != 0) ink++;
        }
        check(ink > 1000, "configured upstream dual-card ring paints visible artwork");

        // Charging scenes keep an arrangement of their own; an absent scene inherits the
        // live one, and a malformed stored value must never replace a working layout.
        TrioSettings charging = dual.copy();
        charging.ringZones = Prefs.decodeLayout("4,1|3|7|");
        String encodedCharge = ChargeLayout.encode(charging);
        check(ChargeLayout.normalize(encodedCharge).equals(encodedCharge),
                "charging arrangement round-trips through the encoder");
        check(ChargeLayout.normalize("").isEmpty(), "missing scene inherits the normal arrangement");
        check(ChargeLayout.normalize("broken").isEmpty(), "malformed scene falls back to inheritance");
        check(ChargeLayout.normalize("1;99999;0;0;|||").isEmpty(), "out-of-range flags are rejected");
        check(ChargeLayout.normalize("1;4;9;0;|||").isEmpty(), "unknown network-type mode is rejected");

        TrioSettings withCharge = dual.copy();
        withCharge.chargingLayout = encodedCharge;
        TrioSettings drawCharge = ChargeLayout.forState(withCharge, true, false);
        check(Arrays.deepEquals(drawCharge.ringZones, charging.ringZones),
                "charging scene draws its own arrangement");
        check(ChargeLayout.forState(withCharge, false, false) == withCharge,
                "idle scene keeps the live snapshot untouched");

        // Fast charging is a third arrangement, not a synonym for ordinary charging.
        TrioSettings quickSource = dual.copy();
        quickSource.ringZones = Prefs.decodeLayout("4,5|1|7|");
        TrioSettings withQuick = withCharge.copy();
        withQuick.quickChargingLayout = ChargeLayout.encode(quickSource);
        check(Arrays.deepEquals(ChargeLayout.forState(withQuick, true, true).ringZones,
                        quickSource.ringZones),
                "fast charging draws its own arrangement");
        check(!Arrays.deepEquals(ChargeLayout.forState(withQuick, true, true).ringZones,
                        drawCharge.ringZones),
                "fast charging differs from ordinary charging");

        check(TrioSettings.fromBundle(withCharge.toBundle()).chargingLayout
                        .equals(withCharge.chargingLayout),
                "charging arrangement survives settings transport");

        System.out.println("Upstream defaults, settings transport, dual-card selection and ring rendering passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
