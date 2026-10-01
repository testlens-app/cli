// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static app.testlens.cli.TestUtil.run;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void printsVersion() {
        var result = run("--version");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().trim()).matches("\\d+(\\.\\d+)+");
        assertThat(result.stderr()).isEmpty();
    }
}
