package app.testlens.cli.tui;

import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.tabs.TabsState;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

final class PrDetailsController {

    static final String VIEW_ID = "details";

    private final Function<KeyEvent, EventResult> keyHandler;
    private final FocusManager focusManager;
    private final TabsState tabsState = new TabsState();
    private @Nullable Consumer<Execution> onSelectedExecution;
    private @Nullable ExecutedTest selectedTest;

    PrDetailsController(Function<KeyEvent, EventResult> keyHandler, FocusManager focusManager) {
        this.keyHandler = keyHandler;
        this.focusManager = focusManager;
    }

    void onSelectedExecution(Consumer<Execution> onSelectedExecution) {
        if (this.onSelectedExecution != null) {
            throw new IllegalStateException("onSelectedExecution was already set");
        }
        this.onSelectedExecution = onSelectedExecution;
        updateDetailsArea();
    }

    ExecutedTest selectedTest() {
        return requireNonNull(selectedTest);
    }

    int selectedExecutionIndex() {
        return requireNonNull(tabsState.selected());
    }

    TabsState tabsState() {
        return tabsState;
    }

    boolean isFocused() {
        return focusManager.isFocused(VIEW_ID);
    }

    Execution selectedExecution() {
        return selectedTest().getExecutions().get(selectedExecutionIndex());
    }

    void selectTest(ExecutedTest test) {
        selectedTest = test;
        tabsState.select(test.getExecutions().size() - 1);
        updateDetailsArea();
    }

    EventResult handleKey(KeyEvent event) {
        if (keyHandler.apply(event).isHandled()) {
            return EventResult.HANDLED;
        }
        // handled regardless of focus, so executions can be switched while browsing tests
        var executionCount = selectedTest().getExecutions().size();
        if (event.isLeft()) {
            var previousExecution = selectedExecutionIndex();
            tabsState.select(Math.max(0, previousExecution - 1));
            if (selectedExecutionIndex() != previousExecution) {
                updateDetailsArea();
            }
            return EventResult.HANDLED;
        }
        if (event.isRight()) {
            var previousExecution = selectedExecutionIndex();
            tabsState.select(Math.min(executionCount - 1, previousExecution + 1));
            if (selectedExecutionIndex() != previousExecution) {
                updateDetailsArea();
            }
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void updateDetailsArea() {
        if (onSelectedExecution != null) {
            onSelectedExecution.accept(selectedExecution());
        }
    }
}
