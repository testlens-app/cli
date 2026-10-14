// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.model.ComparisonFailure;
import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.client.model.Execution.OutcomeEnum;
import app.testlens.cli.client.model.PullRequestResponse;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import app.testlens.cli.tui.controller.PrDetailsController;
import app.testlens.cli.tui.controller.PrTestsTableController;
import app.testlens.cli.tui.controller.PrTuiController;
import app.testlens.cli.tui.view.PrTuiUtils;
import app.testlens.cli.tui.view.PrTuiView;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.ElementRegistry;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.EventRouter;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.RenderThreadTestHelper;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrTuiTest {

    private static final String JOB_URL = "https://github.com/some-org/some-repo/actions/runs/1/job/101?pr=42";

    private static final OffsetDateTime START_TIME = OffsetDateTime.parse("2026-01-02T03:04:05Z");

    private static final KeyEvent LEFT = new KeyEvent(KeyCode.LEFT, KeyModifiers.NONE, 0);

    @BeforeEach
    void markAsRenderThread() {
        RenderThreadTestHelper.markAsRenderThread();
    }

    @AfterEach
    void clearRenderThread() {
        RenderThreadTestHelper.clearRenderThread();
    }

    @Test
    void shows_executions_and_details_of_selected_test() {
        var controller = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            new PullRequestTestsResponse().sha("SHA")
                .addTestsItem(
                    executedTest(
                        "foo()",
                        execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL)
                            .comparisonFailure(new ComparisonFailure().expected("foo bar").actual("foo baz"))
                            .stackTrace("AssertionError: foo")
                    )
                )
                .addTestsItem(
                    executedTest(
                        "bar()",
                        execution(OutcomeEnum.SUCCESSFUL).htmlUrl(JOB_URL),
                        execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL).stackTrace("AssertionError: bar")
                    )
                ),
            new FocusManager()
        );
        var view = new PrTuiView(controller);

        assertThat(render(view))
            .contains(
                "Some PR (#42) · SHA",
                "Time:  2026-01-02 03:04:05Z - 03:04:06Z (1s 500ms)",
                "│  Outcome   Job          Project/Task   Test",
                "│> FAILED    CI / build   :test          FooTests > foo()",
                "│  FLAKY     CI / build   :test          FooTests > bar()",
                "Test:  FooTests > foo()",
                "#1 FAILED",
                "Expected vs. actual",
                "- foo bar",
                "+ foo baz",
                "Stack trace",
                "AssertionError: foo"
            )
            .doesNotContain("#2");

        controller.testTableController().selectTest(1);
        // the last execution is selected by default
        assertThat(render(view)).contains("#1 SUCCESSFUL", "#2 FAILED", "AssertionError: bar");
        assertThat(controller.detailsController().tabsState().selected()).isEqualTo(1);

        controller.detailsController().handleKey(LEFT);
        assertThat(controller.detailsController().tabsState().selected()).isZero();
        assertThat(render(view)).contains("#1 SUCCESSFUL").doesNotContain("Outcome: ", "AssertionError");
    }

    @Test
    void truncates_columns_to_fit_narrow_terminals() {
        var controller = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            new PullRequestTestsResponse().sha("SHA")
                .addTestsItem(
                    new ExecutedTest()
                        .job("CI / Cross-Version / OpenJDK 27 (ea)")
                        .workUnit(":platform-tests:test")
                        .uniqueId("[engine:junit-jupiter]")
                        .displayNames(
                            List.of(
                                "JUnit Jupiter",
                                "ConfigurationMetadataAnnotationProcessorTests",
                                "ConfigurationParameter",
                                "interfaceType()"
                            )
                        )
                        .executions(List.of(execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL)))
                ),
            new FocusManager()
        );
        var view = new PrTuiView(controller);

        assertThat(render(view, 70))
            .contains("│  Outcome   Job                Project/Ta   Test")
            .contains("│> FAILED    CI / Cro…27 (ea)   :plat…test   ConfigurationMetadataAn…")
            .contains("│Test:  ConfigurationMetadataAnnotationProcessorTests > …erfaceType()│");
    }

    @Test
    void shows_a_scrollbar_when_not_all_tests_fit() {
        var fittingResponse = new PullRequestTestsResponse().sha("SHA");
        for (int i = 0; i < 10; i++) {
            fittingResponse
                .addTestsItem(executedTest("test" + i + "()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL)));
        }
        var fittingController = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            fittingResponse,
            new FocusManager()
        );
        assertThat(render(new PrTuiView(fittingController))).doesNotContain("█");

        var response = new PullRequestTestsResponse().sha("SHA");
        for (int i = 0; i < 15; i++) {
            response
                .addTestsItem(executedTest("test" + i + "()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL)));
        }
        var controller =
            new PrTuiController(new PullRequestResponse().number(42).title("Some PR"), response, new FocusManager());
        var view = new PrTuiView(controller);

        assertThat(render(view)).contains("█");
        assertThat(render(view))
            .contains("FooTests > test0()")
            .doesNotContain("FooTests > test14()");

        controller.testTableController().selectTest(14);
        assertThat(render(view))
            .doesNotContain("FooTests > test0()")
            .contains("FooTests > test14()");
    }

    @Test
    void navigates_between_table_and_details_with_tab() {
        var focusManager = new FocusManager();
        var eventRouter = new EventRouter(focusManager, new ElementRegistry());
        var context = new DefaultRenderContext(focusManager, eventRouter);
        var controller = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            new PullRequestTestsResponse().sha("SHA")
                .addTestsItem(executedTest("foo()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL))),
            focusManager
        );
        var view = new PrTuiView(controller);

        renderFrame(view, 120, context, focusManager, eventRouter);
        assertThat(focusManager.focusOrder()).containsExactly("tests", "details");
        assertThat(focusManager.focusedId()).isEqualTo("tests");

        // Enter and Esc no longer switch panes
        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.ENTER))).isEqualTo(EventResult.UNHANDLED);
        eventRouter.route(KeyEvent.ofKey(KeyCode.ESCAPE));
        // the framework clears focus on Esc, but the pane is not switched
        assertThat(focusManager.focusedId()).isNotEqualTo("details");
        focusManager.setFocus(PrTestsTableController.VIEW_ID);
        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.TAB))).isEqualTo(EventResult.HANDLED);
        assertThat(focusManager.focusedId()).isEqualTo("details");
        var detailsFocusedFrame = renderFrame(view, 120, context, focusManager, eventRouter);
        assertThat(detailsFocusedFrame.get(0, 6).style().fg()).contains(Color.CYAN);
        renderFrame(view, 120, context, focusManager, eventRouter);
        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT))).isEqualTo(EventResult.HANDLED);
        assertThat(focusManager.focusedId()).isEqualTo("tests");
        renderBuffer(view, 120, context);
    }

    @Test
    void abbreviates_test_names_keeping_the_outermost_segment() {
        var displayNames = List.of("JUnit Jupiter", "FooTests", "Nested", "bar()");

        assertThat(PrTuiUtils.abbreviatedTestName(displayNames, 30)).isEqualTo("FooTests > Nested > bar()");
        assertThat(PrTuiUtils.abbreviatedTestName(displayNames, 22)).isEqualTo("FooTests > …ed > bar()");
        assertThat(PrTuiUtils.abbreviatedTestName(displayNames, 12)).isEqualTo("FooTests > …");
        assertThat(PrTuiUtils.abbreviatedTestName(displayNames, 8)).isEqualTo("FooTest…");
        assertThat(PrTuiUtils.abbreviatedTestName(List.of("JUnit Jupiter", "FooTests"), 5)).isEqualTo("FooT…");
    }

    @Test
    void colors_execution_tabs_by_outcome() {
        var controller = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            new PullRequestTestsResponse().sha("SHA")
                .addTestsItem(
                    executedTest(
                        "foo()",
                        execution(OutcomeEnum.SUCCESSFUL).htmlUrl(JOB_URL),
                        execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL)
                    )
                ),
            new FocusManager()
        );
        var view = new PrTuiView(controller);
        var buffer = renderBuffer(view, 120);
        var screen = render(view).lines().toList();
        var row = screen.stream().filter(line -> line.contains("#1 SUCCESSFUL")).findFirst().orElseThrow();
        assertThat(row).startsWith("╭ #1 SUCCESSFUL ─ #2 FAILED");
        var y = screen.indexOf(row);
        assertThat(buffer.get(row.indexOf("#1"), y).style().fg()).contains(Color.GREEN);
        assertThat(buffer.get(row.indexOf("#1") - 1, y).style().fg()).contains(Color.GREEN);
        assertThat(buffer.get(row.indexOf("#2"), y).style().fg()).contains(Color.RED);
        var selectedPadding = buffer.get(row.indexOf("#2") - 1, y).style();
        assertThat(selectedPadding.fg()).contains(Color.RED);
        assertThat(selectedPadding.effectiveModifiers()).contains(Modifier.REVERSED);
    }

    @Test
    void doesnt_crash_for_PRs_without_tests() {
        var controller = new PrTuiController(
            new PullRequestResponse().number(3).title("PR without Tests"),
            new PullRequestTestsResponse().sha("SHA"),
            new FocusManager()
        );
        var view = new PrTuiView(controller);

        assertThat(render(view)).contains("No failing tests detected on pull request #3");
    }

    @Test
    void keys_are_handled_without_crashing_for_PRs_without_tests() {
        var focusManager = new FocusManager();
        var controller = new PrTuiController(
            new PullRequestResponse().number(3).title("PR without Tests"),
            new PullRequestTestsResponse().sha("SHA"),
            focusManager
        );
        var view = new PrTuiView(controller);

        assertThat(controller.testTableController().handleKey(new KeyEvent(KeyCode.DOWN, KeyModifiers.NONE, 0)))
            .isEqualTo(EventResult.UNHANDLED);
        assertThat(controller.detailsController().handleKey(new KeyEvent(KeyCode.RIGHT, KeyModifiers.NONE, 0)))
            .isEqualTo(EventResult.UNHANDLED);

        focusManager.setFocus(PrDetailsController.VIEW_ID);
        assertThat(render(view)).contains("No failing tests detected on pull request #3");
    }

    @Test
    void vim_keys_scroll_the_details_pane() {
        var focusManager = new FocusManager();
        var controller = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            new PullRequestTestsResponse().sha("SHA")
                .addTestsItem(
                    executedTest(
                        "foo()",
                        execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL),
                        execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL).stackTrace("""
                                                                                  line one
                                                                                  line two
                                                                                  line three
                                                                                  line four
                                                                                  line five
                                                                                  line six
                                                                                  line seven
                                                                                  line eight
                                                                                  line nine
                                                                                  line ten
                                                                                  line eleven
                                                                                  line twelve
                                                                                  line thirteen
                                                                                  line fourteen
                                                                                  line fifteen
                                                                                  line sixteen
                                                                                  line seventeen
                                                                                  line eighteen
                                                                                  line nineteen
                                                                                  line twenty
                                                                                  line twenty-one""")
                    )
                ),
            focusManager
        );
        var view = new PrTuiView(controller);
        var details = controller.detailsController();
        focusManager.setFocus(PrDetailsController.VIEW_ID);

        renderBuffer(view, 120);
        var state = view.detailsState();

        // h/l switch executions even without focus
        assertThat(details.tabsState().selected()).isEqualTo(1);
        focusManager.setFocus(PrTestsTableController.VIEW_ID);
        details.handleKey(KeyEvent.ofChar('h'));
        assertThat(details.tabsState().selected()).isZero();
        details.handleKey(KeyEvent.ofChar('l'));
        assertThat(details.tabsState().selected()).isEqualTo(1);
        focusManager.setFocus(PrDetailsController.VIEW_ID);

        details.handleKey(KeyEvent.ofChar('j'));
        assertThat(state.scrollRow()).isEqualTo(1);
        details.handleKey(KeyEvent.ofChar('k'));
        assertThat(state.scrollRow()).isZero();
    }

    @Test
    void search_continuously_filters_the_test_list() {
        var focusManager = new FocusManager();
        var controller = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            new PullRequestTestsResponse().sha("SHA")
                .addTestsItem(executedTest("foo()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL)))
                .addTestsItem(executedTest("bar()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL)))
                .addTestsItem(executedTest("baz()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL))),
            focusManager
        );
        var view = new PrTuiView(controller);
        renderBuffer(view, 120);
        focusManager.setFocus(PrTestsTableController.VIEW_ID);
        var table = controller.testTableController();

        table.handleKey(KeyEvent.ofChar('/'));
        assertThat(table.isSearching()).isTrue();
        table.handleKey(KeyEvent.ofChar('b'));
        assertThat(table.tests()).hasSize(2);
        // the best match is selected while searching
        assertThat(table.tableState().selected()).isZero();
        assertThat(render(view)).doesNotContain("FooTests > foo()");
        table.handleKey(KeyEvent.ofChar('z'));
        assertThat(table.tests()).hasSize(1);
        assertThat(table.tableState().selected()).isZero();

        // Enter keeps the filter...
        table.handleKey(new KeyEvent(KeyCode.ENTER, KeyModifiers.NONE, 0));
        assertThat(table.isSearching()).isFalse();
        assertThat(table.tests()).hasSize(1);
        // ...and a new search resets it back to the full list
        table.handleKey(KeyEvent.ofChar('/'));
        assertThat(table.isSearching()).isTrue();
        assertThat(table.tests()).hasSize(3);
        assertThat(table.tableState().selected()).isZero();

        // cancelling keeps the list reset and stays on the selected test
        table.handleKey(new KeyEvent(KeyCode.ESCAPE, KeyModifiers.NONE, 0));
        assertThat(table.isSearching()).isFalse();
        assertThat(controller.isSearching()).isFalse();
        assertThat(table.tests()).hasSize(3);
        assertThat(table.tableState().selected()).isZero();
    }

    @Test
    void escape_resets_a_committed_search() {
        var focusManager = new FocusManager();
        var controller = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            new PullRequestTestsResponse().sha("SHA")
                .addTestsItem(executedTest("foo()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL)))
                .addTestsItem(executedTest("bar()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL))),
            focusManager
        );
        var view = new PrTuiView(controller);
        renderBuffer(view, 120);
        focusManager.setFocus(PrTestsTableController.VIEW_ID);
        var table = controller.testTableController();

        table.handleKey(KeyEvent.ofChar('/'));
        table.handleKey(KeyEvent.ofChar('x'));
        // Enter commits the search term
        table.handleKey(new KeyEvent(KeyCode.ENTER, KeyModifiers.NONE, 0));
        assertThat(table.isSearching()).isFalse();
        assertThat(table.isSearchCommitted()).isTrue();
        assertThat(table.tests()).isEmpty();

        // Esc resets a committed search back to the full list
        table.handleKey(new KeyEvent(KeyCode.ESCAPE, KeyModifiers.NONE, 0));
        assertThat(table.isSearchCommitted()).isFalse();
        assertThat(table.searchQuery()).isEmpty();
        assertThat(table.tests()).hasSize(2);

        // without a committed search, Esc keeps the list unchanged
        table.handleKey(new KeyEvent(KeyCode.ESCAPE, KeyModifiers.NONE, 0));
        assertThat(table.tests()).hasSize(2);
        assertThat(table.isSearchCommitted()).isFalse();
    }

    @Test
    void search_orders_best_matches_first() {
        var focusManager = new FocusManager();
        var controller = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            new PullRequestTestsResponse().sha("SHA")
                .addTestsItem(executedTest("alphatest()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL)))
                .addTestsItem(executedTest("alphabet()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL)))
                .addTestsItem(executedTest("beta()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL))),
            focusManager
        );
        var view = new PrTuiView(controller);
        renderBuffer(view, 120);
        focusManager.setFocus(PrTestsTableController.VIEW_ID);
        var table = controller.testTableController();

        table.handleKey(KeyEvent.ofChar('/'));
        for (var c : "alphat".toCharArray()) {
            table.handleKey(KeyEvent.ofChar(c));
        }
        // in "alphatest()" the final "t" extends the consecutive match, in "alphabet()" it is a gap
        assertThat(table.tests().stream().map(test -> test.getDisplayNames().get(2)).toList())
            .containsExactly("alphatest()", "alphabet()");
        assertThat(table.tableState().selected()).isZero();
    }

    @Test
    void search_highlights_matched_characters() {
        var focusManager = new FocusManager();
        var controller = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            new PullRequestTestsResponse().sha("SHA")
                .addTestsItem(executedTest("foo()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL)))
                .addTestsItem(executedTest("bar()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL))),
            focusManager
        );
        var view = new PrTuiView(controller);
        renderBuffer(view, 120);
        focusManager.setFocus(PrTestsTableController.VIEW_ID);
        var table = controller.testTableController();

        table.handleKey(KeyEvent.ofChar('/'));
        table.handleKey(KeyEvent.ofChar('f'));
        table.handleKey(KeyEvent.ofChar('o'));
        table.handleKey(KeyEvent.ofChar('o'));
        var buffer = renderBuffer(view, 120);
        var screen = render(view).lines().toList();
        var selectedRow = screen.stream().filter(line -> line.contains("FooTests > foo()")).findFirst().orElseThrow();
        var y = screen.indexOf(selectedRow);
        var firstMatch = selectedRow.indexOf("FooTests > foo()");

        // "Foo" at the start of the test name matched the query and is highlighted
        for (int x = firstMatch; x < firstMatch + 3; x++) {
            var style = buffer.get(x, y).style().effectiveModifiers();
            assertThat(style).contains(Modifier.BOLD).contains(Modifier.UNDERLINED);
        }
        // the trailing "Tests > foo()" is not part of the greedy match
        assertThat(buffer.get(firstMatch + 3, y).style().effectiveModifiers()).doesNotContain(Modifier.BOLD);
        // the query also matches the shared class name prefix of the second row
        var otherRow = screen.stream().filter(line -> line.contains("FooTests > bar()")).findFirst().orElseThrow();
        var otherY = screen.indexOf(otherRow);
        var otherName = otherRow.indexOf("FooTests > bar()");
        var otherStyle = buffer.get(otherName, otherY).style().effectiveModifiers();
        assertThat(otherStyle).contains(Modifier.BOLD).contains(Modifier.UNDERLINED);
        assertThat(buffer.get(otherName + 3, otherY).style().effectiveModifiers()).doesNotContain(Modifier.BOLD);
    }

    @Test
    void search_shows_the_search_term_in_the_footer() {
        var focusManager = new FocusManager();
        var controller = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            new PullRequestTestsResponse().sha("SHA")
                .addTestsItem(executedTest("foo()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL))),
            focusManager
        );
        var view = new PrTuiView(controller);
        renderBuffer(view, 120);
        focusManager.setFocus(PrTestsTableController.VIEW_ID);
        var table = controller.testTableController();

        table.handleKey(KeyEvent.ofChar('/'));
        table.handleKey(KeyEvent.ofChar('f'));
        assertThat(render(view)).contains("/f");

        table.handleKey(new KeyEvent(KeyCode.ESCAPE, KeyModifiers.NONE, 0));
        assertThat(render(view)).doesNotContain("/f");
    }

    @Test
    void search_keys_are_swallowed_when_the_filter_matches_nothing() {
        var focusManager = new FocusManager();
        var controller = new PrTuiController(
            new PullRequestResponse().number(42).title("Some PR"),
            new PullRequestTestsResponse().sha("SHA")
                .addTestsItem(executedTest("foo()", execution(OutcomeEnum.FAILED).htmlUrl(JOB_URL))),
            focusManager
        );
        var view = new PrTuiView(controller);
        renderBuffer(view, 120);
        focusManager.setFocus(PrTestsTableController.VIEW_ID);
        var table = controller.testTableController();

        table.handleKey(KeyEvent.ofChar('/'));
        table.handleKey(KeyEvent.ofChar('x'));
        table.handleKey(KeyEvent.ofChar('y'));
        assertThat(table.tests()).isEmpty();
        assertThat(table.isSearching()).isTrue();

        // typing continues into the search, navigation keys cannot move focus away
        table.handleKey(KeyEvent.ofChar('j'));
        assertThat(table.isSearching()).isTrue();
        assertThat(table.searchQuery()).isEqualTo("xyj");
        // Enter commits the search instead of switching to the details pane
        table.handleKey(new KeyEvent(KeyCode.ENTER, KeyModifiers.NONE, 0));
        assertThat(table.isSearching()).isFalse();
        assertThat(focusManager.focusedId()).isEqualTo(PrTestsTableController.VIEW_ID);

        // Esc restores the full list and ends the search
        table.handleKey(KeyEvent.ofChar('/'));
        table.handleKey(new KeyEvent(KeyCode.ESCAPE, KeyModifiers.NONE, 0));
        assertThat(table.isSearching()).isFalse();
        assertThat(table.tests()).hasSize(1);
        assertThat(table.tableState().selected()).isZero();
    }

    private static Execution execution(OutcomeEnum outcome) {
        return new Execution().outcome(outcome).startTime(START_TIME).durationMillis(1500L);
    }

    private static ExecutedTest executedTest(String method, Execution... executions) {
        return new ExecutedTest()
            .job("CI / build")
            .workUnit(":test")
            .uniqueId("[engine:junit-jupiter]")
            .displayNames(List.of("JUnit Jupiter", "FooTests", method))
            .executions(List.of(executions));
    }

    private static String render(PrTuiView view) {
        return render(view, 120);
    }

    private static String render(PrTuiView view, int width) {
        var buffer = renderBuffer(view, width);
        var screen = new StringBuilder();
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                screen.append(buffer.get(x, y).symbol());
            }
            screen.append('\n');
        }
        return screen.toString();
    }

    private static Buffer renderBuffer(PrTuiView view, int width) {
        return renderBuffer(view, width, DefaultRenderContext.createEmpty());
    }

    private static Buffer renderBuffer(PrTuiView view, int width, DefaultRenderContext context) {
        var buffer = Buffer.empty(Rect.of(width, 24));
        var frame = Frame.forTesting(buffer);
        view.render().render(frame, frame.area(), context);
        return buffer;
    }

    private static Buffer renderFrame(
        PrTuiView view,
        int width,
        DefaultRenderContext context,
        FocusManager focusManager,
        EventRouter eventRouter
    ) {
        focusManager.clearFocusables();
        eventRouter.clear();
        return renderBuffer(view, width, context);
    }

}
