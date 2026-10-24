/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import java.util.ArrayList;
import java.util.List;

/** Resolves each meter's own icon row, including notification/QS headers and keyguard. */
final class ColorOsLayout {
    private ColorOsLayout() {}

    interface Tree<N> {
        N parent(N node);
        String id(N node);
        int childCount(N node);
        N childAt(N node, int index);
        boolean isIconContainer(N node);
    }

    private static boolean isRow(String id) {
        return "system_icons".equals(id) || "quick_qs_status_icons".equals(id);
    }

    static <N> N rowFor(N meter, Tree<N> tree) {
        for (N parent = tree.parent(meter); parent != null; parent = tree.parent(parent)) {
            if (isRow(tree.id(parent))) return parent;
        }
        return null;
    }

    static <N> N iconsFor(N meter, Tree<N> tree) {
        N row = rowFor(meter, tree);
        if (row == null) return null;
        List<N> found = new ArrayList<>(2);
        collect(row, row, meter, tree, found);
        // An ambiguous row must not hide another surface's icons.
        return found.size() == 1 ? found.get(0) : null;
    }

    private static <N> void collect(N node, N row, N meter, Tree<N> tree, List<N> found) {
        if (node == meter || found.size() > 1) return;
        if (node != row && isRow(tree.id(node))) return;
        if (tree.isIconContainer(node)) {
            found.add(node);
            return;
        }
        for (int i = 0; i < tree.childCount(node); i++) {
            collect(tree.childAt(node, i), row, meter, tree, found);
        }
    }
}
