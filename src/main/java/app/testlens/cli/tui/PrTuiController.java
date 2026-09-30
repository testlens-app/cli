package app.testlens.cli.tui;

import app.testlens.cli.client.model.PullRequestResponse;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class PrTuiController {

    private static final List<String> VIEW_IDS = List.of(PrTestsTableController.VIEW_ID, PrDetailsController.VIEW_ID);

    private final PullRequestResponse prResponse;
    private final PullRequestTestsResponse testsResponse;
    private final FocusManager focusManager;
    private final PrDetailsController detailsController;
    private final PrTestsTableController testTableController;

    PrTuiController(PullRequestResponse prResponse, PullRequestTestsResponse testsResponse, FocusManager focusManager) {
        this.prResponse = prResponse;
        this.testsResponse = testsResponse;
        this.focusManager = focusManager;
        this.detailsController = new PrDetailsController(
            keyEvent -> navigate(keyEvent, PrDetailsController.VIEW_ID),
            focusManager
        );
        this.testTableController = new PrTestsTableController(
            keyEvent -> navigate(keyEvent, PrTestsTableController.VIEW_ID),
            focusManager,
            testsResponse,
            detailsController::selectTest
        );
    }

    PullRequestResponse prResponse() {
        return prResponse;
    }

    PullRequestTestsResponse testsResponse() {
        return testsResponse;
    }

    boolean hasTests() {
        return !testsResponse.getTests().isEmpty();
    }

    PrTestsTableController testTableController() {
        return testTableController;
    }

    PrDetailsController detailsController() {
        return detailsController;
    }

    boolean isFocused(String id) {
        return focusManager.isFocused(id);
    }

    EventResult navigate(KeyEvent event, String viewId) {
        if (!isFocused(viewId)) {
            return EventResult.UNHANDLED;
        }
        var index = VIEW_IDS.indexOf(viewId);
        if (index == -1) {
            return EventResult.UNHANDLED;
        }
        String target = null;
        if (event.isConfirm()) {
            target = nextViewId(index);
        } else if (event.isCancel()) {
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
