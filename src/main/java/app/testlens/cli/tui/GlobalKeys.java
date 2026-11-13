// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import app.testlens.cli.tui.ScreenRouter.Screen;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * Global key handling of the unified TUI, registered ahead of the modal dialogs (which consume all keys once routed
 * to unfocused elements). Order: screens with a modal input keep every key; then the current screen's global keys
 * (the home screen's quick actions); then {@code g} for the home screen; then back/quit.
 */
public final class GlobalKeys {

    /** Key that pushes (or reveals) the home screen; only meaningful when a home screen exists. */
    public static final char HOME = 'g';

    private GlobalKeys() {}

    public static EventResult handle(
        KeyEvent event,
        ScreenRouter router,
        @Nullable Screen home,
        Runnable quit
    ) {
        if (router.screen().modalInputActive()) {
            return EventResult.UNHANDLED;
        }
        var screenResult = router.screen().handleGlobalKey(event);
        if (screenResult.isHandled()) {
            return screenResult;
        }
        if (home != null && event.isChar(HOME) && router.screen() != home) {
            // g reveals home, discarding the screens above it; at the home screen itself the key is inert
            if (!router.reveal(home)) {
                router.push(home);
            }
            return EventResult.HANDLED;
        }
        return router.handleBackOrQuit(event, quit);
    }
}
