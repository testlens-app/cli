// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import org.jspecify.annotations.Nullable;

final class PrFooterView {

    enum SearchState {
        NONE,
        SEARCHING,
        COMMITTED
    }

    Element render(@Nullable String searchQuery, SearchState searchState) {
        if (searchState == SearchState.SEARCHING) {
            // issue #29: while searching, the footer shows the typed search term
            return text("/" + searchQuery + " ▌  (Enter keep, Esc cancel, Tab switch pane)").reversed().length(1);
        }
        if (searchState == SearchState.COMMITTED) {
            return text("/" + searchQuery + "  (Esc reset search)").reversed().length(1);
        }
        return text(
            "↑/↓ or j/k select test/scroll details · ←/→ or h/l select execution · Tab switch pane · / search · q quit"
        )
            .dim()
            .length(1);
    }
}
