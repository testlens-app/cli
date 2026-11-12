// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui.model;

import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.model.TestResponse;
import java.util.List;

/** A test as identified by its internal ID, as delivered by {@link TestResponse}. */
public record TestInfo(long id, String uniqueId, List<String> displayNames) {

    public TestInfo {
        displayNames = List.copyOf(displayNames);
    }

    public static TestInfo of(TestResponse test) {
        return new TestInfo(requireNonNull(test.getId()), requireNonNull(test.getUniqueId()), test.getDisplayNames());
    }

    public String displayName() {
        // skip the first display name which denotes the test engine, like in the PR comment and CLI
        var names = displayNames.subList(Math.min(1, displayNames.size()), displayNames.size());
        return String.join(" > ", names);
    }
}
