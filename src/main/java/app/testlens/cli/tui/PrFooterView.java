// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import org.jspecify.annotations.Nullable;

final class PrFooterView {

    Element render(@Nullable String searchQuery) {
        if (searchQuery != null) {
            // issue #29: while searching, the footer shows the typed search term
            return text("/" + searchQuery + " ▌  (Enter keep, Esc cancel)").reversed().length(1);
        }
        return text(
            "↑/↓ or j/k select test/scroll details · ←/→ or h/l select execution · Enter/Esc/Tab switch pane · / search · q quit"
        )
            .dim()
            .length(1);
    }
}
