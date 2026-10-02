// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static dev.tamboui.style.Color.YELLOW;
import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;

final class PrTuiView {

    private final PrTuiController controller;

    private final PrHeaderView header;
    private final PrTestsTableView testTable;
    private final PrDetailsView details;
    private final PrFooterView footer;

    PrTuiView(PrTuiController controller) {
        this.controller = controller;
        this.header = new PrHeaderView(controller);
        this.testTable = new PrTestsTableView(controller.testTableController());
        this.details = new PrDetailsView(controller.detailsController());
        this.footer = new PrFooterView();
    }

    Element render() {
        var content = column(header.render(), testTable.render(), details.render(), footer.render());
        if (controller.hasTests()) {
            return content;
        }
        var message = "No failing tests detected on pull request #" + controller.prResponse().getNumber();
        return stack(
            content,
            dialog(text(message)).rounded().borderColor(YELLOW).width(message.length() + 4)
        );
    }
}
