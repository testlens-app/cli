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

    @Test
    void isDisplayed_and_reveal_work_by_identity() {
        var focusManager = new FocusManager();
        var list = new MarkerScreen("list");
        var router = new ScreenRouter(list, focusManager);
        var pr = new MarkerScreen("pr");
        var flakes = new MarkerScreen("flakes");

        assertThat(router.isDisplayed(list)).isTrue();
        assertThat(router.isDisplayed(pr)).isFalse();

        router.push(pr);
        router.push(flakes);
        assertThat(router.screen().focusId()).isEqualTo("flakes");
        focusManager.setFocus("details");

        assertThat(router.reveal(pr)).isTrue();
        assertThat(router.screen().focusId()).isEqualTo("pr");
        assertThat(router.canGoBack()).isTrue();

        assertThat(router.reveal(list)).isTrue();
        assertThat(router.screen().focusId()).isEqualTo("list");
        assertThat(router.canGoBack()).isFalse();

        assertThat(router.reveal(pr)).isFalse();
        assertThat(router.screen().focusId()).isEqualTo("list");
    }

    @Test
    void global_keys_push_home_once_and_g_is_inert_there() {
        var focusManager = new FocusManager();
        var router = new ScreenRouter(new MarkerScreen("pr"), focusManager);
        var home = new MarkerScreen("home");
        var quits = new ArrayList<String>();

        // g pushes home on top of the deep link
        assertThat(GlobalKeys.handle(KeyEvent.ofChar('g'), router, home, () -> quits.add("quit")))
            .isEqualTo(EventResult.HANDLED);
        assertThat(router.screen().focusId()).isEqualTo("home");
        assertThat(router.canGoBack()).isTrue();

        // g on the home screen is inert: it routes to the focused pane, which ignores it
        assertThat(GlobalKeys.handle(KeyEvent.ofChar('g'), router, home, () -> quits.add("quit")))
            .isEqualTo(EventResult.UNHANDLED);
        assertThat(router.screen().focusId()).isEqualTo("home");
        assertThat(quits).isEmpty();

        // q goes back to the deep link first
        assertThat(GlobalKeys.handle(KeyEvent.ofChar('q'), router, home, () -> quits.add("quit")))
            .isEqualTo(EventResult.HANDLED);
        assertThat(router.screen().focusId()).isEqualTo("pr");
        assertThat(quits).isEmpty();

        // q at the deep-link root quits
        assertThat(GlobalKeys.handle(KeyEvent.ofChar('q'), router, home, () -> quits.add("quit")))
            .isEqualTo(EventResult.HANDLED);
        assertThat(quits).containsExactly("quit");
    }

    @Test
    void global_keys_reveal_home_when_it_is_below_the_stack() {
        var focusManager = new FocusManager();
        var home = new MarkerScreen("home");
        var router = new ScreenRouter(home, focusManager);
        router.push(new MarkerScreen("pr"));
        router.push(new MarkerScreen("flakes"));

        assertThat(GlobalKeys.handle(KeyEvent.ofChar('g'), router, home, () -> {}))
            .isEqualTo(EventResult.HANDLED);
        assertThat(router.screen().focusId()).isEqualTo("home");
        // going home discards the screens above it
        assertThat(router.canGoBack()).isFalse();
    }

    @Test
    void global_keys_yield_to_modal_inputs_and_screen_keys() {
        var focusManager = new FocusManager();
        var modal = new ModalScreen("list");
        var router = new ScreenRouter(modal, focusManager);
        var quits = new ArrayList<String>();

        // a modal input keeps all keys, including g and q
        assertThat(GlobalKeys.handle(KeyEvent.ofChar('g'), router, null, () -> {}))
            .isEqualTo(EventResult.UNHANDLED);
        assertThat(GlobalKeys.handle(KeyEvent.ofChar('q'), router, null, () -> {}))
            .isEqualTo(EventResult.UNHANDLED);

        modal.modal = false;
        // the screen's own global keys win over back/quit
        assertThat(GlobalKeys.handle(KeyEvent.ofChar('p'), router, null, () -> {}))
            .isEqualTo(EventResult.HANDLED);
        // unhandled screen keys fall through to back/quit, which quits at the root
        assertThat(GlobalKeys.handle(KeyEvent.ofChar('q'), router, null, () -> quits.add("quit")))
            .isEqualTo(EventResult.HANDLED);
        assertThat(quits).containsExactly("quit");
    }

    private record MarkerScreen(String focusId) implements Screen {
        @Override
        public Element render() {
            throw new UnsupportedOperationException();
        }
    }

    /** A screen that either swallows every key as a modal input or handles {@code p} as a global key. */
    private static final class ModalScreen implements Screen {
        private final String id;
        boolean modal = true;

        ModalScreen(String id) {
            this.id = id;
        }

        @Override
        public String focusId() {
            return id;
        }

        @Override
        public Element render() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean modalInputActive() {
            return modal;
        }

        @Override
        public EventResult handleGlobalKey(KeyEvent event) {
            return event.isChar('p') ? EventResult.HANDLED : EventResult.UNHANDLED;
        }
    }
}
