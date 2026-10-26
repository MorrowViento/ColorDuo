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
 * The arrangement this fork recommends when the user asks for one.
 *
 * <p>An empty drawing page is the honest first install - upstream deliberately ships no
 * arrangement - but "I just want it to look like the screenshots" is a real request, and
 * asking a new user to place four pieces before seeing anything is a poor first run.
 * This is that one-click profile, kept behind an explicit button rather than applied on
 * first launch, so an existing arrangement is never overwritten without being asked.
 *
 * <p>The values are the ColorOS 16 branch's verified profile: the network type and
 * Wi-Fi share the top band, the percentage sits in the middle, and SIM 1's dots fill the
 * bottom. Fast charging moves the bolt in front of the percentage, which is the state
 * most worth reading at a glance. Ordinary charging inherits the base arrangement.
 */
public final class RecommendedLayout {
    /** Ring: type + Wi-Fi on top, percentage in the middle, SIM 1 at the bottom. */
    private static final String RING = "5,1|3|7|";
    /** Capsule: the same reading, arranged across its four cells. */
    private static final String CAPSULE = "5,1|4,3|7|6";
    /** Fast charging on the ring: bolt before the percentage in the middle band. */
    private static final String QUICK_RING = "1;31;1;0;5,1|4,3|7|";
    /** Fast charging on the capsule. */
    private static final String QUICK_CAPSULE = "1;31;1;0;5,1|4,3|7|6";

    private RecommendedLayout() {
    }

    /**
     * A copy of {@code base} with the recommended arrangement for its current style.
     *
     * <p>Colours, sizes, weights and every other appearance value are left alone: the
     * recommendation is about what is placed where, not about overriding a user's look.
     */
    public static TrioSettings of(TrioSettings base) {
        TrioSettings s = base.copy();
        boolean capsule = s.trioStyle == Prefs.STYLE_CAPSULE;
        s.showWifi = true;
        s.showMobile = true;
        s.showValue = true;
        s.showBolt = true;
        s.showSim1 = true;
        s.showSim2 = false;
        s.dualSim = false;
        s.valueCentred = false;
        s.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING;
        s.typeHideOnWifi = true;
        s.ringZones = Prefs.decodeLayout(capsule ? CAPSULE : RING);
        // Ordinary charging keeps the base look; only fast charging gets its own.
        s.chargingLayout = "";
        s.quickChargingLayout = ChargeLayout.normalize(capsule ? QUICK_CAPSULE : QUICK_RING);
        return s;
    }
}
