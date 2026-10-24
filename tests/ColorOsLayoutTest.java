package io.github.yixing233.hyperduo;

import java.util.ArrayList;
import java.util.List;

/** Reference-layout relationships, with independent rows in the same shade window. */
public final class ColorOsLayoutTest {
    static final class Node {
        final String id;
        final boolean icons;
        Node parent;
        final List<Node> children = new ArrayList<>();
        Node(String id) { this(id, false); }
        Node(String id, boolean icons) { this.id = id; this.icons = icons; }
        Node add(Node child) { child.parent = this; children.add(child); return child; }
    }
    static final ColorOsLayout.Tree<Node> TREE = new ColorOsLayout.Tree<>() {
        public Node parent(Node node) { return node.parent; }
        public String id(Node node) { return node.id; }
        public int childCount(Node node) { return node.children.size(); }
        public Node childAt(Node node, int index) { return node.children.get(index); }
        public boolean isIconContainer(Node node) { return node.icons; }
    };
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Node window = new Node("shade_window");
        Node main = window.add(new Node("phone_status_bar")).add(new Node("system_icons"));
        Node mainIcons = main.add(new Node("statusIcons", true));
        Node mainMeter = main.add(new Node("battery"));
        Node keyguard = window.add(new Node("keyguard_header")).add(new Node("system_icons"));
        Node keyguardIcons = keyguard.add(new Node("statusIcons", true));
        Node keyguardMeter = keyguard.add(new Node("battery"));
        Node qs = window.add(new Node("quick_qs_status_icons"));
        Node qsIcons = qs.add(new Node("icons")).add(new Node("statusIcons", true));
        Node qsMeter = qs.add(new Node("batteryRemainingIcon"));
        check(ColorOsLayout.iconsFor(mainMeter, TREE) == mainIcons, "main uses its own icons");
        check(ColorOsLayout.iconsFor(keyguardMeter, TREE) == keyguardIcons, "lock screen uses its own icons");
        check(ColorOsLayout.iconsFor(qsMeter, TREE) == qsIcons, "QS finds the nested icons beside its battery");

        // ColorOS moves a status row to a fake container during notification transitions.
        main.parent.children.remove(main);
        window.add(new Node("simple_qs_fake_statusbar_icons_container")).add(main);
        check(ColorOsLayout.iconsFor(mainMeter, TREE) == mainIcons,
                "a relocated row keeps working without a PhoneStatusBarView ancestor");
        Node orphan = window.add(new Node("unrelated_header")).add(new Node("battery"));
        check(ColorOsLayout.iconsFor(orphan, TREE) == null, "an unbound meter cannot steal another page's icons");
        Node nested = main.add(new Node("system_icons"));
        Node nestedIcons = nested.add(new Node("statusIcons", true));
        Node nestedMeter = nested.add(new Node("battery"));
        check(ColorOsLayout.iconsFor(mainMeter, TREE) == mainIcons, "outer row excludes nested surface");
        check(ColorOsLayout.iconsFor(nestedMeter, TREE) == nestedIcons, "nearest row wins");
        qs.add(new Node("another_icon_container", true));
        check(ColorOsLayout.iconsFor(qsMeter, TREE) == null, "ambiguous rows keep their native icons");
        qs.children.remove(qs.children.size() - 1);
        qs.children.remove(qsMeter);
        main.add(qsMeter);
        check(ColorOsLayout.iconsFor(qsMeter, TREE) == mainIcons, "reparenting resolves a fresh local owner");
        System.out.println("ColorOS main/keyguard/QS row isolation and reparenting passed");
    }
}
