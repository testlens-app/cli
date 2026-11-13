// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static app.testlens.cli.tui.model.TestInfo.of;

import app.testlens.cli.FlakesCommand;
import app.testlens.cli.GitHubClient;
import app.testlens.cli.tui.ScreenRouter.Screen;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TickEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The single TUI of the CLI: all screens share one runner, one router and one executor for asynchronous loads. Only
 * {@code testlens} without arguments starts it, at the home screen; {@code g} pushes the home screen again from any
 * other one (see {@link GlobalKeys}).
 */
public final class TestlensTui {

    private TestlensTui() {}

    /** Runs the TUI at the home screen; the ranking is fetched asynchronously. */
    public static void runHome(TuiSession session) throws Exception {
        try (var runner = ToolkitRunner.create()) {
            var executor = newExecutor();
            try {
                var home = homeScreen(session, runner, executor);
                var router = new ScreenRouter(home, runner.focusManager());
                home.openWith(router);
                runLoop(runner, router, home);
            } finally {
                executor.shutdown();
            }
        }
    }

    private static HomeScreen homeScreen(TuiSession session, ToolkitRunner runner, ExecutorService executor) {
        var loaders = new HomeScreen.HomeLoaders(
            requested -> session.statisticsApi()
                .getFlakinessStatistics(session.owner(), session.repo(), requested, null, null),
            statistic -> FlakesCommand.fetchFlakes(
                session.testsApi(),
                session.owner(),
                session.repo(),
                of(statistic).id(),
                statistic.getBranch(),
                statistic.getWorkUnit(),
                statistic.getJob()
            ),
            number -> {
                var api = session.pullRequestsApi();
                var pr = api.getPullRequest(session.owner(), session.repo(), (int) number);
                return new HomeScreen.PullRequestWithTests(
                    pr,
                    api.getPullRequestTests(session.owner(), session.repo(), (int) number, pr.getHeadSha())
                );
            },
            query -> session.testsApi().searchTests(session.owner(), session.repo(), null, query, null, null),
            requested -> session.statisticsApi().getStatisticsSummary(session.owner(), session.repo(), requested),
            () -> new GitHubClient(session.githubUrl(), session.githubToken())
                .openPullRequests(session.owner(), session.repo())
        );
        return new HomeScreen(session, runner.focusManager(), executor, publisherOf(runner), loaders);
    }

    private static void runLoop(ToolkitRunner runner, ScreenRouter router, Screen home) throws Exception {
        // the modal dialogs consume all keys before unfocused-element routing,
        // so back/quit and the home key have to be handled globally, ahead of the dialogs
        runner.eventRouter().addGlobalHandler(event -> {
            if (event instanceof KeyEvent keyEvent) {
                return GlobalKeys.handle(keyEvent, router, home, runner::quit);
            }
            return EventResult.UNHANDLED;
        });
        runner.run(router::render);
    }

    private static Consumer<Runnable> publisherOf(ToolkitRunner runner) {
        // publish model updates on the render thread, then request a repaint right away
        // (without it, the next tick would repaint within 40 ms)
        return update -> {
            runner.runOnRenderThread(update);
            runner.tuiRunner().dispatch(TickEvent.of(0));
        };
    }

    private static ExecutorService newExecutor() {
        return Executors.newFixedThreadPool(2, Thread.ofPlatform().name("testlens-tui-", 0).daemon(true).factory());
    }
}
