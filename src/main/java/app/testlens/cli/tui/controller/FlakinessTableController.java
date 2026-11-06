// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.controller;

import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.model.FlakinessStatisticResponse;
import app.testlens.cli.tui.view.FlakinessView;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.table.TableState;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Input handling for the ranked flakiness statistics table; the drill-down into a test's executions is delegated to the
 * callback registered via {@link #onOpenTest(Consumer)}, which fetches the executions and pushes the executions screen.
 */
public final class FlakinessTableController {

    public static final String VIEW_ID = "flakiness";

    private final FocusManager focusManager;
    private final List<FlakinessStatisticResponse> statistics;
    private final TableState tableState = new TableState();
    private Consumer<FlakinessStatisticResponse> onOpenTest = statistic -> {};
    private @Nullable String error;

    public FlakinessTableController(
        FocusManager focusManager,
        List<FlakinessStatisticResponse> statistics
    ) {
        this.focusManager = focusManager;
        this.statistics = List.copyOf(statistics);
        if (!statistics.isEmpty()) {
            tableState.select(0);
        }
    }

    /** Registers the callback invoked when the user opens the executions of the selected statistic. */
    public void onOpenTest(Consumer<FlakinessStatisticResponse> onOpenTest) {
        this.onOpenTest = requireNonNull(onOpenTest);
    }

    public List<FlakinessStatisticResponse> statistics() {
        return statistics;
    }

    public TableState tableState() {
        return tableState;
    }

    public boolean isFocused() {
        return focusManager.isFocused(VIEW_ID);
    }

    public void openFailed(String message) {
        error = message;
    }

    public @Nullable String error() {
        return error;
    }

    private void clearError() {
        error = null;
    }

    public EventResult handleKey(KeyEvent event) {
        clearError();
        if (statistics.isEmpty() || !isFocused()) {
            return EventResult.UNHANDLED;
        }
        if (event.isSelect()) {
            onOpenTest.accept(statistics.get(tableState.selected()));
            return EventResult.HANDLED;
        }
        var selected = tableState.selected();
        int target;
        if (event.isUp() || event.isChar('k')) {
            target = selected - 1;
        } else if (event.isDown() || event.isChar('j')) {
            target = selected + 1;
        } else if (event.isPageUp()) {
            target = selected - FlakinessView.MAX_VISIBLE_STATISTICS;
        } else if (event.isPageDown()) {
            target = selected + FlakinessView.MAX_VISIBLE_STATISTICS;
        } else if (event.isHome()) {
            target = 0;
        } else if (event.isEnd()) {
            target = statistics.size() - 1;
        } else {
            return EventResult.UNHANDLED;
        }
        tableState.select(Math.clamp(target, 0, statistics.size() - 1));
        return EventResult.HANDLED;
    }
}
