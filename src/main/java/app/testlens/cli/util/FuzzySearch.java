// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.util;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Fuzzy matching in the spirit of the fzf tool. */
public final class FuzzySearch {

    private FuzzySearch() {}

    /**
     * Fuzzy matches in the spirit of fzf and editors like Sublime Text: the query is a case-insensitive
     * subsequence of the text.
     */
    public static boolean matches(String query, String text) {
        return matchPositions(query, text) != null;
    }

    /**
     * Scores how well the query matches the text, or {@code -1} if the query is not a case-insensitive
     * subsequence of the text.
     *
     * <p>
     * The scoring scheme is inspired by {@code FuzzyScore} from Apache Commons Text
     * (https://commons.apache.org/proper/commons-text/): one point per matched character, plus two bonus
     * points for consecutive matches. Higher scores indicate tighter matches.
     * </p>
     */
    public static int score(String query, String text) {
        var positions = matchPositions(query, text);
        if (positions == null) {
            return -1;
        }
        int score = 0;
        int previous = -2;
        for (var position : positions) {
            score += position == previous + 1 ? 3 : 1;
            previous = position;
        }
        return score;
    }

    /**
     * Returns the indices of the matched characters in {@code text}, in ascending order, or {@code null} if
     * the query is not a case-insensitive subsequence of the text. An empty query matches everything with an
     * empty position list.
     */
    public static int @Nullable [] matchPositions(String query, String text) {
        var q = query.toLowerCase(Locale.ROOT);
        var t = text.toLowerCase(Locale.ROOT);
        var positions = new int[q.length()];
        int textIndex = 0;
        for (int i = 0; i < q.length(); i++) {
            var queryChar = q.charAt(i);
            while (textIndex < t.length() && t.charAt(textIndex) != queryChar) {
                textIndex++;
            }
            if (textIndex == t.length()) {
                return null;
            }
            positions[i] = textIndex;
            textIndex++;
        }
        return positions;
    }
}
