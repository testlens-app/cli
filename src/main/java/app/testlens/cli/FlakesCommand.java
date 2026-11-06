// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static app.testlens.cli.CommandSupport.apiClient;
import static app.testlens.cli.CommandSupport.isInteractiveTerminal;
import static app.testlens.cli.CommandSupport.resolveRepo;
import static app.testlens.cli.util.Console.clearProgress;
import static app.testlens.cli.util.Console.showProgress;
import static app.testlens.cli.util.Console.styled;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import app.testlens.cli.TestCommand.Mode;
import app.testlens.cli.client.ApiException;
import app.testlens.cli.client.api.TestsApi;
import app.testlens.cli.client.model.FlakeResponse;
import app.testlens.cli.client.model.TestExecutionResponse;
import app.testlens.cli.tui.FlakesTui;
import app.testlens.cli.tui.controller.FlakesController;
import app.testlens.cli.tui.model.TestInfo;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * {@code testlens flakes <id>} shows the flakes of one test: the builds in which it flipped its outcome after retries,
 * each with all its executions in chronological order. Options mirror {@link TestCommand}.
 */
@Command(
    name = "flakes",
    description = "Show the flaky builds of a test with all their executions",
    mixinStandardHelpOptions = true
)
public class FlakesCommand implements Callable<Integer> {

    @Spec
    @Nullable
    CommandSpec spec;

    @Parameters(index = "0", description = "Internal test ID, e.g. from testlens statistics flakiness")
    long id;

    @Option(names = "--branch", description = "Only flakes on this branch; PR builds count toward their target branch")
    @Nullable
    String branch;

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
        try {
            var tui = mode == Mode.TUI || (mode == Mode.AUTO && isInteractiveTerminal());
            showProgress(out, ansi, "Fetching test...");
            var test = TestInfo.of(api.getTest(owner, repoName, id));
            clearProgress(out, ansi);
            showProgress(out, ansi, "Fetching flakes...");
            var flakes = fetchFlakes(api, owner, repoName, id, branch);
            clearProgress(out, ansi);
            if (tui) {
                FlakesTui.run(test, branch, flakes);
            } else {
                printFlakes(out, ansi, test, branch, flakes);
            }
            return 0;
        } catch (ApiException e) {
            clearProgress(out, ansi);
            return TestCommand.fail(err, e, "Test not found");
        }
    }

    /** Fetches all pages of the flakes of a test, newest first. */
    public static List<FlakeResponse> fetchFlakes(
        TestsApi api,
        String owner,
        String repo,
        long testId,
        @Nullable String branch
    ) throws ApiException {
        var flakes = new ArrayList<FlakeResponse>();
        for (int page = 1;; page++) {
            var response = api.getTestFlakesWithHttpInfo(owner, repo, testId, null, branch, page, null, Map.of());
            if (response.getData() != null) {
                flakes.addAll(response.getData());
            }
            if (!ExecutionsLoader.hasRelNext(response.getHeaders())) {
                return flakes;
            }
        }
    }

    static void printFlakes(
        PrintWriter out,
        Ansi ansi,
        TestInfo test,
        @Nullable String branch,
        List<FlakeResponse> flakes
    ) {
        out.println(
            styled(ansi, test.displayName(), Style.bold) + " (test " + test.id() + ")"
                + (branch == null ? "" : " · branch " + branch)
        );
        out.println("   " + styled(ansi, test.uniqueId(), Style.faint));
        out.println("   " + styled(ansi, "newest flake first, executions in chronological order", Style.faint));
        if (flakes.isEmpty()) {
            out.println();
            out.println("No flakes in the last 30 days.");
            return;
        }
        for (int i = 0; i < flakes.size(); i++) {
            var flake = flakes.get(i);
            out.println();
            out.println(
                "  " + (i + 1) + ". " + styled(
                    ansi,
                    FlakesController.abbreviatedSha(flake.getSha()) + " @ " + flake.getBranch(),
                    Style.bold
                ) + " · " + flake.getWorkUnit()
            );
            var executions = flake.getExecutions()
                .stream()
                .sorted(comparing(TestExecutionResponse::getStartTime))
                .toList();
            for (int j = 0; j < executions.size(); j++) {
                TestCommand.printExecution(out, ansi, "      ", j + 1, executions.get(j), false);
            }
        }
    }
}
