// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The minimal GitHub REST client for the TUI's discovery panes: only what the testlens API cannot serve (the list of
 * open pull requests). Failures are expected and surfaced as "unavailable" panes, never as errors.
 */
public final class GitHubClient {

    /** One open pull request, as shown in the home screen's PR pane. */
    public record OpenPullRequest(long number, String title, boolean draft) {}

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;
    private final @Nullable String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public GitHubClient(String baseUrl, @Nullable String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    /** The repository's open pull requests, newest first as GitHub returns them. */
    public List<OpenPullRequest> openPullRequests(String owner, String repo) throws IOException, InterruptedException {
        var uri = baseUrl + "/repos/" + owner + "/" + repo + "/pulls?state=open&per_page=25";
        var builder = HttpRequest.newBuilder(URI.create(uri))
            .timeout(TIMEOUT)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        var request = builder.GET().build();
        var response = http.send(request, BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub returned HTTP " + response.statusCode());
        }
        return parsePullRequests(response.body());
    }

    static List<OpenPullRequest> parsePullRequests(String json) throws IOException {
        try {
            return List.of(new tools.jackson.databind.json.JsonMapper().readValue(json, OpenPullRequest[].class));
        } catch (tools.jackson.core.JacksonException e) {
            throw new IOException("Parsing the GitHub pull requests failed", e);
        }
    }
}
