package app.testlens.cli.util;

import app.testlens.cli.client.model.ComparisonFailure;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class TextDiff {

    public record Segment(String text, boolean changed) {}

    public record DiffLine(char marker, List<Segment> segments) {}

    // same setup as PRCommentBlocks in github-pull-request-model
    private static final String OPEN_TAG_PLACEHOLDER = UUID.randomUUID().toString();
    private static final String CLOSE_TAG_PLACEHOLDER = UUID.randomUUID().toString();

    private static final DiffRowGenerator DIFF_ROW_GENERATOR = DiffRowGenerator.create()
        .showInlineDiffs(true)
        .inlineDiffByWord(true)
        .lineNormalizer(Function.identity())
        .oldTag(start -> start ? OPEN_TAG_PLACEHOLDER : CLOSE_TAG_PLACEHOLDER)
        .newTag(start -> start ? OPEN_TAG_PLACEHOLDER : CLOSE_TAG_PLACEHOLDER)
        .build();

    private TextDiff() {}

    public static Optional<List<DiffLine>> diff(@Nullable ComparisonFailure failure) {
        if (failure == null || failure.getExpected() == null || failure.getActual() == null) {
            return Optional.empty();
        }
        var rows = DIFF_ROW_GENERATOR.generateDiffRows(
            failure.getExpected().lines().toList(),
            failure.getActual().lines().toList()
        );
        var lines = new ArrayList<DiffLine>();
        for (var row : rows) {
            if (row.getTag() == DiffRow.Tag.EQUAL) {
                lines.add(new DiffLine(' ', segments(row.getOldLine())));
                continue;
            }
            if (row.getTag() != DiffRow.Tag.INSERT) {
                lines.add(new DiffLine('-', segments(row.getOldLine())));
            }
            if (row.getTag() != DiffRow.Tag.DELETE) {
                lines.add(new DiffLine('+', segments(row.getNewLine())));
            }
        }
        return Optional.of(lines);
    }

    private static List<Segment> segments(String line) {
        var segments = new ArrayList<Segment>();
        var pos = 0;
        while (pos < line.length()) {
            var open = line.indexOf(OPEN_TAG_PLACEHOLDER, pos);
            if (open < 0) {
                segments.add(new Segment(line.substring(pos), false));
                break;
            }
            if (open > pos) {
                segments.add(new Segment(line.substring(pos, open), false));
            }
            var start = open + OPEN_TAG_PLACEHOLDER.length();
            var close = line.indexOf(CLOSE_TAG_PLACEHOLDER, start);
            var end = close < 0 ? line.length() : close;
            segments.add(new Segment(line.substring(start, end), true));
            pos = close < 0 ? end : close + CLOSE_TAG_PLACEHOLDER.length();
        }
        return segments;
    }
}
