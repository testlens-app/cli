// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FuzzySearchTests {

    @Test
    void matches_case_insensitive_subsequences() {
        assertThat(FuzzySearch.matches("CTest", "CalculatorTest")).isTrue();
        assertThat(FuzzySearch.matches("CuTest", "CalculatorTest")).isTrue();
        assertThat(FuzzySearch.matches("ctest", "CalculatorTest")).isTrue();
        assertThat(FuzzySearch.matches("FooTests", "JUnit Jupiter FooTests > foo()")).isTrue();
        assertThat(FuzzySearch.matches("build", "CI / build")).isTrue();
        assertThat(FuzzySearch.matches("Xyz", "CI / build")).isFalse();
    }

    @Test
    void scores_consecutive_matches_higher() {
        // scoring scheme inspired by FuzzyScore from Apache Commons Text
        assertThat(FuzzySearch.score("w", "Workshop")).isEqualTo(1);
        assertThat(FuzzySearch.score("ws", "Workshop")).isEqualTo(2);
        assertThat(FuzzySearch.score("wo", "Workshop")).isEqualTo(4);
        assertThat(FuzzySearch.score("b", "Workshop")).isEqualTo(-1);
        assertThat(FuzzySearch.score("", "anything")).isZero();
    }

    @Test
    void empty_queries_match_everything_with_no_positions() {
        assertThat(FuzzySearch.matches("", "anything")).isTrue();
        assertThat(FuzzySearch.matchPositions("", "anything")).isEmpty();
    }

    @Test
    void match_positions_are_ascending_indices_into_the_text() {
        assertThat(FuzzySearch.matchPositions("CT", "CalculatorTest")).containsExactly(0, 7);
        assertThat(FuzzySearch.matchPositions("cu", "CalculatorTest")).containsExactly(0, 4);
        assertThat(FuzzySearch.matchPositions("Xyz", "CalculatorTest")).isNull();
    }
}
