// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;

final class PrHeaderView {

    private final PrTuiController controller;

    PrHeaderView(PrTuiController controller) {
        this.controller = controller;
    }

    Element render() {
        var pr = controller.prResponse();
        return text(pr.getTitle() + " (#" + pr.getNumber() + ") · " + controller.testsResponse().getSha())
            .bold()
            .cyan()
            .length(1);
    }
}
