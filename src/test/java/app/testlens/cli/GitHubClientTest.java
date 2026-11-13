// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.GitHubClient.OpenPullRequest;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitHubClientTest {

    @Test
    void parses_github_shaped_pull_request_payloads_ignoring_unknown_fields() throws IOException {
        // a realistic slice of GET /repos/{owner}/{repo}/pulls — the CLI only needs number, title and draft
        var json = """
            [
              {
                "url": "https://api.github.com/repos/some-org/some-repo/pulls/42",
                "id": 1234,
                "number": 42,
                "title": "Fix the flaky timeout",
                "draft": false,
                "state": "open",
                "user": { "login": "someone" },
                "head": { "ref": "fix-timeout", "sha": "0123456789abcdef" },
                "base": { "ref": "main" },
                "created_at": "2026-11-01T10:00:00Z",
                "updated_at": "2026-11-10T10:00:00Z"
              },
              {
                "number": 43,
                "title": "Upgrade the build cache",
                "draft": true
              }
            ]
            """;

        List<OpenPullRequest> pullRequests = GitHubClient.parsePullRequests(json);

        assertThat(pullRequests).containsExactly(
            new OpenPullRequest(42, "Fix the flaky timeout", false),
            new OpenPullRequest(43, "Upgrade the build cache", true)
        );
    }
}
