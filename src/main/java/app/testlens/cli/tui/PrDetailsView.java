package app.testlens.cli.tui;

import static app.testlens.cli.tui.PrTuiUtils.abbreviatedTestName;
import static app.testlens.cli.tui.PrTuiUtils.outcomeColor;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richText;
import static dev.tamboui.toolkit.Toolkit.richTextArea;

import app.testlens.cli.PrCommand;
import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.util.TextDiff;
import app.testlens.cli.util.TimeFormat;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
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

final class PrDetailsView {

    private static final int DETAILS_HEADER_HEIGHT = 5;
    private static final int DETAILS_LABEL_WIDTH = "Where: ".length();

    private final PrDetailsController controller;
    private final RichTextAreaElement detailsArea;

    PrDetailsView(PrDetailsController controller) {
        this.controller = controller;
        this.detailsArea = richTextArea()
            .id(PrDetailsController.VIEW_ID)
            .wrapCharacter()
            .scrollbar(ScrollBarPolicy.AS_NEEDED)
            .focusable()
            .onKeyEvent(controller::handleKey);
        controller.onSelectedExecution(
            execution -> detailsArea.text(PrDetailsView.detailsContent(execution)).scrollToLine(0)
        );
    }

    Element render() {
        var test = controller.selectedTest();
        var executionIndex = controller.selectedExecutionIndex();
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

    private Text detailsHeader(ExecutedTest test, Execution execution, int width) {
        return Text.from(
            List.of(
                Line.from(
                    Span.raw("Test:  "),
                    Span.raw(abbreviatedTestName(test.getDisplayNames(), width - DETAILS_LABEL_WIDTH))
                ),
                Line.from(Span.raw("Where: "), Span.raw(test.getJob() + " > " + test.getWorkUnit())),
                Line.from(Span.raw("Time:  "), Span.raw(TimeFormat.formatTime(execution))),
                Line.from(
                    Span.raw("Job:   "),
                    Span.styled(execution.getHtmlUrl(), Style.EMPTY.underlined().hyperlink(execution.getHtmlUrl()))
                )
            )
        );
    }

    static Text detailsContent(Execution execution) {
        var lines = new ArrayList<Line>();
        TextDiff.diff(execution.getComparisonFailure()).ifPresent(diff -> {
            lines.add(heading("Expected vs. actual"));
            diff.forEach(line -> lines.add(diffLine(line)));
            lines.add(Line.from(""));
            lines.add(heading("Stack trace"));
        });
        lines.addAll(stackTrace(execution).lines());
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

    private static Text stackTrace(Execution execution) {
        var stackTrace = execution.getStackTrace();
        if (stackTrace == null) {
            return Text.styled("No stack trace", Style.EMPTY.dim());
        }
        return Text.from(
            stackTrace.replace("\t", "    ")
                .lines()
                .map(
                    line -> PrCommand.isStackFrame(line)
                        ? Line.styled(line, Style.EMPTY.dim())
                        : Line.from(line)
                )
                .toList()
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
