package io.github.yixing233.hyperduo;

/** The diagnostics block must stay parseable and must reflect the live arrangement. */
public final class DiagnosticsTest {
    public static void main(String[] args) {
        TrioSettings settings = TrioSettings.defaults();
        // Set every switch: the defaults are not all off, and the point of the line is that it
        // reports what is actually stored rather than what looks plausible.
        settings.showValue = true;
        settings.showBolt = false;
        settings.showWifi = false;
        settings.showMobile = false;
        settings.showSim1 = true;
        settings.showSim2 = false;
        settings.ringZones = Prefs.decodeLayout("1|3|7|");
        DiagnosticsReport.Facts facts = new DiagnosticsReport.Facts(
                "0.1.1 (10008)", "OnePlus PJZ110", "17 (37)", "17.99.02 (179902)", true);

        String text = DiagnosticsReport.render(facts, settings);
        check(text.startsWith("ColorDuo diagnostics v1\n"), "the report is versioned");
        check(text.contains("module=0.1.1 (10008)\n"), "the module version is reported");
        check(text.contains("device=OnePlus PJZ110\n"), "the device is reported");
        check(text.contains("systemui=17.99.02 (179902)\n"), "the SystemUI build is reported");
        check(text.contains("framework=connected\n"), "the framework state is reported");
        check(text.contains("style=ring\n"), "the arrangement style is reported");
        check(text.contains("zones=1|3|7|\n"), "the layout is reported verbatim");
        check(text.contains("shown=value,sim1\n"), "only switched-on components are listed");
        check(text.contains("type=off\n"), "the network-type mode is reported");
        check(text.contains("charging=inherit\n"), "an absent scene reads as inherit");
        check(text.contains("quick=inherit\n"), "an absent fast-charge scene reads as inherit");
        check(text.contains("dual=off\n"), "the merged-row switch is reported");

        TrioSettings mergedRow = settings.copy();
        mergedRow.dualRow = true;
        check(DiagnosticsReport.render(facts, mergedRow).contains("dual=on\n"),
                "a merged row is reported as on");

        TrioSettings withScene = settings.copy();
        withScene.trioStyle = Prefs.STYLE_CAPSULE;
        withScene.mobileTypeMode = Prefs.MOBILE_TYPE_OUT_RING;
        withScene.chargingLayout = ChargeLayout.encode(settings);
        withScene.quickChargingLayout = ChargeLayout.encode(settings);
        String sceneText = DiagnosticsReport.render(facts, withScene);
        check(sceneText.contains("style=capsule\n"), "the capsule is named");
        check(sceneText.contains("type=outside\n"), "an outside type label is named");
        check(sceneText.contains("charging=custom\n"), "a stored scene reads as custom");
        check(sceneText.contains("quick=custom\n"), "a stored fast-charge scene reads as custom");
        check(!sceneText.contains("charging=inherit"), "the two states stay distinguishable");

        DiagnosticsReport.Facts offline = new DiagnosticsReport.Facts(
                "0.1.1 (10008)", "OnePlus PJZ110", "17 (37)", "unknown", false);
        check(DiagnosticsReport.render(offline, settings).contains("framework=absent\n"),
                "an unbound framework is reported rather than omitted");

        System.out.println("Diagnostics report format passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
