// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import app.testlens.cli.tui.TestlensTui;
import app.testlens.cli.tui.TuiSession;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

@Command(
    name = "testlens",
    mixinStandardHelpOptions = true,
    subcommands = { PrCommand.class, TestCommand.class, FlakesCommand.class, StatisticsCommand.class,
        CompletionCommand.class, },
    versionProvider = Testlens.VersionResourceReader.class
)
public class Testlens implements Callable<Integer> {

    @Spec
    @Nullable
    CommandSpec spec;

    // the connection options are inherited by all subcommands, so they can be specified
    // before or after the subcommand name; they also serve the no-argument TUI launch
    @Option(
        names = "--repo",
        description = "Repository as owner/repo (defaults to the GitHub remote 'origin' of the current directory)",
        scope = ScopeType.INHERIT
    )
    @Nullable
    String repo;

    @Option(
        names = "--token",
        description = "GitHub access token (defaults to $GITHUB_TOKEN or $GH_TOKEN)",
        defaultValue = "${env:GITHUB_TOKEN:-${env:GH_TOKEN}}",
        scope = ScopeType.INHERIT
    )
    @Nullable
    String token;

    @Option(
        names = "--base-url",
        description = "API base URL",
        defaultValue = "https://api.testlens.app",
        hidden = true,
        scope = ScopeType.INHERIT
    )
    @Nullable
    String baseUrl;

    @Option(
        names = "--github-url",
        description = "GitHub API base URL",
        defaultValue = "https://api.github.com",
        hidden = true,
        scope = ScopeType.INHERIT
    )
    @Nullable
    String githubUrl;

    // hidden escape hatch (see docs/debugging.md and the plain-output twins of the commands); the visible
    // switch is the terminal interactivity: interactive terminals get the TUI, pipes and redirects get plain output
    @Option(
        names = "--mode",
        description = "Output mode: ${COMPLETION-CANDIDATES}",
        defaultValue = "auto",
        hidden = true,
        scope = ScopeType.INHERIT
    )
    Mode mode = Mode.AUTO;

    @Override
    public Integer call() throws Exception {
        // reached only when no subcommand was given: open the TUI in an interactive terminal, otherwise show the usage
        var commandLine = requireNonNull(spec).commandLine();
        var err = commandLine.getErr();
        var tui = mode == Mode.TUI || (mode == Mode.AUTO && CommandSupport.isInteractiveTerminal());
        if (!tui) {
            commandLine.usage(err);
            return 2;
        }
        if (token == null || token.isBlank()) {
            err.println("No GitHub token provided (use --token or set GITHUB_TOKEN)");
            return 2;
        }
        var ownerAndRepo = CommandSupport.resolveRepo(repo);
        if (ownerAndRepo == null || ownerAndRepo.length != 2) {
            err.println("Cannot determine repository (use --repo owner/repo)");
            return 2;
        }
        var session = new TuiSession(
            ownerAndRepo[0],
            ownerAndRepo[1],
            CommandSupport.apiClient(token, requireNonNull(baseUrl)),
            requireNonNull(githubUrl),
            token
        );
        TestlensTui.runHome(session);
        return 0;
    }

    static void main(String[] args) {
        System.exit(new CommandLine(new Testlens()).setCaseInsensitiveEnumValuesAllowed(true).execute(args));
    }

    public static class VersionResourceReader implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            var resourceAsStream = getClass().getResourceAsStream("/version.txt");
            if (resourceAsStream == null) {
                return new String[0];
            }
            try (var inputStream = resourceAsStream) {
                return new String[] { new String(inputStream.readAllBytes(), UTF_8).trim() };
            } catch (IOException e) {
                return new String[0];
            }
        }
    }
}
