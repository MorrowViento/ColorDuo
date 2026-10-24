/*
 * Copyright (C) 2026 ColorDuo contributors
 * Based on HyperDuo by yixing233 and contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.yixing233.hyperduo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** ColorOS 17 adapter, verified against OnePlus SystemUI 17.99.02 (API 37). */
final class ColorOsHooks {
    static final String METER_CLASS =
            "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView";
    private static final String BATTERY = "com.oplus.systemui.statusbar.pipeline.battery.";
    private static final String WIFI = "com.android.systemui.statusbar.pipeline.wifi.";
    private static final String MOBILE = "com.oplus.systemui.statusbar.pipeline.mobile.ui.viewmodel.";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Views retain their Host as an attach listener. Neither side of this lookup roots a view tree.
    private static final Map<View, WeakReference<Host>> HOSTS = new WeakHashMap<>();
    private static final Map<View, WeakReference<Host>> CHILDREN = new WeakHashMap<>();
    private static final Map<Integer, Mobile> MOBILES = new ConcurrentHashMap<>();
    private static volatile Object wifiFlow;
    private static volatile Object batteryLevelFlow;
    private static volatile Object chargeFlow;
    private static volatile Object savingFlow;
    private static volatile boolean installed;
    private static boolean refreshQueued;
    private static boolean tickerStarted;
    private static Class<?> meterClass;
    private static Class<?> phoneBarClass;
    private static Context context;

    private ColorOsHooks() {}

    static boolean isInstalled() { return installed; }

    static void install(XposedModule module, ClassLoader loader) {
        meterClass = Refl.cls(METER_CLASS, loader);
        phoneBarClass = Refl.cls("com.android.systemui.statusbar.phone.PhoneStatusBarView", loader);
        if (meterClass == null || phoneBarClass == null) {
            throw new IllegalStateException("ColorOS battery/status bar classes unavailable");
        }
        TrioHooks.useLogger(module);
        installed = true;
        TrioConfig.install(module);
        TrioConfig.addListener(ColorOsHooks::invalidateHosts);

        int count = 0;
        // OEM/R8 builds can inline binder helpers. Filter framework writes by our exact child
        // instances too, so an inlined style update cannot leave a stale restoration snapshot.
        count += hook(module, Refl.method(ImageView.class, "setImageDrawable", Drawable.class), chain -> {
            Host owner = childOwner((View) chain.getThisObject());
            if (owner == null || owner.image != chain.getThisObject()
                    || owner.moduleWriting || owner.nativeDepth != 0) return chain.proceed();
            Drawable drawable = (Drawable) chain.getArg(0);
            if (drawable == owner.glyph) return chain.proceed();
            owner.nativeDrawable = drawable;
            if (!owner.active) return chain.proceed();
            ViewGroup.LayoutParams current = owner.image.getLayoutParams();
            if (current instanceof LinearLayout.LayoutParams
                    && (current.width != owner.ownedWidth || current.height != owner.ownedHeight)) {
                owner.nativeParams = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) current);
            }
            Object result = chain.proceed(new Object[] {owner.glyph});
            owner.enforceImageSize();
            return result;
        });
        count += hook(module, Refl.method(ImageView.class, "setScaleType", ImageView.ScaleType.class), chain -> {
            Host owner = childOwner((View) chain.getThisObject());
            if (owner == null || owner.image != chain.getThisObject()
                    || owner.moduleWriting || owner.nativeDepth != 0) return chain.proceed();
            owner.nativeScaleType = (ImageView.ScaleType) chain.getArg(0);
            return owner.active ? chain.proceed(new Object[] {ImageView.ScaleType.FIT_XY}) : chain.proceed();
        });
        count += hook(module, Refl.method(View.class, "setVisibility", int.class), chain -> {
            View view = (View) chain.getThisObject();
            Host owner = childOwner(view);
            if (owner == null || owner.moduleWriting || owner.nativeDepth != 0) return chain.proceed();
            owner.nativeVisibility.put(view, (Integer) chain.getArg(0));
            Integer desired = owner.visibilityOverrides.get(view);
            return owner.active && desired != null ? chain.proceed(new Object[] {desired}) : chain.proceed();
        });
        for (Method method : meterClass.getDeclaredMethods()) {
            String name = method.getName();
            if (name.equals("onFinishInflate") || name.equals("onAttachedToWindow")
                    || name.equals("bindViewModel") || name.equals("onDarkChanged")
                    || name.equals("onConfigurationChanged") || name.equals("disable")
                    || name.equals("onHiddenChanged") || name.equals("setHiddenIconListChanged")) {
                count += hook(module, method, chain -> {
                    ViewGroup meter = (ViewGroup) chain.getThisObject();
                    Host host = host(meter);
                    if (chain.getExecutable().getName().equals("bindViewModel")) {
                        host.viewModel = chain.getArg(0);
                    }
                    return nativeUpdate(host, chain);
                });
            }
        }

        // All binder writes happen against the native view state. Capture the result once the
        // outermost binder call finishes, then put our drawable back. This also handles the old
        // horizontal style, which uses a separate ProgressBar instead of battery_icon_view.
        Class<?> binder = Refl.cls(BATTERY + "ui.binder.BatteryViewBinder", loader);
        if (binder != null) for (Method method : binder.getDeclaredMethods()) {
            if (!method.getName().startsWith("bind")) continue;
            count += hook(module, method, chain -> {
                Host owner = null;
                for (Object arg : chain.getArgs()) {
                    if (arg instanceof View) {
                        owner = existingHost((View) arg);
                        if (owner != null) break;
                    }
                }
                return owner == null ? chain.proceed() : nativeUpdate(owner, chain);
            });
        }

        count += captureFlow(module, loader, WIFI + "data.repository.WifiRepositorySwitcher",
                "getWifiNetwork", true);
        count += captureFlow(module, loader, WIFI + "data.repository.prod.WifiRepositoryImpl",
                "getWifiNetwork", true);
        count += captureFlow(module, loader, MOBILE + "OplusCellularIconViewModelImpl",
                "getIcon", false);
        count += captureFlow(module, loader, MOBILE + "OplusCellularIconViewModelImpl",
                "getNetworkTypeText", false);
        for (String name : new String[] {"getBatteryLevel", "getChargeState", "isSavingMode"}) {
            Method method = Refl.method(Refl.cls(BATTERY + "data.repository.BatteryRepositoryImpl", loader), name);
            if (method == null) continue;
            count += hook(module, method, chain -> {
                Object result = chain.proceed();
                if (name.equals("getBatteryLevel")) batteryLevelFlow = result;
                else if (name.equals("getChargeState")) chargeFlow = result;
                else savingFlow = result;
                return result;
            });
        }

        TrioHooks.log(TrioHooks.LOG_INFO, "ColorOS adapter installed, hooks=" + count
                + " frameworkApi=" + module.getApiVersion()
                + " SystemUI=" + METER_CLASS);
    }

    private static int hook(XposedModule module, Method method, XposedInterface.Hooker callback) {
        if (method == null) return 0;
        try {
            method.setAccessible(true);
            module.hook(method).setId("colorduo:" + method.toGenericString()).intercept(callback);
            return 1;
        } catch (Throwable error) {
            TrioHooks.log(TrioHooks.LOG_WARN, "ColorOS skip " + method.getName() + ": " + error);
            return 0;
        }
    }

    private static int captureFlow(XposedModule module, ClassLoader loader, String className,
                                   String name, boolean wifi) {
        Method method = Refl.method(Refl.cls(className, loader), name);
        if (method == null) {
            TrioHooks.log(TrioHooks.LOG_WARN, "ColorOS missing " + className + "." + name);
            return 0;
        }
        return hook(module, method, chain -> {
            Object flow = chain.proceed();
            try {
                if (wifi) {
                    wifiFlow = flow;
                } else {
                    int subId = integer(Refl.callByName(chain.getThisObject(), "getSubscriptionId"), -1);
                    if (subId >= 0) {
                        Mobile mobile = MOBILES.computeIfAbsent(subId, ignored -> new Mobile());
                        if (name.equals("getIcon")) mobile.icon = flow;
                        else mobile.type = flow;
                    }
                }
            } catch (Throwable error) {
                TrioHooks.log(TrioHooks.LOG_WARN, "ColorOS signal capture: " + error);
            }
            return flow;
        });
    }

    private static Object nativeUpdate(Host host, XposedInterface.Chain chain) throws Throwable {
        boolean outer = host.nativeDepth++ == 0;
        if (outer) host.restoreBattery();
        try {
            return chain.proceed();
        } finally {
            host.nativeDepth--;
            if (outer) {
                try {
                    host.capture();
                    host.apply();
                } catch (Throwable error) {
                    host.fail(error);
                }
            }
        }
    }

    private static Host host(ViewGroup meter) {
        WeakReference<Host> reference = HOSTS.get(meter);
        Host host = reference == null ? null : reference.get();
        if (host == null) {
            host = new Host(meter);
            HOSTS.put(meter, new WeakReference<>(host));
            meter.addOnAttachStateChangeListener(host);
        }
        return host;
    }

    private static Host existingHost(View view) {
        for (View current = view; current != null; ) {
            WeakReference<Host> reference = HOSTS.get(current);
            Host host = reference == null ? null : reference.get();
            if (host != null) return host;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static Host childOwner(View view) {
        WeakReference<Host> reference = CHILDREN.get(view);
        return reference == null ? null : reference.get();
    }

    static void invalidateHosts() {
        MAIN.post(() -> {
            if (refreshQueued) return;
            refreshQueued = true;
            MAIN.post(() -> {
                refreshQueued = false;
                refreshHosts();
            });
        });
    }

    private static void refreshHosts() {
        ClockTicker.refreshNow();
        for (WeakReference<Host> reference : new ArrayList<>(HOSTS.values())) {
            Host host = reference.get();
            if (host == null || host.nativeDepth != 0) continue;
            try { host.apply(); }
            catch (Throwable error) { host.fail(error); }
        }
    }

    private static void start(Context supplied) {
        if (context == null) {
            Context app = supplied.getApplicationContext();
            context = app == null ? supplied : app;
            TrioConfig.installReceiver(context);
            TrioState.attachContext(context);
        }
        if (tickerStarted) return;
        tickerStarted = true;
        MAIN.post(new Runnable() {
            @Override public void run() {
                boolean interactive = true;
                PowerManager power = context.getSystemService(PowerManager.class);
                if (power != null) interactive = power.isInteractive();
                if (interactive && TrioConfig.get().enabled) refreshHosts();
                MAIN.postDelayed(this, interactive ? 1000L : 5000L);
            }
        });
    }

    private static Object value(Object flow) { return Refl.callByName(flow, "getValue"); }
    private static int integer(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static final class Mobile {
        volatile Object icon;
        volatile Object type;
    }

    private static final class Host implements View.OnAttachStateChangeListener {
        final ViewGroup meter;
        final TrioState state;
        final Glyph glyph;
        final Map<View, Integer> nativeVisibility = new IdentityHashMap<>();
        final Map<View, Integer> visibilityOverrides = new IdentityHashMap<>();
        final ColorOsPolicy.SlotOwnership slots = new ColorOsPolicy.SlotOwnership();
        ImageView image;
        ViewGroup iconContainer;
        Object viewModel;
        Drawable nativeDrawable;
        LinearLayout.LayoutParams nativeParams;
        ImageView.ScaleType nativeScaleType;
        int nativeDepth;
        boolean active;
        boolean failed;
        boolean logged;
        boolean moduleWriting;
        int ownedWidth;
        int ownedHeight;
        long nextSignalSample;
        View extra;

        Host(ViewGroup meter) {
            this.meter = meter;
            state = new TrioState(meter);
            glyph = new Glyph(this);
        }

        View find(String name) {
            int id = meter.getResources().getIdentifier(name, "id", "com.android.systemui");
            return id == 0 ? null : meter.findViewById(id);
        }

        void capture() {
            View view = find("battery_icon_view");
            if (!(view instanceof ImageView)) return;
            image = (ImageView) view;
            nativeDrawable = image.getDrawable();
            if (image.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                nativeParams = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) image.getLayoutParams());
            }
            nativeScaleType = image.getScaleType();
            nativeVisibility.clear();
            visibilityOverrides.clear();
            for (String name : new String[] {"battery_icon_view", "battery_content",
                    "battery_charge_icon", "battery_charge_reverse", "battery_percentage_view"}) {
                View child = find(name);
                if (child != null) {
                    nativeVisibility.put(child, child.getVisibility());
                    CHILDREN.put(child, new WeakReference<>(this));
                }
            }
        }

        boolean primaryBar() {
            for (ViewParent p = meter.getParent(); p instanceof View; p = p.getParent()) {
                if (phoneBarClass.isInstance(p)) return true;
            }
            return false;
        }

        void apply() {
            boolean previous = moduleWriting;
            moduleWriting = true;
            try { applyInternal(); }
            finally { moduleWriting = previous; }
        }

        void applyInternal() {
            if (nativeDepth != 0) return;
            if (image == null) capture();
            boolean wanted = !failed && TrioConfig.get().enabled && primaryBar()
                    && meter.isAttachedToWindow() && meter.getVisibility() == View.VISIBLE
                    && image != null && nativeParams != null;
            if (!wanted) {
                restore();
                return;
            }
            start(meter.getContext());
            readState();
            // An unknown battery state must leave a complete native status bar available.
            if (state.level < 0 || state.level > 100) {
                restore();
                return;
            }
            if (!active) capture();
            active = true;
            TrioAppearance appearance = TrioConfig.appearance();
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(nativeParams);
            int height = Math.round(20 * meter.getResources().getDisplayMetrics().density);
            TrioGeometry.setCapsuleLength(appearance.capsuleLength);
            params.height = appearance.capsule ? TrioGeometry.capHostHeightPx(height * 3 / 4) : height;
            params.width = appearance.capsule ? TrioGeometry.capHostWidthPx(height * 3 / 4)
                    : Math.round(24 * meter.getResources().getDisplayMetrics().density);
            ownedWidth = params.width;
            ownedHeight = params.height;
            if (image.getLayoutParams().width != params.width || image.getLayoutParams().height != params.height) {
                image.setLayoutParams(params);
            }
            image.setScaleType(ImageView.ScaleType.FIT_XY);
            if (image.getDrawable() != glyph) image.setImageDrawable(glyph);
            visibilityOverrides.put(image, View.VISIBLE);
            image.setVisibility(View.VISIBLE);
            hide("battery_content", true);
            hide("battery_percentage_view", appearance.ownsValue());
            hide("battery_charge_icon", appearance.hidesNativeBolt());
            syncExtra(appearance);
            syncSlots(appearance);
            glyph.invalidateSelf();
            if (!logged) {
                logged = true;
                TrioHooks.log(TrioHooks.LOG_INFO, "ColorOS host active: level=" + state.level + " wifiFlow=" + (wifiFlow != null)
                        + " mobileSubscriptions=" + MOBILES.size());
            }
        }

        void hide(String name, boolean hidden) {
            View view = find(name);
            Integer original = nativeVisibility.get(view);
            if (view != null && original != null) {
                if (hidden) visibilityOverrides.put(view, View.GONE);
                else visibilityOverrides.remove(view);
                view.setVisibility(hidden ? View.GONE : original);
            }
        }

        void enforceImageSize() {
            boolean previous = moduleWriting;
            moduleWriting = true;
            try {
                ViewGroup.LayoutParams params = image.getLayoutParams();
                if (params.width != ownedWidth || params.height != ownedHeight) {
                    LinearLayout.LayoutParams fixed = new LinearLayout.LayoutParams(nativeParams);
                    fixed.width = ownedWidth;
                    fixed.height = ownedHeight;
                    image.setLayoutParams(fixed);
                }
            } finally { moduleWriting = previous; }
        }

        void restoreBattery() {
            boolean previous = moduleWriting;
            moduleWriting = true;
            try { restoreBatteryInternal(); }
            finally { moduleWriting = previous; }
        }

        void restoreBatteryInternal() {
            if (active && image != null) {
                image.setImageDrawable(nativeDrawable);
                image.setScaleType(nativeScaleType);
                if (nativeParams != null) image.setLayoutParams(new LinearLayout.LayoutParams(nativeParams));
                for (Map.Entry<View, Integer> entry : nativeVisibility.entrySet()) {
                    entry.getKey().setVisibility(entry.getValue());
                }
            }
            active = false;
            if (extra != null) extra.setVisibility(View.GONE);
        }

        void restore() {
            restoreBattery();
            releaseSlots();
        }

        void fail(Throwable error) {
            failed = true;
            try { restore(); } catch (Throwable ignored) { /* preserve the original exception */ }
            TrioHooks.log(TrioHooks.LOG_WARN, "ColorOS host restored after failure: " + error);
        }

        void readState() {
            Object battery = value(Refl.callByName(viewModel, "getBatteryIcon"));
            if (battery == null) battery = Refl.callByName(meter, "getBatteryIcon");
            state.level = integer(value(batteryLevelFlow), integer(Refl.callByName(battery, "getPowerLevel"), -1));
            state.useTint = true;
            Object color = value(Refl.callByName(viewModel, "getBatteryColor"));
            state.tintColor = integer(Refl.callByName(color, "getSingleToneColor"),
                    integer(Refl.callByName(battery, "getTintColor"), 0xFFFFFFFF));
            Object charge = value(chargeFlow);
            if (charge == null && viewModel != null) {
                charge = Refl.get(Refl.field(viewModel.getClass(), "chargeState"), viewModel);
            }
            state.charging = bool(Refl.callByName(charge, "getCharging"), TrioState.sChargingAttached);
            state.quickCharging = bool(Refl.callByName(charge, "getSuperVoocCharging"), false)
                    || bool(Refl.callByName(charge, "getWirelessVoocCharging"), false);
            state.powerSave = bool(value(savingFlow), bool(Refl.get(
                    Refl.field(viewModel == null ? null : viewModel.getClass(), "isSavingMode"), viewModel), false));
            state.low = state.level >= 0 && state.level <= 15;

            // Dark-mode animation dispatches many binder updates per second. Only cached battery
            // and color values are read on those updates; telephony IPC is bounded independently.
            long now = SystemClock.elapsedRealtime();
            if (now < nextSignalSample) return;
            nextSignalSample = now + 1000L;
            Object network = value(wifiFlow);
            state.wifiLevel = integer(Refl.callByName(network, "getLevel"), TrioState.sWifiLevel);
            state.wifiPresent = network == null ? TrioState.sWifiPresent
                    : network.getClass().getSimpleName().equals("Active");
            if (!state.wifiPresent) state.wifiLevel = -1;
            TrioState.pollSimsNow();
            state.slotLevels = TrioState.sSlotLevels.clone();
            state.mobileLevel = -1;
            state.mobileType = "";
            if (Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0) {
                state.slotLevels = new int[] {-1, -1};
                state.sims = 0;
                return;
            }
            int dataId = SubscriptionManager.getDefaultDataSubscriptionId();
            try {
                SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
                List<SubscriptionInfo> subscriptions = manager == null ? null : manager.getActiveSubscriptionInfoList();
                if (subscriptions != null) for (SubscriptionInfo subscription : subscriptions) {
                    int id = subscription.getSubscriptionId();
                    int slot = subscription.getSimSlotIndex();
                    if (slot < 0 || slot >= state.slotLevels.length) continue;
                    Mobile mobile = MOBILES.get(id);
                    Object signal = mobile == null ? null : value(mobile.icon);
                    if (signal != null && signal.getClass().getName().endsWith("$Cellular")) {
                        boolean inService = bool(Refl.get(Refl.field(signal.getClass(), "isInService"), signal), false);
                        int level = ColorOsPolicy.mobileLevel(integer(Refl.callByName(signal, "getLevel"), -1),
                                integer(Refl.callByName(signal, "getNumberOfLevels"), -1), inService);
                        if (level >= 0) state.slotLevels[slot] = level;
                    } else if (signal != null) {
                        // Satellite models share the icon channel, but are not cellular bars.
                        state.slotLevels[slot] = -1;
                    }
                    if (id == dataId || (dataId < 0 && state.mobileLevel < 0)) {
                        state.mobileLevel = state.slotLevels[slot];
                        Object type = mobile == null ? null : value(mobile.type);
                        if (type instanceof CharSequence) state.mobileType = type.toString();
                    }
                }
            } catch (RuntimeException error) {
                if (TrioConfig.debugLog()) TrioHooks.log(TrioHooks.LOG_WARN, "ColorOS telephony: " + error);
            }
            state.sims = (state.slotLevels[0] >= 0 ? 1 : 0) + (state.slotLevels[1] >= 0 ? 1 : 0);
        }

        @SuppressWarnings("unchecked")
        void updateSlots(Set<String> wanted) {
            if (iconContainer == null) return;
            Object ignored = Refl.get(Refl.field(iconContainer.getClass(), "mIgnoredSlots"), iconContainer);
            if (!(ignored instanceof Set)) return;
            if (slots.sync((Set<String>) ignored, wanted)) {
                iconContainer.requestLayout();
                Refl.callByName(iconContainer, "updateStates");
            }
        }

        void releaseSlots() { updateSlots(Collections.emptySet()); }

        void syncSlots(TrioAppearance appearance) {
            if (iconContainer == null) {
                int id = meter.getResources().getIdentifier("statusIcons", "id", "com.android.systemui");
                for (ViewParent p = meter.getParent(); p instanceof ViewGroup; p = p.getParent()) {
                    View found = ((ViewGroup) p).findViewById(id);
                    if (found instanceof ViewGroup && found != meter) {
                        iconContainer = (ViewGroup) found;
                        break;
                    }
                    if (phoneBarClass.isInstance(p)) break;
                }
            }
            Set<String> wanted = new HashSet<>();
            if (iconContainer != null) for (int i = 0; i < iconContainer.getChildCount(); i++) {
                Object slot = Refl.callByName(iconContainer.getChildAt(i), "getSlot");
                if (!(slot instanceof String)) continue;
                if (slot.equals("wifi") && appearance.ownsWifi() && state.wifiLevel >= 0) wanted.add((String) slot);
                if ((slot.equals("mobile") || slot.equals("stacked_mobile"))
                        && appearance.foldsMobile() && state.sims > 0) wanted.add((String) slot);
            }
            updateSlots(wanted);
        }

        void syncExtra(TrioAppearance appearance) {
            if (extra == null) {
                Readings readings = new Readings(this);
                meter.addView(readings, 0, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                extra = readings;
            }
            ((Readings) extra).update(appearance);
        }

        @Override public void onViewAttachedToWindow(View view) {
            MAIN.post(() -> { try { apply(); } catch (Throwable error) { fail(error); } });
        }
        @Override public void onViewDetachedFromWindow(View view) {
            restore();
            iconContainer = null;
        }
    }

    private static final class Glyph extends Drawable {
        final Host host;
        final Paint layerPaint = new Paint();
        Glyph(Host host) { this.host = host; }

        @Override public void draw(Canvas canvas) {
            if (!host.active || host.failed || getBounds().isEmpty()) return;
            // The shared renderer clears its canvas. A clipped layer confines that clear to the
            // battery drawable, keeping neighbouring SystemUI icons on the parent canvas intact.
            int save = canvas.save();
            try {
                canvas.saveLayer(getBounds().left, getBounds().top,
                        getBounds().right, getBounds().bottom, layerPaint);
                canvas.translate(getBounds().left, getBounds().top);
                canvas.clipRect(0, 0, getBounds().width(), getBounds().height());
                TrioRenderer.drawState(canvas, getBounds().width(), getBounds().height(),
                        host.state, TrioConfig.get());
            } catch (Throwable error) {
                host.failed = true;
                MAIN.post(() -> host.fail(error));
            } finally { canvas.restoreToCount(save); }
        }
        @Override public int getIntrinsicWidth() { return 24; }
        @Override public int getIntrinsicHeight() { return 20; }
        @Override public void setAlpha(int alpha) { layerPaint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { layerPaint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Extra readings live inside the existing battery row and follow its visibility/animations. */
    private static final class Readings extends View {
        final Host host;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        String text = "";
        boolean signal;
        float signalWidth;
        float gap;
        float textWidth;

        Readings(Host host) { super(host.meter.getContext()); this.host = host; }

        void update(TrioAppearance appearance) {
            signal = appearance.stackedOut();
            float density = getResources().getDisplayMetrics().density;
            paint.setTextSize(10 * density * appearance.perfFit());
            paint.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL),
                    appearance.perfTypeWeight(), false));
            gap = 3 * density;
            signalWidth = signal ? appearance.outSignalSize * density : 0;
            StringBuilder value = new StringBuilder();
            if (appearance.typeOutOfRing && !host.state.mobileType.isEmpty()) value.append(host.state.mobileType);
            if (appearance.perfPower && TrioState.sChargingAttached && !Float.isNaN(TrioState.sChargePowerW)) {
                if (value.length() > 0) value.append(' ');
                value.append(String.format(java.util.Locale.ROOT, "%.1fW", TrioState.sChargePowerW));
            }
            if (appearance.perfTemp && (!appearance.perfTempChargingOnly || TrioState.sChargingAttached)
                    && TrioState.sBatteryTempC != TrioState.TEMP_UNKNOWN) {
                if (value.length() > 0) value.append(' ');
                value.append(TrioState.sBatteryTempC).append('°');
            }
            text = value.toString();
            textWidth = paint.measureText(text);
            setVisibility(signal || !text.isEmpty() ? VISIBLE : GONE);
            int width = Math.round(signalWidth + textWidth + (signalWidth > 0 && textWidth > 0 ? gap : 0) + gap);
            if (getLayoutParams().width != width) {
                getLayoutParams().width = width;
                requestLayout();
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int color = host.state.foreground();
            paint.setColor(color);
            if (signal) {
                float base = getHeight() * 0.65f;
                float step = signalWidth / 4;
                int main = host.state.slotLevels[0];
                for (int i = 0; i < 4; i++) {
                    paint.setAlpha(main > i ? 255 : 70);
                    float height = getHeight() * (0.17f + i * 0.10f);
                    canvas.drawRoundRect(i * step, base - height, i * step + step * 0.65f,
                            base, step * 0.25f, step * 0.25f, paint);
                    if (host.state.slotLevels[1] >= 0) {
                        paint.setAlpha(host.state.slotLevels[1] > i ? 255 : 70);
                        canvas.drawCircle(i * step + step * 0.32f, base + step * 0.6f, step * 0.24f, paint);
                    }
                }
            }
            paint.setAlpha(255);
            canvas.drawText(text, signalWidth + (signal && !text.isEmpty() ? gap : 0),
                    (getHeight() - paint.ascent() - paint.descent()) / 2, paint);
        }
    }
}
