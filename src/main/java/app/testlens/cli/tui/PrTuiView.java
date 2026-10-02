// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.text;
import static java.util.Objects.requireNonNull;

import dev.tamboui.toolkit.element.Element;
import org.jspecify.annotations.Nullable;

final class PrTuiView {

    private final PrTuiController controller;

    private final PrHeaderView header;
    private final @Nullable PrTestsTableView testTable;
    private final @Nullable PrDetailsView details;
    private final PrFooterView footer;

    PrTuiView(PrTuiController controller) {
        this.controller = controller;
        this.header = new PrHeaderView(controller);
        if (controller.hasTests()) {
            this.testTable = new PrTestsTableView(controller.testTableController());
            this.details = new PrDetailsView(controller.detailsController());
        } else {
            this.testTable = null;
            this.details = null;
        }
        this.footer = new PrFooterView();
    }

    Element render() {
        if (testTable == null) {
            return column(
                header.render(),
                text("No failing tests detected on pull request #" + controller.prResponse().getNumber()).fill()
            );
        }
        return column(header.render(), testTable.render(), requireNonNull(details).render(), footer.render());
    }
}
