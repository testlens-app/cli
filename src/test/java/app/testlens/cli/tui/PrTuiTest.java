// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.model.ComparisonFailure;
import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.client.model.Execution.OutcomeEnum;
import app.testlens.cli.client.model.PullRequestResponse;
import app.testlens.cli.client.model.PullRequestTestsResponse;
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
    void navigates_between_table_and_details_with_enter_escape_and_tab() {
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

        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.ENTER))).isEqualTo(EventResult.HANDLED);
        assertThat(focusManager.focusedId()).isEqualTo("details");
        var detailsFocusedFrame = renderFrame(view, 120, context, focusManager, eventRouter);
        assertThat(detailsFocusedFrame.get(0, 6).style().fg()).contains(Color.CYAN);
        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.ESCAPE))).isEqualTo(EventResult.HANDLED);
        assertThat(focusManager.focusedId()).isEqualTo("tests");
        renderFrame(view, 120, context, focusManager, eventRouter);
        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.TAB))).isEqualTo(EventResult.HANDLED);
        assertThat(focusManager.focusedId()).isEqualTo("details");
        renderBuffer(view, 120, context);
        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT))).isEqualTo(EventResult.HANDLED);
        assertThat(focusManager.focusedId()).isEqualTo("tests");
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
