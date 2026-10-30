// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.view;

import static app.testlens.cli.tui.controller.ExecutionsController.EXECUTIONS_ID;
import static app.testlens.cli.tui.view.PrTuiUtils.outcomeColor;
import static app.testlens.cli.tui.view.PrTuiUtils.truncate;
import static dev.tamboui.style.Color.YELLOW;
import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richText;
import static dev.tamboui.toolkit.Toolkit.richTextArea;
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.table;
import static dev.tamboui.toolkit.Toolkit.text;

import app.testlens.cli.client.model.TestExecutionResponse;
import app.testlens.cli.tui.ScreenRouter;
import app.testlens.cli.tui.controller.ExecutionsController;
import app.testlens.cli.tui.model.ExecutionsModel;
import app.testlens.cli.util.TimeFormat;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.toolkit.elements.TableElement;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;

/**
 * The detail view of a single test: its recent executions in a table and the selected execution with diagnostics in the
 * details pane below. Reached directly via {@code test <id>} and as the drill-down of the ranked statistics list, which
 * pushes it onto the {@link ScreenRouter}.
 */
public final class ExecutionsView implements ScreenRouter.Screen {

    /** Visible rows of the executions table, shared with the controller for page-wise selection. */
    public static final int MAX_VISIBLE_EXECUTIONS = 10;

    private static final String OUTCOME = "Outcome";
    private static final String WORK_UNIT_LABEL = "Project/Task";
    private static final String BRANCH_LABEL = "Branch";
    private static final String SHA_LABEL = "sha";
    private static final String START_LABEL = "Start";
    private static final String NO_EXECUTIONS_MESSAGE = "No executions of this test in the last 30 days.";
    private static final int COLUMN_SPACING = 3;
    private static final int TABLE_DECORATION_WIDTH = 2 + 2 + 4 * COLUMN_SPACING;
    private static final int TABLE_CHROME_HEIGHT = 3;
    private static final int DETAILS_LABEL_WIDTH = "Where: ".length();
    private static final int DETAILS_HEADER_HEIGHT = 5;
    private static final int SHA_WIDTH = 7;
    private static final int MAX_COLUMN_WIDTH = 32;
    private static final int MIN_WORK_UNIT_WIDTH = 10;
    private static final int MIN_BRANCH_WIDTH = 8;
    private static final int START_WIDTH = "yyyy-MM-dd HH:mm".length();
    private static final DateTimeFormatter TABLE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ExecutionsController controller;
    private final ExecutionsModel model;
    private final RichTextAreaElement detailsArea;
    private final TableElement table;
    private int tableWidth = -1;
    private ExecutionsModel.@Nullable Snapshot lastSnapshot;

    public ExecutionsView(ExecutionsController controller, ExecutionsModel model) {
        this.controller = controller;
        this.model = model;
        this.detailsArea = richTextArea()
            .id(ExecutionsController.DETAILS_ID)
            .wrapCharacter()
            .scrollbar(ScrollBarPolicy.AS_NEEDED)
            .focusable()
            .onKeyEvent(controller::handleDetailsKey);
        controller.bindDetailsArea(detailsArea);
        this.table = executionsTable();
    }

    @Override
    public String focusId() {
        return EXECUTIONS_ID;
    }

    @Override
    public Element render() {
        sync();
        var executions = model.executions();
        var rowCount = executions.isEmpty() ? MAX_VISIBLE_EXECUTIONS : executions.size();
        var constraint = Constraint.length(Math.min(rowCount, MAX_VISIBLE_EXECUTIONS) + TABLE_CHROME_HEIGHT);
        var content = column(header(), executionTable(constraint), details(), footer());
        if (!executions.isEmpty()) {
            return content;
        }
        return stack(
            content,
            dialog(text(NO_EXECUTIONS_MESSAGE)).rounded().borderColor(YELLOW).width(NO_EXECUTIONS_MESSAGE.length() + 4)
        );
    }

    private Element header() {
        return text(title()).bold().cyan().length(1);
    }

    private String title() {
        return model.test().displayName() + " · test " + model.test().id();
    }

    private Element footer() {
        return text(
            "↑/↓ or j/k select execution/scroll · ←/→ or h/l select execution · Tab switch pane · Esc/q back"
        )
            .dim()
            .length(1);
    }

    private TableElement executionsTable() {
        return table()
            .id(EXECUTIONS_ID)
            .title("Executions")
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
            .onKeyEvent(controller::handleTableKey);
    }

    private Element executionTable(Constraint constraint) {
        return new Element() {
            @Override
            public void render(Frame frame, Rect area, RenderContext context) {
                fitColumns(area.width());
                context.renderChild(table, frame, area);
                var executions = model.executions();
                var visibleRows = area.height() - TABLE_CHROME_HEIGHT;
                if (visibleRows > 0 && executions.size() > visibleRows) {
                    var track = new Rect(area.right() - 1, area.top() + TABLE_CHROME_HEIGHT - 1, 1, visibleRows);
                    Scrollbar.vertical()
                        .render(
                            track,
                            frame.buffer(),
                            new ScrollbarState().contentLength(executions.size())
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

    // the executions do not change while the screen is visible, so the column layout is memoized by width only
    private void fitColumns(int width) {
        if (width == tableWidth) {
            return;
        }
        tableWidth = width;
        var executions = model.executions();
        var outcomeWidth = outcomeWidth(executions);
        var available = Math.max(0, width - TABLE_DECORATION_WIDTH - outcomeWidth - SHA_WIDTH - START_WIDTH);
        var workUnitWidth = contentWidth(WORK_UNIT_LABEL, executions, TestExecutionResponse::getWorkUnit);
        var branchWidth = contentWidth(BRANCH_LABEL, executions, TestExecutionResponse::getBranch);
        var overflow = workUnitWidth + branchWidth - available;
        var branchShrink = Math.clamp(overflow, 0, Math.max(0, branchWidth - MIN_BRANCH_WIDTH));
        branchWidth -= branchShrink;
        workUnitWidth -= Math.clamp(overflow - branchShrink, 0, Math.max(0, workUnitWidth - MIN_WORK_UNIT_WIDTH));

        var finalWorkUnitWidth = workUnitWidth;
        var finalBranchWidth = branchWidth;
        table
            .rows(
                executions
                    .stream()
                    .map(execution -> executionRow(execution, finalWorkUnitWidth, finalBranchWidth))
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

    private static int outcomeWidth(List<TestExecutionResponse> executions) {
        var contentWidth = executions
            .stream()
            .mapToInt(execution -> CharWidth.of(execution.getOutcome().name()))
            .max()
            .orElse(0);
        return Math.max(CharWidth.of(OUTCOME), contentWidth);
    }

    private static int contentWidth(
        String header,
        List<TestExecutionResponse> executions,
        Function<TestExecutionResponse, String> value
    ) {
        var contentWidth = executions
            .stream()
            .mapToInt(execution -> CharWidth.of(value.apply(execution)))
            .max()
            .orElse(0);
        // at least as wide as the header, so the header is not truncated, capped to keep the test column usable
        return Math.min(Math.max(CharWidth.of(header), contentWidth), MAX_COLUMN_WIDTH);
    }

    private static Row executionRow(TestExecutionResponse execution, int workUnitWidth, int branchWidth) {
        var sha = execution.getSha();
        return Row.from(
            Cell.from(Span.styled(execution.getOutcome().name(), Style.EMPTY.fg(outcomeColor(execution.getOutcome())))),
            Cell.from(truncate(execution.getWorkUnit(), workUnitWidth, CharWidth.TruncatePosition.MIDDLE)),
            Cell.from(truncate(execution.getBranch(), branchWidth, CharWidth.TruncatePosition.MIDDLE)),
            Cell.from(Span.styled(abbreviatedSha(sha), Style.EMPTY.dim())),
            Cell.from(
                Span.styled(
                    TABLE_TIME.format(execution.getStartTime().atZoneSameInstant(ZoneOffset.UTC)),
                    Style.EMPTY.dim()
                )
            )
        );
    }

    private static String abbreviatedSha(String sha) {
        // fixed-width cell without an ellipsis: the sha column holds exactly SHA_WIDTH characters
        return sha.length() > SHA_WIDTH ? sha.substring(0, SHA_WIDTH) : sha;
    }

    // pushes model changes into the persistent details widget; memoized by value, so it is a no-op without changes
    private void sync() {
        var snapshot = model.snapshot();
        if (snapshot.equals(lastSnapshot)) {
            return;
        }
        lastSnapshot = snapshot;
        if (!snapshot.executions().isEmpty()) {
            var execution = snapshot.executions().get(snapshot.executionIndex());
            detailsArea
                .text(ExecutionContent.of(execution.getComparisonFailure(), execution.getStackTrace()))
                .scrollToLine(0);
        }
    }

    private Element details() {
        return panel(
            widthAware(
                Constraint.length(DETAILS_HEADER_HEIGHT),
                width -> richText(detailsHeader(width)).ellipsis()
            ),
            detailsArea.fill()
        )
            .rounded()
            .borderColor(controller.isFocused(ExecutionsController.DETAILS_ID) ? Color.CYAN : Color.DARK_GRAY)
            .fill();
    }

    private Text detailsHeader(int width) {
        if (model.executions().isEmpty()) {
            return Text.from(List.of(Line.from("")));
        }
        var snapshot = model.snapshot();
        var execution = snapshot.executions().get(snapshot.executionIndex());
        var valueWidth = Math.max(width - DETAILS_LABEL_WIDTH, 0);
        var lines = new ArrayList<Line>();
        lines.add(
            Line.from(
                Span.raw("Exec:  "),
                Span.raw(
                    "#" + (snapshot.executionIndex() + 1) + "/" + snapshot.executions().size()
                        + ", " + TimeFormat.formatTime(execution.getStartTime(), execution.getDurationMillis())
                )
            )
        );
        lines.add(valueLine("Where: ", execution.getWorkUnit(), valueWidth));
        lines.add(valueLine("Ref:   ", execution.getBranch(), valueWidth));
        lines.add(valueLine("Sha:   ", execution.getSha(), valueWidth));
        var htmlUrl = execution.getHtmlUrl();
        if (htmlUrl != null) {
            lines.add(
                Line.from(
                    Span.raw("Job:   "),
                    Span.styled(
                        htmlUrl.length() <= valueWidth
                            ? htmlUrl
                            : truncate(htmlUrl, valueWidth, CharWidth.TruncatePosition.MIDDLE),
                        Style.EMPTY.underlined().hyperlink(htmlUrl)
                    )
                )
            );
        }
        return Text.from(lines);
    }

    private static Line valueLine(String label, String value, int width) {
        return Line.from(Span.raw(label), Span.raw(truncate(value, width, CharWidth.TruncatePosition.END)));
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
