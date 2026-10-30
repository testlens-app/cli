// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static app.testlens.cli.StatisticsCommand.printStatistics;
import static app.testlens.cli.StatisticsCommand.severityIcon;
import static app.testlens.cli.TestUtil.run;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.ApiClient;
import app.testlens.cli.client.model.FailedCount;
import app.testlens.cli.client.model.FlakinessStatisticResponse;
import app.testlens.cli.client.model.FlakyCount;
import app.testlens.cli.client.model.Link;
import app.testlens.cli.client.model.StatisticLinks;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import picocli.CommandLine.Help.Ansi;
import tools.jackson.databind.ObjectMapper;

class StatisticsCommandTest {

    private static final String REPO = "some-org/some-repo";

    private static final String FLAKINESS_PATH = "/v1/repos/some-org/some-repo/statistics/flakiness";

    private static final ObjectMapper OBJECT_MAPPER = ApiClient.createDefaultObjectMapper();

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .failOnUnmatchedRequests(true)
        .build();

    @Test
    void prints_ranked_statistics_with_ids_and_severity_icons() {
        stubFlakiness(
            statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 200L, 10, Severity.HIGH, 1L),
            statistic(1703, "CalculatorTests", "adds()", ":core:test", 4L, 200L, 2, Severity.LOW, 2L)
        );

        var result =
            run(
                "statistics",
                "flakiness",
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
            "Flakiness ranking",
            "  1. 🔴 NetworkTests > connects()",
            "      work unit :core:test · flaky 20/200 (10%) · failed 1 · test 1729",
            "",
            "  2. 🟡 CalculatorTests > adds()",
            "      work unit :core:test · flaky 4/200 (2%) · failed 2 · test 1703"
        );
    }

    @Test
    void prints_the_requested_branch() {
        stubFlakiness(statistic(1729, "NetworkTests", "connects()", ":core:test", 20L, 200L, 10, Severity.HIGH, 1L));

        var result = run(
            "statistics",
            "flakiness",
            "--branch",
            "release/1.0",
            "--repo",
            REPO,
            "--token",
            "good",
            "--mode=cli",
            "--base-url",
            wireMock.baseUrl()
        );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).startsWith("Flakiness ranking · branch release/1.0");
    }

    @Test
    void prints_a_hint_for_a_branch_without_statistics() {
        stubFlakiness();

        var result =
            run(
                "statistics",
                "flakiness",
                "--repo",
                REPO,
                "--token",
                "good",
                "--mode=cli",
                "--base-url",
                wireMock.baseUrl()
            );

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("Flakiness ranking\n\nNo flaky or failing tests.\n");
    }

    @Test
    void unknown_repository_exits_nonzero() {
        wireMock.stubFor(get(urlPathEqualTo(FLAKINESS_PATH)).willReturn(notFound()));

        var result =
            run("statistics", "flakiness", "--repo", REPO, "--token", "good", "--base-url", wireMock.baseUrl());

        assertThat(result.exitCode()).isOne();
        assertThat(result.stderr()).contains("Repository not found");
    }

    /** The severity icon mirrors the Test Dashboard icons of the statistics model, including NONE being green. */
    @Test
    void severity_icons_follow_the_dashboard() {
        assertThat(severityIcon(null)).isEqualTo(" ");
        assertThat(severityIcon(FlakyCount.SeverityEnum.NONE)).isEqualTo("🟢");
        assertThat(severityIcon(FlakyCount.SeverityEnum.LOW)).isEqualTo("🟡");
        assertThat(severityIcon(FlakyCount.SeverityEnum.MEDIUM)).isEqualTo("🟠");
        assertThat(severityIcon(FlakyCount.SeverityEnum.HIGH)).isEqualTo("🔴");
    }

    @Test
    void prints_empty_ranking_via_cli_formatting() {
        var plain = new java.io.StringWriter();
        printStatistics(new java.io.PrintWriter(plain, true), Ansi.OFF, "main", List.of());
        assertThat(plain.toString()).isEqualTo("Flakiness ranking · branch main\n\nNo flaky or failing tests.\n");
    }

    private void stubFlakiness(FlakinessStatisticResponse... statistics) {
        wireMock.stubFor(
            get(urlPathEqualTo(FLAKINESS_PATH)).willReturn(json(List.of(statistics)))
        );
    }

    private enum Severity {
        NONE,
        LOW,
        MEDIUM,
        HIGH
    }

    private static FlakinessStatisticResponse statistic(
        long id,
        String className,
        String method,
        String workUnit,
        Long flakyCount,
        Long total,
        Integer percentage,
        Severity severity,
        Long failedCount
    ) {
        return new FlakinessStatisticResponse()
            .uniqueId("[engine:junit-jupiter]/[class:" + className + "]/[method:" + method + "]")
            .displayNames(List.of("JUnit Jupiter", className, method))
            .workUnit(workUnit)
            .failed(new FailedCount().count(failedCount).total(200L).percentage(1))
            .flaky(
                new FlakyCount().count(flakyCount)
                    .total(total)
                    .percentage(percentage)
                    .severity(FlakyCount.SeverityEnum.valueOf(severity.name()))
            )
            .links(new StatisticLinks().test(new Link().href("/v1/repos/some-org/some-repo/tests/" + id)));
    }

    private static ResponseDefinitionBuilder json(Object body) {
        return okJson(OBJECT_MAPPER.writeValueAsString(body));
    }
}
