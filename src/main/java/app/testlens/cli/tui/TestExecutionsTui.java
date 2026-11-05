// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import app.testlens.cli.ExecutionsLoader;
import app.testlens.cli.client.ApiException;
import app.testlens.cli.tui.controller.ExecutionsController;
import app.testlens.cli.tui.model.ExecutionsModel;
import app.testlens.cli.tui.model.TestInfo;
import app.testlens.cli.tui.view.ExecutionsView;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;

/** The test executions screen of the TUI, entered directly via {@code testlens test <id>}. */
public final class TestExecutionsTui {

    private TestExecutionsTui() {}

    public static void run(TestInfo test, ExecutionsLoader loader) throws Exception {
        try (var runner = ToolkitRunner.create()) {
            var first = loader.first();
            var model = new ExecutionsModel(test, first.executions(), first.hasMore());
            var controller = new ExecutionsController(runner.focusManager(), model, () -> {
                try {
                    var next = loader.next();
                    if (next != null) {
                        model.append(next);
                        return true;
                    }
                    return false;
                } catch (ApiException e) {
                    // without a dedicated error dialog, the next page is simply not appended
                    return false;
                }
            });
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
