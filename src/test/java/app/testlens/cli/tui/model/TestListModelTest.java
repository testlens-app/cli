// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.client.model.Execution.OutcomeEnum;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestListModelTest {

    private final List<ExecutedTest> selections = new ArrayList<>();

    private TestListModel model(String... testNames) {
        var tests = new ArrayList<ExecutedTest>();
        for (var name : testNames) {
            tests.add(
                new ExecutedTest()
                    .displayNames(List.of("JUnit Jupiter", "FooTests", name))
                    .executions(List.of(new Execution().outcome(OutcomeEnum.FAILED)))
            );
        }
        return new TestListModel(tests, selections::add);
    }

    @Test
    void starts_with_the_first_test_selected_and_notifies_the_listener() {
        var model = model("foo()", "bar()");

        assertThat(model.selectedIndex()).isZero();
        assertThat(model.selectedTest()).isSameAs(model.tests().getFirst());
        assertThat(selections).hasSize(1);
    }

    @Test
    void selecting_the_same_index_does_not_notify() {
        var model = model("foo()", "bar()");
        selections.clear();

        assertThat(model.selectTest(0)).isFalse();
        assertThat(selections).isEmpty();

        assertThat(model.selectTest(1)).isTrue();
        assertThat(selections).hasSize(1);
    }

    @Test
    void searching_filters_continuously_and_selects_the_best_match() {
        var model = model("alphatest()", "alphabet()", "beta()");

        model.startSearch();
        assertThat(model.isSearching()).isTrue();
        assertThat(model.tests()).hasSize(3);

        model.appendChar('b');
        assertThat(model.tests()).hasSize(2);
        assertThat(model.selectedIndex()).isZero();

        // commits the search; the filter stays
        model.commitSearch();
        assertThat(model.isSearching()).isFalse();
        assertThat(model.isSearchCommitted()).isTrue();
        assertThat(model.tests()).hasSize(2);
    }

    @Test
    void resetting_a_committed_search_restores_the_full_list() {
        var model = model("foo()", "bar()");

        model.startSearch();
        model.appendChar('x');
        assertThat(model.tests()).isEmpty();
        model.commitSearch();

        model.resetSearch();
        assertThat(model.isSearchCommitted()).isFalse();
        assertThat(model.searchQuery()).isEmpty();
        assertThat(model.tests()).hasSize(2);
    }

    @Test
    void cancelling_a_search_restores_the_previous_selection() {
        var model = model("foo()", "bar()");

        model.startSearch();
        model.appendChar('b');
        assertThat(model.selectedIndex()).isZero();

        model.cancelSearch();
        assertThat(model.isSearching()).isFalse();
        assertThat(model.searchQuery()).isEmpty();
        assertThat(model.tests()).hasSize(2);
        // the selection followed the test (bar), which is at index 1 in the full list
        assertThat(model.selectedIndex()).isEqualTo(1);
    }

    @Test
    void backspace_removes_the_last_character() {
        var model = model("alphatest()", "beta()");

        model.startSearch();
        model.appendChar('a');
        model.appendChar('l');
        assertThat(model.searchQuery()).isEqualTo("al");
        // "al" only matches "alphatest()"; "beta()" has no "l"
        assertThat(model.tests()).hasSize(1);

        model.backspace();
        assertThat(model.searchQuery()).isEqualTo("a");
        assertThat(model.tests()).hasSize(2);
    }
}
