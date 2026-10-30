// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static app.testlens.cli.tui.controller.FlakinessTableController.VIEW_ID;
import static app.testlens.cli.tui.view.PrTuiUtils.abbreviatedTestName;
import static app.testlens.cli.tui.view.PrTuiUtils.truncate;
import static dev.tamboui.style.Color.RED;
import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.table;
import static dev.tamboui.toolkit.Toolkit.text;

import app.testlens.cli.client.model.FlakinessStatisticResponse;
import app.testlens.cli.client.model.FlakyCount;
import app.testlens.cli.tui.ScreenRouter;
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
 * The ranked flakiness list: one turn table row per statistic (test and work unit), ranked by flakiness. Selecting a
 * row and pressing Enter drills down into the executions screen of the entry's test, pushed onto the
 * {@link ScreenRouter}.
 */
public final class FlakinessView implements ScreenRouter.Screen {

    /** Visible rows of the statistics table, shared with the controller for page-wise selection. */
    public static final int MAX_VISIBLE_STATISTICS = 10;

    private static final String SEVERITY_HEADER = "Severity";
    private static final String WORK_UNIT_HEADER = "Project/Task";
    private static final String FLAKY_HEADER = "Flaky";
    private static final String FAILED_HEADER = "Failed";
    private static final int COLUMN_SPACING = 3;
    private static final int TABLE_DECORATION_WIDTH = 2 + 2 + 4 * COLUMN_SPACING;
    private static final int TABLE_CHROME_HEIGHT = 3;

    private final FlakinessTableController controller;
    private final @Nullable String branch;
    private final TableElement table;
    private int tableWidth = -1;

    public FlakinessView(FlakinessTableController controller, @Nullable String branch) {
        this.controller = controller;
        this.branch = branch;
        this.table = table()
            .id(VIEW_ID)
            .title("Flakiest tests")
            .rounded()
            .borderColor(Color.DARK_GRAY)
            .columnSpacing(COLUMN_SPACING)
            .header(
                Row
                    .from(
                        Cell.from(SEVERITY_HEADER),
                        Cell.from(WORK_UNIT_HEADER),
                        Cell.from(FLAKY_HEADER),
                        Cell.from(FAILED_HEADER),
                        Cell.from("Test")
                    )
            )
            .state(controller.tableState())
            .highlightStyle(Style.EMPTY.reversed())
            .focusable()
            .onKeyEvent(controller::handleKey);
    }

    @Override
    public String focusId() {
        return VIEW_ID;
    }

    @Override
    public Element render() {
        var statistics = controller.statistics();
        var rowCount = Math.min(statistics.size(), MAX_VISIBLE_STATISTICS) + TABLE_CHROME_HEIGHT;
        var content = column(header(), statisticsTable(Constraint.length(Math.max(rowCount, 1))), footer());
        var error = controller.error();
        if (error == null) {
            return content;
        }
        return stack(content, dialog(text(error)).rounded().borderColor(RED).width(error.length() + 4));
    }

    private Element header() {
        var title = branch == null ? "Flakiness ranking" : "Flakiness ranking · branch " + branch;
        return text(title).bold().cyan().length(1);
    }

    private Element footer() {
        return text("↑/↓ select · Enter view executions · Esc/q quit").dim().length(1);
    }

    private Element statisticsTable(Constraint constraint) {
        return new Element() {
            @Override
            public void render(Frame frame, Rect area, RenderContext context) {
                fitColumns(area.width());
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

    // the statistics do not change while the screen is visible, so the layout is memoized by width only
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
        var flakyWidth = maxColumnWidth(FLAKY_HEADER, statistics, FlakinessView::flakyLabel);
        var failedWidth =
            maxColumnWidth(FAILED_HEADER, statistics, statistic -> failedLabel(statistic.getFailed().getCount()));
        var available = Math.max(0, width - TABLE_DECORATION_WIDTH - severityWidth - flakyWidth - failedWidth);
        var workUnitWidth = Math
            .min(maxColumnWidth(WORK_UNIT_HEADER, statistics, FlakinessStatisticResponse::getWorkUnit), available);
        var testWidth = Math.max(1, available - workUnitWidth);

        var finalWorkUnitWidth = workUnitWidth;
        table
            .rows(
                statistics.stream()
                    .map(statistic -> statisticRow(statistic, finalWorkUnitWidth, testWidth))
                    .toList()
            )
            .widths(
                Constraint.length(severityWidth),
                Constraint.length(finalWorkUnitWidth),
                Constraint.length(flakyWidth),
                Constraint.length(failedWidth),
                Constraint.fill()
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
        int workUnitWidth,
        int testWidth
    ) {
        var severity = statistic.getFlaky().getSeverity();
        return Row.from(
            Cell.from(Span.styled(severityLabel(severity), Style.EMPTY.fg(severityColor(severity)))),
            Cell.from(truncate(statistic.getWorkUnit(), workUnitWidth, CharWidth.TruncatePosition.MIDDLE)),
            Cell.from(flakyLabel(statistic)),
            Cell.from(failedLabel(statistic.getFailed().getCount())),
            Cell.from(abbreviatedTestName(statistic.getDisplayNames(), testWidth))
        );
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
