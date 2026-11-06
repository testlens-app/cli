// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static app.testlens.cli.tui.controller.FlakesController.VIEW_ID;
import static app.testlens.cli.tui.controller.FlakesController.abbreviatedSha;
import static app.testlens.cli.tui.view.PrTestsTableView.colorFor;
import static app.testlens.cli.tui.view.PrTestsTableView.outcomeLabel;
import static app.testlens.cli.tui.view.PrTuiUtils.truncate;
import static dev.tamboui.style.Color.YELLOW;
import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.table;
import static dev.tamboui.toolkit.Toolkit.text;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.FlakeResponse;
import app.testlens.cli.tui.ScreenRouter;
import app.testlens.cli.tui.controller.FlakesController;
import app.testlens.cli.tui.model.AggregatedOutcome;
import app.testlens.cli.tui.model.TestInfo;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * The flakes of a test: one table row per build in which the test flaked, and the executions of the selected flake in
 * chronological order as tabs of the details pane below, like in the PR TUI. Reached directly via
 * {@code flakes <id>} and as the drill-down of the ranked statistics list, which pushes it onto the
 * {@link ScreenRouter}.
 */
public final class FlakesView implements ScreenRouter.Screen {

    /** Visible rows of the flakes table, shared with the controller for page-wise selection. */
    public static final int MAX_VISIBLE_FLAKES = 10;

    private static final String OUTCOME = "Outcome";
    private static final String WORK_UNIT_LABEL = "Project/Task";
    private static final String BRANCH_LABEL = "Branch";
    private static final String SHA_LABEL = "sha";
    private static final String START_LABEL = "Start";
    private static final String NO_FLAKES_MESSAGE = "No flakes of this test in the last 30 days.";
    private static final int COLUMN_SPACING = 3;
    private static final int TABLE_DECORATION_WIDTH = 2 + 2 + 4 * COLUMN_SPACING;
    private static final int TABLE_CHROME_HEIGHT = 3;
    private static final int SHA_WIDTH = 7;
    private static final int MAX_COLUMN_WIDTH = 32;
    private static final int MIN_WORK_UNIT_WIDTH = 10;
    private static final int MIN_BRANCH_WIDTH = 8;
    private static final int START_WIDTH = "yyyy-MM-dd HH:mm".length();
    private static final DateTimeFormatter TABLE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FlakesController controller;
    private final String title;
    private final TableElement table;
    private final FlakeDetailsView details;
    private int tableWidth = -1;

    public FlakesView(FlakesController controller, TestInfo test, @Nullable String branch, @Nullable String workUnit) {
        this.controller = controller;
        this.title = test.displayName() + " · test " + test.id() + (branch == null ? "" : " · branch " + branch)
            + (workUnit == null ? "" : " · task/project " + workUnit);
        this.details = new FlakeDetailsView(controller.detailsController(), controller.model());
        this.table = table()
            .id(VIEW_ID)
            .title("Flakes")
            .rounded()
            .borderColor(Color.DARK_GRAY)
            .columnSpacing(COLUMN_SPACING)
            .header(
                Row.from(
                    Cell.from(OUTCOME),
                    Cell.from(WORK_UNIT_LABEL),
                    Cell.from(BRANCH_LABEL),
                    Cell.from(SHA_LABEL),
                    Cell.from(START_LABEL)
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
        table.borderColor(controller.isFocused() ? Color.CYAN : Color.DARK_GRAY);
        var flakes = controller.flakes();
        var rowCount = flakes.isEmpty() ? MAX_VISIBLE_FLAKES : flakes.size();
        var constraint = Constraint.length(Math.min(rowCount, MAX_VISIBLE_FLAKES) + TABLE_CHROME_HEIGHT);
        var content = column(
            text(title).bold().cyan().length(1),
            flakesTable(constraint),
            details.render(),
            text("↑/↓ or j/k select flake · ←/→ or h/l select execution · Tab switch pane · Esc/q back").dim()
                .length(1)
        );
        if (!flakes.isEmpty()) {
            return content;
        }
        return stack(
            content,
            dialog(text(NO_FLAKES_MESSAGE)).rounded().borderColor(YELLOW).width(NO_FLAKES_MESSAGE.length() + 4)
        );
    }

    private Element flakesTable(Constraint constraint) {
        return new Element() {
            @Override
            public void render(Frame frame, Rect area, RenderContext context) {
                fitColumns(area.width());
                context.renderChild(table, frame, area);
                var size = controller.flakes().size();
                var visibleRows = area.height() - TABLE_CHROME_HEIGHT;
                if (visibleRows > 0 && size > visibleRows) {
                    var track = new Rect(area.right() - 1, area.top() + TABLE_CHROME_HEIGHT - 1, 1, visibleRows);
                    Scrollbar.vertical()
                        .render(
                            track,
                            frame.buffer(),
                            new ScrollbarState().contentLength(size)
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

    // the flakes do not change while the screen is visible, so the layout is memoized by width only
    private void fitColumns(int width) {
        if (width == tableWidth) {
            return;
        }
        tableWidth = width;
        var outcomeWidth = Math.max(
            CharWidth.of(OUTCOME),
            controller.tests().stream().mapToInt(test -> CharWidth.of(outcomeLabel(outcome(test)))).max().orElse(0)
        );
        var available = Math.max(0, width - TABLE_DECORATION_WIDTH - outcomeWidth - SHA_WIDTH - START_WIDTH);
        var workUnitWidth = contentWidth(WORK_UNIT_LABEL, FlakeResponse::getWorkUnit);
        var branchWidth = contentWidth(BRANCH_LABEL, FlakeResponse::getBranch);
        var overflow = workUnitWidth + branchWidth - available;
        var branchShrink = Math.clamp(overflow, 0, Math.max(0, branchWidth - MIN_BRANCH_WIDTH));
        branchWidth -= branchShrink;
        workUnitWidth -= Math.clamp(overflow - branchShrink, 0, Math.max(0, workUnitWidth - MIN_WORK_UNIT_WIDTH));

        var finalWorkUnitWidth = workUnitWidth;
        var finalBranchWidth = branchWidth;
        table
            .rows(
                IntStream.range(0, controller.flakes().size())
                    .mapToObj(
                        i -> flakeRow(
                            controller.flakes().get(i),
                            controller.tests().get(i),
                            finalWorkUnitWidth,
                            finalBranchWidth
                        )
                    )
                    .toList()
            )
            .widths(
                Constraint.length(outcomeWidth),
                Constraint.length(finalWorkUnitWidth),
                Constraint.length(finalBranchWidth),
                Constraint.length(SHA_WIDTH),
                Constraint.length(START_WIDTH)
            );
    }

    private int contentWidth(String header, Function<FlakeResponse, String> value) {
        var contentWidth = controller.flakes()
            .stream()
            .mapToInt(flake -> CharWidth.of(value.apply(flake)))
            .max()
            .orElse(0);
        return Math.min(Math.max(CharWidth.of(header), contentWidth), MAX_COLUMN_WIDTH);
    }

    private static AggregatedOutcome outcome(ExecutedTest test) {
        return AggregatedOutcome.from(test.getExecutions());
    }

    private static Row flakeRow(FlakeResponse flake, ExecutedTest test, int workUnitWidth, int branchWidth) {
        var outcome = outcome(test);
        // executions are in chronological order, so the first one started the flake
        var start = test.getExecutions().getFirst().getStartTime();
        return Row.from(
            Cell.from(Span.styled(outcomeLabel(outcome), Style.EMPTY.fg(colorFor(outcome)))),
            Cell.from(truncate(flake.getWorkUnit(), workUnitWidth, CharWidth.TruncatePosition.MIDDLE)),
            Cell.from(truncate(flake.getBranch(), branchWidth, CharWidth.TruncatePosition.MIDDLE)),
            Cell.from(Span.styled(abbreviatedSha(flake.getSha()), Style.EMPTY.dim())),
            Cell.from(Span.styled(TABLE_TIME.format(start.atZoneSameInstant(ZoneOffset.UTC)), Style.EMPTY.dim()))
        );
    }
}
