// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.model.FailedCount;
import app.testlens.cli.client.model.FlakinessStatisticResponse;
import app.testlens.cli.client.model.FlakyCount;
import app.testlens.cli.client.model.Link;
import app.testlens.cli.client.model.StatisticLinks;
import app.testlens.cli.tui.controller.FlakinessTableController;
import app.testlens.cli.tui.view.FlakinessView;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.RenderThreadTestHelper;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlakinessViewTest {

    @BeforeEach
    void markAsRenderThread() {
        RenderThreadTestHelper.markAsRenderThread();
    }

    @AfterEach
    void clearRenderThread() {
        RenderThreadTestHelper.clearRenderThread();
    }

    @Test
    void shows_the_ranked_statistics_table() {
        var view = view(
            "main",
            statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH),
            statistic(1703, "CalculatorTests", "adds()", ":core:testJava21", 4L, 2, FlakyCount.SeverityEnum.LOW)
        );

        assertThat(render(view)).contains(
            "Flakiness ranking · branch main",
            "Flakiest tests",
            "connects()",
            "adds()"
        );
        // the first row is selected by default
        assertThat(render(view)).contains(":core:test");
    }

    @Test
    void enter_notifies_the_drill_down_callback() {
        var focusManager = new FocusManager();
        var controller = new FlakinessTableController(
            focusManager,
            List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            )
        );
        var view = new FlakinessView(controller, null);
        var opened = new ArrayList<FlakinessStatisticResponse>();
        controller.onOpenTest(opened::add);
        focusManager.setFocus(FlakinessTableController.VIEW_ID);

        assertThat(render(view)).contains("connects()");

        controller.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
        assertThat(opened).hasSize(1);
    }

    private static FlakinessView view(String branch, FlakinessStatisticResponse... statistics) {
        var controller = new FlakinessTableController(new FocusManager(), List.of(statistics));
        return new FlakinessView(controller, branch);
    }

    private static String render(FlakinessView view) {
        var buffer = renderBuffer(view);
        var screen = new StringBuilder();
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                screen.append(buffer.get(x, y).symbol());
            }
            screen.append('\n');
        }
        return screen.toString();
    }

    private static Buffer renderBuffer(FlakinessView view) {
        var buffer = Buffer.empty(Rect.of(120, 24));
        var frame = dev.tamboui.terminal.Frame.forTesting(buffer);
        view.render().render(frame, frame.area(), DefaultRenderContext.createEmpty());
        return buffer;
    }

    private static FlakinessStatisticResponse statistic(
        long id,
        String className,
        String method,
        String workUnit,
        Long flakyCount,
        Integer percentage,
        FlakyCount.SeverityEnum severity
    ) {
        return new FlakinessStatisticResponse()
            .uniqueId("[engine:junit-jupiter]/[class:" + className + "]/[method:" + method + "]")
            .displayNames(List.of("JUnit Jupiter", className, method))
            .workUnit(workUnit)
            .failed(new FailedCount().count(1L).total(200L).percentage(1))
            .flaky(new FlakyCount().count(flakyCount).total(200L).percentage(percentage).severity(severity))
            .links(new StatisticLinks().test(new Link().href("/v1/repos/some-org/some-repo/tests/" + id)));
    }
}
