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
import app.testlens.cli.client.model.FlakeResponse;
import app.testlens.cli.client.model.Link;
import app.testlens.cli.client.model.TestExecutionResponse;
import app.testlens.cli.client.model.TestLinks;
import app.testlens.cli.client.model.TestResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.ObjectMapper;

class FlakesCommandTest {

    private static final String REPO = "some-org/some-repo";

    private static final String TEST_PATH = "/v1/repos/some-org/some-repo/tests/1729";

    private static final String FLAKES_PATH = TEST_PATH + "/flakes";

    private static final String JOB_URL = "https://github.com/some-org/some-repo/actions/runs/1/job/101";

    private static final ObjectMapper OBJECT_MAPPER = ApiClient.createDefaultObjectMapper();

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .failOnUnmatchedRequests(true)
        .build();

    @Test
    void prints_flakes_with_executions_in_chronological_order() {
        stubTest();
        wireMock.stubFor(
            get(urlPathEqualTo(FLAKES_PATH))
                .withQueryParam("branch", equalTo("main"))
                .willReturn(json(List.of(flake("0123456789", ":core:test"))))
        );

        var result = run(
            "flakes",
            "1729",
            "--branch",
            "main",
            "--repo",
            REPO,
            "--token",
            "good",
            "--mode=cli",
            "--base-url",
            wireMock.baseUrl()
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().lines()).containsExactly(
            "NetworkTests > connects() (test 1729) · branch main",
            "   [engine:junit-jupiter]/[class:NetworkTests]/[method:connects()]",
            "   newest flake first, executions in chronological order",
            "",
            "  1. 0123456 @ main · :core:test",
            "      1. FAILED (2026-01-02 03:04:05Z - 03:04:07Z (2s), see " + JOB_URL + ")",
            "         java.lang.AssertionError: boom",
            "      2. SUCCESSFUL (2026-01-02 04:04:05Z - 04:04:07Z (2s))"
        );
    }

    @Test
    void follows_pagination() {
        stubTest();
        wireMock.stubFor(
            get(urlPathEqualTo(FLAKES_PATH))
                .withQueryParam("page", equalTo("1"))
                .willReturn(
                    json(List.of(flake("1111111111", ":first")))
                        .withHeader("Link", "<" + FLAKES_PATH + "?page=2>; rel=\"next\"")
                )
        );
        wireMock.stubFor(
            get(urlPathEqualTo(FLAKES_PATH))
                .withQueryParam("page", equalTo("2"))
                .willReturn(json(List.of(flake("2222222222", ":second"))))
        );

        var result =
            run("flakes", "1729", "--repo", REPO, "--token", "good", "--mode=cli", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("  1. 1111111 @ main · :first", "  2. 2222222 @ main · :second");
    }

    @Test
    void reports_a_missing_test() {
        wireMock.stubFor(get(TEST_PATH).willReturn(notFound()));

        var result =
            run("flakes", "1729", "--repo", REPO, "--token", "good", "--mode=cli", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("Test not found");
    }

    private void stubTest() {
        wireMock.stubFor(
            get(TEST_PATH).willReturn(
                json(
                    new TestResponse()
                        .id(1729L)
                        .uniqueId("[engine:junit-jupiter]/[class:NetworkTests]/[method:connects()]")
                        .displayNames(List.of("JUnit Jupiter", "NetworkTests", "connects()"))
                        .links(new TestLinks().executions(new Link().href(TEST_PATH + "/executions")))
                )
            )
        );
    }

    // the executions are deliberately newest first, to verify they are printed chronologically
    private static FlakeResponse flake(String sha, String workUnit) {
        return new FlakeResponse()
            .sha(sha)
            .branch("main")
            .workUnit(workUnit)
            .executions(
                List.of(
                    execution(TestExecutionResponse.OutcomeEnum.SUCCESSFUL, "2026-01-02T04:04:05Z", sha, workUnit),
                    execution(TestExecutionResponse.OutcomeEnum.FAILED, "2026-01-02T03:04:05Z", sha, workUnit)
                        .htmlUrl(JOB_URL)
                        .stackTrace("java.lang.AssertionError: boom")
                )
            );
    }

    private static TestExecutionResponse execution(
        TestExecutionResponse.OutcomeEnum outcome,
        String startTime,
        String sha,
        String workUnit
    ) {
        return new TestExecutionResponse()
            .outcome(outcome)
            .startTime(OffsetDateTime.parse(startTime))
            .durationMillis(2000L)
            .sha(sha)
            .branch("main")
            .workUnit(workUnit);
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(Object body) {
        return okJson(OBJECT_MAPPER.writeValueAsString(body));
    }
}
