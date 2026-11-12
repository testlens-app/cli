// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static app.testlens.cli.TestUtil.run;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompletionCommandTest {

    private static final Set<String> COMMANDS = Set.of("pr", "completion");

    @ParameterizedTest
    @ValueSource(strings = { "bash", "zsh" })
    void printsBashCompletionScript(String shell) {
        var result = run("completion", "--shell", shell);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout())
            .contains("complete -F _complete_testlens -o default testlens")
            .contains("bashcompinit")
            .contains(COMMANDS);
    }

    @Test
    void printsFishCompletionScript() {
        var result = run("completion", "-s", "fish");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout())
            .contains("complete -c testlens")
            .contains(COMMANDS);
    }

    @Test
    void usesBashAsDefault() {
        var result = run("completion");

        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    void rejectsUnsupportedShell() {
        var result = run("completion", "-s", "powershell");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("powershell");
    }
}
