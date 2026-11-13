// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import app.testlens.cli.tui.ScreenRouter.Screen;
import app.testlens.cli.tui.controller.PrTestsTableController;
import app.testlens.cli.tui.controller.PrTuiController;
import app.testlens.cli.tui.view.PrTuiView;
import dev.tamboui.toolkit.element.Element;

/**
 * The pull request screen of the unified TUI: the tests of the PR and the tabbed details pane of the selected test.
 * Reached via {@code testlens pr <number>} and pushed by the home screen's PR prompt.
 */
public final class PrScreen implements Screen {

    private final PrTuiController controller;
    private final PrTuiView view;

    public PrScreen(PrTuiController controller, PrTuiView view) {
        this.controller = controller;
        this.view = view;
    }

    @Override
    public String focusId() {
        return PrTestsTableController.VIEW_ID;
    }

    @Override
    public Element render() {
        return view.render();
    }

    @Override
    public boolean modalInputActive() {
        // while the test table search is open, q must be typed into the search, not quit the TUI
        return controller.isSearching();
    }
}
