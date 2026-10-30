// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static app.testlens.cli.tui.view.PrTuiUtils.abbreviatedTestName;
import static app.testlens.cli.tui.view.PrTuiUtils.outcomeColor;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richText;
import static dev.tamboui.toolkit.Toolkit.richTextArea;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.tui.controller.PrDetailsController;
import app.testlens.cli.tui.model.DetailsModel;
import app.testlens.cli.tui.model.PrTuiModel;
import app.testlens.cli.util.TimeFormat;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.RichTextState;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.tabs.Tabs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;

public final class PrDetailsView {

    private static final int DETAILS_HEADER_HEIGHT = 5;
    private static final int DETAILS_LABEL_WIDTH = "Where: ".length();

    private final PrDetailsController controller;
    private final PrTuiModel model;
    private final RichTextAreaElement detailsArea;
    private @Nullable DetailsModel lastDetails;

    public PrDetailsView(PrDetailsController controller, PrTuiModel model) {
        this.controller = controller;
        this.model = model;
        this.detailsArea = richTextArea()
            .id(PrDetailsController.VIEW_ID)
            .wrapCharacter()
            .scrollbar(ScrollBarPolicy.AS_NEEDED)
            .focusable()
            .onKeyEvent(controller::handleKey);
        controller.bindDetailsArea(detailsArea);
    }

    public RichTextState detailsState() {
        return detailsArea.state();
    }

    public Element render() {
        sync();
        var details = model.details();
        var test = details.test();
        if (test == null) {
            return panel(detailsArea.fill()).rounded().borderColor(Color.DARK_GRAY).fill();
        }
        var executionIndex = details.executionIndex();
        var executions = test.getExecutions();

        var titles = new ArrayList<Line>();
        for (int i = 0; i < executions.size(); i++) {

            var outcome = executions.get(i).getOutcome();
            titles.add(
                Line.from(Span.styled(" #" + (i + 1) + " " + outcome + " ", Style.EMPTY.fg(outcomeColor(outcome))))
            );
        }
        var tabs = Tabs.builder()
            .titles(titles)
            .divider("─")
            .highlightStyle(Style.EMPTY.bold().reversed())
            .build();
        var state = controller.tabsState();
        var panel = panel(
            widthAware(
                Constraint.length(DETAILS_HEADER_HEIGHT),
                width -> richText(detailsHeader(test, executions.get(executionIndex), width)).ellipsis()
            ),
            detailsArea.fill()
        )
            .rounded()
            .borderColor(controller.isFocused() ? Color.CYAN : Color.DARK_GRAY)
            .fill();
        return new Element() {
            @Override
            public void render(Frame frame, Rect area, RenderContext context) {
                context.renderChild(panel, frame, area);
                if (area.width() > 2 && area.height() > 0) {
                    frame.renderStatefulWidget(tabs, new Rect(area.x() + 1, area.y(), area.width() - 2, 1), state);
                }
            }

            @Override
            public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
                return panel.preferredSize(availableWidth, availableHeight, context);
            }

            @Override
            public Constraint constraint() {
                return panel.constraint();
            }
        };
    }

    // pushes model changes into the persistent details widget; memoized by value, so it is a no-op without changes
    private void sync() {
        var current = model.details();
        if (current.equals(lastDetails)) {
            return;
        }
        lastDetails = current;
        if (current.test() != null) {
            detailsArea
                .text(
                    ExecutionContent.of(current.execution().getComparisonFailure(), current.execution().getStackTrace())
                )
                .scrollToLine(0);
        }
    }

    private Text detailsHeader(ExecutedTest test, Execution execution, int width) {
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
