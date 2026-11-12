// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static dev.tamboui.toolkit.Toolkit.richText;

import app.testlens.cli.client.model.Execution;
import app.testlens.cli.tui.controller.PrDetailsController;
import app.testlens.cli.tui.model.PrTuiModel;
import app.testlens.cli.util.Console;
import app.testlens.cli.util.TimeFormat;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import java.util.ArrayList;
import org.jspecify.annotations.Nullable;

/**
 * The details pane of the flakes screen: the shared execution tabs, in chronological order, with time and job only.
 * Unlike the PR details pane, it omits the test and its location, which are the same for all executions of a flake
 * and shown in the screen title and the table.
 */
public final class FlakeDetailsView {

    private static final int DETAILS_HEADER_HEIGHT = 3;

    private final ExecutionTabsPane pane;

    public FlakeDetailsView(PrDetailsController controller, PrTuiModel model) {
        this.pane = new ExecutionTabsPane(
            controller,
            model,
            (test, execution) -> {
                var environment = Console.environment(execution.getBuildEnvironment());
                return richText(detailsHeader(execution, environment))
                    .ellipsis()
                    .length(DETAILS_HEADER_HEIGHT + (environment == null ? 0 : 1));
            }
        );
    }

    public Element render() {
        return pane.render();
    }

    private static Text detailsHeader(Execution execution, @Nullable String environment) {
        var htmlUrl = execution.getHtmlUrl();
        var lines = new ArrayList<Line>();
        lines.add(
            Line.from(
                Span.raw("Time: "),
                Span.raw(TimeFormat.formatTime(execution.getStartTime(), execution.getDurationMillis()))
            )
        );
        lines.add(
            Line.from(
                Span.raw("Job:  "),
                // a missing job URL is mapped to an empty one
                htmlUrl.isEmpty()
                    ? Span.raw("-")
                    : Span.styled(htmlUrl, Style.EMPTY.underlined().hyperlink(htmlUrl))
            )
        );
        if (environment != null) {
            lines.add(Line.from(Span.raw("Env:  "), Span.raw(environment)));
        }
        return Text.from(lines);
    }
}
