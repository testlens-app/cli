// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.controller;

import static java.util.Comparator.comparing;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.client.model.FlakeResponse;
import app.testlens.cli.client.model.TestExecutionResponse;
import app.testlens.cli.tui.model.PrTuiModel;
import app.testlens.cli.tui.model.TestInfo;
import app.testlens.cli.tui.view.FlakesView;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.table.TableState;
import java.util.List;
import java.util.Objects;

/**
 * Input handling of the flakes screen: the flakes table and the tabbed details pane of the PR TUI, which shows the
 * executions of the selected flake. Each flake is presented to the details pane as an {@link ExecutedTest}.
 */
public final class FlakesController {

    public static final String VIEW_ID = "flakes";

    private final FocusManager focusManager;
    private final List<FlakeResponse> flakes;
    private final List<ExecutedTest> tests;
    private final PrTuiModel model;
    private final PrDetailsController detailsController;
    private final TableState state = new TableState();
    private int selectedIndex = -1;

    public FlakesController(FocusManager focusManager, TestInfo test, List<FlakeResponse> flakes) {
        this.focusManager = focusManager;
        // a flake without executions cannot be shown in the tabbed details pane
        this.flakes = flakes.stream().filter(flake -> !flake.getExecutions().isEmpty()).toList();
        this.tests = this.flakes.stream().map(flake -> toExecutedTest(test, flake)).toList();
        this.model = new PrTuiModel(test.displayName(), "", tests);
        this.detailsController = new PrDetailsController(this::navigate, focusManager, model);
        if (!tests.isEmpty()) {
            select(0);
        }
    }

    /**
     * Presents a flake as a test with executions: the job is the commit the flake happened on, and the executions are
     * in chronological order.
     */
    public static ExecutedTest toExecutedTest(TestInfo test, FlakeResponse flake) {
        return new ExecutedTest()
            .job(abbreviatedSha(flake.getSha()) + " @ " + flake.getBranch())
            .workUnit(flake.getWorkUnit())
            .uniqueId(test.uniqueId())
            .displayNames(test.displayNames())
            .executions(
                flake.getExecutions()
                    .stream()
                    .sorted(comparing(TestExecutionResponse::getStartTime))
                    .map(FlakesController::toExecution)
                    .toList()
            );
    }

    private static Execution toExecution(TestExecutionResponse execution) {
        return new Execution()
            .outcome(Execution.OutcomeEnum.valueOf(execution.getOutcome().name()))
            .startTime(execution.getStartTime())
            .durationMillis(execution.getDurationMillis())
            .htmlUrl(Objects.requireNonNullElse(execution.getHtmlUrl(), ""))
            .stackTrace(execution.getStackTrace())
            .comparisonFailure(execution.getComparisonFailure())
            .buildEnvironment(execution.getBuildEnvironment());
    }

    public static String abbreviatedSha(String sha) {
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }

    public List<FlakeResponse> flakes() {
        return flakes;
    }

    /** The flakes as presented to the details pane, index-aligned with {@link #flakes()}. */
    public List<ExecutedTest> tests() {
        return tests;
    }

    public PrTuiModel model() {
        return model;
    }

    public PrDetailsController detailsController() {
        return detailsController;
    }

    public TableState tableState() {
        return state;
    }

    public boolean isFocused() {
        return focusManager.isFocused(VIEW_ID);
    }

    public EventResult handleKey(KeyEvent event) {
        if (navigate(event).isHandled()) {
            return EventResult.HANDLED;
        }
        if (!isFocused() || tests.isEmpty()) {
            return EventResult.UNHANDLED;
        }
        int target;
        if (event.isUp() || event.isChar('k')) {
            target = selectedIndex - 1;
        } else if (event.isDown() || event.isChar('j')) {
            target = selectedIndex + 1;
        } else if (event.isPageUp()) {
            target = selectedIndex - FlakesView.MAX_VISIBLE_FLAKES;
        } else if (event.isPageDown()) {
            target = selectedIndex + FlakesView.MAX_VISIBLE_FLAKES;
        } else if (event.isHome()) {
            target = 0;
        } else if (event.isEnd()) {
            target = tests.size() - 1;
        } else {
            return EventResult.UNHANDLED;
        }
        select(Math.clamp(target, 0, tests.size() - 1));
        return EventResult.HANDLED;
    }

    private void select(int index) {
        if (index == selectedIndex) {
            return;
        }
        selectedIndex = index;
        state.select(index);
        detailsController.selectTest(tests.get(index));
    }

    // Tab moves from the table to the details pane, Shift+Tab back
    private EventResult navigate(KeyEvent event) {
        if (!event.isKey(KeyCode.TAB)) {
            return EventResult.UNHANDLED;
        }
        if (!event.hasShift() && isFocused()) {
            focusManager.setFocus(PrDetailsController.VIEW_ID);
            return EventResult.HANDLED;
        }
        if (event.hasShift() && focusManager.isFocused(PrDetailsController.VIEW_ID)) {
            focusManager.setFocus(VIEW_ID);
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }
}
