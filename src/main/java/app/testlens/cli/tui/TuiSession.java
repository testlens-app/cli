// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli.tui;

import app.testlens.cli.client.ApiClient;
import app.testlens.cli.client.api.PullRequestsApi;
import app.testlens.cli.client.api.StatisticsApi;
import app.testlens.cli.client.api.TestsApi;
import org.jspecify.annotations.Nullable;

/**
 * The repository context of one TUI session: the resolved repository, an authenticated API client for the testlens API
 * and the GitHub credentials for the discovery panes. Screens create the API interfaces they need from it on demand.
 */
public record TuiSession(
    String owner, String repo, ApiClient apiClient, String githubUrl, @Nullable String githubToken
) {

    public TestsApi testsApi() {
        return new TestsApi(apiClient);
    }

    public StatisticsApi statisticsApi() {
        return new StatisticsApi(apiClient);
    }

    public PullRequestsApi pullRequestsApi() {
        return new PullRequestsApi(apiClient);
    }
}
