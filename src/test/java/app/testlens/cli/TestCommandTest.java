// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static app.testlens.cli.TestUtil.run;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.ApiClient;
import app.testlens.cli.client.model.ComparisonFailure;
import app.testlens.cli.client.model.Link;
import app.testlens.cli.client.model.TestExecutionResponse;
import app.testlens.cli.client.model.TestLinks;
import app.testlens.cli.client.model.TestResponse;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import picocli.CommandLine.Help.Ansi;
import tools.jackson.databind.ObjectMapper;

class TestCommandTest {

    private static final String REPO = "some-org/some-repo";

    private static final String TESTS_PATH = "/v1/repos/some-org/some-repo/tests";

    private static final String TEST_PATH = "/v1/repos/some-org/some-repo/tests/1729";

    private static final String EXECUTIONS_PATH = TEST_PATH + "/executions";

    private static final String JOB_URL = "https://github.com/some-org/some-repo/actions/runs/1/job/101";

    private static final String OTHER_JOB_URL = "https://github.com/some-org/some-repo/actions/runs/2/job/201";

    private static final ObjectMapper OBJECT_MAPPER = ApiClient.createDefaultObjectMapper();

    private static final OffsetDateTime START_TIME = OffsetDateTime.parse("2026-01-02T03:04:05Z");

    private static final String UNIQUE_ID = "[engine:junit-jupiter]/[class:NetworkTests]/[method:connects()]";

    private static final Pattern OSC8_LINK = Pattern.compile("\\u001B]8;;(.*?)\\u001B\\\\");

    private static final Pattern ANSI_CODE = Pattern.compile("\\u001B\\[(\\d+)m");

    private static final Map<String, String> ANSI_CODE_NAMES = Map.of(
        "0",
        "reset",
        "1",
        "bold",
        "2",
        "faint",
        "4",
        "underline",
        "7",
        "reverse",
        "24",
        "no-underline",
        "31",
        "red",
        "32",
        "green",
        "33",
        "yellow",
        "34",
        "blue"
    );

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .failOnUnmatchedRequests(true)
        .build();

    @Test
    void prints_executions_with_diagnostics() {
        stubTestAndExecutions(true);

        var result =
            run("test", "1729", "--repo", REPO, "--token", "good", "--mode=cli", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().lines()).containsExactly(
            "NetworkTests > connects() (test 1729)",
            "   [engine:junit-jupiter]/[class:NetworkTests]/[method:connects()]",
            "    1. FAILED (2026-01-02 03:04:05Z - 03:04:07Z (2s), see " + JOB_URL + ")",
            "       :core:test · 0123456 @ main",
            "       - foo bar",
            "       + foo baz",
            "       java.lang.AssertionError: boom",
            "           at Foo.bar(Foo.java:1)",
            "    2. SUCCESSFUL (2026-01-02 04:04:05Z - 04:04:07Z (2s), see " + OTHER_JOB_URL + ")",
            "       :core:testJava21 · 89abcde @ main"
        );
    }

    @Test
    void prints_executions_without_a_job_link() {
        stubTestAndExecutions(false);

        var result =
            run("test", "1729", "--repo", REPO, "--token", "good", "--mode=cli", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).doesNotContain("see ").contains("    1. FAILED (");
    }

    @Test
    void prints_executions_in_ansi() {
        stubTestAndExecutions(true);

        var result = run(
            Ansi.ON,
            "test",
            "1729",
            "--repo",
            REPO,
            "--token",
            "good",
            "--mode=cli",
            "--base-url",
            wireMock.baseUrl()
        );

        assertThat(result.exitCode()).isZero();
        var readable = withReadableAnsiCodes(result.stdout());
        assertThat(readable).contains("<bold>NetworkTests > connects()<reset> (test 1729)");
        assertThat(readable)
            .contains("<faint>[engine:junit-jupiter]/[class:NetworkTests]/[method:connects()]<reset>");
        assertThat(readable).contains("<red>FAILED<reset>");
        assertThat(readable).contains("<red>- <reset><red>foo <reset><red><reverse><bold>bar<reset>");
        assertThat(readable).contains("<faint>:core:test · 0123456 @ main<reset>");
        assertThat(readable).contains("<link " + JOB_URL + "><underline>job<reset></link>");
    }

    @Test
    void prints_search_results_by_query() {
        stubSearch("query", "connects");

        var result =
            run("test", "--repo", REPO, "--token", "good", "--query", "connects", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().lines()).containsExactly(
            "  1. test 1729 NetworkTests > connects()",
            "      [engine:junit-jupiter]/[class:NetworkTests]/[method:connects()]"
        );
    }

    @Test
    void prints_search_results_by_unique_id() {
        stubSearch("uniqueId", UNIQUE_ID);

        var result =
            run("test", "--repo", REPO, "--token", "good", "--unique-id", UNIQUE_ID, "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().lines().toList().get(0)).startsWith("  1. test 1729 NetworkTests > connects()");
    }

    @Test
    void prints_no_tests_found_for_empty_search() {
        stubSearchReturning("[]", "query", "connects");

        var result =
            run("test", "--repo", REPO, "--token", "good", "--query", "connects", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("No tests found.\n");
    }

    @Test
    void unknown_test_exits_nonzero() {
        wireMock.stubFor(get(TEST_PATH).willReturn(notFound()));

        var result = run("test", "1729", "--repo", REPO, "--token", "good", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode()).isOne();
        assertThat(result.stderr()).contains("Test not found");
    }

    @Test
    void unknown_repository_is_reported_for_search() {
        wireMock.stubFor(get(urlPathEqualTo(TESTS_PATH)).willReturn(notFound()));

        var result =
            run("test", "--repo", REPO, "--token", "good", "--query", "oops", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode()).isOne();
        assertThat(result.stderr()).contains("Repository not found");
    }

    @Test
    void rejects_id_together_with_a_search_filter() {
        var result = run(
            "test",
            "1729",
            "--query",
            "oops",
            "--repo",
            REPO,
            "--token",
            "good",
            "--base-url",
            wireMock.baseUrl()
        );

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("either a test id or a search filter");
    }

    @Test
    void requires_an_id_or_a_filter() {
        var result = run("test", "--repo", REPO, "--token", "good", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("Provide a test id, or search tests");
    }

    private void stubTestAndExecutions(boolean withJobUrl) {
        wireMock.stubFor(get(TEST_PATH).willReturn(json(testResponse())));
        stubExecutions(withJobUrl);
    }

    private void stubExecutions(boolean withJobUrl) {
        wireMock
            .stubFor(
                get(urlPathEqualTo(EXECUTIONS_PATH))
                    .willReturn(json(List.of(failedExecution(withJobUrl), successfulExecution(withJobUrl))))
            );
    }

    private void stubSearch(String param, String value) {
        stubSearchReturning(OBJECT_MAPPER.writeValueAsString(List.of(testResponse())), param, value);
    }

    private void stubSearchReturning(String body, String param, String value) {
        wireMock
            .stubFor(
                get(urlPathEqualTo(TESTS_PATH))
                    .withQueryParam(param, equalTo(value))
                    .willReturn(okJson(body))
            );
    }

    private static TestExecutionResponse failedExecution(boolean withJobUrl) {
        var execution = new TestExecutionResponse()
            .outcome(TestExecutionResponse.OutcomeEnum.FAILED)
            .startTime(START_TIME)
            .durationMillis(2000L)
            .sha("0123456789abcdef0123456789abcdef01234567")
            .branch("main")
            .workUnit(":core:test")
            .comparisonFailure(new ComparisonFailure().expected("foo bar").actual("foo baz"))
            .stackTrace("java.lang.AssertionError: boom\n\tat Foo.bar(Foo.java:1)");
        return withJobUrl ? execution.htmlUrl(JOB_URL) : execution;
    }

    private static TestExecutionResponse successfulExecution(boolean withJobUrl) {
        var execution = new TestExecutionResponse()
            .outcome(TestExecutionResponse.OutcomeEnum.SUCCESSFUL)
            .startTime(OffsetDateTime.parse("2026-01-02T04:04:05Z"))
            .durationMillis(2000L)
            .sha("89abcdef0123456789abcdef0123456789abcdef")
            .branch("main")
            .workUnit(":core:testJava21");
        return withJobUrl ? execution.htmlUrl(OTHER_JOB_URL) : execution;
    }

    private static TestResponse testResponse() {
        return new TestResponse()
            .id(1729L)
            .uniqueId(UNIQUE_ID)
            .displayNames(List.of("JUnit Jupiter", "NetworkTests", "connects()"))
            .links(new TestLinks().executions(new Link().href(EXECUTIONS_PATH)));
    }

    private String withReadableAnsiCodes(String text) {
        var withLinks = OSC8_LINK.matcher(text)
            .replaceAll(match -> match.group(1).isEmpty() ? "</link>" : "<link " + match.group(1) + ">");
        return ANSI_CODE.matcher(withLinks.replace("\r\u001B[2K", "<clear>"))
            .replaceAll(match -> "<" + ANSI_CODE_NAMES.getOrDefault(match.group(1), match.group(1)) + ">");
    }

    private static ResponseDefinitionBuilder json(Object body) {
        return okJson(OBJECT_MAPPER.writeValueAsString(body));
    }
}
