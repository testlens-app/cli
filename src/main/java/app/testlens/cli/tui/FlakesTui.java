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
        List<FlakeResponse> flakes
    ) throws Exception {
        try (var runner = ToolkitRunner.create()) {
            var controller = new FlakesController(runner.focusManager(), test, flakes);
            var router = new ScreenRouter(new FlakesView(controller, test, branch, workUnit), runner.focusManager());
            // the modal "no flakes" dialog consumes all keys before unfocused-element routing,
            // so back/quit has to be handled globally, ahead of the dialog
            runner.eventRouter().addGlobalHandler(event -> {
                if (event instanceof KeyEvent keyEvent) {
                    return router.handleBackOrQuit(keyEvent, runner::quit);
                }
                return EventResult.UNHANDLED;
            });
            runner.run(router::render);
        }
    }
}
