// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import app.testlens.cli.client.model.TestExecutionResponse;
import app.testlens.cli.tui.controller.ExecutionsController;
import app.testlens.cli.tui.model.ExecutionsModel;
import app.testlens.cli.tui.model.TestInfo;
import app.testlens.cli.tui.view.ExecutionsView;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import java.util.List;

/** The test executions screen of the TUI, entered directly via {@code testlens test <id>}. */
public final class TestExecutionsTui {

    private TestExecutionsTui() {}

    public static void run(TestInfo test, List<TestExecutionResponse> executions) throws Exception {
        try (var runner = ToolkitRunner.create()) {
            var model = new ExecutionsModel(test, executions);
            var controller = new ExecutionsController(runner.focusManager(), model);
            var router = new ScreenRouter(new ExecutionsView(controller, model), runner.focusManager());
            // the modal "no executions" dialog consumes all keys before unfocused-element routing,
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
