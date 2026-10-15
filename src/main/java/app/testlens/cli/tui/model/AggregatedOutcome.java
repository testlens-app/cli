// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.model;

import app.testlens.cli.client.model.Execution;
import app.testlens.cli.client.model.Execution.OutcomeEnum;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregated outcome of all executions of a test. A test is flaky if it succeeded in at least one execution and failed
 * in another.
 */
public record AggregatedOutcome(OutcomeEnum max, boolean flaky) {

    public static AggregatedOutcome from(List<Execution> executions) {
        var outcomes = executions.stream().map(Execution::getOutcome).collect(Collectors.toSet());
        var max = Collections.max(outcomes);
        var flaky = max == OutcomeEnum.FAILED && outcomes.contains(OutcomeEnum.SUCCESSFUL);
        return new AggregatedOutcome(max, flaky);
    }
}
