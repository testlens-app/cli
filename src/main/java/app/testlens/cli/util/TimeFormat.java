// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.util;

import app.testlens.cli.client.model.Execution;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

public final class TimeFormat {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'Z'");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss'Z'");

    private TimeFormat() {}

    /** Formats start and end time in UTC and the duration, e.g. {@code 2026-01-02 03:04:05Z - 03:04:06Z (1s 500ms)}. */
    public static String formatTime(Execution execution) {
        var start = execution.getStartTime().atZoneSameInstant(ZoneOffset.UTC);
        var durationMillis = execution.getDurationMillis();
        if (durationMillis == null) {
            return DATE_TIME.format(start);
        }
        var duration = formatDuration(durationMillis);
        var end = start.plus(Duration.ofMillis(durationMillis));
        return DATE_TIME.format(start) + " - "
            + (end.toLocalDate().equals(start.toLocalDate()) ? TIME : DATE_TIME).format(end)
            + " (" + duration + ")";
    }

    static String formatDuration(long millis) {
        if (millis == 0) {
            return "0ms";
        }
        var parts = new StringJoiner(" ");
        appendIfPositive(parts, millis / 3_600_000, "h");
        appendIfPositive(parts, millis / 60_000 % 60, "m");
        appendIfPositive(parts, millis / 1000 % 60, "s");
        appendIfPositive(parts, millis % 1000, "ms");
        return parts.toString();
    }

    private static void appendIfPositive(StringJoiner parts, long value, String unit) {
        if (value > 0) {
            parts.add(value + unit);
        }
    }
}
