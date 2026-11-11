// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static app.testlens.cli.TestUtil.run;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompletionCommandTest {

    @Test
    void printsCompletionScript() {
        var result = run("completion");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout())
            .contains("complete -F _complete_testlens -o default testlens")
            .contains("bashcompinit")
            .contains("pr")
            .contains("completion");
    }
}
