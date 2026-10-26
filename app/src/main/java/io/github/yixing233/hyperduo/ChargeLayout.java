/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of HyperDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.yixing233.hyperduo;

/**
 * Per-style arrangements for the charging scenes.
 *
 * <p>A phone spends a meaningful part of its day on a charger, and the glyph that
 * reads best at 64 % idle is not the one that reads best while a 120 W brick pushes
 * the number up - the owner of this module wants the percentage in the middle while
 * charging and the bolt while fast charging, or the reverse. Upstream stores one
 * arrangement per style ({@link Prefs#KEY_RING_LAYOUT}); this adds two more, one for
 * ordinary charging and one for fast charging, encoded exactly the same way.
 *
 * <p>Sizes and colours stay shared on purpose: they are properties of the component,
 * not of the scene, and duplicating them would mean writing every slider twice.
 *
 * <p>An empty stored value means "inherit the normal arrangement", which is what every
 * install that predates this feature holds. An <em>explicitly empty</em> arrangement
 * (every component switched off) is encoded as such and is not the same thing.
 */
public final class ChargeLayout {
    /** The normal, not-charging arrangement. */
    public static final int BASE = 0;
    /** Plugged in, ordinary charging. */
    public static final int CHARGING = 1;
    /** Plugged in, SuperVOOC / wireless fast charging. */
    public static final int QUICK = 2;

    /**
     * Highest flag value the encoder can produce.
     *
     * <p>Ten booleans: Wi-Fi, mobile, value, bolt, SIM 1, SIM 2, dual SIM, centred
     * value, stacked signal, charging-shows-value. A stored value above this is
     * corrupt, not merely unfamiliar, and is rejected rather than truncated.
     */
    private static final int MAX_FLAGS = 1023;

    private ChargeLayout() {
    }

    /** The scene a battery state maps to. */
    public static int scene(boolean charging, boolean quick) {
        return charging ? (quick ? QUICK : CHARGING) : BASE;
    }

    /** The preference key holding {@code scene}'s arrangement. */
    public static String key(int scene) {
        if (scene == CHARGING) return Prefs.KEY_CHARGING_LAYOUT;
        if (scene == QUICK) return Prefs.KEY_QUICK_CHARGING_LAYOUT;
        throw new IllegalArgumentException("Not a charging scene");
    }

    /** Encodes {@code s} into the same {@code "1;flags;type;signal;zones"} form. */
    public static String encode(TrioSettings s) {
        int flags = (s.showWifi ? 1 : 0) | (s.showMobile ? 2 : 0) | (s.showValue ? 4 : 0)
                | (s.showBolt ? 8 : 0) | (s.showSim1 ? 16 : 0) | (s.showSim2 ? 32 : 0)
                | (s.dualSim ? 64 : 0) | (s.valueCentred ? 128 : 0)
                | (s.stackedSignal ? 256 : 0) | (s.chargingShowsValue ? 512 : 0);
        return "1;" + flags + ";" + s.mobileTypeMode + ";" + s.signalMode + ";"
                + Prefs.encodeLayout(s.ringZones);
    }

    /** Re-encodes a stored value, or returns "" when it is absent or malformed. */
    public static String normalize(String raw) {
        TrioSettings decoded = decode(raw, null);
        return decoded == null ? "" : encode(decoded);
    }

    private static TrioSettings decode(String raw, TrioSettings base) {
        if (raw == null || raw.isEmpty() || raw.length() > 1024) return null;
        String[] fields = raw.split(";", -1);
        if (fields.length != 5 || !fields[0].equals("1")) return null;
        try {
            int flags = Integer.parseInt(fields[1]);
            int type = Integer.parseInt(fields[2]), signal = Integer.parseInt(fields[3]);
            if (flags < 0 || flags > MAX_FLAGS || type < Prefs.MOBILE_TYPE_OFF
                    || type > Prefs.MOBILE_TYPE_OUT_RING || signal < Prefs.MIN_SIGNAL_MODE
                    || signal > Prefs.MAX_SIGNAL_MODE) return null;
            TrioSettings s = base == null ? TrioSettings.defaults() : base.copy();
            s.showWifi = (flags & 1) != 0;
            s.showMobile = (flags & 2) != 0;
            s.showValue = (flags & 4) != 0;
            s.showBolt = (flags & 8) != 0;
            s.showSim1 = (flags & 16) != 0;
            s.showSim2 = (flags & 32) != 0;
            s.dualSim = (flags & 64) != 0;
            s.valueCentred = (flags & 128) != 0;
            s.stackedSignal = (flags & 256) != 0;
            s.chargingShowsValue = (flags & 512) != 0;
            s.mobileTypeMode = type;
            s.signalMode = signal;
            s.ringZones = Prefs.decodeLayout(fields[4]);
            return s;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** The settings to draw with, given the runtime charging state. */
    public static TrioSettings forState(TrioSettings base, boolean charging, boolean quick) {
        return forScene(base, scene(charging, quick));
    }

    /**
     * The settings that belong to {@code scene}.
     *
     * <p>Returns {@code base} itself for the idle scene, so the common path keeps passing
     * the live snapshot through unchanged. A scene with nothing stored decodes as a copy of
     * the base arrangement, which is what lets the editor start from the current look and
     * diverge only where the user changes something.
     */
    public static TrioSettings forScene(TrioSettings base, int scene) {
        if (scene == BASE) return base;
        String raw = scene == QUICK ? base.quickChargingLayout : base.chargingLayout;
        TrioSettings s = decode(raw, base);
        return s == null ? base.copy() : s;
    }
}
