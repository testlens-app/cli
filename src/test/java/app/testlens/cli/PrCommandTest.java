// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.ApiClient;
import app.testlens.cli.client.model.ComparisonFailure;
import app.testlens.cli.client.model.ExecutedTest;
import app.testlens.cli.client.model.Execution;
import app.testlens.cli.client.model.Execution.OutcomeEnum;
import app.testlens.cli.client.model.JobStatus;
import app.testlens.cli.client.model.PullRequestResponse;
import app.testlens.cli.client.model.PullRequestTestsResponse;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Help.Ansi;
import tools.jackson.databind.ObjectMapper;

class PrCommandTest {

    private static final String PR_PATH = "/v1/repos/some-org/some-repo/pulls/42";

    private static final ObjectMapper OBJECT_MAPPER = ApiClient.createDefaultObjectMapper();

    private static final String JOB_URL = "https://github.com/some-org/some-repo/actions/runs/1/job/101?pr=42";

    private static final OffsetDateTime START_TIME = OffsetDateTime.parse("2026-01-02T03:04:05Z");

    private static final Pattern OSC8_LINK = Pattern.compile("\u001B]8;;(.*?)\u001B\\\\");

    private static final Pattern ANSI_CODE = Pattern.compile("\u001B\\[(\\d+)m");

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
    void prints_pr_info_and_tests_with_stack_traces() {
        stubPullRequestWithTests("SHA");

        var result = run("42", "--repo", "some-org/some-repo", "--token", "good", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode).isZero();
        assertThat(result.stdout.lines()).containsExactly(
            "Some PR (#42)",
            "Head:  SHA",
            "Tests: 7 executed",
            "Jobs:  2/3 completed",
            "",
            "Commit: SHA",
            "",
            "CI / build > :test",
            "  FooTests > bar()",
            "    1. FAILED (2026-01-02 03:04:05Z - 03:04:06Z (1s 500ms), see " + JOB_URL + ")",
            "       - foo bar",
            "       + foo baz",
            "       java.lang.AssertionError: boom",
            "           at Foo.bar(Foo.java:1)",
            "       Caused by: java.lang.IllegalStateException: first",
            "       second",
            "           ... 1 more",
            "    2. SKIPPED (2026-01-02 03:04:05Z, see " + JOB_URL + ")"
        );
        assertThat(result.stdout).doesNotContain("build-tool");
    }

    @Test
    void colors_title_test_header_and_outcomes() {
        stubPullRequestWithTests("SHA");

        var result = run(
            Ansi.ON,
            "42",
            "--repo",
            "some-org/some-repo",
            "--token",
            "good",
            "--base-url",
            wireMock.baseUrl()
        );

        assertThat(result.exitCode).isZero();
        assertThat(withReadableAnsiCodes(result.stdout).lines()).containsExactly(
            "<faint>Fetching pull request...<reset><clear><bold><blue>Some PR (<link https://github.com/some-org/some-repo/pull/42><underline>#42<no-underline></link>)<reset>",
            "Head:  SHA",
            "Tests: 7 executed",
            "Jobs:  2/3 completed",
            "",
            "<faint>Fetching tests...<reset><clear>Commit: SHA",
            "",
            "<underline>CI / build > :test<reset>",
            "  <bold>FooTests > bar()<reset>",
            "    1. <red>FAILED<reset> (2026-01-02 03:04:05Z - 03:04:06Z (1s 500ms), see <link " + JOB_URL
                + "><underline>job<reset></link>)",
            "       <red>- <reset><red>foo <reset><red><reverse><bold>bar<reset>",
            "       <green>+ <reset><green>foo <reset><green><reverse><bold>baz<reset>",
            "       java.lang.AssertionError: boom",
            "       <faint>    at Foo.bar(Foo.java:1)<reset>",
            "       Caused by: java.lang.IllegalStateException: first",
            "       second",
            "       <faint>    ... 1 more<reset>",
            "    2. <yellow>SKIPPED<reset> (2026-01-02 03:04:05Z, see <link " + JOB_URL
                + "><underline>job<reset></link>)"
        );
    }

    @Test
    void shows_tests_of_given_sha() {
        stubPullRequestWithTests("OTHER");

        var result = run(
            "42",
            "--repo",
            "some-org/some-repo",
            "--sha",
            "OTHER",
            "--token",
            "good",
            "--base-url",
            wireMock.baseUrl()
        );

        assertThat(result.exitCode).isZero();
        assertThat(result.stdout.lines()).contains("Head:  SHA", "Commit: OTHER", "  FooTests > bar()");
    }

    @Test
    void unknown_pr_exits_nonzero() {
        wireMock.stubFor(get(PR_PATH).willReturn(notFound()));

        var result = run("42", "--repo", "some-org/some-repo", "--token", "good", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode).isOne();
        assertThat(result.stderr).contains("Pull request not found");
        assertThat(result.stdout).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "git@github.com:some-org/some-repo.git",
            "git@github.com:some-org/some-repo",
            "https://github.com/some-org/some-repo.git",
            "https://github.com/some-org/some-repo\n",
        }
    )
    void parses_github_remotes(String url) {
        assertThat(PrCommand.parseRemote(url)).containsExactly("some-org", "some-repo");
    }

    @Test
    void ignores_non_github_remotes() {
        assertThat(PrCommand.parseRemote("https://gitlab.com/some-org/some-repo.git")).isNull();
    }

    private static String withReadableAnsiCodes(String text) {
        var withLinks = OSC8_LINK.matcher(text)
            .replaceAll(match -> match.group(1).isEmpty() ? "</link>" : "<link " + match.group(1) + ">");
        return ANSI_CODE.matcher(withLinks.replace("\r\u001B[2K", "<clear>"))
            .replaceAll(match -> "<" + ANSI_CODE_NAMES.getOrDefault(match.group(1), match.group(1)) + ">");
    }

    private static void stubPullRequestWithTests(String testsSha) {
        wireMock.stubFor(
            get(PR_PATH)
                .withHeader("Authorization", equalTo("Bearer good"))
                .willReturn(
                    json(
                        new PullRequestResponse()
                            .number(42)
                            .title("Some PR")
                            .headSha("SHA")
                            .testCount(7L)
                            .jobs(new JobStatus().completed(2L).total(3L))
                    )
                )
        );
        wireMock.stubFor(
            get(urlPathEqualTo(PR_PATH + "/tests"))
                .withQueryParam("sha", equalTo(testsSha))
                .withHeader("Authorization", equalTo("Bearer good"))
                .willReturn(
                    json(
                        new PullRequestTestsResponse()
                            .sha(testsSha)
                            .addTestsItem(
                                new ExecutedTest()
                                    .job("CI / build")
                                    .workUnit(":test")
                                    .uniqueId("[engine:junit-jupiter]")
                                    .displayNames(List.of("JUnit Jupiter", "FooTests", "bar()"))
                                    .addExecutionsItem(
                                        new Execution()
                                            .outcome(OutcomeEnum.FAILED)
                                            .startTime(START_TIME)
                                            .durationMillis(1500L)
                                            .htmlUrl(JOB_URL)
                                            .comparisonFailure(
                                                new ComparisonFailure().expected("foo bar").actual("foo baz")
                                            )
                                            .stackTrace(
                                                """
                                                java.lang.AssertionError: boom
                                                \tat Foo.bar(Foo.java:1)
                                                Caused by: java.lang.IllegalStateException: first
                                                second
                                                \t... 1 more
                                                """
                                            )
                                    )
                                    .addExecutionsItem(
                                        new Execution()
                                            .outcome(OutcomeEnum.SKIPPED)
                                            .startTime(START_TIME)
                                            .durationMillis(null)
                                            .htmlUrl(JOB_URL)
                                    )
                            )
                    )
                )
        );
    }

    @Test
    void prints_plain_output_in_cli_mode() {
        stubPullRequestWithTests("SHA");

        var result = run(
            "42",
            "--repo",
            "some-org/some-repo",
            "--token",
            "good",
            "--mode=cli",
            "--base-url",
            wireMock.baseUrl()
        );

        assertThat(result.exitCode).isZero();
        assertThat(result.stdout).startsWith("Some PR (#42)").contains("FooTests > bar()");
    }

    private static ResponseDefinitionBuilder json(Object body) {
        return okJson(OBJECT_MAPPER.writeValueAsString(body));
    }

    private static Result run(String... args) {
        return run(Ansi.OFF, args);
    }

    private static Result run(Ansi ansi, String... args) {
        var out = new StringWriter();
        var err = new StringWriter();
        var exitCode = new CommandLine(new PrCommand())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .setOut(new PrintWriter(out, true))
            .setErr(new PrintWriter(err, true))
            .setColorScheme(Help.defaultColorScheme(ansi))
            .execute(args);
        return new Result(exitCode, out.toString(), err.toString());
    }

    private record Result(int exitCode, String stdout, String stderr) {}
}
