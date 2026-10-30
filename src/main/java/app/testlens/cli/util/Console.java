// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.util;

import app.testlens.cli.util.TextDiff.DiffLine;
import java.io.PrintWriter;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.IStyle;

/** Console output helpers shared by the commands. */
public final class Console {

    private Console() {}

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
