// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.model;

import app.testlens.cli.client.model.TestExecutionResponse;
import java.util.List;

/**
 * Model of the test executions screen: the test, its executions (newest first, as delivered by the API), and the
 * execution currently shown in the details pane. The selection index always points at the execution shown below the
 * table, so moving the table selection updates the details pane.
 */
public final class ExecutionsModel {

    /** Immutable snapshot of the mutable screen state, so views can detect changes by value. */
    public record Snapshot(TestInfo test, List<TestExecutionResponse> executions, int executionIndex) {
        public Snapshot {
            executions = List.copyOf(executions);
            executionIndex = Math.clamp(executionIndex, 0, Math.max(0, executions.size() - 1));
        }
    }

    private final TestInfo test;
    private final List<TestExecutionResponse> executions;
    private int executionIndex;

    public ExecutionsModel(TestInfo test, List<TestExecutionResponse> executions) {
        this.test = test;
        this.executions = List.copyOf(executions);
        // the executions arrive newest first, so the details pane starts on the most recent one
        this.executionIndex = 0;
    }

    public TestInfo test() {
        return test;
    }

    public List<TestExecutionResponse> executions() {
        return executions;
    }

    public Snapshot snapshot() {
        return new Snapshot(test, executions, executionIndex);
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
