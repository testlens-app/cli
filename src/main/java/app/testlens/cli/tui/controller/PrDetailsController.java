// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.controller;

import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.tui.model.PrTuiModel;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.tabs.TabsState;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Input handling for the details pane. Owns the widget state (execution tabs, scrollable details area) and keeps it in
 * sync with the details model; the domain state itself lives in {@link PrTuiModel}.
 */
public final class PrDetailsController {

    public static final String VIEW_ID = "details";

    private final Function<KeyEvent, EventResult> keyHandler;
    private final FocusManager focusManager;
    private final PrTuiModel model;
    private final TabsState tabsState = new TabsState();
    private @Nullable RichTextAreaElement detailsArea;

    public PrDetailsController(
        Function<KeyEvent, EventResult> keyHandler,
        FocusManager focusManager,
        PrTuiModel model
    ) {
        this.keyHandler = keyHandler;
        this.focusManager = focusManager;
        this.model = model;
    }

    public void bindDetailsArea(RichTextAreaElement detailsArea) {
        if (this.detailsArea != null) {
            throw new IllegalStateException("detailsArea was already set");
        }
        this.detailsArea = detailsArea;
    }

    public TabsState tabsState() {
        return tabsState;
    }

    public boolean isFocused() {
        return focusManager.isFocused(VIEW_ID);
    }

    /** Follows the test selection of the tests table; the last execution is shown. */
    public void selectTest(ExecutedTest test) {
        model.selectTest(test);
        tabsState.select(model.details().executionIndex());
    }

    public EventResult handleKey(KeyEvent event) {
        var details = model.details();
        if (details.test() == null) {
            return keyHandler.apply(event).isHandled() ? EventResult.HANDLED : EventResult.UNHANDLED;
        }
        if (keyHandler.apply(event).isHandled()) {
            return EventResult.HANDLED;
        }
        // handled regardless of focus, so executions can be switched while browsing tests
        if (event.isLeft() || event.isChar('h')) {
            model.selectExecution(details.executionIndex() - 1);
            tabsState.select(model.details().executionIndex());
            return EventResult.HANDLED;
        }
        if (event.isRight() || event.isChar('l')) {
            model.selectExecution(details.executionIndex() + 1);
            tabsState.select(model.details().executionIndex());
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
}
