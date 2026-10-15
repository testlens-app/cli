// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.model;

import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.PullRequestResponse;
import app.testlens.cli.client.model.PullRequestTestsResponse;

/**
 * Model of the PR test browser TUI: the pull request data and the state of the details pane. Exposes immutable
 * snapshots ({@link #details()}), so views can render consistently and detect changes by value.
 */
public final class PrTuiModel {

    private final PullRequestResponse prResponse;
    private final PullRequestTestsResponse testsResponse;
    private DetailsModel details = DetailsModel.EMPTY;

    public PrTuiModel(PullRequestResponse prResponse, PullRequestTestsResponse testsResponse) {
        this.prResponse = requireNonNull(prResponse);
        this.testsResponse = requireNonNull(testsResponse);
    }

    public boolean hasTests() {
        return !testsResponse.getTests().isEmpty();
    }

    /** Current state of the details pane as an immutable snapshot. */
    public DetailsModel details() {
        return details;
    }

    /** Selects the given test in the details pane; the last execution is shown by default. */
    public void selectTest(ExecutedTest test) {
        details = details.withTest(test);
    }

    /** Switches the execution shown in the details pane; the index is clamped to the selected test. */
    public void selectExecution(int index) {
        details = details.withExecutionIndex(index);
    }

    public String getTitle() {
        return prResponse.getTitle() + " (#" + prResponse.getNumber() + ") · " + testsResponse.getSha();
    }

    public int getPrNumber() {
        return requireNonNull(prResponse.getNumber());
    }
}
