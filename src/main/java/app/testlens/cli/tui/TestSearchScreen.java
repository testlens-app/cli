// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import static app.testlens.cli.CommandSupport.testName;
import static app.testlens.cli.tui.controller.TestSearchController.VIEW_ID;
import static dev.tamboui.style.Color.DARK_GRAY;
import static dev.tamboui.style.Color.RED;
import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.table;
import static dev.tamboui.toolkit.Toolkit.text;

import app.testlens.cli.ExecutionsLoader;
import app.testlens.cli.client.ApiException;
import app.testlens.cli.client.model.TestResponse;
import app.testlens.cli.tui.ScreenRouter.Screen;
import app.testlens.cli.tui.controller.ExecutionsController;
import app.testlens.cli.tui.controller.TestSearchController;
import app.testlens.cli.tui.model.ExecutionsModel;
import app.testlens.cli.tui.model.TestInfo;
import app.testlens.cli.tui.view.ExecutionsView;
import dev.tamboui.layout.Constraint;
import dev.tamboui.style.Style;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The results of a test search on the home screen: one row per test, drilling down into the executions screen of the
 * selected test. Pushed by the home screen's {@code /} action.
 */
public final class TestSearchScreen implements Screen {

    private final TuiSession session;
    private final FocusManager focusManager;
    private final ExecutorService executor;
    private final Consumer<Runnable> publish;
    private final Consumer<Screen> opener;
    private final String query;
    private final TestSearchController listController;

    private @Nullable String pending;
    private @Nullable String error;

    public TestSearchScreen(
        TuiSession session,
        FocusManager focusManager,
        ExecutorService executor,
        Consumer<Runnable> publish,
        Consumer<Screen> opener,
        String query,
        List<TestResponse> tests
    ) {
        this.session = session;
        this.focusManager = focusManager;
        this.executor = executor;
        this.publish = publish;
        this.opener = opener;
        this.query = query;
        this.listController = new TestSearchController(focusManager, tests);
    }

    /** Wires the drill-down callback, before the first key event. */
    public void openWith() {
        listController.onOpenTest(this::openExecutions);
    }

    @Override
    public String focusId() {
        return VIEW_ID;
    }

    @Override
    public Element render() {
        Element content = column(
            header(),
            tableElement(),
            text("↑/↓ select · Enter executions · Esc/q back").dim().length(1)
        );
        if (error != null) {
            content = stack(content, dialog(text(error)).rounded().borderColor(RED).width(error.length() + 4));
        }
        if (pending != null) {
            content = stack(content, dialog(text(pending)).rounded());
        }
        return content;
    }

    @Override
    public boolean modalInputActive() {
        // while the executions fetch runs, its modal dialog must receive quit/back keys
        return pending != null;
    }

    private Element header() {
        return text("Tests matching \"" + query + "\"").bold().cyan().length(1);
    }

    private Element tableElement() {
        var tests = listController.tests();
        var visibleRows = Math.min(tests.size(), 10);
        return table()
            .id(VIEW_ID)
            .title("Tests")
            .rounded()
            .borderColor(DARK_GRAY)
            .header(Row.from(Cell.from("Test"), Cell.from("ID"), Cell.from("Unique ID")))
            .rows(
                tests.stream()
                    .map(TestSearchScreen::testRow)
                    .toList()
            )
            .widths(Constraint.fill(), Constraint.length(9), Constraint.fill())
            .state(listController.tableState())
            .highlightStyle(Style.EMPTY.reversed())
            .focusable()
            .onKeyEvent(listController::handleKey)
            .constraint(Constraint.length(Math.max(visibleRows + 3, 1)));
    }

    private static Row testRow(TestResponse test) {
        return Row.from(
            Cell.from(testName(test.getDisplayNames())),
            Cell.from(Long.toString(test.getId())),
            Cell.from(test.getUniqueId())
        );
    }

    private void openExecutions(TestInfo test) {
        pending = "Fetching executions of " + test.displayName() + "...";
        executor.submit(() -> {
            try {
                var loader = new ExecutionsLoader(
                    session.testsApi(),
                    session.owner(),
                    session.repo(),
                    test.id(),
                    null,
                    null,
                    null
                );
                var first = loader.first();
                publish.accept(() -> {
                    pending = null;
                    var model = new ExecutionsModel(test, first.executions(), first.hasMore());
                    var controller = new ExecutionsController(focusManager, model, () -> {
                        try {
                            var next = loader.next();
                            if (next != null) {
                                model.append(next);
                                return true;
                            }
                            return false;
                        } catch (ApiException e) {
                            // without a dedicated error dialog, the next page is simply not appended
                            return false;
                        }
                    });
                    opener.accept(new ExecutionsView(controller, model, null, null, null));
                });
            } catch (ApiException e) {
                publish.accept(() -> {
                    pending = null;
                    error = ApiErrors.message(e, "Test not found");
                });
            }
        });
    }
}
