// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import app.testlens.cli.client.model.Execution;
import dev.tamboui.style.Color;
import dev.tamboui.text.CharWidth;

import java.util.List;

public class PrTuiUtils {

    private static final String ELLIPSIS = "…";

    /**
     * Keeps the outermost segment (e.g. the class) and abbreviates the remaining ones from the start so the innermost
     * segment (e.g. the method) stays visible.
     */
    static String abbreviatedTestName(List<String> displayNames, int width) {
        // skip the first display name which denotes the test engine
        var names = displayNames.subList(Math.min(1, displayNames.size()), displayNames.size());
        if (names.size() <= 1) {
            return truncate(String.join("", names), width, CharWidth.TruncatePosition.END);
        }
        var first = names.getFirst() + " > ";
        var rest = String.join(" > ", names.subList(1, names.size()));
        var restWidth = width - CharWidth.of(first);
        // at least the ellipsis and one character of the rest
        if (restWidth < 2) {
            return truncate(first + rest, width, CharWidth.TruncatePosition.END);
        }
        return first + truncate(rest, restWidth, CharWidth.TruncatePosition.START);
    }

    static String truncate(String value, int width, CharWidth.TruncatePosition position) {
        return CharWidth.truncateWithEllipsis(value, width, ELLIPSIS, position);
    }

    static Color outcomeColor(Execution.OutcomeEnum outcome) {
        return switch (outcome) {
            case SUCCESSFUL, REUSED -> Color.GREEN;
            case FAILED -> Color.RED;
            case SKIPPED, MUTED, ABORTED -> Color.YELLOW;
        };
    }
}
