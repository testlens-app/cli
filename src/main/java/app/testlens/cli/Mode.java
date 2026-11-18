// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

/** Whether a command renders the TUI or prints plain output; exposed via the hidden {@code --mode} escape hatch. */
public enum Mode {
    AUTO,
    TUI,
    CLI,
}
