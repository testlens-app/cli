// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import app.testlens.cli.client.ApiClient;
import app.testlens.cli.client.ApiException;
import app.testlens.cli.client.api.PullRequestsApi;
import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.client.model.PullRequestResponse;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import app.testlens.cli.tui.PrTui;
import app.testlens.cli.util.TextDiff;
import app.testlens.cli.util.TimeFormat;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.IStyle;
import picocli.CommandLine.Help.Ansi.Style;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "pr", description = "Show the tests of a pull request", mixinStandardHelpOptions = true)
public class PrCommand implements Callable<Integer> {

    private static final Pattern GITHUB_REMOTE = Pattern.compile("github\\.com[:/]([^/]+)/(.+?)(?:\\.git)?/?$");

    static final Pattern STACK_FRAME = Pattern.compile("\\s+(at .*|\\.\\.\\. \\d+ more)");

    public static boolean isStackFrame(String line) {
        return STACK_FRAME.matcher(line).matches();
    }

    @Spec
    @Nullable
    CommandSpec spec;

    @Parameters(description = "Pull request number", arity = "1")
    int number;

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

    @Option(names = "--sha", description = "Commit sha to show the tests of (defaults to the head of the pull request)")
    @Nullable
    String sha;

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
        var commandLine = requireNonNull(spec).commandLine();
        var out = commandLine.getOut();
        var err = commandLine.getErr();
        if (token == null || token.isBlank()) {
            err.println("No GitHub token provided (use --token or set GITHUB_TOKEN)");
            return 2;
        }
        var ownerAndRepo = repo != null ? repo.split("/", 2) : parseRemote(gitRemoteUrl());
        if (ownerAndRepo == null || ownerAndRepo.length != 2) {
            err.println("Cannot determine repository (use --repo owner/repo)");
            return 2;
        }
        var apiClient = new ApiClient();
        apiClient.updateBaseUri(requireNonNull(baseUrl));
        apiClient.setRequestInterceptor(request -> request.header("Authorization", "Bearer " + token));
        var api = new PullRequestsApi(apiClient);
        var ansi = commandLine.getColorScheme().ansi();
        try {
            var tui = mode == Mode.TUI || (mode == Mode.AUTO && isInteractiveTerminal());
            showProgress(out, ansi, "Fetching pull request...");
            var pr = api.getPullRequest(ownerAndRepo[0], ownerAndRepo[1], number);
            clearProgress(out, ansi);
            var prUrl = "https://github.com/" + ownerAndRepo[0] + "/" + ownerAndRepo[1] + "/pull/" + pr.getNumber();
            if (!tui) {
                printPullRequest(out, ansi, pr, prUrl);
                out.println();
            }
            showProgress(out, ansi, "Fetching tests...");
            var tests =
                api.getPullRequestTests(ownerAndRepo[0], ownerAndRepo[1], number, sha != null ? sha : pr.getHeadSha());
            clearProgress(out, ansi);
            if (tui) {
                PrTui.run(pr, tests);
            } else {
                printTests(out, ansi, tests);
            }
            return 0;
        } catch (ApiException e) {
            clearProgress(out, ansi);
            err.println(
                switch (e.getCode()) {
                    case 401 -> "Invalid GitHub token";
                    case 404 -> "Pull request not found";
                    default -> "Request failed: " + e.getMessage();
                }
            );
            return 1;
        }
    }

    enum Mode {
        AUTO,
        TUI,
        CLI
    }

    private static boolean isInteractiveTerminal() {
        var console = System.console();
        return console != null && console.isTerminal();
    }

    static String testName(ExecutedTest test) {
        // skip the first display name which denotes the test engine, like in the PR comment
        var displayNames = test.getDisplayNames();
        return String.join(" > ", displayNames.subList(Math.min(1, displayNames.size()), displayNames.size()));
    }

    static String @Nullable [] parseRemote(@Nullable String url) {
        if (url == null) {
            return null;
        }
        var matcher = GITHUB_REMOTE.matcher(url.strip());
        return matcher.find() ? new String[] { matcher.group(1), matcher.group(2) } : null;
    }

    private static @Nullable String gitRemoteUrl() {
        try {
            var process = new ProcessBuilder("git", "remote", "get-url", "origin")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            var output = new String(process.getInputStream().readAllBytes(), UTF_8);
            return process.waitFor() == 0 ? output : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static void printPullRequest(PrintWriter out, Ansi ansi, PullRequestResponse pr, String url) {
        var number = "#" + pr.getNumber();
        var numberLink =
            ansi.enabled() ? link(Style.underline.on() + number + Style.underline.off(), url) : number;
        out.println(styled(ansi, pr.getTitle() + " (" + numberLink + ")", Style.bold, Style.fg_blue));
        out.println("Head:  " + pr.getHeadSha());
        out.println("Tests: " + pr.getTestCount() + " executed");
        out.println("Jobs:  " + pr.getJobs().getCompleted() + "/" + pr.getJobs().getTotal() + " completed");
    }

    private static void printTests(PrintWriter out, Ansi ansi, PullRequestTestsResponse tests) {
        out.println("Commit: " + tests.getSha());
        var executedTests = tests.getTests();
        if (executedTests.isEmpty()) {
            out.println();
            out.println("No failed or flaky tests.");
        }
        var testsByWorkUnit = executedTests.stream()
            .collect(groupingBy(test -> test.getJob() + " > " + test.getWorkUnit(), LinkedHashMap::new, toList()));
        testsByWorkUnit.forEach((workUnit, testsInWorkUnit) -> {
            out.println();
            out.println(styled(ansi, workUnit, Style.underline));
            for (var test : testsInWorkUnit) {
                out.println("  " + styled(ansi, testName(test), Style.bold));
                var executions = test.getExecutions();
                for (int i = 0; i < executions.size(); i++) {
                    var execution = executions.get(i);
                    var outcome = execution.getOutcome();
                    var jobLink = ansi.enabled()
                        ? link(styled(ansi, "job", Style.underline), execution.getHtmlUrl())
                        : execution.getHtmlUrl();
                    out.println(
                        "    " + (i + 1) + ". " + styled(ansi, outcome.toString(), color(outcome)) + " ("
                            + TimeFormat.formatTime(execution) + ", see " + jobLink + ")"
                    );
                    TextDiff.diff(execution.getComparisonFailure())
                        .ifPresent(diff -> diff.forEach(line -> out.println("       " + diffLine(ansi, line))));
                    var stackTrace = execution.getStackTrace();
                    if (stackTrace != null) {
                        // terminals align tabs to their tab stops, which would make frames barely indented
                        stackTrace.replace("\t", "    ")
                            .lines()
                            .map(line -> isStackFrame(line) ? styled(ansi, line, Style.faint) : line)
                            .forEach(line -> out.println("       " + line));
                    }
                }
            }
        });
    }

    private static void showProgress(PrintWriter out, Ansi ansi, String message) {
        if (ansi.enabled()) {
            out.print(styled(ansi, message, Style.faint));
            out.flush();
        }
    }

    private static void clearProgress(PrintWriter out, Ansi ansi) {
        if (ansi.enabled()) {
            // carriage return + erase line
            out.print("\r\u001B[2K");
            out.flush();
        }
    }

    private static Style color(Execution.OutcomeEnum outcome) {
        return switch (outcome) {
            case SUCCESSFUL, REUSED -> Style.fg_green;
            case FAILED -> Style.fg_red;
            case SKIPPED, MUTED, ABORTED -> Style.fg_yellow;
        };
    }

    private static String link(String text, String url) {
        // OSC 8 hyperlink, see https://gist.github.com/egmontkob/eb114294efbcd5adb1944c9f3cb5feda
        return "\u001B]8;;" + url + "\u001B\\" + text + "\u001B]8;;\u001B\\";
    }

    private static String diffLine(Ansi ansi, TextDiff.DiffLine line) {
        var color = line.marker() == '-' ? Style.fg_red : line.marker() == '+' ? Style.fg_green : null;
        var result = new StringBuilder(color == null ? "  " : styled(ansi, line.marker() + " ", color));
        for (var segment : line.segments()) {
            result.append(
                color == null
                    ? segment.text()
                    : segment.changed()
                        ? styled(ansi, segment.text(), color, Style.reverse, Style.bold)
                        : styled(ansi, segment.text(), color)
            );
        }
        return result.toString();
    }

    private static String styled(Ansi ansi, String text, IStyle... styles) {
        return ansi.enabled() ? Style.on(styles) + text + Style.reset.on() : text;
    }
}
