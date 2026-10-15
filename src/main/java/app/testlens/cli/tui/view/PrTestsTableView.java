// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static app.testlens.cli.tui.view.PrTuiUtils.abbreviatedTestName;
import static app.testlens.cli.tui.view.PrTuiUtils.outcomeColor;
import static app.testlens.cli.tui.view.PrTuiUtils.truncate;
import static dev.tamboui.toolkit.Toolkit.table;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.tui.controller.PrTestsTableController;
import app.testlens.cli.tui.model.AggregatedOutcome;
import app.testlens.cli.tui.model.FuzzySearch;
import app.testlens.cli.tui.model.TestListModel;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.CharWidth.TruncatePosition;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.TableElement;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class PrTestsTableView {

    public static final int MAX_VISIBLE_TESTS = 10;
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
    private static final Style MATCH_HIGHLIGHT_STYLE = Style.EMPTY.bold().underlined();

    private final PrTestsTableController controller;
    private final TestListModel model;
    private final TableElement table;
    private int tableWidth = -1;
    private @Nullable TableContent lastContent;

    private record TableContent(List<ExecutedTest> tests, String query) {}

    public PrTestsTableView(PrTestsTableController controller) {
        this.controller = controller;
        this.model = controller.model();
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

    public Element render() {
        table.borderColor(controller.isFocused() ? Color.CYAN : Color.DARK_GRAY);
        var tests = model.tests();
        // reserve space for a full table even when empty, so the popup has a natural backdrop
        var rowCount = tests.isEmpty() ? MAX_VISIBLE_TESTS : tests.size();
        return testTableWithScrollbar(Constraint.length(Math.min(rowCount, MAX_VISIBLE_TESTS) + TABLE_CHROME_HEIGHT));
    }

    private Element testTableWithScrollbar(Constraint constraint) {
        return new Element() {
            @Override
            public void render(Frame frame, Rect area, RenderContext context) {
                fitColumns(area.width());
                context.renderChild(table, frame, area);
                var tests = model.tests();
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
        // memoized by value, so rows are only rebuilt when the model state actually changed
        var content = new TableContent(model.tests(), model.searchQuery());
        if (width == tableWidth && content.equals(lastContent)) {
            return;
        }
        tableWidth = width;
        lastContent = content;
        var outcomeWidth = columnWidth(OUTCOME, test -> outcomeLabel(AggregatedOutcome.from(test.getExecutions())));
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
        var query = content.query();
        table
            .rows(
                content.tests()
                    .stream()
                    .map(test -> testRow(test, finalJobWidth, finalWorkUnitWidth, testWidth, query))
                    .toList()
            )
            .widths(
                Constraint.length(outcomeWidth),
                Constraint.length(jobWidth),
                Constraint.length(workUnitWidth),
                Constraint.fill()
            );
    }

    private int columnWidth(String header, Function<ExecutedTest, String> value) {
        var contentWidth = model.tests().stream().mapToInt(test -> CharWidth.of(value.apply(test))).max().orElse(0);
        return Math.clamp(CharWidth.of(header), contentWidth, MAX_COLUMN_WIDTH);
    }

    private static Row testRow(ExecutedTest test, int jobWidth, int workUnitWidth, int testWidth, String query) {
        var outcome = AggregatedOutcome.from(test.getExecutions());
        return Row.from(
            Cell.from(Span.styled(outcomeLabel(outcome), Style.EMPTY.fg(colorFor(outcome)))),
            Cell.from(truncate(test.getJob(), jobWidth, TruncatePosition.MIDDLE)),
            Cell.from(truncate(test.getWorkUnit(), workUnitWidth, TruncatePosition.MIDDLE)),
            highlighted(abbreviatedTestName(test.getDisplayNames(), testWidth), query)
        );
    }

    // presentation of the aggregated outcome: the model only knows the domain state
    private static Color colorFor(AggregatedOutcome outcome) {
        return outcome.flaky() ? Color.YELLOW : outcomeColor(outcome.max());
    }

    private static String outcomeLabel(AggregatedOutcome outcome) {
        return outcome.flaky() ? "FLAKY" : outcome.max().name();
    }

    // highlights the characters matched by the current search term, so it is apparent why a test is listed
    private static Cell highlighted(String name, String query) {
        var positions = query.isEmpty() ? null : FuzzySearch.matchPositions(query, name);
        if (positions == null) {
            return Cell.from(name);
        }
        var spans = new ArrayList<Span>();
        int start = 0;
        for (var position : positions) {
            if (position > start) {
                spans.add(Span.raw(name.substring(start, position)));
            }
            spans.add(Span.styled(name.substring(position, position + 1), MATCH_HIGHLIGHT_STYLE));
            start = position + 1;
        }
        if (start < name.length()) {
            spans.add(Span.raw(name.substring(start)));
        }
        return Cell.from(Line.from(spans));
    }
}
