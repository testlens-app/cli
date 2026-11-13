// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.controller;

import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.model.TestResponse;
import app.testlens.cli.tui.model.TestInfo;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.table.TableState;
import java.util.List;
import java.util.function.Consumer;

/**
 * Input handling of the test search results table; opening a test is delegated to the callback registered via
 * {@link #onOpenTest(Consumer)}, which fetches the test's executions and pushes the executions screen.
 */
public final class TestSearchController {

    public static final String VIEW_ID = "search-results";

    private final FocusManager focusManager;
    private List<TestResponse> tests;
    private final TableState tableState = new TableState();
    private Consumer<TestInfo> onOpenTest = test -> {};

    public TestSearchController(FocusManager focusManager, List<TestResponse> tests) {
        this.focusManager = focusManager;
        this.tests = List.copyOf(tests);
        if (!tests.isEmpty()) {
            tableState.select(0);
        }
    }

    /** Registers the callback invoked when the user opens the executions of the selected test. */
    public void onOpenTest(Consumer<TestInfo> onOpenTest) {
        this.onOpenTest = requireNonNull(onOpenTest);
    }

    public List<TestResponse> tests() {
        return tests;
    }

    public TableState tableState() {
        return tableState;
    }

    public boolean isFocused() {
        return focusManager.isFocused(VIEW_ID);
    }

    public EventResult handleKey(KeyEvent event) {
        if (tests.isEmpty() || !isFocused()) {
            return EventResult.UNHANDLED;
        }
        if (event.isSelect()) {
            onOpenTest.accept(TestInfo.of(tests.get(tableState.selected())));
            return EventResult.HANDLED;
        }
        var selected = tableState.selected();
        int target;
        if (event.isUp() || event.isChar('k')) {
            target = selected - 1;
        } else if (event.isDown() || event.isChar('j')) {
            target = selected + 1;
        } else if (event.isPageUp()) {
            target = selected - 10;
        } else if (event.isPageDown()) {
            target = selected + 10;
        } else if (event.isHome()) {
            target = 0;
        } else if (event.isEnd()) {
            target = tests.size() - 1;
        } else {
            return EventResult.UNHANDLED;
        }
        tableState.select(Math.clamp(target, 0, tests.size() - 1));
        return EventResult.HANDLED;
    }
}
