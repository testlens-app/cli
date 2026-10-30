// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.model.TestExecutionResponse;
import app.testlens.cli.tui.controller.ExecutionsController;
import app.testlens.cli.tui.model.ExecutionsModel;
import app.testlens.cli.tui.model.TestInfo;
import app.testlens.cli.tui.view.ExecutionsView;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.focus.FocusManager;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionsViewTest {

    private static final OffsetDateTime START_TIME = OffsetDateTime.parse("2026-01-02T03:04:05Z");

    private static final TestInfo TEST =
        new TestInfo(
            1729L,
            "[engine:junit-jupiter]/[class:NetworkTests]/[method:connects()]",
            List.of("JUnit Jupiter", "NetworkTests", "connects()")
        );

    @BeforeEach
    void markAsRenderThread() {
        dev.tamboui.tui.RenderThreadTestHelper.markAsRenderThread();
    }

    @AfterEach
    void clearRenderThread() {
        dev.tamboui.tui.RenderThreadTestHelper.clearRenderThread();
    }

    @Test
    void shows_executions_table_and_details_of_the_newest_execution() {
        var view = view(
            TEST,
            execution(TestExecutionResponse.OutcomeEnum.FAILED, ":core:test"),
            execution(TestExecutionResponse.OutcomeEnum.SUCCESSFUL, ":core:testJava21")
        );

        assertThat(render(view)).contains(
            "NetworkTests > connects() · test 1729",
            "Executions",
            "FAILED",
            ":core:test",
            ":core:testJava21",
            "Exec:  #1/2, 2026-01-02 03:04:05Z - 03:04:07Z (2s)"
        );
    }

    @Test
    void shows_a_dialog_without_executions() {
        var view = view(TEST);

        assertThat(render(view)).contains("No executions of this test in the last 30 days.");
    }

    @Test
    void truncates_columns_with_long_work_units_and_branches() {
        // regression: Math.clamp threw for min > max when the longest work unit exceeded the column cap
        var view = view(
            TEST,
            execution(TestExecutionResponse.OutcomeEnum.FAILED, ":platform-tests:testJava27EaContainer")
                .branch("refs/pull/1234/merge"),
            execution(TestExecutionResponse.OutcomeEnum.SUCCESSFUL, ":core:test").branch("main")
        );

        assertThat(render(view)).contains(":core:test").contains("main").contains("…");
    }

    @Test
    void moves_the_selection_and_the_details_pane_follows() {
        var focusManager = new FocusManager();
        var controller = new ExecutionsController(
            focusManager,
            new ExecutionsModel(
                TEST,
                List.of(
                    execution(TestExecutionResponse.OutcomeEnum.FAILED, ":core:test"),
                    execution(TestExecutionResponse.OutcomeEnum.SUCCESSFUL, ":core:testJava21")
                )
            )
        );
        var view = new ExecutionsView(controller, controller.model());
        focusManager.setFocus(ExecutionsController.EXECUTIONS_ID);

        controller.handleTableKey(dev.tamboui.tui.event.KeyEvent.ofChar('j'));
        assertThat(controller.model().snapshot().executionIndex()).isEqualTo(1);
        assertThat(render(view)).contains("Exec:  #2/2");
    }

    private static ExecutionsView view(TestInfo test, TestExecutionResponse... executions) {
        var controller = new ExecutionsController(
            new dev.tamboui.toolkit.focus.FocusManager(),
            new ExecutionsModel(test, List.of(executions))
        );
        return new ExecutionsView(controller, controller.model());
    }

    private static TestExecutionResponse execution(TestExecutionResponse.OutcomeEnum outcome, String workUnit) {
        return new TestExecutionResponse()
            .outcome(outcome)
            .startTime(START_TIME)
            .durationMillis(2000L)
            .sha("0123456789abcdef0123456789abcdef01234567")
            .branch("main")
            .workUnit(workUnit);
    }

    private static String render(ExecutionsView view) {
        return render(view, 120);
    }

    private static String render(ExecutionsView view, int width) {
        var buffer = Buffer.empty(Rect.of(width, 24));
        var frame = dev.tamboui.terminal.Frame.forTesting(buffer);
        view.render().render(frame, frame.area(), DefaultRenderContext.createEmpty());
        var screen = new StringBuilder();
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                screen.append(buffer.get(x, y).symbol());
            }
            screen.append('\n');
        }
        return screen.toString();
    }
}
