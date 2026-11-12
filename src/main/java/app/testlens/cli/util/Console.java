// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.util;

import app.testlens.cli.client.model.BuildEnvironment;
import app.testlens.cli.util.TextDiff.DiffLine;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.IStyle;

/** Console output helpers shared by the commands. */
public final class Console {

    private Console() {}

    /**
     * Human-readable build environment, e.g. {@code "Linux 6.8.0-45-generic · amd64 · Java 21.0.5"}; {@code null}
     * without any data, so callers can omit the environment instead of printing placeholders.
     */
    public static @Nullable String environment(@Nullable BuildEnvironment environment) {
        if (environment == null) {
            return null;
        }
        var os = Stream.of(environment.getOsName(), environment.getOsVersion())
            .filter(Objects::nonNull)
            .collect(Collectors.joining(" "));
        var parts = Stream
            .of(
                os.isEmpty() ? null : os,
                environment.getOsArch(),
                environment.getJavaVersion() == null ? null : "Java " + environment.getJavaVersion()
            )
            .filter(Objects::nonNull)
            .toList();
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    public static String styled(Ansi ansi, String text, IStyle... styles) {
        return ansi.enabled() ? Ansi.Style.on(styles) + text + Ansi.Style.reset.on() : text;
    }

    /** OSC 8 hyperlink, see https://gist.github.com/egmontkob/eb114294efbcd5adb1944c9f3cb5feda */
    public static String link(String text, String url) {
        return "\u001B]8;;" + url + "\u001B\\" + text + "\u001B]8;;\u001B\\";
    }

    public static void showProgress(PrintWriter out, Ansi ansi, String message) {
        if (ansi.enabled()) {
            out.print(styled(ansi, message, Ansi.Style.faint));
            out.flush();
        }
    }

    public static void clearProgress(PrintWriter out, Ansi ansi) {
        if (ansi.enabled()) {
            // carriage return + erase line
            out.print("\r\u001B[2K");
            out.flush();
        }
    }

    public static String diffLine(Ansi ansi, DiffLine line) {
        var color = line.marker() == '-' ? Ansi.Style.fg_red : line.marker() == '+' ? Ansi.Style.fg_green : null;
        var result = new StringBuilder(color == null ? "  " : styled(ansi, line.marker() + " ", color));
        for (var segment : line.segments()) {
            result.append(
                color == null
                    ? segment.text()
                    : segment.changed()
                        ? styled(ansi, segment.text(), color, Ansi.Style.reverse, Ansi.Style.bold)
                        : styled(ansi, segment.text(), color)
            );
        }
        return result.toString();
    }
}
