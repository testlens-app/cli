// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static app.testlens.cli.CommandSupport.apiClient;
import static app.testlens.cli.CommandSupport.isInteractiveTerminal;
import static app.testlens.cli.CommandSupport.resolveRepo;
import static app.testlens.cli.util.Console.clearProgress;
import static app.testlens.cli.util.Console.showProgress;
import static app.testlens.cli.util.Console.styled;
import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.ApiException;
import app.testlens.cli.client.api.StatisticsApi;
import app.testlens.cli.client.api.TestsApi;
import app.testlens.cli.client.model.FlakinessStatisticResponse;
import app.testlens.cli.client.model.FlakyCount;
import app.testlens.cli.tui.StatisticsTui;
import app.testlens.cli.tui.model.TestInfo;
import java.util.List;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.Style;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * The statistics command group; see the ADR-013 triage workflow. The only statistic type of the first version is
 * {@code flakiness}, serving the ranked triage list.
 */
@Command(
    name = "statistics",
    description = "Ranked test statistics of a repository",
    mixinStandardHelpOptions = true,
    subcommands = { StatisticsCommand.Flakiness.class }
)
public class StatisticsCommand {

    @Command(
        name = "flakiness",
        description = "Show the tests of a branch ranked by flakiness",
        mixinStandardHelpOptions = true
    )
    static class Flakiness implements Callable<Integer> {

        @Spec
        @Nullable
        CommandSpec spec;

        @Option(
            names = "--branch",
            description = "Branch to report on (defaults to the repository's default branch)"
        )
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
            var apiClient = apiClient(token, requireNonNull(baseUrl));
            try {
                var api = new StatisticsApi(apiClient);
                var tui = mode == Mode.TUI || (mode == Mode.AUTO && isInteractiveTerminal());
                showProgress(out, ansi, "Fetching statistics...");
                var statistics = api.getFlakinessStatistics(owner, repoName, branch, null, null);
                clearProgress(out, ansi);
                if (tui) {
                    StatisticsTui.run(new TestsApi(apiClient), owner, repoName, branch, statistics);
                } else {
                    printStatistics(out, ansi, branch, statistics);
                }
                return 0;
            } catch (ApiException e) {
                clearProgress(out, ansi);
                err.println(
                    switch (e.getCode()) {
                        case 401 -> "Invalid GitHub token";
                        case 404 -> "Repository not found";
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
    }

    static void printStatistics(
        java.io.PrintWriter out,
        Ansi ansi,
        @Nullable String branch,
        List<FlakinessStatisticResponse> statistics
    ) {
        out.println(styled(ansi, "Flakiness ranking", Style.bold) + (branch == null ? "" : " · branch " + branch));
        if (statistics.isEmpty()) {
            out.println();
            out.println("No flaky or failing tests.");
            return;
        }
        for (int i = 0; i < statistics.size(); i++) {
            var statistic = statistics.get(i);
            if (i > 0) {
                out.println();
            }
            out.println(
                "  " + (i + 1) + ". " + severityIcon(statistic.getFlaky().getSeverity()) + " "
                    + styled(ansi, CommandSupport.testName(statistic.getDisplayNames()), Style.bold)
            );
            out.println("      " + styled(ansi, metaLine(statistic), Style.faint));
        }
    }

    private static String metaLine(FlakinessStatisticResponse statistic) {
        var failed = statistic.getFailed();
        var flaky = statistic.getFlaky();
        return "work unit " + statistic.getWorkUnit() + " · flaky " + flaky.getCount() + "/" + flaky.getTotal()
            + " (" + flaky.getPercentage() + "%) · failed " + failed.getCount() + " · test " + testId(statistic);
    }

    private static long testId(FlakinessStatisticResponse statistic) {
        return TestInfo.of(statistic).id();
    }

    static String severityIcon(FlakyCount.@Nullable SeverityEnum severity) {
        if (severity == null) {
            return " ";
        }
        // mirrors the icons of the Test Dashboard (FlakinessSeverity in the statistics model)
        return switch (severity) {
            case NONE -> "🟢";
            case LOW -> "🟡";
            case MEDIUM -> "🟠";
            case HIGH -> "🔴";
        };
    }
}
