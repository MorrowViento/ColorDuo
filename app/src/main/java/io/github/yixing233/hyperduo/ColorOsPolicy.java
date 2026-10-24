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

    /** A cold repository must not suppress a working live connection or rendered Wi-Fi model. */
    static int wifiLevel(Boolean connected, int platformLevel, Boolean renderedConnected,
                         int renderedLevel, int repositoryLevel, boolean carrierMerged) {
        if (carrierMerged || Boolean.FALSE.equals(connected)) return -1;
        if (Boolean.FALSE.equals(renderedConnected) && !Boolean.TRUE.equals(connected)) return -1;
        if (validWifiLevel(renderedLevel)) return renderedLevel;
        if (validWifiLevel(repositoryLevel)) return repositoryLevel;
        return Boolean.TRUE.equals(connected) && validWifiLevel(platformLevel) ? platformLevel : -1;
    }

    private static boolean validWifiLevel(int level) { return level >= 0 && level <= 4; }

    /** ColorOS strips InnerClass annotations, so getSimpleName() is not a reliable type test. */
    static int wifiModelLevel(Class<?> activeType, Object model) {
        if (activeType == null || !activeType.isInstance(model)) return -1;
        Object value;
        try {
            java.lang.reflect.Method getter = model.getClass().getMethod("getLevel");
            getter.setAccessible(true);
            value = getter.invoke(model);
        } catch (ReflectiveOperationException | RuntimeException missingGetter) {
            try {
                java.lang.reflect.Field field = model.getClass().getDeclaredField("level");
                field.setAccessible(true);
                value = field.get(model);
            } catch (ReflectiveOperationException | RuntimeException missingField) {
                return -1;
            }
        }
        int level = value instanceof Number ? ((Number) value).intValue() : -1;
        return validWifiLevel(level) ? level : -1;
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
