/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import android.content.Context;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import java.util.ArrayList;
import java.util.List;

/** One bounded telephony snapshot shared by all status-bar, keyguard and QS hosts. */
final class ColorOsRadio {
    static final class Sample {
        final List<SubscriptionInfo> subscriptions;
        final int dataId;
        final int dataSlot;
        final DuoPolicy.Radio[] services;
        final DuoPolicy.Radio radio;
        final DuoPolicy.Radio reportedRadio;

        Sample(List<SubscriptionInfo> subscriptions, int dataId, int dataSlot,
               DuoPolicy.Radio[] services, DuoPolicy.Radio radio) {
            this(subscriptions, dataId, dataSlot, services, radio, radio);
        }

        private Sample(List<SubscriptionInfo> subscriptions, int dataId, int dataSlot,
                       DuoPolicy.Radio[] services, DuoPolicy.Radio radio, DuoPolicy.Radio reportedRadio) {
            this.subscriptions = subscriptions;
            this.dataId = dataId;
            this.dataSlot = dataSlot;
            this.services = services;
            this.radio = radio;
            this.reportedRadio = reportedRadio;
        }

        Sample displayedAs(DuoPolicy.Radio displayed) {
            return new Sample(subscriptions, dataId, dataSlot, services, displayed, reportedRadio);
        }

        String registrationKey() {
            int[] ids = {-1, -1};
            if (subscriptions != null) for (SubscriptionInfo info : subscriptions) {
                if (info != null && info.getSimSlotIndex() >= 0 && info.getSimSlotIndex() < ids.length) {
                    ids[info.getSimSlotIndex()] = info.getSubscriptionId();
                }
            }
            return dataId + ":" + ids[0] + ":" + ids[1];
        }
    }

    private long nextSample;
    private Sample latest = unknown();
    private final DuoPolicy.RadioStability stability = new DuoPolicy.RadioStability();

    synchronized Sample sample(Context context, long now) {
        if (now < nextSample) return latest;
        nextSample = now + 1000L;
        Sample reported = read(context);
        latest = reported.displayedAs(stability.filter(reported.radio, reported.registrationKey(),
                reported.subscriptions != null && !reported.subscriptions.isEmpty(), now));
        return latest;
    }

    private static Sample unknown() {
        return new Sample(null, -1, -1,
                new DuoPolicy.Radio[] {DuoPolicy.Radio.UNKNOWN, DuoPolicy.Radio.UNKNOWN}, DuoPolicy.Radio.UNKNOWN);
    }

    private static Sample read(Context context) {
        try {
            if (Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0) {
                return new Sample(List.of(), -1, -1, unknown().services, DuoPolicy.Radio.AIRPLANE);
            }
            SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
            TelephonyManager telephony = context.getSystemService(TelephonyManager.class);
            if (manager == null || telephony == null) return unknown();
            List<SubscriptionInfo> active = manager.getActiveSubscriptionInfoList();
            if (active == null) return unknown();
            List<SubscriptionInfo> subscriptions = new ArrayList<>(active);
            int dataId = SubscriptionManager.getDefaultDataSubscriptionId();
            int dataSlot = -1;
            DuoPolicy.Radio[] services = unknown().services;
            for (SubscriptionInfo info : subscriptions) {
                if (info == null) continue;
                int slot = info.getSimSlotIndex();
                if (slot < 0 || slot >= services.length) continue;
                if (info.getSubscriptionId() == dataId) dataSlot = slot;
                try {
                    ServiceState service = telephony.createForSubscriptionId(info.getSubscriptionId()).getServiceState();
                    if (service != null) {
                        Object data = Refl.callByName(service, "getDataRegistrationState");
                        if (!(data instanceof Number)) data = Refl.callByName(service, "getDataRegState");
                        Object emergency = Refl.callByName(service, "isEmergencyOnly");
                        services[slot] = DuoPolicy.service(service.getState(),
                                data instanceof Number ? ((Number) data).intValue() : -1,
                                emergency instanceof Boolean ? (Boolean) emergency : null, searching(service));
                    }
                } catch (RuntimeException unavailable) { /* preserve UNKNOWN for this line */ }
            }
            // During re-enable the default ID can still refer to an inactive subscription.
            // Resolve one real line so the host does not paint an unavailable cached ID.
            if (dataSlot < 0 && !subscriptions.isEmpty()) {
                for (SubscriptionInfo info : subscriptions) {
                    if (info == null) continue;
                    int slot = info.getSimSlotIndex();
                    if (slot < 0 || slot >= services.length) continue;
                    if (dataSlot < 0 || (services[dataSlot] != DuoPolicy.Radio.AVAILABLE
                            && services[slot] == DuoPolicy.Radio.AVAILABLE)) {
                        dataSlot = slot;
                        dataId = info.getSubscriptionId();
                    }
                }
            }
            Boolean present = subscriptions.isEmpty() ? simPresent(telephony) : Boolean.TRUE;
            return new Sample(subscriptions, dataId, dataSlot, services,
                    DuoPolicy.radio(false, subscriptions.size(), present, dataSlot, services));
        } catch (RuntimeException unavailable) {
            return unknown();
        }
    }

    private static Boolean searching(ServiceState state) {
        Object registrations = Refl.callByName(state, "getNetworkRegistrationInfoList");
        if (!(registrations instanceof Iterable<?>)) return null;
        Boolean known = null;
        for (Object info : (Iterable<?>) registrations) {
            Object transport = Refl.callByName(info, "getTransportType");
            // WWAN only; a Wi-Fi/IMS registration cannot mute cellular service.
            if (!(transport instanceof Number) || ((Number) transport).intValue() != 1) continue;
            Object value = Refl.callByName(info, "isNetworkSearching");
            if (!(value instanceof Boolean)) value = Refl.callByName(info, "isSearching");
            if (Boolean.TRUE.equals(value)) return Boolean.TRUE;
            if (Boolean.FALSE.equals(value)) known = Boolean.FALSE;
        }
        return known;
    }

    private static Boolean simPresent(TelephonyManager telephony) {
        boolean unknown = false;
        int modems = Math.max(1, Math.min(2, telephony.getActiveModemCount()));
        for (int slot = 0; slot < modems; slot++) {
            int state = telephony.getSimState(slot);
            if (state == TelephonyManager.SIM_STATE_UNKNOWN) unknown = true;
            else if (state != TelephonyManager.SIM_STATE_ABSENT) return Boolean.TRUE;
        }
        return unknown ? null : Boolean.FALSE;
    }
}
