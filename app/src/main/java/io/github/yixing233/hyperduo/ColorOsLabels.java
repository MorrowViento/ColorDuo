/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import android.content.Context;
import android.content.res.Resources;

/** Module strings must be resolved from the module APK, not SystemUI's resource IDs. */
final class ColorOsLabels {
    private static Resources moduleResources;
    private ColorOsLabels() {}

    static String radioNotice(Context host, DuoPolicy.Radio radio) {
        int resource = switch (radio) {
            case NO_SIM -> R.string.duo_no_sim;
            case NO_SERVICE -> R.string.duo_no_service;
            case EMERGENCY_ONLY -> R.string.duo_emergency_only;
            default -> 0;
        };
        if (resource == 0) return "";
        try {
            if (moduleResources == null) {
                moduleResources = host.createPackageContext(BuildConfig.APPLICATION_ID, 0).getResources();
            }
            return moduleResources.getString(resource);
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return radio == DuoPolicy.Radio.EMERGENCY_ONLY ? "SOS"
                    : radio == DuoPolicy.Radio.NO_SIM ? "No SIM" : "No Service";
        }
    }
}
