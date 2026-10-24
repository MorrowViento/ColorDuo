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

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * HyperDuo — folds the Wi-Fi signal and the mobile signal into MIUI's battery
 * icon, drawing one trio glyph (battery ring + Wi-Fi arcs + signal dots) in the
 * 28dp x 20dp slot the battery icon already occupies.
 *
 * <p>Entry point declared in {@code META-INF/xposed/java_init.list}; scope in
 * {@code META-INF/xposed/scope.list}.
 */
public final class HyperDuoModule extends XposedModule {

    private static final String TAG = "ColorDuo";
    private static final int LOG_INFO = 4;
    private static final int LOG_WARN = 5;

    private static final String TARGET_PACKAGE = "com.android.systemui";

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        final String pkg = param.getPackageName();
        if (!TARGET_PACKAGE.equals(pkg)) {
            return;
        }
        try {
            if (Refl.cls(ColorOsHooks.METER_CLASS, param.getClassLoader()) != null) {
                ColorOsHooks.install(this, param.getClassLoader());
            } else {
                TrioHooks.install(this, param.getClassLoader());
            }
        } catch (Throwable t) {
            log(LOG_WARN, "install failed: " + t);
        }
    }

    private void log(int priority, String msg) {
        try {
            log(priority, TAG, msg);
        } catch (Throwable ignored) {
            // never let logging break the module
        }
    }
}
