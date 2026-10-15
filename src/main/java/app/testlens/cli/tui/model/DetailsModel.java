// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.model;

import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot of the details pane state: the selected test and which of its executions is shown. Instances are
 * value objects, so views can detect changes by {@code equals} instead of identity.
 */
public record DetailsModel(@Nullable ExecutedTest test, int executionIndex) {

    /** State of the details pane before any test was selected. */
    public static final DetailsModel EMPTY = new DetailsModel(null, 0);

    public DetailsModel {
        if (test != null) {
            executionIndex = Math.clamp(executionIndex, 0, test.getExecutions().size() - 1);
        }
    }

    /** Selects a test; the last execution is shown by default. */
    public DetailsModel withTest(ExecutedTest test) {
        return new DetailsModel(requireNonNull(test), test.getExecutions().size() - 1);
    }

    /** Switches to the execution with the given index, clamped to the executions of the selected test. */
    public DetailsModel withExecutionIndex(int index) {
        if (test == null) {
            return this;
        }
        var updated = new DetailsModel(test, index);
        return updated.equals(this) ? this : updated;
    }

    public int executionCount() {
        return test == null ? 0 : test.getExecutions().size();
    }

    /** The execution shown in the details pane. */
    public Execution execution() {
        return requireNonNull(test, "no test selected").getExecutions().get(executionIndex);
    }
}
