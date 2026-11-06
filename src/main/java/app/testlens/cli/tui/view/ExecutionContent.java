// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import app.testlens.cli.PrCommand;
import app.testlens.cli.client.model.ComparisonFailure;
import app.testlens.cli.util.TextDiff;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import java.util.ArrayList;
import org.jspecify.annotations.Nullable;

/** The diagnostics part of an execution detail view: the comparison failure diff and the stack trace. */
public final class ExecutionContent {

    private ExecutionContent() {}

    public static Text of(@Nullable ComparisonFailure comparisonFailure, @Nullable String stackTrace) {
        var lines = new ArrayList<Line>();
        TextDiff.diff(comparisonFailure).ifPresent(diff -> {
            lines.add(heading("Expected vs. actual"));
            diff.forEach(line -> lines.add(diffLine(line)));
            lines.add(Line.from(""));
            lines.add(heading("Stack trace"));
        });
        lines.addAll(stackTrace(stackTrace).lines());
        return Text.from(lines);
    }

    private static Line heading(String title) {
        return Line.styled(title, Style.EMPTY.bold());
    }

    private static Line diffLine(TextDiff.DiffLine line) {
        var color = line.marker() == '-' ? Color.RED : line.marker() == '+' ? Color.GREEN : null;
        var style = color == null ? Style.EMPTY : Style.EMPTY.fg(color);
        var spans = new ArrayList<Span>();
        spans.add(Span.styled(line.marker() + " ", style));
        for (var segment : line.segments()) {
            spans
                .add(Span.styled(segment.text(), segment.changed() && color != null ? style.reversed().bold() : style));
        }
        return Line.from(spans);
    }

    private static Text stackTrace(@Nullable String stackTrace) {
        if (stackTrace == null) {
            return Text.styled("No stack trace", Style.EMPTY.dim());
        }
        return Text.from(
            stackTrace.replace("\t", "    ")
                .lines()
                .map(
                    line -> PrCommand.isStackFrame(line) ? Line.styled(line, Style.EMPTY.dim()) : Line.from(line)
                )
                .toList()
        );
    }
}
