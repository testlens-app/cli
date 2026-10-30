// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static app.testlens.cli.CommandSupport.apiClient;
import static app.testlens.cli.CommandSupport.isInteractiveTerminal;
import static app.testlens.cli.CommandSupport.resolveRepo;
import static app.testlens.cli.util.Console.clearProgress;
import static app.testlens.cli.util.Console.diffLine;
import static app.testlens.cli.util.Console.link;
import static app.testlens.cli.util.Console.showProgress;
import static app.testlens.cli.util.Console.styled;
import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.ApiException;
import app.testlens.cli.client.api.TestsApi;
import app.testlens.cli.client.model.TestExecutionResponse;
import app.testlens.cli.client.model.TestResponse;
import app.testlens.cli.tui.TestExecutionsTui;
import app.testlens.cli.tui.model.TestInfo;
import app.testlens.cli.util.TextDiff;
import app.testlens.cli.util.TimeFormat;
import java.util.List;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.Style;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code testlens test <id>} shows the recent executions of one test in a detail view. With {@code --unique-id} or
 * {@code --query} in place of the id, it prints the matching tests of the search instead. Options mirror
 * {@link PrCommand}.
 */
@Command(
    name = "test",
    description = "Show the executions of a test, or search tests by unique ID or name",
    mixinStandardHelpOptions = true
)
public class TestCommand implements Callable<Integer> {

    @Spec
    @Nullable
    CommandSpec spec;

    @Parameters(index = "0", arity = "0..1", description = "Internal test ID, e.g. from testlens statistics flakiness")
    @Nullable
    Long id;

    @Option(names = "--unique-id", description = "Print the test that matches the test unique ID exactly")
    @Nullable
    String uniqueId;

    @Option(
        names = "--query",
        description = "Print tests where the query matches the unique ID or a display name, case-insensitively"
    )
    @Nullable
    String query;

    @Option(
        names = "--repo",
        description = "Repository as owner/repo (defaults to the GitHub remote 'origin' of the current directory)"
    )
    @Nullable
    String repo;

    @Option(
        names = "--token",
        description = "GitHub access token (defaults to $GITHUB_TOKEN or $GH_TOKEN)",
        defaultValue = "${env:GITHUB_TOKEN:-${env:GH_TOKEN}}"
    )
    @Nullable
    String token;

    @Option(
        names = "--mode",
        description = "Output mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}, uses the TUI in an interactive terminal)",
        defaultValue = "auto"
    )
    Mode mode = Mode.AUTO;

    @Option(
        names = "--base-url",
        description = "API base URL",
        defaultValue = "https://api.testlens.app",
        hidden = true
    )
    @Nullable
    String baseUrl;

    @Override
    public Integer call() throws Exception {
        var out = requireNonNull(spec).commandLine().getOut();
        var err = spec.commandLine().getErr();
        if (id != null && (uniqueId != null || query != null)) {
            err.println("Provide either a test id or a search filter (--unique-id/--query), not both");
            return 2;
        }
        if (id == null && uniqueId == null && query == null) {
            err.println("Provide a test id, or search tests with --unique-id or --query");
            return 2;
        }
        if (token == null || token.isBlank()) {
            err.println("No GitHub token provided (use --token or set GITHUB_TOKEN)");
            return 2;
        }
        var ownerAndRepo = resolveRepo(repo);
        if (ownerAndRepo == null || ownerAndRepo.length != 2) {
            err.println("Cannot determine repository (use --repo owner/repo)");
            return 2;
        }
        var owner = ownerAndRepo[0];
        var repoName = ownerAndRepo[1];
        var ansi = spec.commandLine().getColorScheme().ansi();
        var api = new TestsApi(apiClient(token, requireNonNull(baseUrl)));
        if (id == null) {
            return searchTests(out, ansi, api, err, owner, repoName);
        }
        return showTest(out, ansi, api, err, owner, repoName, id);
    }

    private int searchTests(
        java.io.PrintWriter out,
        Ansi ansi,
        TestsApi api,
        java.io.PrintWriter err,
        String owner,
        String repoName
    ) throws Exception {
        try {
            showProgress(out, ansi, "Searching tests...");
            var tests = api.searchTests(owner, repoName, uniqueId, query, null, null);
            clearProgress(out, ansi);
            printSearchResults(out, ansi, tests);
            return 0;
        } catch (ApiException e) {
            clearProgress(out, ansi);
            return fail(err, e, "Repository not found");
        }
    }

    private int showTest(
        java.io.PrintWriter out,
        Ansi ansi,
        TestsApi api,
        java.io.PrintWriter err,
        String owner,
        String repoName,
        long testId
    ) throws Exception {
        try {
            var tui = mode == Mode.TUI || (mode == Mode.AUTO && isInteractiveTerminal());
            showProgress(out, ansi, "Fetching test...");
            var test = api.getTest(owner, repoName, testId);
            clearProgress(out, ansi);
            showProgress(out, ansi, "Fetching executions...");
            var executions = api.getTestExecutions(owner, repoName, testId, null, null, null, null);
            clearProgress(out, ansi);
            var info = TestInfo.of(test);
            if (tui) {
                TestExecutionsTui.run(info, executions);
            } else {
                printTest(out, ansi, info, executions);
            }
            return 0;
        } catch (ApiException e) {
            clearProgress(out, ansi);
            return fail(err, e, "Test not found");
        }
    }

    static int fail(java.io.PrintWriter err, ApiException e, String notFoundMessage) {
        err.println(
            switch (e.getCode()) {
                case 401 -> "Invalid GitHub token";
                case 404 -> notFoundMessage;
                default -> "Request failed: " + e.getMessage();
            }
        );
        return 1;
    }

    static void printSearchResults(java.io.PrintWriter out, Ansi ansi, List<TestResponse> tests) {
        if (tests.isEmpty()) {
            out.println("No tests found.");
            return;
        }
        for (int i = 0; i < tests.size(); i++) {
            var test = tests.get(i);
            if (i > 0) {
                out.println();
            }
            out.println(
                "  " + (i + 1) + ". " + styled(ansi, "test " + test.getId(), Style.bold) + " " + testName(test)
            );
            out.println("      " + styled(ansi, test.getUniqueId(), Style.faint));
        }
    }

    static String testName(TestResponse test) {
        // skip the first display name which denotes the test engine, like in the PR comment
        return CommandSupport.testName(test.getDisplayNames());
    }

    static void printTest(
        java.io.PrintWriter out,
        Ansi ansi,
        TestInfo test,
        List<TestExecutionResponse> executions
    ) {
        out.println(styled(ansi, test.displayName(), Style.bold) + " (test " + test.id() + ")");
        out.println("   " + styled(ansi, test.uniqueId(), Style.faint));
        if (executions.isEmpty()) {
            out.println();
            out.println("No executions in the last 30 days.");
            return;
        }
        for (int i = 0; i < executions.size(); i++) {
            var execution = executions.get(i);
            var outcome = execution.getOutcome();
            var htmlUrl = execution.getHtmlUrl();
            var seeJob = htmlUrl == null
                ? ""
                : ", see " + (ansi.enabled()
                    ? link(styled(ansi, "job", Style.underline), htmlUrl)
                    : htmlUrl);
            out.println(
                "    " + (i + 1) + ". " + styled(ansi, outcome.toString(), color(outcome)) + " ("
                    + TimeFormat.formatTime(execution.getStartTime(), execution.getDurationMillis()) + seeJob + ")"
            );
            out.println(
                "       " + styled(
                    ansi,
                    execution.getWorkUnit() + " · " + sha(execution) + " @ " + execution.getBranch(),
                    Style.faint
                )
            );
            TextDiff.diff(execution.getComparisonFailure())
                .ifPresent(diff -> diff.forEach(line -> out.println("       " + diffLine(ansi, line))));
            var stackTrace = execution.getStackTrace();
            if (stackTrace != null) {
                // terminals align tabs to their tab stops, which would make frames barely indented
                stackTrace.replace("\t", "    ")
                    .lines()
                    .map(line -> PrCommand.isStackFrame(line) ? styled(ansi, line, Style.faint) : line)
                    .forEach(line -> out.println("       " + line));
            }
        }
    }

    private static String sha(TestExecutionResponse execution) {
        var sha = execution.getSha();
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }

    static Style color(TestExecutionResponse.OutcomeEnum outcome) {
        return switch (outcome) {
            case SUCCESSFUL, REUSED -> Style.fg_green;
            case FAILED -> Style.fg_red;
            case SKIPPED, MUTED, ABORTED -> Style.fg_yellow;
        };
    }

    enum Mode {
        AUTO,
        TUI,
        CLI
    }
}
