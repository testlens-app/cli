// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.util;

import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.model.ComparisonFailure;
import app.testlens.cli.util.TextDiff.DiffLine;
import app.testlens.cli.util.TextDiff.Segment;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextDiffTest {

    @Test
    void is_empty_without_both_values() {
        assertThat(TextDiff.diff(null)).isEmpty();
        assertThat(TextDiff.diff(new ComparisonFailure().expected("a"))).isEmpty();
        assertThat(TextDiff.diff(new ComparisonFailure().actual("a"))).isEmpty();
    }

    @Test
    void marks_changed_words_within_a_line() {
        assertThat(diff("foo bar\nsame", "foo baz\nsame")).containsExactly(
            new DiffLine('-', List.of(new Segment("foo ", false), new Segment("bar", true))),
            new DiffLine('+', List.of(new Segment("foo ", false), new Segment("baz", true))),
            new DiffLine(' ', List.of(new Segment("same", false)))
        );
    }

    @Test
    void shows_added_and_removed_lines() {
        assertThat(diff("a\nb", "a\nc\nd")).extracting(DiffLine::marker).containsExactly(' ', '-', '+', '+');
        assertThat(diff("a\nb", "a")).containsExactly(
            new DiffLine(' ', List.of(new Segment("a", false))),
            new DiffLine('-', List.of(new Segment("b", true)))
        );
    }

    @Test
    void identical_values_have_no_changes() {
        assertThat(diff("x", "x")).containsExactly(new DiffLine(' ', List.of(new Segment("x", false))));
    }

    private static List<DiffLine> diff(String expected, String actual) {
        return TextDiff.diff(new ComparisonFailure().expected(expected).actual(actual)).orElseThrow();
    }
}
