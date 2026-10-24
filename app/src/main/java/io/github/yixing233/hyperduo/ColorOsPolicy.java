/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import java.util.HashSet;
import java.util.Set;

/** ROM-independent rules used by the ColorOS adapter. */
final class ColorOsPolicy {
    private ColorOsPolicy() {}

    static int mobileLevel(int level, int numberOfLevels, boolean inService) {
        if (!inService) return 0;
        if (level < 0 || numberOfLevels < 2) return -1;
        return Math.round(Math.min(level, numberOfLevels - 1) * 4f / (numberOfLevels - 1));
    }

    /** Remembers only slots this adapter added; existing block lists belong to their owners. */
    static final class SlotOwnership {
        private final Set<String> added = new HashSet<>();

        boolean sync(Set<String> nativeSlots, Set<String> requested) {
            boolean changed = false;
            for (String slot : new HashSet<>(added)) {
                if (!requested.contains(slot)) {
                    changed |= nativeSlots.remove(slot);
                    added.remove(slot);
                }
            }
            for (String slot : requested) {
                if (nativeSlots.add(slot)) {
                    added.add(slot);
                    changed = true;
                }
            }
            return changed;
        }
    }
}
