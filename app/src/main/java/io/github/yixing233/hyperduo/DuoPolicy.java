/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

/** State rules shared by the ColorOS adapter, live rendering and previews. */
public final class DuoPolicy {
    private DuoPolicy() {}

    public enum Radio { UNKNOWN, AVAILABLE, NO_SIM, NO_SERVICE, EMERGENCY_ONLY, AIRPLANE }
    public enum Charge { UNKNOWN, DISCONNECTED, CHARGING, PAUSED, FULL }

    // BatteryManager status values. Current magnitude never determines charge state.
    static Charge charge(int status, int plugged) {
        if (status == 2) return Charge.CHARGING;
        if (status == 5) return Charge.FULL;
        if (plugged != 0 && (status == 3 || status == 4)) return Charge.PAUSED;
        if (status == 3 || status == 4) return Charge.DISCONNECTED;
        return Charge.UNKNOWN;
    }

    // ServiceState: a working packet-data registration is service even without voice.
    static Radio service(int voiceRegistration, int dataRegistration, Boolean emergencyOnly) {
        if (voiceRegistration == 0 || dataRegistration == 0) return Radio.AVAILABLE;
        if (Boolean.TRUE.equals(emergencyOnly)) return Radio.EMERGENCY_ONLY;
        if (voiceRegistration == 3 || (voiceRegistration == 1 && dataRegistration > 0)) return Radio.NO_SERVICE;
        return Radio.UNKNOWN;
    }

    static Radio radio(boolean airplane, int subscriptions, Boolean simPresent,
                       int dataSlot, Radio[] services) {
        if (airplane) return Radio.AIRPLANE;
        if (subscriptions < 0) return Radio.UNKNOWN;
        if (subscriptions == 0) {
            return Boolean.FALSE.equals(simPresent) ? Radio.NO_SIM : Radio.UNKNOWN;
        }
        if (dataSlot >= 0 && dataSlot < services.length) return services[dataSlot];
        for (Radio service : services) if (service == Radio.AVAILABLE) return service;
        for (Radio service : services) if (service != Radio.UNKNOWN) return service;
        return Radio.UNKNOWN;
    }

    static boolean hasNotice(Radio radio) {
        return radio == Radio.NO_SIM || radio == Radio.NO_SERVICE || radio == Radio.EMERGENCY_ONLY;
    }

    static boolean foldMobile(boolean owns, int readings, Radio radio, boolean noticeVisible) {
        if (!owns) return false;
        if (hasNotice(radio)) return noticeVisible;
        return radio == Radio.AVAILABLE && readings > 0;
    }

    /** Automatic rows follow the data line; named SIM components keep physical slots. */
    static int primarySlot(int dataSlot, int[] levels) {
        if (levels == null || levels.length < 2) return -1;
        if (dataSlot >= 0 && dataSlot < 2 && levels[dataSlot] >= 0) return dataSlot;
        return levels[0] >= 0 ? 0 : levels[1] >= 0 ? 1 : -1;
    }

    static int[] automaticLevels(int[] levels, int dataSlot) {
        if (levels == null || levels.length < 2) return levels;
        return primarySlot(dataSlot, levels) == 1
                ? new int[] {levels[1], levels[0]} : levels;
    }

    static int pausedColor(int foreground) { return (foreground & 0xff000000) | 0x008e8e93; }
}
