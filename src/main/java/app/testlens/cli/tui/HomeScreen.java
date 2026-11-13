// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static app.testlens.cli.tui.controller.FlakinessTableController.VIEW_ID;
import static dev.tamboui.style.Color.DARK_GRAY;
import static dev.tamboui.style.Color.RED;
import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.table;
import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textInput;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;

import app.testlens.cli.GitHubClient;
import app.testlens.cli.client.ApiException;
import app.testlens.cli.client.model.FlakeResponse;
import app.testlens.cli.client.model.FlakiestJob;
import app.testlens.cli.client.model.FlakinessStatisticResponse;
import app.testlens.cli.client.model.FlakyCount;
import app.testlens.cli.client.model.Severity;
import app.testlens.cli.client.model.StatisticsSummaryResponse;
import app.testlens.cli.client.model.TestResponse;
import app.testlens.cli.tui.ScreenRouter.Screen;
import app.testlens.cli.tui.controller.FlakesController;
import app.testlens.cli.tui.controller.FlakinessTableController;
import app.testlens.cli.tui.controller.ListPaneController;
import app.testlens.cli.tui.controller.PrTuiController;
import app.testlens.cli.tui.model.TestInfo;
import app.testlens.cli.tui.view.FlakesView;
import app.testlens.cli.tui.view.FlakinessTableView;
import app.testlens.cli.tui.view.PrTuiView;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.TableState;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The home screen of the unified TUI: the ranked flakiness statistics of the default (or selected) branch, with a
 * client-side summary strip and quick actions (open a pull request, search tests, switch branch). Started by
 * {@code testlens} without arguments and by {@code testlens statistics flakiness}; also pushed via {@code g} from any
 * other screen. The ranking is loaded asynchronously, and so is the drill-down into a test's flakes.
 */
public final class HomeScreen implements Screen {

    /** Loads the ranked flakiness statistics of a branch; {@code null} means the repository's default branch. */
    @FunctionalInterface
    public interface RankingLoader {

        List<FlakinessStatisticResponse> load(@Nullable String branch) throws ApiException;
    }

    /** Fetches all flakes of the selected statistic, scoped to its branch, work unit and job. */
    @FunctionalInterface
    public interface FlakesFetcher {

        List<FlakeResponse> fetch(FlakinessStatisticResponse statistic) throws ApiException;
    }

    /** Fetches a pull request and its tests for the PR prompt. */
    @FunctionalInterface
    public interface PullRequestLoader {

        PullRequestWithTests load(long number) throws ApiException;
    }

    /** Searches tests for the home screen's test search prompt. */
    @FunctionalInterface
    public interface TestSearcher {

        List<TestResponse> search(String query) throws ApiException;
    }

    /** Loads the dashboard-grade summary of a branch; {@code null} means the repository's default branch. */
    @FunctionalInterface
    public interface SummaryLoader {

        StatisticsSummaryResponse load(@Nullable String branch) throws ApiException;
    }

    /**
     * Loads the repository's open pull requests from GitHub for the discovery pane; any failure means "unavailable".
     */
    @FunctionalInterface
    public interface PullRequestsLoader {

        List<GitHubClient.OpenPullRequest> load() throws Exception;
    }

    /** A pull request together with its tests, as needed for the PR screen. */
    public record PullRequestWithTests(
        app.testlens.cli.client.model.PullRequestResponse pr,
        app.testlens.cli.client.model.PullRequestTestsResponse tests
    ) {}

    /** The asynchronous data loaders of the home screen, wired by {@link TestlensTui} in production. */
    public record HomeLoaders(
        RankingLoader ranking,
        FlakesFetcher flakes,
        PullRequestLoader pullRequest,
        TestSearcher tests,
        SummaryLoader summary,
        PullRequestsLoader pullRequests
    ) {}

    private enum State {
        LOADING,
        LOADED,
        FAILED
    }

    private static final String PROMPT_INPUT_ID = "prompt-input";

    /** A modal prompt shown over the home screen: title, its input state and the action on submit. */
    record Prompt(String title, TextInputState input, Consumer<String> onSubmit) {}

    private final TuiSession session;
    private final FocusManager focusManager;
    private final ExecutorService executor;
    private final Consumer<Runnable> publish;
    private final HomeLoaders loaders;
    private final FlakinessTableController listController;
    private final FlakinessTableView table;
    private final ListPaneController<GitHubClient.OpenPullRequest> pullRequestsController;
    private final ListPaneController<FlakiestJob> jobsController;
    private final ListPaneController<String> branchesController;

    private State state;
    private @Nullable List<FlakinessStatisticResponse> statistics;
    private @Nullable StatisticsSummaryResponse summary;
    private @Nullable List<GitHubClient.OpenPullRequest> pullRequests;
    private @Nullable String pullRequestsError;
    private @Nullable String error;
    private @Nullable String branch;
    private @Nullable String pending;
    private @Nullable Prompt prompt;
    private @Nullable ScreenRouter router;
    private boolean loadStarted;
    private int generation;

    public HomeScreen(
        TuiSession session,
        FocusManager focusManager,
        ExecutorService executor,
        Consumer<Runnable> publish,
        HomeLoaders loaders
    ) {
        this.session = session;
        this.focusManager = focusManager;
        this.executor = executor;
        this.publish = publish;
        this.loaders = loaders;
        this.listController = new FlakinessTableController(focusManager, List.of());
        this.table = new FlakinessTableView(listController);
        this.pullRequestsController = new ListPaneController<>(focusManager, "pull-requests", 5);
        this.jobsController = new ListPaneController<>(focusManager, "jobs", 5);
        this.branchesController = new ListPaneController<>(focusManager, "branches", 5);
        this.state = State.LOADING;
    }

    /** The open prompt, for tests; {@code null} when none. */
    @Nullable
    Prompt prompt() {
        return prompt;
    }

    /** The branches pane controller, for tests. */
    ListPaneController<String> branchesForTesting() {
        return branchesController;
    }

    /**
     * Wires the router and the drill-down callbacks, before the first render or key event; needed for drill-down
     * pushes and the footer hint.
     */
    public void openWith(ScreenRouter router) {
        this.router = router;
        listController.onOpenTest(this::openFlakes);
        pullRequestsController.onEnter(pullRequest -> openPullRequest(pullRequest.number()));
        branchesController.onEnter(this::switchBranch);
    }

    @Override
    public String focusId() {
        return VIEW_ID;
    }

    @Override
    public Element render() {
        loadIfPending();
        Element content = switch (state) {
            case LOADING -> column(header(), text("Fetching flakiness ranking...").dim().length(1));
            case FAILED -> column(header());
            case LOADED -> loadedContent();
        };
        var drillDownError = listController.error();
        if (drillDownError != null) {
            content = withDialog(content, drillDownError);
        } else if (state == State.FAILED) {
            content = withDialog(content, requireNonNullElse(error, "Loading the statistics failed"));
        }
        if (pending != null) {
            content = stack(content, dialog(text(pending)).rounded());
        }
        if (prompt != null) {
            content = stack(content, promptDialog(prompt));
        }
        return content;
    }

    @Override
    public boolean modalInputActive() {
        // while a fetch runs or a prompt is open, its modal dialog must receive quit/back keys
        return pending != null || prompt != null;
    }

    /**
     * The home screen's quick actions, handled globally so they work while no pane is focused: {@code p} opens a pull
     * request, {@code /} searches tests, {@code b} switches the branch.
     */
    @Override
    public EventResult handleGlobalKey(KeyEvent event) {
        if (state != State.LOADED || pending != null) {
            return EventResult.UNHANDLED;
        }
        if (event.isChar('p')) {
            openPrompt(new Prompt("Open pull request", new TextInputState(), this::submitPullRequestPrompt));
            return EventResult.HANDLED;
        }
        if (event.isChar('/')) {
            openPrompt(new Prompt("Search tests", new TextInputState(), this::submitSearchPrompt));
            return EventResult.HANDLED;
        }
        if (event.isChar('b')) {
            openPrompt(
                new Prompt("Branch", new TextInputState(requireNonNullElse(branch, "")), this::submitBranchPrompt)
            );
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void openPrompt(Prompt newPrompt) {
        prompt = newPrompt;
        focusManager.setFocus(PROMPT_INPUT_ID);
    }

    private Element promptDialog(Prompt openPrompt) {
        var hint = "Enter confirm · Esc cancel";
        return dialog(
            textInput(openPrompt.input()).id(PROMPT_INPUT_ID).onSubmit(() -> submitPrompt(openPrompt)),
            text(hint).dim()
        )
            .title(openPrompt.title())
            .rounded()
            .width(Math.max(openPrompt.title().length(), hint.length()) + 4)
            .onCancel(this::closePrompt);
    }

    private void closePrompt() {
        prompt = null;
    }

    void submitPrompt(Prompt closed) {
        prompt = null;
        closed.onSubmit().accept(closed.input().text().strip());
    }

    private void submitPullRequestPrompt(String value) {
        if (!value.matches("\\d+")) {
            listController.openFailed("Enter a pull request number, e.g. 42");
            return;
        }
        openPullRequest(Long.parseLong(value));
    }

    private void openPullRequest(long number) {
        pending = "Fetching pull request #" + number + "...";
        executor.submit(() -> {
            try {
                var withTests = loaders.pullRequest().load(number);
                publish.accept(() -> {
                    pending = null;
                    var controller = new PrTuiController(withTests.pr(), withTests.tests(), focusManager);
                    requireNonNull(router, "home screen is not wired to a router")
                        .push(new PrScreen(controller, new PrTuiView(controller)));
                });
            } catch (ApiException e) {
                publish.accept(() -> {
                    pending = null;
                    listController.openFailed(ApiErrors.message(e, "Pull request not found"));
                });
            }
        });
    }

    private void submitSearchPrompt(String value) {
        if (value.isEmpty()) {
            return;
        }
        pending = "Searching tests...";
        executor.submit(() -> {
            try {
                var tests = loaders.tests().search(value);
                publish.accept(() -> {
                    pending = null;
                    var screenRouter = requireNonNull(router, "home screen is not wired to a router");
                    if (tests.isEmpty()) {
                        listController.openFailed("No tests found.");
                    } else {
                        var searchScreen = new TestSearchScreen(
                            session,
                            focusManager,
                            executor,
                            publish,
                            screenRouter::push,
                            value,
                            tests
                        );
                        searchScreen.openWith();
                        screenRouter.push(searchScreen);
                    }
                });
            } catch (ApiException e) {
                publish.accept(() -> {
                    pending = null;
                    listController.openFailed(ApiErrors.message(e, "Repository not found"));
                });
            }
        });
    }

    private void submitBranchPrompt(String value) {
        var summary = this.summary;
        if (summary != null && !summary.getBranches().contains(value)) {
            var known = summary.getBranches().stream().limit(5).collect(Collectors.joining(", "));
            if (summary.getBranches().size() > 5) {
                known += ", …";
            }
            listController.openFailed("No statistics for branch " + value + " · known: " + known);
            return;
        }
        switchBranch(value.isEmpty() ? null : value);
    }

    private Element loadedContent() {
        List<FlakinessStatisticResponse> current = requireNonNullElse(statistics, List.of());
        return column(
            header(),
            statStrip(current),
            row(
                column(table.element()).percent(50).fill(),
                jobsPane().fill()
            ).fill(),
            // one flexible blank cell between the dashboard rows: it takes the terminal's odd leftover, so the
            // two rows split an even share and all panels render at the same height
            text("").length(1),
            row(
                branchesPane().percent(50),
                pullRequestsPane().fill()
            ).fill(),
            footer()
        );
    }

    /** The open pull requests from GitHub, or the reason they are unavailable. */
    private dev.tamboui.toolkit.elements.Panel pullRequestsPane() {
        var pullRequests = this.pullRequests;
        Element inner;
        if (pullRequests == null) {
            inner = text(pullRequestsError == null ? "Loading..." : "Pull requests unavailable (GitHub).").dim();
        } else if (pullRequests.isEmpty()) {
            inner = text("No open pull requests.").dim();
        } else {
            inner = listTable(
                "pull-requests",
                Row.from(Cell.from("PR"), Cell.from("Title")),
                pullRequests
                    .stream()
                    .map(
                        pullRequest -> Row.from(
                            Cell.from("#" + pullRequest.number()),
                            Cell.from(pullRequest.draft() ? "[draft] " + pullRequest.title() : pullRequest.title())
                        )
                    )
                    .toList(),
                List.of(Constraint.length(8), Constraint.fill()),
                pullRequestsController
            );
        }
        return panel("Open pull requests", inner).rounded().borderColor(DARK_GRAY);
    }

    /** The jobs with the most flaky runs, from the summary; informational, not selectable. */
    private dev.tamboui.toolkit.elements.Panel jobsPane() {
        var summary = this.summary;
        Element inner;
        if (summary == null) {
            inner = text("Statistics unavailable.").dim();
        } else {
            var jobs = summary.getFlakiestJobs();
            if (jobs.isEmpty()) {
                inner = text("No flaky jobs.").dim();
            } else {
                inner = listTable(
                    "jobs",
                    Row.from(Cell.from("Job"), Cell.from("Flaky"), Cell.from("Worst")),
                    jobs
                        .stream()
                        .limit(10)
                        .map(
                            job -> Row.from(
                                Cell.from(job.getJob()),
                                Cell.from(Long.toString(job.getFlakyTests())),
                                Cell.from(job.getWorstPercentage() + "%")
                            )
                        )
                        .toList(),
                    List.of(Constraint.fill(), Constraint.length(7), Constraint.length(6)),
                    null
                );
            }
        }
        return panel("Flakiest jobs", inner).rounded().borderColor(DARK_GRAY);
    }

    /** The branches with rolled-up statistics, from the summary; Enter switches to the selected branch. */
    private dev.tamboui.toolkit.elements.Panel branchesPane() {
        var summary = this.summary;
        Element inner;
        if (summary == null) {
            inner = text("Statistics unavailable.").dim();
        } else if (summary.getBranches().isEmpty()) {
            inner = text("No branches with statistics.").dim();
        } else {
            inner = listTable(
                "branches",
                Row.from(Cell.from("Branch")),
                summary
                    .getBranches()
                    .stream()
                    .map(
                        name -> Row.from(
                            Cell.from(
                                Span.styled(name, Style.EMPTY.fg(Objects.equals(name, branch) ? Color.CYAN : null))
                            )
                        )
                    )
                    .toList(),
                List.of(Constraint.fill()),
                branchesController
            );
        }
        return panel("Branches", inner).rounded().borderColor(DARK_GRAY);
    }

    /**
     * A selection table for a pane: no border of its own, the enclosing panel draws the frame. With a controller the
     * pane is selectable — the cursor shows only while the pane is focused, and only when there is something to
     * select; without one (e.g. the jobs pane) it renders plain rows.
     */
    private Element listTable(
        String id,
        Row header,
        List<Row> rows,
        List<Constraint> widths,
        @Nullable ListPaneController<?> controller
    ) {
        var base = table().id(id).header(header).rows(rows).widths(widths.toArray(Constraint[]::new));
        final dev.tamboui.toolkit.elements.TableElement table;
        if (controller == null) {
            table = base.state(new TableState());
        } else {
            var styled = base
                .state(controller.tableState())
                .highlightStyle(controller.isFocused() ? Style.EMPTY.reversed() : Style.EMPTY);
            table = controller.items().isEmpty() ? styled : styled.focusable().onKeyEvent(controller::handleKey);
        }
        return new Element() {
            @Override
            public void render(Frame frame, Rect area, RenderContext context) {
                context.renderChild(table, frame, area);
            }

            @Override
            public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
                return Size.UNKNOWN;
            }

            @Override
            public Constraint constraint() {
                return Constraint.fill();
            }
        };
    }

    private Element withDialog(Element content, String message) {
        return stack(content, dialog(text(message)).rounded().borderColor(RED).width(message.length() + 4));
    }

    private Element header() {
        var title = "TestLens · " + session.owner() + "/" + session.repo()
            + (branch == null ? "" : " · branch " + branch);
        return text(title).bold().cyan().length(1);
    }

    private Element footer() {
        var line = new StringBuilder();
        var action = focusedAction();
        if (action != null) {
            line.append(action).append(" · ");
        }
        var backOrQuit = router != null && router.canGoBack() ? "Esc/q back" : "q quit";
        line.append("↑/↓ select · Tab pane · p PR · / search · b branch · ").append(backOrQuit);
        return text(line.toString()).dim().length(1);
    }

    /**
     * What Enter would do in the focused pane, so a selection always announces its meaning before it is committed;
     * {@code null} when the focused pane has nothing to act on.
     */
    private @Nullable String focusedAction() {
        if (listController.isFocused() && !listController.statistics().isEmpty()) {
            var selected = listController.statistics().get(listController.tableState().selected());
            return "Enter: flakes of " + TestInfo.of(selected).displayName();
        }
        if (pullRequestsController.isFocused() && !pullRequestsController.items().isEmpty()) {
            var selected = pullRequestsController.items().get(pullRequestsController.tableState().selected());
            return "Enter: open #" + selected.number();
        }
        if (branchesController.isFocused() && !branchesController.items().isEmpty()) {
            var selected = branchesController.items().get(branchesController.tableState().selected());
            return Objects.equals(selected, branch)
                ? "Enter: reload " + selected
                : "Enter: switch to " + selected;
        }
        return null;
    }

    /**
     * The stat strip: the server-grade summary when the summary endpoint answered, otherwise a client-side reduction
     * of the ranking, and a plain empty-state line when there is nothing to summarize.
     */
    private Element statStrip(List<FlakinessStatisticResponse> statistics) {
        if (statistics.isEmpty()) {
            return text("No flaky or failing tests in the last 30 days.").dim().length(1);
        }
        var summary = this.summary;
        if (summary != null) {
            var line = new StringBuilder()
                .append(summary.getFlakyTests())
                .append(" flaky tests")
                .append(" · ")
                .append(requireNonNullElse(summary.getSeverity(), new Severity()).getHigh())
                .append(" high")
                .append(" · ")
                .append(summary.getConsistentlyFailingTests())
                .append(" consistently failing");
            var flakiestJobs = summary.getFlakiestJobs();
            if (!flakiestJobs.isEmpty()) {
                line.append(" · worst job: ").append(flakiestJobs.getFirst().getJob());
            }
            if (requireNonNullElse(summary.getStatisticsIncomplete(), false)) {
                line.append(" · statistics incomplete");
            }
            return text(line.toString()).dim().length(1);
        }
        var flakyTests = statistics.stream().map(FlakinessStatisticResponse::getUniqueId).distinct().count();
        var highSeverity = statistics
            .stream()
            .filter(statistic -> statistic.getFlaky().getSeverity() == FlakyCount.SeverityEnum.HIGH)
            .map(FlakinessStatisticResponse::getUniqueId)
            .distinct()
            .count();
        var line = flakyTests + " flaky tests · " + highSeverity + " high severity";
        var worstJob = worstJob(statistics);
        if (worstJob != null) {
            line += " · worst job: " + worstJob;
        }
        return text(line).dim().length(1);
    }

    private @Nullable String worstJob(List<FlakinessStatisticResponse> statistics) {
        Map<String, Long> flakyRunsByJob = statistics
            .stream()
            .filter(statistic -> statistic.getJob() != null)
            .collect(
                groupingBy(
                    FlakinessStatisticResponse::getJob,
                    summingLong(statistic -> statistic.getFlaky().getCount())
                )
            );
        return flakyRunsByJob
            .entrySet()
            .stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private void loadIfPending() {
        if (loadStarted) {
            return;
        }
        loadStarted = true;
        load();
    }

    private void load() {
        state = State.LOADING;
        error = null;
        summary = null;
        generation++;
        var requestedGeneration = generation;
        var requestedBranch = branch;
        executor.submit(() -> {
            try {
                var fetched = loaders.ranking().load(requestedBranch);
                publish.accept(() -> {
                    if (stale(requestedGeneration)) {
                        return;
                    }
                    statistics = fetched;
                    // without a branch, the server reports on the default branch, which the statistics name
                    branch = requestedBranch != null
                        ? requestedBranch
                        : fetched.stream()
                            .findFirst()
                            .map(FlakinessStatisticResponse::getBranch)
                            .orElse(requestedBranch);
                    listController.setStatistics(fetched);
                    table.dataChanged();
                    state = State.LOADED;
                });
            } catch (ApiException e) {
                publish.accept(() -> {
                    if (stale(requestedGeneration)) {
                        return;
                    }
                    error = ApiErrors.message(e, "Repository not found");
                    state = State.FAILED;
                });
            }
        });
        executor.submit(() -> {
            try {
                var fetched = loaders.summary().load(requestedBranch);
                publish.accept(() -> {
                    if (stale(requestedGeneration)) {
                        return;
                    }
                    summary = fetched;
                    jobsController.setItems(
                        fetched.getFlakiestJobs().stream().limit(4).toList()
                    );
                    branchesController.setItems(fetched.getBranches());
                    var currentBranchIndex = fetched.getBranches().indexOf(branch);
                    if (currentBranchIndex >= 0) {
                        branchesController.tableState().select(currentBranchIndex);
                    }
                });
            } catch (ApiException e) {
                // the summary enriches the stat strip; without it, the client-side strip stands in
            }
        });
        executor.submit(() -> {
            try {
                var fetched = loaders.pullRequests().load();
                publish.accept(() -> {
                    if (stale(requestedGeneration)) {
                        return;
                    }
                    pullRequests = fetched;
                    pullRequestsError = null;
                    pullRequestsController.setItems(fetched);
                });
            } catch (Exception e) {
                publish.accept(() -> {
                    if (stale(requestedGeneration)) {
                        return;
                    }
                    pullRequests = null;
                    pullRequestsError = "failed";
                });
            }
        });
    }

    /**
     * Whether another load was issued since the fetch (a branch switch), which invalidates the fetch's results.
     * A generation counter instead of a branch comparison, because deriving the default branch in the ranking's
     * publish must not invalidate the summary arriving in the same generation.
     */
    private boolean stale(int requestedGeneration) {
        return generation != requestedGeneration;
    }

    private void switchBranch(@Nullable String value) {
        branch = value;
        load();
    }

    private void openFlakes(FlakinessStatisticResponse statistic) {
        var test = TestInfo.of(statistic);
        pending = "Fetching flakes of " + test.displayName() + "...";
        executor.submit(() -> {
            try {
                var flakes = loaders.flakes().fetch(statistic);
                publish.accept(() -> {
                    pending = null;
                    var screenRouter = requireNonNull(router, "home screen is not wired to a router");
                    screenRouter.push(
                        new FlakesView(
                            new FlakesController(focusManager, test, flakes),
                            test,
                            statistic.getBranch(),
                            statistic.getWorkUnit(),
                            statistic.getJob()
                        )
                    );
                });
            } catch (ApiException e) {
                publish.accept(() -> {
                    pending = null;
                    listController.openFailed(ApiErrors.message(e, "Test not found"));
                });
            }
        });
    }
}
