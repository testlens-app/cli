package app.testlens.cli.util;

import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.model.Execution;
import java.time.OffsetDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeFormatTest {

    @ParameterizedTest
    @CsvSource(
        textBlock = """
                    0,          0ms
                    42,         42ms
                    1500,       1s 500ms
                    61000,      1m 1s
                    3661000,    1h 1m 1s
                    3600000,    1h
                    """
    )
    void formats_durations(long millis, String expected) {
        assertThat(TimeFormat.formatDuration(millis)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        nullValues = "null",
        textBlock = """
                    2026-01-02T03:04:05Z      | 1500    | 2026-01-02 03:04:05Z - 03:04:06Z (1s 500ms)
                    2026-01-02T03:04:05Z      | null    | 2026-01-02 03:04:05Z
                    2026-01-02T04:04:05+01:00 | 1000    | 2026-01-02 03:04:05Z - 03:04:06Z (1s)
                    2026-01-02T23:59:59Z      | 2000    | 2026-01-02 23:59:59Z - 2026-01-03 00:00:01Z (2s)
                    """
    )
    void formats_start_end_and_duration(String start, Long durationMillis, String expected) {
        var execution = new Execution().startTime(OffsetDateTime.parse(start)).durationMillis(durationMillis);

        assertThat(TimeFormat.formatTime(execution)).isEqualTo(expected);
    }
}
