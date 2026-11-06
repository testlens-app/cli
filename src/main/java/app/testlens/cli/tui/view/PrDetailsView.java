// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static app.testlens.cli.tui.view.PrTuiUtils.abbreviatedTestName;
import static dev.tamboui.toolkit.Toolkit.richText;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.tui.controller.PrDetailsController;
import app.testlens.cli.tui.model.PrTuiModel;
import app.testlens.cli.util.TimeFormat;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.RichTextState;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import java.util.List;
import java.util.function.IntFunction;

/** The details pane of the PR screen: the shared execution tabs with the test, its location, time and job. */
public final class PrDetailsView {

    private static final int DETAILS_HEADER_HEIGHT = 5;
    private static final int DETAILS_LABEL_WIDTH = "Where: ".length();

    private final ExecutionTabsPane pane;

    public PrDetailsView(PrDetailsController controller, PrTuiModel model) {
        this.pane = new ExecutionTabsPane(
            controller,
            model,
            (test, execution) -> widthAware(
                Constraint.length(DETAILS_HEADER_HEIGHT),
                width -> richText(detailsHeader(test, execution, width)).ellipsis()
            )
        );
    }

    public RichTextState detailsState() {
        return pane.detailsState();
    }

    public Element render() {
        return pane.render();
    }

    private static Text detailsHeader(ExecutedTest test, Execution execution, int width) {
        return Text.from(
            List.of(
                Line.from(
                    Span.raw("Test:  "),
                    Span.raw(abbreviatedTestName(test.getDisplayNames(), width - DETAILS_LABEL_WIDTH))
                ),
                Line.from(Span.raw("Where: "), Span.raw(test.getJob() + " > " + test.getWorkUnit())),
                Line.from(
                    Span.raw("Time:  "),
                    Span.raw(TimeFormat.formatTime(execution.getStartTime(), execution.getDurationMillis()))
                ),
                Line.from(
                    Span.raw("Job:   "),
                    Span.styled(execution.getHtmlUrl(), Style.EMPTY.underlined().hyperlink(execution.getHtmlUrl()))
                )
            )
        );
    }

    private static Element widthAware(Constraint constraint, IntFunction<Element> factory) {
        return new Element() {
            @Override
            public void render(Frame frame, Rect area, RenderContext context) {
                context.renderChild(factory.apply(area.width()), frame, area);
            }

            @Override
            public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
                return Size.UNKNOWN;
            }

            @Override
            public Constraint constraint() {
                return constraint;
            }
        };
    }
}
