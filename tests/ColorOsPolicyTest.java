package io.github.yixing233.hyperduo;

import java.util.HashSet;
import java.util.Set;

/** Run with tests/coloros-policy.sh; no Android runtime or phone is needed. */
public final class ColorOsPolicyTest {
    public static void main(String[] args) {
        equal(4, ColorOsPolicy.mobileLevel(4, 5, true), "four-bar full service");
        equal(4, ColorOsPolicy.mobileLevel(5, 6, true), "five-bar full service");
        equal(2, ColorOsPolicy.mobileLevel(3, 6, true), "five-bar intermediate service");
        equal(4, ColorOsPolicy.mobileLevel(9, 6, true), "out-of-range native level");
        equal(0, ColorOsPolicy.mobileLevel(4, 5, false), "cached strength after service loss");
        equal(-1, ColorOsPolicy.mobileLevel(-1, 5, true), "unknown strength");
        equal(-1, ColorOsPolicy.mobileLevel(0, 1, true), "unknown scale");

        equal(3, ColorOsPolicy.wifiLevel(true, 3, null, -1, -1, false),
                "connected Wi-Fi survives a cold Inactive repository (alpha1 regression)");
        equal(3, ColorOsPolicy.wifiLevel(true, 3, false, -1, -1, false),
                "reconnect overrides the previous disconnected rendering snapshot");
        equal(2, ColorOsPolicy.wifiLevel(true, 4, true, 2, 4, false),
                "the native rendered level takes precedence over cached repository strength");
        equal(-1, ColorOsPolicy.wifiLevel(false, 4, true, 4, 4, false),
                "turning off Wi-Fi clears stale connected models and cached RSSI");
        equal(2, ColorOsPolicy.wifiLevel(null, -1, true, 2, -1, false),
                "native rendering works when framework queries are unavailable");
        equal(-1, ColorOsPolicy.wifiLevel(null, -1, false, -1, 4, false),
                "native disconnect beats a stale repository when the framework is unknown");
        equal(0, ColorOsPolicy.wifiLevel(true, 0, null, -1, -1, false),
                "zero is a valid connected signal level");
        equal(-1, ColorOsPolicy.wifiLevel(null, 3, null, -1, -1, false),
                "unknown connectivity does not claim Wi-Fi from an old RSSI reading alone");
        equal(-1, ColorOsPolicy.wifiLevel(true, 4, false, -1, -1, true),
                "carrier-merged Wi-Fi stays in the native cellular channel");
        equal(-1, ColorOsPolicy.wifiLevel(true, 8, null, -1, -1, false),
                "invalid levels leave the native icon available");
        WifiNetworkModel$Active stripped = new WifiNetworkModel$Active();
        check(!stripped.getClass().getSimpleName().equals("Active"),
                "fixture reproduces a binary nested name without InnerClass annotations");
        equal(3, ColorOsPolicy.wifiModelLevel(WifiNetworkModel$Active.class, stripped),
                "annotation-stripped Active model is recognized by its actual runtime type");
        equal(-1, ColorOsPolicy.wifiModelLevel(WifiNetworkModel$Active.class, new Unrelated$Active()),
                "an unrelated Active class must not claim the Wi-Fi icon");
        equal(2, ColorOsPolicy.wifiModelLevel(FieldOnlyActive.class, new FieldOnlyActive()),
                "a removed getter can fall back to the native model field");
        equal(-1, ColorOsPolicy.wifiModelLevel(null, stripped), "missing model type keeps native fallback");

        ColorOsPolicy.SlotOwnership owner = new ColorOsPolicy.SlotOwnership();
        Set<String> nativeSlots = new HashSet<>(Set.of("alarm_clock", "wifi"));
        check(owner.sync(nativeSlots, Set.of("wifi", "mobile", "stacked_mobile")), "enable changes slots");
        check(nativeSlots.containsAll(Set.of("alarm_clock", "wifi", "mobile", "stacked_mobile")), "merge slots");
        check(!owner.sync(nativeSlots, Set.of("wifi", "mobile", "stacked_mobile")), "repeat does not request layout");
        check(owner.sync(nativeSlots, Set.of("wifi")), "release mobile independently");
        check(nativeSlots.equals(Set.of("wifi", "alarm_clock")), "preserve pre-existing slots");
        check(!owner.sync(nativeSlots, Set.of()), "pre-existing wifi never became ours");
        nativeSlots.remove("wifi");
        check(owner.sync(nativeSlots, Set.of("wifi")), "claim wifi after other owner releases it");
        check(owner.sync(nativeSlots, Set.of()), "disable releases own wifi");
        check(nativeSlots.equals(Set.of("alarm_clock")), "disable retains unrelated system policy");
        check(!owner.sync(nativeSlots, Set.of()), "repeated disable is idempotent");
        System.out.println("ColorOS Wi-Fi resolution, signal normalization and slot restoration passed");
    }

    private static void equal(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

// Top-level classes containing '$' deliberately have no enclosing/inner-class metadata,
// matching the Active class in SystemUI 17.99.02. A normal nested test class would hide the bug.
final class WifiNetworkModel$Active {
    public int getLevel() { return 3; }
}
final class Unrelated$Active {
    public int getLevel() { return 4; }
}
final class FieldOnlyActive {
    public final int level = 2;
}
