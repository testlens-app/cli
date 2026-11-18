// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.GitHubClient;
import app.testlens.cli.client.ApiClient;
import app.testlens.cli.client.ApiException;
import app.testlens.cli.client.model.FailedCount;
import app.testlens.cli.client.model.FlakiestJob;
import app.testlens.cli.client.model.FlakinessStatisticResponse;
import app.testlens.cli.client.model.FlakyCount;
import app.testlens.cli.client.model.Link;
import app.testlens.cli.client.model.PullRequestResponse;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import app.testlens.cli.client.model.Severity;
import app.testlens.cli.client.model.StatisticLinks;
import app.testlens.cli.client.model.StatisticsSummaryResponse;
import app.testlens.cli.client.model.TestResponse;
import app.testlens.cli.tui.controller.FlakinessTableController;
import app.testlens.cli.tui.view.FlakesView;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.ElementRegistry;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.EventRouter;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.RenderThreadTestHelper;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeScreenTest {

    private static final ExecutorService DIRECT_EXECUTOR = new DirectExecutor();

    @BeforeEach
    void markAsRenderThread() {
        RenderThreadTestHelper.markAsRenderThread();
    }

    @AfterEach
    void clearRenderThread() {
        RenderThreadTestHelper.clearRenderThread();
    }

    @Test
    void shows_the_ranked_statistics_table_with_the_summary_strip() {
        var view = home(
            DIRECT_EXECUTOR,
            new FocusManager(),
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH),
                statistic(1703, "CalculatorTests", "adds()", ":core:testJava21", 4L, 2, FlakyCount.SeverityEnum.LOW)
            )
        );

        // the ranking pane is half the dashboard width: compact columns, the innermost test segment visible
        assertThat(render(view)).contains(
            "TestLens · some-org/some-repo · branch main",
            "2 flaky tests · 1 high severity · worst job: CI / build",
            "Flakiest tests",
            "connects()",
            "adds()"
        );
        // the job column drops out in the compact ranking; jobs live in the pane beside it
        assertThat(render(view)).doesNotContain("Severity   Flaky      Failed   Test            Job");
    }

    @Test
    void shows_a_placeholder_until_the_ranking_is_loaded() {
        var executor = new QueueingExecutor();
        var view = home(
            executor,
            new FocusManager(),
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            )
        );

        assertThat(render(view)).contains("TestLens · some-org/some-repo", "Fetching flakiness ranking...");
        assertThat(render(view)).doesNotContain("connects()");
    }

    @Test
    void loads_the_ranking_asynchronously_and_derives_the_default_branch() {
        var executor = new QueueingExecutor();
        var view = home(
            executor,
            new FocusManager(),
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            )
        );
        render(view);

        executor.runAll();

        assertThat(render(view)).contains("· branch main", "1 flaky tests", "connects()");
    }

    @Test
    void shows_an_error_when_the_load_fails() {
        var executor = new QueueingExecutor();
        var view = home(executor, new FocusManager(), loaded -> {
            throw new ApiException("not found", 404, HttpHeaders.of(Map.of(), (key, value) -> true), "");
        });
        render(view);

        executor.runAll();

        assertThat(render(view)).contains("TestLens · some-org/some-repo", "Repository not found");
    }

    @Test
    void enter_fetches_the_flakes_of_the_selected_statistic_and_pushes_their_screen() {
        var focusManager = new FocusManager();
        var fetched = new ArrayList<FlakinessStatisticResponse>();
        var view = home(
            DIRECT_EXECUTOR,
            focusManager,
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            ),
            statistic -> {
                fetched.add(statistic);
                return List.of();
            }
        );
        var eventRouter = new EventRouter(focusManager, new ElementRegistry());
        var context = new DefaultRenderContext(focusManager, eventRouter);
        var router = new ScreenRouter(view, focusManager);
        view.openWith(router);
        renderFrame(view, context, focusManager, eventRouter);
        assertThat(focusManager.focusedId()).isEqualTo(FlakinessTableController.VIEW_ID);

        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.ENTER))).isEqualTo(EventResult.HANDLED);

        // the direct executor fetched the flakes and pushed their screen within the routed key event
        assertThat(router.screen()).isInstanceOf(FlakesView.class);
        assertThat(fetched).hasSize(1);
    }

    @Test
    void enter_shows_an_error_when_the_flakes_fetch_fails() {
        var focusManager = new FocusManager();
        var view = home(
            DIRECT_EXECUTOR,
            focusManager,
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            ),
            statistic -> {
                throw new ApiException("not found", 404, HttpHeaders.of(Map.of(), (key, value) -> true), "");
            }
        );
        var eventRouter = new EventRouter(focusManager, new ElementRegistry());
        var context = new DefaultRenderContext(focusManager, eventRouter);
        var router = new ScreenRouter(view, focusManager);
        view.openWith(router);
        renderFrame(view, context, focusManager, eventRouter);

        eventRouter.route(KeyEvent.ofKey(KeyCode.ENTER));

        assertThat(router.screen()).isEqualTo(view);
        assertThat(render(view)).contains("Test not found");
    }

    @Test
    void p_opens_the_pull_request_prompt() {
        var view = home(
            DIRECT_EXECUTOR,
            new FocusManager(),
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            )
        );
        render(view); // completes the load, so the quick actions are available

        assertThat(view.handleGlobalKey(KeyEvent.ofChar('p'))).isEqualTo(EventResult.HANDLED);
        assertThat(render(view)).contains("Open pull request", "Enter confirm · Esc cancel");
        // the prompt is a modal input
        assertThat(view.modalInputActive()).isTrue();
    }

    @Test
    void the_pr_prompt_rejects_input_that_is_not_a_number() {
        var view = home(
            DIRECT_EXECUTOR,
            new FocusManager(),
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            )
        );
        render(view);
        view.handleGlobalKey(KeyEvent.ofChar('p'));
        var prompt = requireNonNull(view.prompt(), "the p action opens a prompt");

        prompt.input().setText("abc");
        view.submitPrompt(prompt);

        assertThat(render(view)).contains("Enter a pull request number, e.g. 42");
        assertThat(view.prompt()).isNull();
    }

    @Test
    void the_pr_prompt_fetches_and_pushes_the_pull_request_screen() {
        var focusManager = new FocusManager();
        var view = home(
            DIRECT_EXECUTOR,
            focusManager,
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            ),
            statistic -> List.of(),
            number -> new HomeScreen.PullRequestWithTests(
                new PullRequestResponse().number(42).title("Some PR"),
                new PullRequestTestsResponse().sha("SHA")
            ),
            query -> List.of(),
            branch -> {
                throw new AssertionError("unexpected summary fetch");
            },
            () -> List.of()
        );
        var router = new ScreenRouter(view, focusManager);
        view.openWith(router);
        render(view);
        view.handleGlobalKey(KeyEvent.ofChar('p'));
        var prompt = requireNonNull(view.prompt(), "the p action opens a prompt");

        prompt.input().setText("42");
        view.submitPrompt(prompt);

        assertThat(router.screen()).isInstanceOf(PrScreen.class);
    }

    @Test
    void the_search_prompt_pushes_the_results_screen() {
        var focusManager = new FocusManager();
        var view = home(
            DIRECT_EXECUTOR,
            focusManager,
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            ),
            statistic -> List.of(),
            number -> {
                throw new AssertionError("unexpected pull request fetch");
            },
            query -> List.of(
                new TestResponse().id(7L)
                    .uniqueId("[engine:junit-jupiter]/[class:FooTests]/[method:bar()]")
                    .displayNames(List.of("JUnit Jupiter", "FooTests", "bar()"))
            ),
            branch -> {
                throw new AssertionError("unexpected summary fetch");
            },
            () -> List.of()
        );
        var router = new ScreenRouter(view, focusManager);
        view.openWith(router);
        render(view);
        view.handleGlobalKey(KeyEvent.ofChar('/'));
        var prompt = requireNonNull(view.prompt(), "the / action opens a prompt");

        prompt.input().setText("FooTests");
        view.submitPrompt(prompt);

        assertThat(router.screen()).isInstanceOf(TestSearchScreen.class);
    }

    @Test
    void escape_closes_the_prompt() {
        var focusManager = new FocusManager();
        var view = home(
            DIRECT_EXECUTOR,
            focusManager,
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            )
        );
        var eventRouter = new EventRouter(focusManager, new ElementRegistry());
        var context = new DefaultRenderContext(focusManager, eventRouter);
        renderFrame(view, context, focusManager, eventRouter);
        view.handleGlobalKey(KeyEvent.ofChar('p'));
        renderFrame(view, context, focusManager, eventRouter); // registers the prompt input

        // Escape with the focused input declining the key reaches the modal dialog, which cancels
        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.ESCAPE))).isEqualTo(EventResult.HANDLED);

        assertThat(view.prompt()).isNull();
        assertThat(view.modalInputActive()).isFalse();
    }

    @Test
    void the_summary_enriches_the_stat_strip() {
        var view = home(
            DIRECT_EXECUTOR,
            new FocusManager(),
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            ),
            statistic -> List.of(),
            number -> {
                throw new AssertionError("unexpected pull request fetch");
            },
            query -> {
                throw new AssertionError("unexpected test search");
            },
            branch -> summary(),
            () -> List.of()
        );

        assertThat(render(view)).contains(
            "TestLens · some-org/some-repo · branch main",
            "42 flaky tests · 7 high · 3 consistently failing · worst job: CI / build"
        );
    }

    @Test
    void the_stat_strip_falls_back_when_the_summary_fails() {
        var view = home(
            DIRECT_EXECUTOR,
            new FocusManager(),
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            ),
            statistic -> List.of(),
            number -> {
                throw new AssertionError("unexpected pull request fetch");
            },
            query -> {
                throw new AssertionError("unexpected test search");
            },
            branch -> {
                throw new ApiException("boom", 502, HttpHeaders.of(Map.of(), (key, value) -> true), "");
            },
            () -> List.of()
        );

        // the client-side reduction of the ranking stands in for the failed summary
        assertThat(render(view)).contains("1 flaky tests · 1 high severity · worst job: CI / build");
    }

    @Test
    void the_branch_prompt_rejects_unknown_branches() {
        var focusManager = new FocusManager();
        var rankings = new ArrayList<String>();
        var view = home(
            DIRECT_EXECUTOR,
            focusManager,
            branch -> {
                rankings.add(branch);
                return List.of(
                    statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
                );
            },
            statistic -> List.of(),
            number -> {
                throw new AssertionError("unexpected pull request fetch");
            },
            query -> {
                throw new AssertionError("unexpected test search");
            },
            branch -> summary(),
            () -> List.of()
        );
        var router = new ScreenRouter(view, focusManager);
        view.openWith(router);
        render(view);
        view.handleGlobalKey(KeyEvent.ofChar('b'));
        var prompt = requireNonNull(view.prompt(), "the b action opens a prompt");

        prompt.input().setText("nope");
        view.submitPrompt(prompt);

        assertThat(render(view)).contains("No statistics for branch nope · known: main, release/1.2");
        assertThat(view.prompt()).isNull();
        // the unknown branch did not trigger a reload
        assertThat(rankings).hasSize(1);
    }

    @Test
    void the_branch_prompt_accepts_known_branches_and_reloads() {
        var focusManager = new FocusManager();
        var rankings = new ArrayList<String>();
        var view = home(
            DIRECT_EXECUTOR,
            focusManager,
            branch -> {
                rankings.add(branch);
                return List.of(
                    statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
                );
            },
            statistic -> List.of(),
            number -> {
                throw new AssertionError("unexpected pull request fetch");
            },
            query -> {
                throw new AssertionError("unexpected test search");
            },
            branch -> summary(),
            () -> List.of()
        );
        var router = new ScreenRouter(view, focusManager);
        view.openWith(router);
        render(view);
        view.handleGlobalKey(KeyEvent.ofChar('b'));
        var prompt = requireNonNull(view.prompt(), "the b action opens a prompt");

        prompt.input().setText("release/1.2");
        view.submitPrompt(prompt);

        // the direct executor reloaded ranking and summary for the known branch
        assertThat(rankings).containsExactly(null, "release/1.2");
        assertThat(render(view)).contains("· branch release/1.2");
    }

    @Test
    void the_panes_render_the_discovery_data() {
        var view = home(
            DIRECT_EXECUTOR,
            new FocusManager(),
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            ),
            statistic -> List.of(),
            number -> {
                throw new AssertionError("unexpected pull request fetch");
            },
            query -> {
                throw new AssertionError("unexpected test search");
            },
            branch -> summary(),
            () -> List.of(
                new GitHubClient.OpenPullRequest(42, "Fix the flaky timeout", false),
                new GitHubClient.OpenPullRequest(43, "Upgrade the build cache", true)
            )
        );

        assertThat(render(view)).contains(
            "Open pull requests",
            "#42",
            "Fix the flaky timeout",
            "[draft] Upgrade the build cache",
            "Flakiest jobs",
            "CI / build",
            "Branches",
            "release/1.2"
        );
    }

    @Test
    void enter_on_a_pull_request_opens_its_screen() {
        var focusManager = new FocusManager();
        var view = home(
            DIRECT_EXECUTOR,
            focusManager,
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            ),
            statistic -> List.of(),
            number -> new HomeScreen.PullRequestWithTests(
                new PullRequestResponse().number(42).title("Some PR"),
                new PullRequestTestsResponse().sha("SHA")
            ),
            query -> {
                throw new AssertionError("unexpected test search");
            },
            branch -> summary(),
            () -> List.of(new GitHubClient.OpenPullRequest(42, "Some PR", false))
        );
        var eventRouter = new EventRouter(focusManager, new ElementRegistry());
        var context = new DefaultRenderContext(focusManager, eventRouter);
        var router = new ScreenRouter(view, focusManager);
        view.openWith(router);
        renderFrame(view, context, focusManager, eventRouter);
        // Tab moves the focus from the ranking table past the branches pane to the PR pane
        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.TAB))).isEqualTo(EventResult.HANDLED);
        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.TAB))).isEqualTo(EventResult.HANDLED);
        assertThat(focusManager.focusedId()).isEqualTo("pull-requests");

        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.ENTER))).isEqualTo(EventResult.HANDLED);

        // the direct executor fetched the PR and pushed its screen within the routed key event
        assertThat(router.screen()).isInstanceOf(PrScreen.class);
    }

    @Test
    void enter_on_a_branch_switches_to_it() {
        var focusManager = new FocusManager();
        var rankings = new ArrayList<String>();
        var view = home(
            DIRECT_EXECUTOR,
            focusManager,
            branch -> {
                rankings.add(branch);
                return List.of(
                    statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
                );
            },
            statistic -> List.of(),
            number -> {
                throw new AssertionError("unexpected pull request fetch");
            },
            query -> {
                throw new AssertionError("unexpected test search");
            },
            branch -> summary(),
            () -> List.of()
        );
        var eventRouter = new EventRouter(focusManager, new ElementRegistry());
        var context = new DefaultRenderContext(focusManager, eventRouter);
        var router = new ScreenRouter(view, focusManager);
        view.openWith(router);
        render(view);
        focusManager.setFocus("branches");
        renderFrame(view, context, focusManager, eventRouter);
        // select the second branch, then Enter
        view.branchesForTesting().tableState().select(1);

        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.ENTER))).isEqualTo(EventResult.HANDLED);

        assertThat(rankings).containsExactly(null, "release/1.2");
        assertThat(render(view)).contains("· branch release/1.2");
    }

    @Test
    void unavailable_pull_requests_render_a_dim_note() {
        var view = home(
            DIRECT_EXECUTOR,
            new FocusManager(),
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            ),
            statistic -> List.of(),
            number -> {
                throw new AssertionError("unexpected pull request fetch");
            },
            query -> {
                throw new AssertionError("unexpected test search");
            },
            branch -> summary(),
            () -> {
                throw new java.io.IOException("no network");
            }
        );

        assertThat(render(view)).contains("Open pull requests", "Pull requests unavailable (GitHub).");
    }

    @Test
    void tab_cycles_through_the_actionable_panes_only() {
        var focusManager = new FocusManager();
        var view = home(
            DIRECT_EXECUTOR,
            focusManager,
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            ),
            statistic -> List.of(),
            number -> {
                throw new AssertionError("unexpected pull request fetch");
            },
            query -> {
                throw new AssertionError("unexpected test search");
            },
            branch -> summary(),
            () -> List.of(new GitHubClient.OpenPullRequest(42, "Some PR", false))
        );
        var eventRouter = new EventRouter(focusManager, new ElementRegistry());
        var context = new DefaultRenderContext(focusManager, eventRouter);
        var router = new ScreenRouter(view, focusManager);
        view.openWith(router);
        renderFrame(view, context, focusManager, eventRouter);

        // the jobs pane is informational and not part of the focus cycle; Tab follows visual order
        assertThat(focusManager.focusOrder()).containsExactly("flakiness", "branches", "pull-requests");

        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.TAB))).isEqualTo(EventResult.HANDLED);
        assertThat(focusManager.focusedId()).isEqualTo("branches");
        assertThat(eventRouter.route(KeyEvent.ofKey(KeyCode.TAB))).isEqualTo(EventResult.HANDLED);
        assertThat(focusManager.focusedId()).isEqualTo("pull-requests");
    }

    @Test
    void the_footer_announces_the_pending_action_of_the_focused_pane() {
        var focusManager = new FocusManager();
        var view = home(
            DIRECT_EXECUTOR,
            focusManager,
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            ),
            statistic -> List.of(),
            number -> {
                throw new AssertionError("unexpected pull request fetch");
            },
            query -> {
                throw new AssertionError("unexpected test search");
            },
            branch -> summary(),
            () -> List.of()
        );
        var eventRouter = new EventRouter(focusManager, new ElementRegistry());
        var context = new DefaultRenderContext(focusManager, eventRouter);
        var router = new ScreenRouter(view, focusManager);
        view.openWith(router);
        renderFrame(view, context, focusManager, eventRouter);

        // ranking focused: the drill-down target
        assertThat(render(view)).contains("Enter: flakes of NetworkTests > connects()");

        // branches focused on another branch: the pending switch
        focusManager.setFocus("branches");
        view.branchesForTesting().tableState().select(1);
        renderFrame(view, context, focusManager, eventRouter);
        assertThat(render(view)).contains("Enter: switch to release/1.2");
    }

    @Test
    void quick_actions_are_inert_while_loading() {
        var executor = new QueueingExecutor();
        var view = home(
            executor,
            new FocusManager(),
            loaded -> List.of(
                statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 10, FlakyCount.SeverityEnum.HIGH)
            )
        );
        render(view);

        assertThat(view.handleGlobalKey(KeyEvent.ofChar('p'))).isEqualTo(EventResult.UNHANDLED);
        assertThat(render(view)).doesNotContain("Open pull request");
    }

    private static StatisticsSummaryResponse summary() {
        return new StatisticsSummaryResponse()
            .branch("main")
            .windowDays(30)
            .flakyTests(42L)
            .severity(new Severity().low(20L).medium(15L).high(7L))
            .flakiestJobs(List.of(new FlakiestJob().job("CI / build").flakyTests(10L).worstPercentage(10)))
            .branches(List.of("main", "release/1.2"))
            .totalTests(1234L)
            .sessionCount(512L)
            .activeBranches(8L)
            .consistentlyFailingTests(3L)
            .statisticsIncomplete(false);
    }

    private static HomeScreen home(ExecutorService executor, FocusManager focusManager) {
        return home(
            executor,
            focusManager,
            loaded -> {
                throw new AssertionError("unexpected ranking fetch");
            },
            statistic -> List.of()
        );
    }

    private static HomeScreen home(
        ExecutorService executor,
        FocusManager focusManager,
        HomeScreen.RankingLoader loader
    ) {
        return home(executor, focusManager, loader, statistic -> List.of());
    }

    private static HomeScreen home(
        ExecutorService executor,
        FocusManager focusManager,
        HomeScreen.RankingLoader loader,
        HomeScreen.FlakesFetcher flakesFetcher
    ) {
        return home(
            executor,
            focusManager,
            loader,
            flakesFetcher,
            number -> {
                throw new AssertionError("unexpected pull request fetch");
            },
            query -> {
                throw new AssertionError("unexpected test search");
            },
            branch -> {
                throw new AssertionError("unexpected summary fetch");
            },
            () -> List.of()
        );
    }

    private static HomeScreen home(
        ExecutorService executor,
        FocusManager focusManager,
        HomeScreen.RankingLoader loader,
        HomeScreen.FlakesFetcher flakesFetcher,
        HomeScreen.PullRequestLoader pullRequestLoader,
        HomeScreen.TestSearcher testSearcher,
        HomeScreen.SummaryLoader summaryLoader,
        HomeScreen.PullRequestsLoader pullRequestsLoader
    ) {
        var session = new TuiSession("some-org", "some-repo", new ApiClient(), "https://api.github.com", null);
        return new HomeScreen(
            session,
            focusManager,
            executor,
            Runnable::run,
            new HomeScreen.HomeLoaders(
                loader,
                flakesFetcher,
                pullRequestLoader,
                testSearcher,
                summaryLoader,
                pullRequestsLoader
            )
        );
    }

    private static String render(HomeScreen screen) {
        var buffer = Buffer.empty(Rect.of(120, 24));
        var frame = dev.tamboui.terminal.Frame.forTesting(buffer);
        screen.render().render(frame, frame.area(), DefaultRenderContext.createEmpty());
        var rendered = new StringBuilder();
        for (int y = 0; y < buffer.height(); y++) {
            for (int x = 0; x < buffer.width(); x++) {
                rendered.append(buffer.get(x, y).symbol());
            }
            rendered.append('\n');
        }
        return rendered.toString();
    }

    private static void renderFrame(
        HomeScreen screen,
        DefaultRenderContext context,
        FocusManager focusManager,
        EventRouter eventRouter
    ) {
        focusManager.clearFocusables();
        eventRouter.clear();
        var buffer = Buffer.empty(Rect.of(120, 24));
        var frame = dev.tamboui.terminal.Frame.forTesting(buffer);
        screen.render().render(frame, frame.area(), context);
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
            .job("CI / build")
            .branch("main")
            .failed(new FailedCount().count(1L).total(200L).percentage(1))
            .flaky(new FlakyCount().count(flakyCount).total(200L).percentage(percentage).severity(severity))
            .links(new StatisticLinks().test(new Link().href("/v1/repos/some-org/some-repo/tests/" + id)));
    }

    /** Runs every submitted task inline, so asynchronous loads complete within the test call. */
    private static final class DirectExecutor extends AbstractExecutorService {

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return true;
        }

        @Override
        public boolean isTerminated() {
            return true;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    /** Queues submitted tasks until {@link #runAll()}, simulating an in-flight asynchronous fetch. */
    private static final class QueueingExecutor extends AbstractExecutorService {

        private final List<Runnable> tasks = new ArrayList<>();

        void runAll() {
            var queued = List.copyOf(tasks);
            tasks.clear();
            queued.forEach(Runnable::run);
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return true;
        }

        @Override
        public boolean isTerminated() {
            return true;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
