package app.testlens.cli.tui;

import static app.testlens.cli.tui.PrTuiUtils.abbreviatedTestName;
import static app.testlens.cli.tui.PrTuiUtils.outcomeColor;
import static app.testlens.cli.tui.PrTuiUtils.truncate;
import static dev.tamboui.toolkit.Toolkit.table;
import static java.util.stream.Collectors.toSet;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.client.model.Execution.OutcomeEnum;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.CharWidth.TruncatePosition;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.TableElement;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

final class PrTestsTableView {

    static final int MAX_VISIBLE_TESTS = 10;
    private static final String OUTCOME = "Outcome";
    private static final String JOB = "Job";
    private static final String WORK_UNIT_LABEL = "Project/Task";
    private static final int MAX_COLUMN_WIDTH = 40;
    private static final int MIN_JOB_WIDTH = 16;
    private static final int MIN_WORK_UNIT_WIDTH = 10;
    private static final int MIN_TEST_WIDTH = 30;
    private static final int COLUMN_SPACING = 3;
    private static final int TABLE_DECORATION_WIDTH = 2 + 2 + 3 * COLUMN_SPACING;
    private static final int TABLE_CHROME_HEIGHT = 3;

    private final PrTestsTableController controller;
    private final TableElement table;
    private int tableWidth = -1;

    PrTestsTableView(PrTestsTableController controller) {
        this.controller = controller;
        table = table()
            .id(PrTestsTableController.VIEW_ID)
            .title("Tests")
            .rounded()
            .borderColor(Color.DARK_GRAY)
            .columnSpacing(COLUMN_SPACING)
            .header(Row.from(Cell.from(OUTCOME), Cell.from(JOB), Cell.from(WORK_UNIT_LABEL), Cell.from("Test")))
            .state(controller.tableState())
            .highlightStyle(Style.EMPTY.reversed())
            .focusable()
            .onKeyEvent(controller::handleKey);
    }

    Element render() {
        table.borderColor(controller.isFocused() ? Color.CYAN : Color.DARK_GRAY);
        var tests = controller.tests();
        return testTableWithScrollbar(
            Constraint.length(Math.min(tests.size(), MAX_VISIBLE_TESTS) + TABLE_CHROME_HEIGHT)
        );
    }

    private Element testTableWithScrollbar(Constraint constraint) {
        return new Element() {
            @Override
            public void render(Frame frame, Rect area, RenderContext context) {
                fitColumns(area.width());
                context.renderChild(table, frame, area);
                var tests = controller.tests();
                var visibleRows = area.height() - TABLE_CHROME_HEIGHT;
                if (tests.size() > visibleRows) {
                    var track = new Rect(area.right() - 1, area.top() + TABLE_CHROME_HEIGHT - 1, 1, visibleRows);
                    Scrollbar.vertical()
                        .render(
                            track,
                            frame.buffer(),
                            new ScrollbarState().contentLength(tests.size())
                                .viewportContentLength(visibleRows)
                                .position(controller.tableState().offset())
                        );
                }
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

    private void fitColumns(int width) {
        if (width == tableWidth) {
            return;
        }
        tableWidth = width;
        var tests = controller.tests();
        var outcomeWidth = columnWidth(OUTCOME, test -> AggregatedOutcome.from(test.getExecutions()).label());
        var available = width - TABLE_DECORATION_WIDTH - outcomeWidth;
        var jobWidth = columnWidth(JOB, ExecutedTest::getJob);
        var workUnitWidth = columnWidth(WORK_UNIT_LABEL, ExecutedTest::getWorkUnit);
        // when the test column would get too narrow, shrink the work unit first, then the job
        var overflow = jobWidth + workUnitWidth + MIN_TEST_WIDTH - available;
        var workUnitShrink = Math.clamp(overflow, 0, Math.max(0, workUnitWidth - MIN_WORK_UNIT_WIDTH));
        workUnitWidth -= workUnitShrink;
        jobWidth -= Math.clamp(overflow - workUnitShrink, 0, Math.max(0, jobWidth - MIN_JOB_WIDTH));
        var testWidth = Math.max(1, available - jobWidth - workUnitWidth);

        var finalJobWidth = jobWidth;
        var finalWorkUnitWidth = workUnitWidth;
        table.rows(tests.stream().map(test -> testRow(test, finalJobWidth, finalWorkUnitWidth, testWidth)).toList())
            .widths(
                Constraint.length(outcomeWidth),
                Constraint.length(jobWidth),
                Constraint.length(workUnitWidth),
                Constraint.fill()
            );
    }

    private int columnWidth(String header, Function<ExecutedTest, String> value) {
        var contentWidth =
            controller.tests().stream().mapToInt(test -> CharWidth.of(value.apply(test))).max().orElse(0);
        return Math.clamp(CharWidth.of(header), contentWidth, MAX_COLUMN_WIDTH);
    }

    private static Row testRow(ExecutedTest test, int jobWidth, int workUnitWidth, int testWidth) {
        var aggregatedOutcome = AggregatedOutcome.from(test.getExecutions());
        return Row.from(
            Cell.from(Span.styled(aggregatedOutcome.label(), Style.EMPTY.fg(aggregatedOutcome.color()))),
            Cell.from(truncate(test.getJob(), jobWidth, TruncatePosition.MIDDLE)),
            Cell.from(truncate(test.getWorkUnit(), workUnitWidth, TruncatePosition.MIDDLE)),
            Cell.from(abbreviatedTestName(test.getDisplayNames(), testWidth))
        );
    }

    private record AggregatedOutcome(OutcomeEnum max, boolean flaky) {

        static AggregatedOutcome from(List<Execution> executions) {
            var outcomes = executions.stream().map(Execution::getOutcome).collect(toSet());
            var max = Collections.max(outcomes);
            var flaky = max == OutcomeEnum.FAILED && outcomes.contains(OutcomeEnum.SUCCESSFUL);
            return new AggregatedOutcome(max, flaky);
        }

        Color color() {
            return flaky ? Color.YELLOW : outcomeColor(max);
        }

        String label() {
            return flaky ? "FLAKY" : max.name();
        }
    }
}
