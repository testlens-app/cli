// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.model;

import app.testlens.cli.ExecutionsLoader;
import app.testlens.cli.client.model.TestExecutionResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Model of the test executions screen: the test, its executions (newest first, as delivered by the API), and the
 * execution currently shown in the details pane. The selection index always points at the execution shown below the
 * table, so moving the table selection updates the details pane. Further pages loaded into the model append to the
 * executions, so the list can grow while the user scrolls past the end.
 */
public final class ExecutionsModel {

    /** Immutable snapshot of the mutable screen state, so views can detect changes by value. */
    public record Snapshot(TestInfo test, List<TestExecutionResponse> executions, int executionIndex, boolean hasMore) {
        public Snapshot {
            executions = List.copyOf(executions);
        }
    }

    private final TestInfo test;
    private List<TestExecutionResponse> executions;
    private boolean hasMore;
    private int executionIndex;

    public ExecutionsModel(TestInfo test, List<TestExecutionResponse> executions, boolean hasMore) {
        this.test = test;
        this.executions = List.copyOf(executions);
        this.hasMore = hasMore;
        // the executions arrive newest first, so the details pane starts on the most recent one
        this.executionIndex = 0;
    }

    public TestInfo test() {
        return test;
    }

    public List<TestExecutionResponse> executions() {
        return executions;
    }

    public boolean hasMoreExecutions() {
        return hasMore;
    }

    /** Appends a further page of executions to the list, keeping the selection at its current execution. */
    public void append(ExecutionsLoader.Fetched fetched) {
        var appended = new ArrayList<>(executions);
        appended.addAll(fetched.executions());
        executions = List.copyOf(appended);
        hasMore = fetched.hasMore();
    }

    public Snapshot snapshot() {
        return new Snapshot(test, executions, executionIndex, hasMore);
    }

    public TestExecutionResponse execution() {
        return executions.get(executionIndex);
    }

    /** Switches the shown execution; the index is clamped to the available executions. */
    public void selectExecution(int index) {
        executionIndex = Math.clamp(index, 0, executions.size() - 1);
    }

    public void selectPreviousExecution() {
        selectExecution(executionIndex - 1);
    }

    public void selectNextExecution() {
        selectExecution(executionIndex + 1);
    }
}
