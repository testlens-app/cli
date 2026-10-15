// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.controller;

import app.testlens.cli.client.model.PullRequestResponse;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import app.testlens.cli.tui.model.PrTuiModel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class PrTuiController {

    private static final List<String> VIEW_IDS = List.of(PrTestsTableController.VIEW_ID, PrDetailsController.VIEW_ID);

    private final PrTuiModel model;
    private final FocusManager focusManager;
    private final PrDetailsController detailsController;
    private final PrTestsTableController testTableController;

    public PrTuiController(
        PullRequestResponse prResponse,
        PullRequestTestsResponse testsResponse,
        FocusManager focusManager
    ) {
        this.model = new PrTuiModel(prResponse, testsResponse);
        this.focusManager = focusManager;
        this.detailsController = new PrDetailsController(
            keyEvent -> navigate(keyEvent, PrDetailsController.VIEW_ID),
            focusManager,
            model
        );
        // created after the details controller, so the initial test selection reaches the details pane
        this.testTableController = new PrTestsTableController(
            keyEvent -> navigate(keyEvent, PrTestsTableController.VIEW_ID),
            focusManager,
            testsResponse,
            detailsController::selectTest
        );
    }

    public PrTuiModel model() {
        return model;
    }

    public boolean hasTests() {
        return model.hasTests();
    }

    public boolean isSearching() {
        return testTableController.isSearching();
    }

    public PrTestsTableController testTableController() {
        return testTableController;
    }

    public PrDetailsController detailsController() {
        return detailsController;
    }

    public boolean isFocused(String id) {
        return focusManager.isFocused(id);
    }

    public EventResult navigate(KeyEvent event, String viewId) {
        if (!isFocused(viewId)) {
            return EventResult.UNHANDLED;
        }
        var index = VIEW_IDS.indexOf(viewId);
        if (index == -1) {
            return EventResult.UNHANDLED;
        }
        String target = null;
        if (event.isKey(KeyCode.TAB) && !event.hasShift()) {
            target = nextViewId(index);
        } else if (event.isKey(KeyCode.TAB) && event.hasShift()) {
            target = previousViewId(index);
        }
        if (target == null) {
            return EventResult.UNHANDLED;
        }
        focusManager.setFocus(target);
        return EventResult.HANDLED;
    }

    private static @Nullable String previousViewId(int index) {
        return index > 0 ? VIEW_IDS.get(index - 1) : null;
    }

    private static @Nullable String nextViewId(int index) {
        return index + 1 < VIEW_IDS.size() ? VIEW_IDS.get(index + 1) : null;
    }
}
