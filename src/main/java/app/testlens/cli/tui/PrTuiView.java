package app.testlens.cli.tui;

import static dev.tamboui.toolkit.Toolkit.column;
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
        if (!controller.hasTests()) {
            return column(header.render(), text("No failed or flaky tests.").fill());
        }
        return column(header.render(), testTable.render(), details.render(), footer.render());
    }

}
