// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;

final class PrFooterView {

    Element render() {
        return text(
            "↑/↓ or j/k select test/scroll details · ←/→ or h/l select execution · Enter/Esc/Tab switch pane · q quit"
        )
            .dim()
            .length(1);
    }
}
