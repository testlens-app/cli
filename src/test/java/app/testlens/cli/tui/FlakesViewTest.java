// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.model.FlakeResponse;
import app.testlens.cli.client.model.TestExecutionResponse;
import app.testlens.cli.tui.controller.FlakesController;
import app.testlens.cli.tui.model.TestInfo;
import app.testlens.cli.tui.view.FlakesView;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlakesViewTest {

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
    void shows_flakes_table_and_executions_as_chronological_tabs_with_the_newest_selected() {
        var focusManager = new FocusManager();
        var controller = new FlakesController(focusManager, TEST, List.of(flake("0123456789", ":core:test")));

        var screen = render(new FlakesView(controller, TEST, "main", null));

        assertThat(screen).contains(
            "NetworkTests > connects() · test 1729 · branch main",
            "Outcome",
            "Project/Task",
            "FLAKY",
            ":core:test",
            "0123456",
            "2026-01-02 03:04",
            // the newest execution is shown by default
            "Time: 2026-01-02 04:04:05Z",
            "Job:  -"
        );
        // the same for all executions of a flake, so not repeated in the details pane
        assertThat(screen).doesNotContain("Test:", "Where:");
        assertThat(screen.indexOf("#1 FAILED")).isPositive().isLessThan(screen.indexOf("#2 SUCCESSFUL"));
    }

    @Test
    void moves_the_selection_and_the_details_pane_follows() {
        var focusManager = new FocusManager();
        var controller = new FlakesController(
            focusManager,
            TEST,
            List.of(flake("1111111111", ":first"), flake("2222222222", ":second"))
        );
        var view = new FlakesView(controller, TEST, null, null);
        focusManager.setFocus(FlakesController.VIEW_ID);

        controller.handleKey(KeyEvent.ofChar('j'));

        render(view);
        assertThat(requireNonNull(controller.model().details().test()).getWorkUnit())
            .isEqualTo(":second");
    }

    @Test
    void shows_a_dialog_without_flakes() {
        var controller = new FlakesController(new FocusManager(), TEST, List.of());

        assertThat(render(new FlakesView(controller, TEST, null, null)))
            .contains("No flakes of this test in the last 30 days.");
    }

    // the executions are deliberately newest first, to verify the tabs are chronological
    private static FlakeResponse flake(String sha, String workUnit) {
        return new FlakeResponse()
            .sha(sha)
            .branch("main")
            .workUnit(workUnit)
            .executions(
                List.of(
                    execution(TestExecutionResponse.OutcomeEnum.SUCCESSFUL, "2026-01-02T04:04:05Z", sha, workUnit),
                    execution(TestExecutionResponse.OutcomeEnum.FAILED, "2026-01-02T03:04:05Z", sha, workUnit)
                )
            );
    }

    private static TestExecutionResponse execution(
        TestExecutionResponse.OutcomeEnum outcome,
        String startTime,
        String sha,
        String workUnit
    ) {
        return new TestExecutionResponse()
            .outcome(outcome)
            .startTime(OffsetDateTime.parse(startTime))
            .durationMillis(2000L)
            .sha(sha)
            .branch("main")
            .workUnit(workUnit);
    }

    private static String render(FlakesView view) {
        var buffer = Buffer.empty(Rect.of(120, 30));
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
