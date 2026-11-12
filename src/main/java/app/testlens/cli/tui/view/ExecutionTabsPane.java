// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static app.testlens.cli.tui.view.PrTuiUtils.outcomeColor;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richTextArea;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.tui.controller.PrDetailsController;
import app.testlens.cli.tui.model.DetailsModel;
import app.testlens.cli.tui.model.PrTuiModel;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.RichTextState;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.tabs.Tabs;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * The details pane shared by the PR and flakes screens: the executions of the selected test as tabs on the top border,
 * a view-specific header, and the scrollable diagnostics of the selected execution.
 */
final class ExecutionTabsPane {

    private final PrDetailsController controller;
    private final PrTuiModel model;
    private final BiFunction<ExecutedTest, Execution, Element> header;
    private final RichTextAreaElement detailsArea;
    private @Nullable DetailsModel lastDetails;

    /** The header factory receives the selected test and execution and returns an element with a fixed height. */
    ExecutionTabsPane(
        PrDetailsController controller,
        PrTuiModel model,
        BiFunction<ExecutedTest, Execution, Element> header
    ) {
        this.controller = controller;
        this.model = model;
        this.header = header;
        this.detailsArea = richTextArea()
            .id(PrDetailsController.VIEW_ID)
            .wrapCharacter()
            .scrollbar(ScrollBarPolicy.AS_NEEDED)
            .focusable()
            .onKeyEvent(controller::handleKey);
        controller.bindDetailsArea(detailsArea);
    }

    RichTextState detailsState() {
        return detailsArea.state();
    }

    Element render() {
        sync();
        var details = model.details();
        var test = details.test();
        if (test == null) {
            return panel(detailsArea.fill()).rounded().borderColor(Color.DARK_GRAY).fill();
        }
        var tabs = Tabs.builder()
            .titles(
                IntStream.range(0, test.getExecutions().size())
                    .mapToObj(i -> tabTitle(i, test.getExecutions().get(i)))
                    .toList()
            )
            .divider("─")
            .highlightStyle(Style.EMPTY.bold().reversed())
            .build();
        var state = controller.tabsState();
        var panel = panel(header.apply(test, details.execution()), detailsArea.fill())
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

    private static Line tabTitle(int index, Execution execution) {
        var outcome = execution.getOutcome();
        return Line.from(Span.styled(" #" + (index + 1) + " " + outcome + " ", Style.EMPTY.fg(outcomeColor(outcome))));
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
}
