// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import app.testlens.cli.client.model.PullRequestResponse;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;

public final class PrTui {

    public static void run(PullRequestResponse prResponse, PullRequestTestsResponse testsResponse) throws Exception {
        try (var runner = ToolkitRunner.create()) {
            var controller = new PrTuiController(prResponse, testsResponse, runner.focusManager());
            var view = new PrTuiView(controller);
            // the modal "no tests" dialog consumes all keys before unfocused-element routing,
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
