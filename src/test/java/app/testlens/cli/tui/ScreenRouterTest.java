// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.tui.ScreenRouter.Screen;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ScreenRouterTest {

    @Test
    void pushes_and_restores_focus_when_going_back() {
        var focusManager = new FocusManager();
        focusManager.setFocus("list");
        var router = new ScreenRouter(new MarkerScreen("list"), focusManager);
        assertThat(router.canGoBack()).isFalse();

        router.push(new MarkerScreen("executions"));
        assertThat(router.screen().focusId()).isEqualTo("executions");
        assertThat(focusManager.focusedId()).isEqualTo("executions");
        assertThat(router.canGoBack()).isTrue();

        assertThat(router.back()).isTrue();
        assertThat(router.screen().focusId()).isEqualTo("list");
        assertThat(focusManager.focusedId()).isEqualTo("list");
        assertThat(router.canGoBack()).isFalse();

        // at the bottom, back is a no-op
        assertThat(router.back()).isFalse();
    }

    @Test
    void back_restores_the_focus_of_the_screen_below() {
        var focusManager = new FocusManager();
        var router = new ScreenRouter(new MarkerScreen("list"), focusManager);

        // the user had switched to the details pane of the list screen before drilling down
        focusManager.setFocus("details");
        router.push(new MarkerScreen("executions"));
        router.back();

        assertThat(focusManager.focusedId()).isEqualTo("details");
    }

    @Test
    void q_goes_back_deeper_and_quits_at_the_root() {
        var focusManager = new FocusManager();
        var router = new ScreenRouter(new MarkerScreen("list"), focusManager);
        router.push(new MarkerScreen("executions"));
        var quits = new ArrayList<String>();

        assertThat(router.handleBackOrQuit(KeyEvent.ofChar('q'), () -> quits.add("quit")))
            .isEqualTo(EventResult.HANDLED);
        assertThat(quits).isEmpty();
        assertThat(router.canGoBack()).isFalse();

        assertThat(router.handleBackOrQuit(KeyEvent.ofChar('q'), () -> quits.add("quit")))
            .isEqualTo(EventResult.HANDLED);
        assertThat(quits).containsExactly("quit");
    }

    @Test
    void escape_goes_back_when_there_is_a_screen_below() {
        var focusManager = new FocusManager();
        var router = new ScreenRouter(new MarkerScreen("list"), focusManager);

        assertThat(router.handleBackOrQuit(KeyEvent.ofKey(KeyCode.ESCAPE), () -> {}))
            .isEqualTo(EventResult.UNHANDLED);

        router.push(new MarkerScreen("executions"));
        assertThat(router.handleBackOrQuit(KeyEvent.ofKey(KeyCode.ESCAPE), () -> {}))
            .isEqualTo(EventResult.HANDLED);
        assertThat(router.canGoBack()).isFalse();
    }

    private record MarkerScreen(String focusId) implements Screen {
        @Override
        public Element render() {
            throw new UnsupportedOperationException();
        }
    }
}
