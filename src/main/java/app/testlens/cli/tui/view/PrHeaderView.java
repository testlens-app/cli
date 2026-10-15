// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;

public final class PrHeaderView {

    private final String title;

    public PrHeaderView(String title) {
        this.title = title;
    }

    public Element render() {
        return text(title)
            .bold()
            .cyan()
            .length(1);
    }

}
