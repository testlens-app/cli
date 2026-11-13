// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static app.testlens.cli.tui.view.PrTuiUtils.abbreviatedTestName;
import static app.testlens.cli.tui.view.PrTuiUtils.truncate;
import static dev.tamboui.toolkit.Toolkit.table;

import app.testlens.cli.client.model.FlakinessStatisticResponse;
import app.testlens.cli.client.model.FlakyCount;
import app.testlens.cli.tui.controller.FlakinessTableController;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.TableElement;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The ranked flakiness table: one row per statistic (test and work unit), ranked by flakiness, with the visible-row
 * scrollbar and memoized column widths. Extracted from the former FlakinessView, shared by the home screen.
 */
public final class FlakinessTableView {

    /** Visible rows of the statistics table, shared with the controller for page-wise selection. */
    public static final int MAX_VISIBLE_STATISTICS = 10;

    private static final String SEVERITY_HEADER = "Severity";
    private static final String FLAKY_HEADER = "Flaky";
    private static final String FAILED_HEADER = "Failed";
    private static final String TEST_HEADER = "Test";
    private static final String JOB_HEADER = "Job";
    private static final String WORK_UNIT_HEADER = "Project/Task";
    private static final int COLUMN_SPACING = 3;
    private static final int TABLE_DECORATION_WIDTH = 2 + 2 + 5 * COLUMN_SPACING;
    private static final int TABLE_CHROME_HEIGHT = 3;
    private static final int MAX_WORK_UNIT_WIDTH = 32;

    private final FlakinessTableController controller;
    private final TableElement table;
    // the statistics do not change while the screen is visible, so the layout is memoized by width only
    private int tableWidth = -1;

    public FlakinessTableView(FlakinessTableController controller) {
        this.controller = controller;
        this.table = table()
            .id(FlakinessTableController.VIEW_ID)
            .title("Flakiest tests")
            .rounded()
            .borderColor(Color.DARK_GRAY)
            .columnSpacing(COLUMN_SPACING)
            .header(
                Row
                    .from(
                        Cell.from(SEVERITY_HEADER),
                        Cell.from(FLAKY_HEADER),
                        Cell.from(FAILED_HEADER),
                        Cell.from(TEST_HEADER),
                        Cell.from(JOB_HEADER),
                        Cell.from(WORK_UNIT_HEADER)
                    )
            )
            .state(controller.tableState())
            .highlightStyle(Style.EMPTY.reversed())
            .focusable()
            .onKeyEvent(controller::handleKey);
    }

    /** Invalidates the memoized column widths; call after the statistics changed. */
    public void dataChanged() {
        tableWidth = -1;
    }

    /** The table element, sized to the visible rows plus chrome, with a scrollbar when rows overflow. */
    public Element element() {
        var constraint = Constraint.fill();
        return new Element() {
            @Override
            public void render(Frame frame, Rect area, RenderContext context) {
                fitColumns(area.width());
                table.highlightStyle(controller.isFocused() ? Style.EMPTY.reversed() : Style.EMPTY);
                context.renderChild(table, frame, area);
                var statistics = controller.statistics();
                var visibleRows = area.height() - TABLE_CHROME_HEIGHT;
                if (visibleRows > 0 && statistics.size() > visibleRows) {
                    var track = new Rect(area.right() - 1, area.top() + TABLE_CHROME_HEIGHT - 1, 1, visibleRows);
                    Scrollbar.vertical()
                        .render(
                            track,
                            frame.buffer(),
                            new ScrollbarState().contentLength(statistics.size())
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
        var statistics = controller.statistics();
        var severityWidth = maxColumnWidth(
            SEVERITY_HEADER,
            statistics,
            statistic -> severityLabel(statistic.getFlaky().getSeverity())
        );
        var flakyWidth = maxColumnWidth(FLAKY_HEADER, statistics, FlakinessTableView::flakyLabel);
        var failedWidth =
            maxColumnWidth(FAILED_HEADER, statistics, statistic -> failedLabel(statistic.getFailed().getCount()));
        var available = Math.max(0, width - TABLE_DECORATION_WIDTH - severityWidth - flakyWidth - failedWidth);
        // in narrow panes (the dashboard split) the de-emphasized job and work unit columns drop out — the jobs
        // pane next to the ranking covers jobs — and the test name abbreviates to its innermost segment
        var compact = available < 34;
        var jobWidth = compact
            ? 0
            : Math.min(
                maxColumnWidth(JOB_HEADER, statistics, FlakinessStatisticResponse::getJob),
                Math.min(MAX_WORK_UNIT_WIDTH, Math.max(1, available))
            );
        var workUnitWidth = compact
            ? 0
            : Math.min(
                maxColumnWidth(WORK_UNIT_HEADER, statistics, FlakinessStatisticResponse::getWorkUnit),
                Math.min(MAX_WORK_UNIT_WIDTH, Math.max(1, available - jobWidth))
            );
        var testWidth = Math.max(1, available - jobWidth - workUnitWidth);

        var finalWorkUnitWidth = workUnitWidth;
        table
            .rows(
                statistics.stream()
                    .map(statistic -> statisticRow(statistic, jobWidth, finalWorkUnitWidth, testWidth))
                    .toList()
            )
            .widths(
                Constraint.length(severityWidth),
                Constraint.length(flakyWidth),
                Constraint.length(failedWidth),
                Constraint.fill(),
                Constraint.length(jobWidth),
                Constraint.length(finalWorkUnitWidth)
            );
    }

    private static int maxColumnWidth(
        String header,
        List<FlakinessStatisticResponse> statistics,
        Function<FlakinessStatisticResponse, String> value
    ) {
        var contentWidth = statistics
            .stream()
            .mapToInt(statistic -> CharWidth.of(value.apply(statistic)))
            .max()
            .orElse(0);
        return Math.max(CharWidth.of(header), contentWidth);
    }

    private static Row statisticRow(
        FlakinessStatisticResponse statistic,
        int jobWidth,
        int workUnitWidth,
        int testWidth
    ) {
        var severity = statistic.getFlaky().getSeverity();
        var compact = jobWidth == 0 && workUnitWidth == 0;
        return Row.from(
            Cell.from(Span.styled(severityLabel(severity), Style.EMPTY.fg(severityColor(severity)))),
            Cell.from(flakyLabel(statistic)),
            Cell.from(failedLabel(statistic.getFailed().getCount())),
            Cell.from(testNameCell(statistic.getDisplayNames(), testWidth, compact)),
            // de-emphasized: job and work unit differentiate the rows of one test (e.g. a JDK matrix) but are the
            // least relevant part of the ranking scan
            Cell.from(
                Span.styled(
                    truncate(statistic.getJob(), jobWidth, CharWidth.TruncatePosition.MIDDLE),
                    Style.EMPTY.dim()
                )
            ),
            Cell.from(
                Span.styled(
                    truncate(statistic.getWorkUnit(), workUnitWidth, CharWidth.TruncatePosition.MIDDLE),
                    Style.EMPTY.dim()
                )
            )
        );
    }

    /**
     * The test cell of a ranking row. Wide layouts keep the class prefix and abbreviate the middle; narrow dashboard
     * panes truncate to the innermost segment, which is the identity worth scanning.
     */
    private static String testNameCell(List<String> displayNames, int width, boolean compact) {
        if (compact) {
            var name = String.join(" > ", displayNames.subList(Math.min(1, displayNames.size()), displayNames.size()));
            return truncate(name, width, CharWidth.TruncatePosition.START);
        }
        return abbreviatedTestName(displayNames, width);
    }

    private static String severityLabel(FlakyCount.@Nullable SeverityEnum severity) {
        return severity == null ? "" : severity.name();
    }

    private static Color severityColor(FlakyCount.@Nullable SeverityEnum severity) {
        if (severity == null) {
            return Color.DARK_GRAY;
        }
        // mirrors the colors of the Test Dashboard severity icons (FlakinessSeverity in the statistics model)
        return switch (severity) {
            case NONE -> Color.GREEN;
            case LOW -> Color.YELLOW;
            case MEDIUM -> Color.LIGHT_RED;
            case HIGH -> Color.RED;
        };
    }

    private static String flakyLabel(FlakinessStatisticResponse statistic) {
        return statistic.getFlaky().getCount() + " (" + statistic.getFlaky().getPercentage() + "%)";
    }

    private static String failedLabel(Long count) {
        return String.valueOf(count);
    }
}
