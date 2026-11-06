// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import app.testlens.cli.client.ApiClient;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Shared plumbing of the commands: repository resolution, token handling and API client setup. The convention
 * established by {@link PrCommand}: {@code --repo} defaults to the GitHub remote {@code origin} of the current
 * directory, {@code --token} to {@code $GITHUB_TOKEN} or {@code $GH_TOKEN}.
 */
public final class CommandSupport {

    private static final Pattern GITHUB_REMOTE = Pattern.compile("github\\.com[:/]([^/]+)/(.+?)(?:\\.git)?/?$");

    private CommandSupport() {}

    static String @Nullable [] resolveRepo(@Nullable String repoOption) {
        return repoOption != null ? repoOption.split("/", 2) : parseRemote(gitRemoteUrl());
    }

    /** Parses owner/repo from a GitHub remote URL. */
    static String @Nullable [] parseRemote(@Nullable String url) {
        if (url == null) {
            return null;
        }
        var matcher = GITHUB_REMOTE.matcher(url.strip());
        return matcher.find() ? new String[] { matcher.group(1), matcher.group(2) } : null;
    }

    static boolean isInteractiveTerminal() {
        var console = System.console();
        return console != null && console.isTerminal();
    }

    static ApiClient apiClient(String token, String baseUrl) {
        var apiClient = new ApiClient();
        apiClient.updateBaseUri(requireNonNull(baseUrl));
        apiClient.setRequestInterceptor(request -> request.header("Authorization", "Bearer " + token));
        return apiClient;
    }

    /** Display name of a test, skipping the first display name which denotes the test engine. */
    static String testName(List<String> displayNames) {
        return String.join(" > ", displayNames.subList(Math.min(1, displayNames.size()), displayNames.size()));
    }

    private static @Nullable String gitRemoteUrl() {
        try {
            var process = new ProcessBuilder("git", "remote", "get-url", "origin")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            var output = new String(process.getInputStream().readAllBytes(), UTF_8);
            return process.waitFor() == 0 ? output : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
