// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import app.testlens.cli.client.model.FlakeResponse;
import app.testlens.cli.tui.controller.FlakesController;
import app.testlens.cli.tui.model.TestInfo;
import app.testlens.cli.tui.view.FlakesView;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The flakes screen of the TUI, entered directly via {@code testlens flakes <id>}. */
public final class FlakesTui {

    private FlakesTui() {}

    public static void run(
        TestInfo test,
        @Nullable String branch,
        @Nullable String workUnit,
        @Nullable String job,
        List<FlakeResponse> flakes
    ) throws Exception {
        try (var runner = ToolkitRunner.create()) {
            var controller = new FlakesController(runner.focusManager(), test, flakes);
            var view = new FlakesView(controller, test, branch, workUnit, job);
            // the modal "no flakes" dialog consumes all keys before unfocused-element routing,
            // so quit has to be handled globally, ahead of the dialog
            runner.eventRouter().addGlobalHandler(event -> {
                if (event instanceof KeyEvent keyEvent && keyEvent.isQuit()) {
                    runner.quit();
                    return EventResult.HANDLED;
                }
                return EventResult.UNHANDLED;
            });
            runner.run(view::render);
        }
    }
}
