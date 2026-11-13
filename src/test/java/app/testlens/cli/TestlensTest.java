// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static app.testlens.cli.TestUtil.run;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The no-argument launch of the root command: it opens the TUI in an interactive terminal (not testable here, since
 * tests have no TTY) and shows the usage otherwise.
 */
class TestlensTest {

    @Test
    void without_a_tty_the_root_shows_the_usage() {
        var result = run();

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("Usage: testlens", "--repo=<repo>", "pr", "statistics");
    }

    @Test
    void the_root_lists_the_inherited_connection_options_in_subcommand_help() {
        var result = run("pr", "--help");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("--repo=<repo>", "--token=<token>");
        // the hidden escape hatch stays undocumented
        assertThat(result.stdout()).doesNotContain("--mode", "--base-url");
    }
}
