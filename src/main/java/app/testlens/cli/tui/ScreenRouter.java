// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Optional;

/**
 * Navigation between the screens of a multi-screen TUI. Screens are stacked: pushing a screen remembers the focus of
 * the screen below it, going back restores that focus. The bottom of the stack is the screen the TUI was started with.
 */
public final class ScreenRouter {

    /** A view that can be shown by the router; its render output occupies the whole terminal. */
    public interface Screen {

        /** The view ID of the screen's initially focused pane, restored when the screen becomes visible again. */
        String focusId();

        Element render();
    }

    private final FocusManager focusManager;
    private final ArrayDeque<Screen> stack = new ArrayDeque<>();
    private final ArrayDeque<Optional<String>> focusStack = new ArrayDeque<>();

    public ScreenRouter(Screen rootScreen, FocusManager focusManager) {
        this.focusManager = focusManager;
        stack.push(rootScreen);
    }

    public Screen screen() {
        return stack.peek();
    }

    public boolean canGoBack() {
        return stack.size() > 1;
    }

    /** Shows the given screen on top of the current one, remembering the currently focused pane. */
    public void push(Screen screen) {
        focusStack.push(Optional.ofNullable(focusManager.focusedId()));
        stack.push(screen);
        focusManager.setFocus(screen.focusId());
    }

    /** Returns to the screen below, restoring its focus; returns whether there was one. */
    public boolean back() {
        if (!canGoBack()) {
            return false;
        }
        stack.pop();
        var focus = focusStack.pop();
        // null focus means "the automatic first-focusable of the screen below", not a specific pane
        focusManager.setFocus(focus.orElse(null));
        return true;
    }

    public Element render() {
        return screen().render();
    }

    /** The back/quit handling shared by all router-based TUIs: Esc/q go back, and quit at the root screen. */
    public EventResult handleBackOrQuit(KeyEvent event, Runnable quit) {
        if (event.isQuit()) {
            if (canGoBack()) {
                back();
            } else {
                quit.run();
            }
            return EventResult.HANDLED;
        }
        if (event.isCancel() && canGoBack()) {
            back();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }
}
