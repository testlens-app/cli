// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.controller;

import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Tab navigation between the panes of one screen, in the order of the given view IDs. */
public final class PaneSwitcher {

    private PaneSwitcher() {}

    public static EventResult switchFocus(FocusManager focusManager, List<String> viewIds, KeyEvent event) {
        if (!event.isKey(KeyCode.TAB)) {
            return EventResult.UNHANDLED;
        }
        var index = viewIds.indexOf(focusManager.focusedId());
        if (index == -1) {
            return EventResult.UNHANDLED;
        }
        String target = event.hasShift() ? previous(index, viewIds) : next(index, viewIds);
        if (target == null) {
            return EventResult.UNHANDLED;
        }
        focusManager.setFocus(target);
        return EventResult.HANDLED;
    }

    private static @Nullable String previous(int index, List<String> viewIds) {
        return index > 0 ? viewIds.get(index - 1) : null;
    }

    private static @Nullable String next(int index, List<String> viewIds) {
        return index + 1 < viewIds.size() ? viewIds.get(index + 1) : null;
    }
}
