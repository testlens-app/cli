// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static dev.tamboui.toolkit.Toolkit.text;

import app.testlens.cli.tui.controller.PrTuiController;
import dev.tamboui.toolkit.element.Element;

public final class PrHeaderView {

    private final PrTuiController controller;

    public PrHeaderView(PrTuiController controller) {
        this.controller = controller;
    }

    public Element render() {
        var pr = controller.prResponse();
        return text(pr.getTitle() + " (#" + pr.getNumber() + ") · " + controller.testsResponse().getSha())
            .bold()
            .cyan()
            .length(1);
    }
}
