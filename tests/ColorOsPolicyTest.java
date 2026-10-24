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
        System.out.println("ColorOS signal normalization and slot restoration passed");
    }

    private static void equal(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
