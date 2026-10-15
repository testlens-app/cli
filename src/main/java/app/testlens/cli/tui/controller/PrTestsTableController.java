// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.controller;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import app.testlens.cli.tui.model.TestListModel;
import app.testlens.cli.tui.view.PrTestsTableView;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.table.TableState;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Input handling for the tests table. Owns the table widget state and keeps it in sync with the {@link TestListModel};
 * filtering, searching and the selection itself live in the model.
 */
public final class PrTestsTableController {

    public static final String VIEW_ID = "tests";

    private final Function<KeyEvent, EventResult> keyHandler;
    private final FocusManager focusManager;
    private final TestListModel model;
    private final TableState state = new TableState();

    public PrTestsTableController(
        Function<KeyEvent, EventResult> keyHandler,
        FocusManager focusManager,
        PullRequestTestsResponse tests,
        Consumer<ExecutedTest> onSelectedTest
    ) {
        this.keyHandler = keyHandler;
        this.focusManager = focusManager;
        this.model = new TestListModel(tests.getTests(), onSelectedTest);
        if (model.selectedIndex() >= 0) {
            state.select(model.selectedIndex());
        }
    }

    public TestListModel model() {
        return model;
    }

    public TableState tableState() {
        return state;
    }

    public boolean isFocused() {
        return focusManager.isFocused(VIEW_ID);
    }

    public boolean isSearching() {
        return model.isSearching();
    }

    public boolean isSearchCommitted() {
        return model.isSearchCommitted();
    }

    public String searchQuery() {
        return model.searchQuery();
    }

    public void selectTest(int index) {
        if (model.selectTest(index)) {
            state.select(index);
        }
    }

    public EventResult handleKey(KeyEvent event) {
        // search keys are handled first, so an empty filtered list cannot hijack navigation keys
        if (model.isSearching()) {
            // pane switching stays available while searching
            if (event.isKey(KeyCode.TAB)) {
                return keyHandler.apply(event).isHandled() ? EventResult.HANDLED : EventResult.UNHANDLED;
            }
            handleSearchKey(event);
            syncSelection();
            return EventResult.HANDLED;
        }
        if (isFocused() && event.isChar('/')) {
            model.startSearch();
            syncSelection();
            return EventResult.HANDLED;
        }
        if (model.isSearchCommitted() && event.isKey(KeyCode.ESCAPE)) {
            // resetting a committed search restores the full, unfiltered list
            model.resetSearch();
            syncSelection();
            return EventResult.HANDLED;
        }
        if (model.tests().isEmpty()) {
            return keyHandler.apply(event).isHandled() ? EventResult.HANDLED : EventResult.UNHANDLED;
        }
        if (keyHandler.apply(event).isHandled()) {
            return EventResult.HANDLED;
        }
        if (!isFocused()) {
            return EventResult.UNHANDLED;
        }
        var selected = model.selectedIndex();
        int target;
        if (event.isUp() || event.isChar('k')) {
            target = selected - 1;
        } else if (event.isDown() || event.isChar('j')) {
            target = selected + 1;
        } else if (event.isPageUp()) {
            target = selected - PrTestsTableView.MAX_VISIBLE_TESTS;
        } else if (event.isPageDown()) {
            target = selected + PrTestsTableView.MAX_VISIBLE_TESTS;
        } else if (event.isHome()) {
            target = 0;
        } else if (event.isEnd()) {
            target = model.tests().size() - 1;
        } else {
            return EventResult.UNHANDLED;
        }
        selectTest(Math.clamp(target, 0, model.tests().size() - 1));
        return EventResult.HANDLED;
    }

    private void handleSearchKey(KeyEvent event) {
        switch (event.code()) {
            case ESCAPE -> model.cancelSearch();
            case ENTER -> model.commitSearch();
            case BACKSPACE -> model.backspace();
            case CHAR -> {
                if (!event.hasCtrl()) {
                    model.appendChar(event.codePoint());
                }
            }
            default -> {
                // swallow everything else while searching
            }
        }
    }

    // the model is the source of truth for the selection; the table state follows it
    private void syncSelection() {
        state.select(model.selectedIndex());
    }
}
