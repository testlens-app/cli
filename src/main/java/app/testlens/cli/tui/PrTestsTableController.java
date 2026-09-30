package app.testlens.cli.tui;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.table.TableState;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

final class PrTestsTableController {

    static final String VIEW_ID = "tests";

    private final Function<KeyEvent, EventResult> keyHandler;
    private final FocusManager focusManager;
    private final List<ExecutedTest> tests;
    private final Consumer<ExecutedTest> onSelectedTest;
    private final TableState state = new TableState();

    PrTestsTableController(
        Function<KeyEvent, EventResult> keyHandler,
        FocusManager focusManager,
        PullRequestTestsResponse tests,
        Consumer<ExecutedTest> onSelectedTest
    ) {
        this.keyHandler = keyHandler;
        this.focusManager = focusManager;
        this.tests = tests.getTests();
        this.onSelectedTest = onSelectedTest;
        if (!this.tests.isEmpty()) {
            selectTest(0);
        }
    }

    List<ExecutedTest> tests() {
        return tests;
    }

    TableState tableState() {
        return state;
    }

    boolean isFocused() {
        return focusManager.isFocused(VIEW_ID);
    }

    void selectTest(int index) {
        if (!Objects.equals(index, state.selected())) {
            state.select(index);
            onSelectedTest.accept(tests.get(index));
        }
    }

    EventResult handleKey(KeyEvent event) {
        if (keyHandler.apply(event).isHandled()) {
            return EventResult.HANDLED;
        }
        if (!isFocused()) {
            return EventResult.UNHANDLED;
        }
        var selected = (int) state.selected();
        int target;
        if (event.isUp()) {
            target = selected - 1;
        } else if (event.isDown()) {
            target = selected + 1;
        } else if (event.isPageUp()) {
            target = selected - PrTestsTableView.MAX_VISIBLE_TESTS;
        } else if (event.isPageDown()) {
            target = selected + PrTestsTableView.MAX_VISIBLE_TESTS;
        } else if (event.isHome()) {
            target = 0;
        } else if (event.isEnd()) {
            target = tests.size() - 1;
        } else {
            return EventResult.UNHANDLED;
        }
        selectTest(Math.clamp(target, 0, tests.size() - 1));
        return EventResult.HANDLED;
    }
}
