// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.controller;

import static java.util.Objects.requireNonNull;

import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.table.TableState;
import java.util.List;
import java.util.function.Consumer;

/**
 * Input handling for one list pane of the home dashboard: a selectable list with an enter action. The pane's table is
 * built by the view; this controller owns the selection state and the keys.
 */
public final class ListPaneController<T> {

    private final FocusManager focusManager;
    private final String viewId;
    private final int pageStep;
    private List<T> items = List.of();
    private final TableState tableState = new TableState();
    private Consumer<T> onEnter = item -> {};

    public ListPaneController(FocusManager focusManager, String viewId, int pageStep) {
        this.focusManager = focusManager;
        this.viewId = viewId;
        this.pageStep = pageStep;
    }

    /** Registers the callback invoked when the user presses Enter on the selected item. */
    public void onEnter(Consumer<T> onEnter) {
        this.onEnter = requireNonNull(onEnter);
    }

    /** Replaces the items, resetting the selection to the first row. */
    public void setItems(List<T> items) {
        this.items = List.copyOf(items);
        if (!items.isEmpty()) {
            tableState.select(0);
        }
    }

    public List<T> items() {
        return items;
    }

    public TableState tableState() {
        return tableState;
    }

    public boolean isFocused() {
        return focusManager.isFocused(viewId);
    }

    public EventResult handleKey(KeyEvent event) {
        if (items.isEmpty() || !isFocused()) {
            return EventResult.UNHANDLED;
        }
        if (event.isSelect()) {
            onEnter.accept(items.get(tableState.selected()));
            return EventResult.HANDLED;
        }
        var selected = tableState.selected();
        int target;
        if (event.isUp() || event.isChar('k')) {
            target = selected - 1;
        } else if (event.isDown() || event.isChar('j')) {
            target = selected + 1;
        } else if (event.isPageUp()) {
            target = selected - pageStep;
        } else if (event.isPageDown()) {
            target = selected + pageStep;
        } else if (event.isHome()) {
            target = 0;
        } else if (event.isEnd()) {
            target = items.size() - 1;
        } else {
            return EventResult.UNHANDLED;
        }
        tableState.select(Math.clamp(target, 0, items.size() - 1));
        return EventResult.HANDLED;
    }
}
