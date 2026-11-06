// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.model;

import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.PullRequestResponse;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import java.util.List;

/**
 * Model of the PR test browser TUI: the pull request data and the state of the details pane. Exposes immutable
 * snapshots ({@link #details()}), so views can render consistently and detect changes by value. Also backs the details
 * pane of the flakes screen, where each flake is presented as an {@link ExecutedTest}.
 */
public final class PrTuiModel {

    private final String title;
    private final String emptyMessage;
    private final boolean hasTests;
    private DetailsModel details = DetailsModel.EMPTY;

    public PrTuiModel(PullRequestResponse prResponse, PullRequestTestsResponse testsResponse) {
        this(
            prResponse.getTitle() + " (#" + prResponse.getNumber() + ") · " + testsResponse.getSha(),
            "No failing tests detected on pull request #" + prResponse.getNumber(),
            testsResponse.getTests()
        );
    }

    public PrTuiModel(String title, String emptyMessage, List<ExecutedTest> tests) {
        this.title = requireNonNull(title);
        this.emptyMessage = requireNonNull(emptyMessage);
        this.hasTests = !tests.isEmpty();
    }

    public boolean hasTests() {
        return hasTests;
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
        return title;
    }

    /** Message of the dialog shown when there are no tests. */
    public String emptyMessage() {
        return emptyMessage;
    }
}
