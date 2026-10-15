// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static dev.tamboui.style.Color.YELLOW;
import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;

import app.testlens.cli.tui.controller.PrTuiController;
import app.testlens.cli.tui.model.TestListModel;
import dev.tamboui.style.RichTextState;
import dev.tamboui.toolkit.element.Element;
import org.jspecify.annotations.Nullable;

public final class PrTuiView {

    private final PrTuiController controller;

    private final PrHeaderView header;
    private final PrTestsTableView testTable;
    private final PrDetailsView details;
    private final PrFooterView footer;

    public PrTuiView(PrTuiController controller) {
        this.controller = controller;
        this.header = new PrHeaderView(controller.model().getTitle());
        this.testTable = new PrTestsTableView(controller.testTableController());
        this.details = new PrDetailsView(controller.detailsController(), controller.model());
        this.footer = new PrFooterView();
    }

    public Element render() {
        var tests = controller.testTableController().model();
        var query = activeSearchQuery(tests);
        var searchState = tests.isSearching()
            ? PrFooterView.SearchState.SEARCHING
            : tests.isSearchCommitted() ? PrFooterView.SearchState.COMMITTED : PrFooterView.SearchState.NONE;
        var content = column(header.render(), testTable.render(), details.render(), footer.render(query, searchState));
        if (controller.hasTests()) {
            return content;
        }
        var message = "No failing tests detected on pull request #" + controller.model().getPrNumber();
        return stack(
            content,
            dialog(text(message)).rounded().borderColor(YELLOW).width(message.length() + 4)
        );
    }

    public RichTextState detailsState() {
        return details.detailsState();
    }

    private @Nullable String activeSearchQuery(TestListModel tests) {
        return tests.isSearching() || tests.isSearchCommitted() ? tests.searchQuery() : null;
    }
}
