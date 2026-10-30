// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.controller;

import static java.util.Objects.requireNonNull;

import app.testlens.cli.tui.model.ExecutionsModel;
import app.testlens.cli.tui.view.ExecutionsView;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.table.TableState;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Input handling for the test executions screen with its two panes: the executions table and the scrollable details
 * area. The model is the source of truth for the selection; the table state follows it.
 */
public final class ExecutionsController {

    public static final String EXECUTIONS_ID = "executions";
    public static final String DETAILS_ID = "execution-details";

    private static final List<String> PANE_IDS = List.of(EXECUTIONS_ID, DETAILS_ID);

    private final FocusManager focusManager;
    private final ExecutionsModel model;
    private final TableState tableState = new TableState();
    private @Nullable RichTextAreaElement detailsArea;

    public ExecutionsController(FocusManager focusManager, ExecutionsModel model) {
        this.focusManager = focusManager;
        this.model = model;
        tableState.select(model.snapshot().executionIndex());
    }

    public ExecutionsModel model() {
        return model;
    }

    public TableState tableState() {
        return tableState;
    }

    public void bindDetailsArea(RichTextAreaElement detailsArea) {
        if (this.detailsArea != null) {
            throw new IllegalStateException("detailsArea was already set");
        }
        this.detailsArea = detailsArea;
    }

    public boolean isFocused(String viewId) {
        return focusManager.isFocused(viewId);
    }

    public EventResult handleTableKey(KeyEvent event) {
        if (PaneSwitcher.switchFocus(focusManager, PANE_IDS, event).isHandled()) {
            return EventResult.HANDLED;
        }
        if (model.executions().isEmpty() || !isFocused(EXECUTIONS_ID)) {
            return EventResult.UNHANDLED;
        }
        var selected = model.snapshot().executionIndex();
        int target;
        if (event.isUp() || event.isChar('k')) {
            target = selected - 1;
        } else if (event.isDown() || event.isChar('j')) {
            target = selected + 1;
        } else if (event.isPageUp()) {
            target = selected - ExecutionsView.MAX_VISIBLE_EXECUTIONS;
        } else if (event.isPageDown()) {
            target = selected + ExecutionsView.MAX_VISIBLE_EXECUTIONS;
        } else if (event.isHome()) {
            target = 0;
        } else if (event.isEnd()) {
            target = model.executions().size() - 1;
        } else {
            return EventResult.UNHANDLED;
        }
        model.selectExecution(target);
        tableState.select(model.snapshot().executionIndex());
        return EventResult.HANDLED;
    }

    public EventResult handleDetailsKey(KeyEvent event) {
        if (PaneSwitcher.switchFocus(focusManager, PANE_IDS, event).isHandled()) {
            return EventResult.HANDLED;
        }
        // handled regardless of focus, so executions can be switched while scrolling
        if (event.isLeft() || event.isChar('h')) {
            model.selectPreviousExecution();
            tableState.select(model.snapshot().executionIndex());
            return EventResult.HANDLED;
        }
        if (event.isRight() || event.isChar('l')) {
            model.selectNextExecution();
            tableState.select(model.snapshot().executionIndex());
            return EventResult.HANDLED;
        }
        if (!isFocused(DETAILS_ID)) {
            return EventResult.UNHANDLED;
        }
        var textState = requireNonNull(detailsArea).state();
        if (event.isChar('j')) {
            textState.scrollDown();
        } else if (event.isChar('k')) {
            textState.scrollUp();
        } else {
            return EventResult.UNHANDLED;
        }
        return EventResult.HANDLED;
    }
}
