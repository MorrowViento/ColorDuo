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
        check(Arrays.equals(fresh.ringZones[0], new int[] {Prefs.PART_BOLT, Prefs.PART_VALUE}),
                "charging falls back to percentage");
        check(Arrays.equals(fresh.ringZones[1], new int[] {Prefs.PART_WIFI}), "Wi-Fi placed");
        check(Arrays.equals(fresh.ringZones[2], new int[] {Prefs.PART_SIM_1, Prefs.PART_SIM_2}),
                "SIM 1 falls back to SIM 2");
        check(TrioAppearance.of(fresh).foldsMobile(), "fresh defaults actually draw signal");
        check(TrioAppearance.of(fresh).ownsWifi(), "fresh defaults draw Wi-Fi");
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
                "existing packed layouts take precedence over new-install defaults");

        BufferedImage image = new BufferedImage(240, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            TrioRenderer.drawInto(new Canvas(graphics), 240, 200, 65, false, false,
                    false, false, 4, 4, new int[] {4, -1}, "5G", 0xFFFFFFFF, fresh, true, true);
        } finally { graphics.dispose(); }
        int ink = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, y) >>> 24) != 0) ink++;
        }
        check(ink > 1000, "new-install ring paints visible artwork");
        System.out.println("ColorOS defaults, settings transport, ownership and ring rendering passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
