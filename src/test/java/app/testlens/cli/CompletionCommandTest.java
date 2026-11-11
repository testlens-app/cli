// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static app.testlens.cli.TestUtil.run;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompletionCommandTest {

    @ParameterizedTest
    @ValueSource(strings = { "bash", "zsh" })
    void printsBashCompletionScript(String shell) {
        var result = run("completion", "--shell", shell);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout())
            .contains("complete -F _complete_testlens -o default testlens")
            .contains("bashcompinit")
            .contains("pr")
            .contains("completion");
    }

    @Test
    void printsFishCompletionScript() {
        var result = run("completion", "-s", "fish");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout())
            .isEqualTo(
                """
                complete -c testlens -n 'not __fish_seen_subcommand_from pr completion' -s h -l help -d 'Show this help message and exit.'
                complete -c testlens -n 'not __fish_seen_subcommand_from pr completion' -s V -l version -d 'Print version information and exit.'
                complete -c testlens -n 'not __fish_seen_subcommand_from pr completion' -f -a pr -d 'Show the tests of a pull request'
                complete -c testlens -n '__fish_seen_subcommand_from pr' -l repo -r -d 'Repository as owner/repo (defaults to the GitHub remote \\'origin\\' of the current directory)'
                complete -c testlens -n '__fish_seen_subcommand_from pr' -l token -r -d 'GitHub access token (defaults to $GITHUB_TOKEN or $GH_TOKEN)'
                complete -c testlens -n '__fish_seen_subcommand_from pr' -l sha -r -d 'Commit sha to show the tests of (defaults to the head of the pull request)'
                complete -c testlens -n '__fish_seen_subcommand_from pr' -l mode -r -f -a 'AUTO TUI CLI' -d 'Output mode: AUTO, TUI, CLI (default: auto, uses the TUI in an interactive terminal)'
                complete -c testlens -n '__fish_seen_subcommand_from pr' -s h -l help -d 'Show this help message and exit.'
                complete -c testlens -n '__fish_seen_subcommand_from pr' -s V -l version -d 'Print version information and exit.'
                complete -c testlens -n 'not __fish_seen_subcommand_from pr completion' -f -a completion -d 'Generate a completion script for bash, zsh, or fish'
                complete -c testlens -n '__fish_seen_subcommand_from completion' -s s -l shell -r -f -a 'BASH ZSH FISH' -d 'Shell type: BASH, ZSH, FISH'
                complete -c testlens -n '__fish_seen_subcommand_from completion' -s h -l help -d 'Show this help message and exit.'
                complete -c testlens -n '__fish_seen_subcommand_from completion' -s V -l version -d 'Print version information and exit.'
                """
            );
    }

    @Test
    void requiresShell() {
        var result = run("completion");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("--shell");
    }

    @Test
    void rejectsUnsupportedShell() {
        var result = run("completion", "-s", "powershell");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("powershell");
    }
}
