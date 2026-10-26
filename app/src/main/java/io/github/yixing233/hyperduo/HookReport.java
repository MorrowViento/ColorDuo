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
 * What the adapter has actually managed to hook, as one line, for the debug log.
 *
 * <p>The settings app can describe the configuration but not the result: whether the
 * battery host took the glyph, whether the Wi-Fi model was ever seen, whether any mobile
 * model was captured. Those facts only exist inside SystemUI, and a report that cannot see
 * them cannot answer the one question a ColorOS port keeps raising - "is this build even
 * hooking the thing I think it is".
 *
 * <p>Deliberately a log line and not an IPC channel. The 16 branch answers a broadcast from
 * the settings app; that is the right end state, but a receiver registered inside SystemUI
 * is also the change most likely to take the status bar down when it is wrong, and it cannot
 * be verified without the device it runs on. This half is inert: if the numbers are wrong
 * the only thing that is wrong is a log line.
 */
public final class HookReport {
    private static volatile boolean adapterInstalled;
    private static volatile int hosts;
    private static volatile boolean logged;

    private HookReport() {
    }

    /** Called once the adapter's hooks are in place. */
    public static void adapterInstalled() {
        adapterInstalled = true;
    }

    /** Called every time a host takes the glyph; the count is the useful part. */
    public static void hostApplied() {
        hosts++;
    }

    /** One line, key=value, in the same shape as the settings-side report. */
    public static String render(boolean wifiSeen, int mobileModels) {
        return "compat adapter=" + (adapterInstalled ? 1 : 0)
                + " hosts=" + hosts
                + " wifi=" + (wifiSeen ? 1 : 0)
                + " mobile=" + mobileModels;
    }

    /** True exactly once per process, so the summary is not repeated on every frame. */
    public static boolean firstHost() {
        if (logged) return false;
        logged = true;
        return true;
    }
}
