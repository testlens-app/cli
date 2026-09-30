package app.testlens.cli.tui;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;

final class PrFooterView {

    Element render() {
        return text("↑/↓ select test/scroll details · ←/→ select execution · Enter/Esc/Tab switch pane · q quit")
            .dim()
            .length(1);
    }
}
