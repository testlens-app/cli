// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import app.testlens.cli.client.model.PullRequestResponse;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import dev.tamboui.toolkit.app.ToolkitRunner;

public final class PrTui {

    public static void run(PullRequestResponse prResponse, PullRequestTestsResponse testsResponse) throws Exception {
        try (var runner = ToolkitRunner.create()) {
            var controller = new PrTuiController(prResponse, testsResponse, runner.focusManager());
            var view = new PrTuiView(controller);
            runner.run(view::render);
        }
    }
}
