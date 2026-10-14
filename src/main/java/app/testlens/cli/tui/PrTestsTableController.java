// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import app.testlens.cli.util.FuzzySearch;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.table.TableState;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

final class PrTestsTableController {

    static final String VIEW_ID = "tests";

    private final Function<KeyEvent, EventResult> keyHandler;
    private final FocusManager focusManager;
    private final List<ExecutedTest> allTests;
    private List<ExecutedTest> visibleTests;
    private final Consumer<ExecutedTest> onSelectedTest;
    private final TableState state = new TableState();
    private boolean searching;
    private final StringBuilder query = new StringBuilder();
    private @Nullable ExecutedTest selected;

    PrTestsTableController(
        Function<KeyEvent, EventResult> keyHandler,
        FocusManager focusManager,
        PullRequestTestsResponse tests,
        Consumer<ExecutedTest> onSelectedTest
    ) {
        this.keyHandler = keyHandler;
        this.focusManager = focusManager;
        this.allTests = tests.getTests();
        this.visibleTests = allTests;
        this.onSelectedTest = onSelectedTest;
        if (!allTests.isEmpty()) {
            selectTest(0);
        }
    }

    // the (continuously filtered) list currently shown in the table
    List<ExecutedTest> tests() {
        return visibleTests;
    }

    TableState tableState() {
        return state;
    }

    boolean isFocused() {
        return focusManager.isFocused(VIEW_ID);
    }

    boolean isSearching() {
        return searching;
    }

    String searchQuery() {
        return query.toString();
    }

    void selectTest(int index) {
        if (!Objects.equals(index, state.selected())) {
            selected = visibleTests.get(index);
            state.select(index);
            onSelectedTest.accept(selected);
        }
    }

    EventResult handleKey(KeyEvent event) {
        // search keys are handled first, so an empty filtered list cannot hijack navigation keys
        if (searching) {
            handleSearchKey(event);
            return EventResult.HANDLED;
        }
        if (isFocused() && event.isChar('/')) {
            searching = true;
            query.setLength(0);
            // starting a new search resets a committed filter back to the full list
            applyFilter(true);
            return EventResult.HANDLED;
        }
        if (visibleTests.isEmpty()) {
            return keyHandler.apply(event).isHandled() ? EventResult.HANDLED : EventResult.UNHANDLED;
        }
        if (keyHandler.apply(event).isHandled()) {
            return EventResult.HANDLED;
        }
        if (!isFocused()) {
            return EventResult.UNHANDLED;
        }
        var selected = (int) state.selected();
        int target;
        if (event.isUp() || event.isChar('k')) {
            target = selected - 1;
        } else if (event.isDown() || event.isChar('j')) {
            target = selected + 1;
        } else if (event.isPageUp()) {
            target = selected - PrTestsTableView.MAX_VISIBLE_TESTS;
        } else if (event.isPageDown()) {
            target = selected + PrTestsTableView.MAX_VISIBLE_TESTS;
        } else if (event.isHome()) {
            target = 0;
        } else if (event.isEnd()) {
            target = visibleTests.size() - 1;
        } else {
            return EventResult.UNHANDLED;
        }
        selectTest(Math.clamp(target, 0, visibleTests.size() - 1));
        return EventResult.HANDLED;
    }

    private void handleSearchKey(KeyEvent event) {
        switch (event.code()) {
            case ESCAPE -> {
                query.setLength(0);
                applyFilter(false);
                searching = false;
            }
            case ENTER, TAB -> searching = false;
            case BACKSPACE -> {
                if (!query.isEmpty()) {
                    query.deleteCharAt(query.length() - 1);
                    applyFilter(true);
                }
            }
            case CHAR -> {
                if (!event.hasCtrl()) {
                    query.appendCodePoint(event.codePoint());
                    applyFilter(true);
                }
            }
            default -> {
                // swallow everything else while searching
            }
        }
    }

    // while typing, the best match is selected (selectTop); on cancel, the previous selection is restored
    private void applyFilter(boolean selectTop) {
        var q = query.toString();
        visibleTests = q.isEmpty()
            ? allTests
            // best matches first; ties keep the original table order
            : IntStream.range(0, allTests.size())
                .mapToObj(
                    index -> new Match(allTests.get(index), FuzzySearch.score(q, haystack(allTests.get(index))), index)
                )
                .filter(match -> match.score >= 0)
                .sorted(
                    Comparator.comparingInt((Match match) -> match.score)
                        .reversed()
                        .thenComparingInt(match -> match.index)
                )
                .map(match -> match.test)
                .toList();
        if (selectTop) {
            if (!visibleTests.isEmpty()) {
                forceSelect(0);
            } else {
                selected = null;
                state.select(0);
            }
            return;
        }
        var kept = visibleTests.indexOf(selected);
        if (kept >= 0) {
            selectTest(kept);
        } else if (!visibleTests.isEmpty()) {
            forceSelect(0);
        } else {
            selected = null;
            state.select(0);
        }
    }

    private record Match(ExecutedTest test, int score, int index) {}

    private void forceSelect(int index) {
        selected = visibleTests.get(index);
        state.select(index);
        onSelectedTest.accept(selected);
    }

    private static String haystack(ExecutedTest test) {
        return String.join(" ", test.getDisplayNames());
    }
}
