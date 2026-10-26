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
 * A plain-text snapshot of what the module believes, for pasting into a bug report.
 *
 * <p>The ColorOS 16 branch answers a broadcast from the hooked process and reports hook
 * state; this is the half that needs no collaboration - the settings app already knows the
 * device, the SystemUI version and every stored value, and those are what most layout
 * reports turn out to be about ("my ring has the percentage in the middle and it still
 * shows 5G").
 *
 * <p>Key=value rather than prose: it survives translation, it is grep-able, and a line that
 * is missing is visibly missing. The format is versioned so a later revision can add lines
 * without readers guessing which shape they are looking at.
 */
public final class DiagnosticsReport {
    /** Facts the caller has to collect: everything here needs a Context or the framework. */
    /**
     * The ordered broadcast the settings app sends to ask the hooked process what it hooked.
     * Declared here so both sides read the same strings; the permission is defined in the
     * manifest and is signature-level.
     */
    public static final String REPORT_ACTION =
            "io.github.chorusfruit_233.colorduo.action.HOOK_REPORT";
    public static final String REPORT_PERMISSION =
            "io.github.chorusfruit_233.colorduo.permission.HOOK_REPORT";
    public static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    public static final class Facts {
        public final String moduleVersion;
        public final String device;
        public final String android;
        public final String systemUi;
        public final boolean frameworkConnected;

        public Facts(String moduleVersion, String device, String android, String systemUi,
                     boolean frameworkConnected) {
            this.moduleVersion = moduleVersion;
            this.device = device;
            this.android = android;
            this.systemUi = systemUi;
            this.frameworkConnected = frameworkConnected;
        }
    }

    private DiagnosticsReport() {
    }

    public static String render(Facts facts, TrioSettings settings) {
        StringBuilder out = new StringBuilder(512);
        out.append("ColorDuo diagnostics v1\n");
        out.append("module=").append(facts.moduleVersion).append('\n');
        out.append("device=").append(facts.device).append('\n');
        out.append("android=").append(facts.android).append('\n');
        out.append("systemui=").append(facts.systemUi).append('\n');
        out.append("framework=").append(facts.frameworkConnected ? "connected" : "absent").append('\n');
        out.append("style=").append(settings.trioStyle == Prefs.STYLE_CAPSULE ? "capsule" : "ring")
                .append('\n');
        out.append("zones=").append(Prefs.encodeLayout(settings.ringZones)).append('\n');
        out.append("shown=").append(shown(settings)).append('\n');
        out.append("type=").append(typeMode(settings.mobileTypeMode)).append('\n');
        out.append("charging=").append(scene(settings.chargingLayout)).append('\n');
        out.append("quick=").append(scene(settings.quickChargingLayout)).append('\n');
        out.append("debug=").append(settings.debugLog ? "on" : "off").append('\n');
        out.append("dual=").append(settings.dualRow ? "on" : "off").append('\n');
        return out.toString();
    }

    /** The component switches that are on, in a stable order so two reports compare by eye. */
    private static String shown(TrioSettings s) {
        StringBuilder out = new StringBuilder();
        append(out, "value", s.showValue);
        append(out, "bolt", s.showBolt);
        append(out, "wifi", s.showWifi);
        append(out, "mobile", s.showMobile);
        append(out, "sim1", s.showSim1);
        append(out, "sim2", s.showSim2);
        return out.toString();
    }

    private static void append(StringBuilder out, String name, boolean on) {
        if (!on) return;
        if (out.length() > 0) out.append(',');
        out.append(name);
    }

    private static String typeMode(int mode) {
        if (mode == Prefs.MOBILE_TYPE_IN_RING) return "ring";
        if (mode == Prefs.MOBILE_TYPE_OUT_RING) return "outside";
        return "off";
    }

    /** "inherit" is a real state here, not an absence: it means the idle arrangement is used. */
    private static String scene(String encoded) {
        return encoded == null || encoded.isEmpty() ? "inherit" : "custom";
    }
}
