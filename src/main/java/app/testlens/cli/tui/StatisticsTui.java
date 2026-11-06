// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static app.testlens.cli.tui.model.TestInfo.of;

import app.testlens.cli.FlakesCommand;
import app.testlens.cli.client.ApiException;
import app.testlens.cli.client.api.TestsApi;
import app.testlens.cli.client.model.FlakinessStatisticResponse;
import app.testlens.cli.tui.controller.FlakesController;
import app.testlens.cli.tui.controller.FlakinessTableController;
import app.testlens.cli.tui.view.FlakesView;
import app.testlens.cli.tui.view.FlakinessView;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The ranked flakiness list screen of the TUI, entered via {@code testlens statistics flakiness}. Selecting a test
 * drills down into the flakes of the test on the statistic's branch and work unit by pushing them onto the router.
 */
public final class StatisticsTui {

    private StatisticsTui() {}

    public static void run(
        TestsApi testsApi,
        String owner,
        String repo,
        @Nullable String branch,
        List<FlakinessStatisticResponse> statistics
    ) throws Exception {
        try (var runner = ToolkitRunner.create()) {
            var focusManager = runner.focusManager();
            var listController = new FlakinessTableController(focusManager, statistics);
            var router = new ScreenRouter(new FlakinessView(listController, branch), focusManager);
            listController.onOpenTest(
                statistic -> openFlakes(testsApi, owner, repo, focusManager, listController, router, statistic)
            );
            // the modal error dialog consumes all keys before unfocused-element routing,
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

    private static void openFlakes(
        TestsApi testsApi,
        String owner,
        String repo,
        FocusManager focusManager,
        FlakinessTableController listController,
        ScreenRouter router,
        FlakinessStatisticResponse statistic
    ) {
        try {
            var test = of(statistic);
            var flakes = FlakesCommand.fetchFlakes(testsApi, owner, repo, test.id(), statistic.getBranch(), statistic.getWorkUnit());
            var controller = new FlakesController(focusManager, test, flakes);
            router.push(new FlakesView(controller, test, statistic.getBranch(), statistic.getWorkUnit()));
        } catch (ApiException e) {
            listController.openFailed(errorMessage(e));
        }
    }

    static String errorMessage(ApiException e) {
        return switch (e.getCode()) {
            case 401 -> "Invalid GitHub token";
            case 404 -> "Test not found";
            default -> "Fetching flakes failed (HTTP " + e.getCode() + ")";
        };
    }
}
