// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import app.testlens.cli.client.ApiException;
import app.testlens.cli.client.api.TestsApi;
import app.testlens.cli.client.model.TestExecutionResponse;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Loads the pages of the executions endpoint for one test, excluding successful executions: the detail view is a
 * triage view, and flaky tests execute successfully many times, crowding out the failures. The loaded pages follow the
 * server's {@code rel="next"} links, one page per {@code next()} call.
 */
public final class ExecutionsLoader {

    /**
     * Raw outcome filter of the triage view: everything that is not a successful (or reused-successful) execution.
     * Shared by the detail view's initial fetch and its page loads.
     */
    public static final List<String> NON_SUCCESSFUL_OUTCOMES = List.of("FAILED", "ABORTED", "MUTED", "SKIPPED");

    /** One fetched page: the executions of that page and whether another page may follow. */
    public record Fetched(List<TestExecutionResponse> executions, boolean hasMore) {}

    private final TestsApi api;
    private final String owner;
    private final String repo;
    private final long testId;
    private int nextPage;
    private boolean hasMore;

    public ExecutionsLoader(TestsApi api, String owner, String repo, long testId) {
        this.api = api;
        this.owner = owner;
        this.repo = repo;
        this.testId = testId;
    }

    /** Fetches the first page; resets the loader, so it can also be re-called after a back-navigation. */
    public Fetched first() throws ApiException {
        return fetch(1);
    }

    /** Fetches the next page, or {@code null} when the last page was already loaded. */
    public @Nullable Fetched next() throws ApiException {
        return hasMore ? fetch(nextPage) : null;
    }

    private Fetched fetch(int page) throws ApiException {
        nextPage = page + 1;
        var response = api
            .getTestExecutionsWithHttpInfo(
                owner,
                repo,
                testId,
                NON_SUCCESSFUL_OUTCOMES,
                null,
                null,
                null,
                page,
                null,
                Map.of()
            );
        hasMore = hasRelNext(response.getHeaders());
        return new Fetched(response.getData() == null ? List.of() : response.getData(), hasMore);
    }

    public static boolean hasRelNext(Map<String, List<String>> headers) {
        return headers.entrySet()
            .stream()
            .filter(header -> header.getKey().equalsIgnoreCase("Link"))
            .flatMap(header -> header.getValue().stream())
            .anyMatch(ExecutionsLoader::hasRelNext);
    }

    private static boolean hasRelNext(String linkHeader) {
        return linkHeader.matches(".*rel\\s*=\\s*\"?next\"?.*");
    }
}
