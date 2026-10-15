// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.model;

import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.model.ExecutedTest;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * Domain state of the tests table: the full list of failing tests, the list currently shown (continuously filtered by
 * the fuzzy search), and the selection.
 *
 * <p>Notifies the given listener whenever the selected test changes, so e.g. the details pane can follow the
 * selection. Notified on every change, including programmatically forced ones.
 */
public final class TestListModel {

    private final List<ExecutedTest> allTests;
    private final Consumer<ExecutedTest> onSelectedTest;
    private List<ExecutedTest> visibleTests;
    private final StringBuilder query = new StringBuilder();
    private boolean searching;
    private boolean searchCommitted;
    private int selectedIndex = -1;
    private @Nullable ExecutedTest selected;

    public TestListModel(List<ExecutedTest> allTests, Consumer<ExecutedTest> onSelectedTest) {
        this.allTests = List.copyOf(allTests);
        this.onSelectedTest = requireNonNull(onSelectedTest);
        this.visibleTests = this.allTests;
        if (!this.allTests.isEmpty()) {
            selectTest(0);
        }
    }

    /** The (continuously filtered) list currently shown in the table. */
    public List<ExecutedTest> tests() {
        return visibleTests;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public @Nullable ExecutedTest selectedTest() {
        return selected;
    }

    public boolean isSearching() {
        return searching;
    }

    public boolean isSearchCommitted() {
        return searchCommitted;
    }

    public String searchQuery() {
        return query.toString();
    }

    /**
     * Selects the test at the given index of {@link #tests()} and returns whether the selection changed. Selection
     * changes are reported to the listener.
     */
    public boolean selectTest(int index) {
        if (index == selectedIndex) {
            return false;
        }
        selectedIndex = index;
        selected = visibleTests.get(index);
        onSelectedTest.accept(selected);
        return true;
    }

    /** Starts a new search, which resets a committed filter back to the full list. */
    public void startSearch() {
        searching = true;
        searchCommitted = false;
        query.setLength(0);
        applyFilter(true);
    }

    /** Appends a character to the search query and re-filters, selecting the best match. */
    public void appendChar(int codePoint) {
        query.appendCodePoint(codePoint);
        applyFilter(true);
    }

    /** Removes the last character of the search query and re-filters, selecting the best match. */
    public void backspace() {
        if (!query.isEmpty()) {
            query.deleteCharAt(query.length() - 1);
            applyFilter(true);
        }
    }

    /** Cancels an uncommitted search, restoring the previous selection. */
    public void cancelSearch() {
        query.setLength(0);
        applyFilter(false);
        searching = false;
    }

    /** Commits the current search term; Esc can reset it later. */
    public void commitSearch() {
        searching = false;
        searchCommitted = true;
    }

    /** Resets a committed search back to the full, unfiltered list. */
    public void resetSearch() {
        searchCommitted = false;
        query.setLength(0);
        applyFilter(false);
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
                clearSelection();
            }
            return;
        }
        var kept = selected == null ? -1 : visibleTests.indexOf(selected);
        if (kept >= 0) {
            selectTest(kept);
        } else if (!visibleTests.isEmpty()) {
            forceSelect(0);
        } else {
            clearSelection();
        }
    }

    private void forceSelect(int index) {
        selectedIndex = index;
        selected = visibleTests.get(index);
        onSelectedTest.accept(selected);
    }

    private void clearSelection() {
        selected = null;
        selectedIndex = 0;
    }

    private record Match(ExecutedTest test, int score, int index) {}

    private static String haystack(ExecutedTest test) {
        return String.join(" ", test.getDisplayNames());
    }
}
