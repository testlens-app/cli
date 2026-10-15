// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.client.model.Execution.OutcomeEnum;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetailsModelTest {

    @Test
    void empty_state_has_no_test() {
        var details = DetailsModel.EMPTY;

        assertThat(details.test()).isNull();
        assertThat(details.executionCount()).isZero();
    }

    @Test
    void selecting_a_test_shows_the_last_execution_by_default() {
        var model = DetailsModel.EMPTY.withTest(test(3));

        assertThat(model.executionIndex()).isEqualTo(2);
        assertThat(model.executionCount()).isEqualTo(3);
        assertThat(model.execution()).isEqualTo(execution(2));
    }

    @Test
    void switching_executions_is_clamped_to_the_executions_of_the_selected_test() {
        var model = DetailsModel.EMPTY.withTest(test(2)).withExecutionIndex(5);

        assertThat(model.executionIndex()).isEqualTo(1);

        var unchanged = model.withExecutionIndex(-1);
        assertThat(unchanged.executionIndex()).isZero();
    }

    @Test
    void switching_to_the_current_execution_keeps_the_same_snapshot() {
        var model = DetailsModel.EMPTY.withTest(test(2));

        assertThat(model.withExecutionIndex(1)).isSameAs(model);
        assertThat(DetailsModel.EMPTY.withExecutionIndex(3)).isSameAs(DetailsModel.EMPTY);
    }

    @Test
    void snapshots_with_the_same_test_and_execution_index_are_equal() {
        var test = test(2);
        assertThat(DetailsModel.EMPTY.withTest(test)).isEqualTo(DetailsModel.EMPTY.withTest(test));
        assertThat(DetailsModel.EMPTY.withTest(test)).isNotEqualTo(DetailsModel.EMPTY.withTest(test(3)));
    }

    private static ExecutedTest test(int executions) {
        var list = new ArrayList<Execution>();
        for (int i = 0; i < executions; i++) {
            list.add(execution(i));
        }
        return new ExecutedTest().displayNames(List.of("JUnit Jupiter", "FooTests", "foo()")).executions(list);
    }

    private static Execution execution(int seed) {
        return new Execution().outcome(OutcomeEnum.FAILED).stackTrace("AssertionError: " + seed);
    }
}
