// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.model;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import app.testlens.cli.client.model.FlakinessStatisticResponse;
import app.testlens.cli.client.model.TestResponse;
import java.util.List;

/**
 * A test as identified by its internal ID, independent of the API resource it originates from: the same test is
 * addressed by its internal ID when entered directly ({@link TestResponse}) or from a ranked statistics entry.
 */
public record TestInfo(long id, String uniqueId, List<String> displayNames) {

    public TestInfo {
        displayNames = List.copyOf(displayNames);
    }

    public static TestInfo of(TestResponse test) {
        return new TestInfo(
            requireNonNull(test.getId()),
            requireNonNull(test.getUniqueId()),
            requireNonNullElse(test.getDisplayNames(), List.of())
        );
    }

    /** Builds the test info of a flakiness statistic via the internal test ID in its {@code test} link. */
    public static TestInfo of(FlakinessStatisticResponse statistic) {
        var links = requireNonNull(statistic.getLinks(), "statistic has no links");
        var test = requireNonNull(links.getTest(), "statistic has no test link");
        var href = requireNonNull(test.getHref());
        var id = Long.parseLong(href.substring(href.lastIndexOf('/') + 1));
        return new TestInfo(
            id,
            requireNonNull(statistic.getUniqueId()),
            requireNonNullElse(statistic.getDisplayNames(), List.of())
        );
    }

    public String displayName() {
        // skip the first display name which denotes the test engine, like in the PR comment and CLI
        var names = displayNames.subList(Math.min(1, displayNames.size()), displayNames.size());
        return String.join(" > ", names);
    }
}
