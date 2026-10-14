// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.controller;

import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.tabs.TabsState;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class PrDetailsController {

    public static final String VIEW_ID = "details";

    private final Function<KeyEvent, EventResult> keyHandler;
    private final FocusManager focusManager;
    private final TabsState tabsState = new TabsState();
    private @Nullable Consumer<Execution> onSelectedExecution;
    private @Nullable RichTextAreaElement detailsArea;
    private @Nullable ExecutedTest selectedTest;

    public PrDetailsController(Function<KeyEvent, EventResult> keyHandler, FocusManager focusManager) {
        this.keyHandler = keyHandler;
        this.focusManager = focusManager;
    }

    public void onSelectedExecution(Consumer<Execution> onSelectedExecution) {
        if (this.onSelectedExecution != null) {
            throw new IllegalStateException("onSelectedExecution was already set");
        }
        this.onSelectedExecution = onSelectedExecution;
        updateDetailsArea();
    }

    public void bindDetailsArea(RichTextAreaElement detailsArea) {
        if (this.detailsArea != null) {
            throw new IllegalStateException("detailsArea was already set");
        }
        this.detailsArea = detailsArea;
    }

    @Nullable
    public ExecutedTest selectedTest() {
        return selectedTest;
    }

    public int selectedExecutionIndex() {
        return requireNonNull(tabsState.selected());
    }

    public TabsState tabsState() {
        return tabsState;
    }

    public boolean isFocused() {
        return focusManager.isFocused(VIEW_ID);
    }

    public Execution selectedExecution() {
        return requireNonNull(selectedTest).getExecutions().get(selectedExecutionIndex());
    }

    public void selectTest(ExecutedTest test) {
        selectedTest = test;
        tabsState.select(test.getExecutions().size() - 1);
        updateDetailsArea();
    }

    public EventResult handleKey(KeyEvent event) {
        if (selectedTest == null) {
            return keyHandler.apply(event).isHandled() ? EventResult.HANDLED : EventResult.UNHANDLED;
        }
        if (keyHandler.apply(event).isHandled()) {
            return EventResult.HANDLED;
        }
        // handled regardless of focus, so executions can be switched while browsing tests
        var executionCount = requireNonNull(selectedTest).getExecutions().size();
        if (event.isLeft() || event.isChar('h')) {
            var previousExecution = selectedExecutionIndex();
            tabsState.select(Math.max(0, previousExecution - 1));
            if (selectedExecutionIndex() != previousExecution) {
                updateDetailsArea();
            }
            return EventResult.HANDLED;
        }
        if (event.isRight() || event.isChar('l')) {
            var previousExecution = selectedExecutionIndex();
            tabsState.select(Math.min(executionCount - 1, previousExecution + 1));
            if (selectedExecutionIndex() != previousExecution) {
                updateDetailsArea();
            }
            return EventResult.HANDLED;
        }
        if (!isFocused()) {
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

    private void updateDetailsArea() {
        if (onSelectedExecution != null && selectedTest != null) {
            onSelectedExecution.accept(selectedExecution());
        }
    }
}
