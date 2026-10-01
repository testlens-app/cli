// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import picocli.CommandLine;

public class TestUtil {
    static Result run(String... args) {
        return run(CommandLine.Help.Ansi.OFF, args);
    }

    static Result run(CommandLine.Help.Ansi ansi, String... args) {
        var out = new StringWriter();
        var err = new StringWriter();
        var exitCode = new CommandLine(new Testlens())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .setOut(new PrintWriter(out, true))
            .setErr(new PrintWriter(err, true))
            .setColorScheme(CommandLine.Help.defaultColorScheme(ansi))
            .execute(args);
        return new Result(exitCode, out.toString(), err.toString());
    }

    record Result(int exitCode, String stdout, String stderr) {}
}
