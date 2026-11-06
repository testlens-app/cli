// SPDX-License-Identifier: Apache-2.0
package app.testlens.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import app.testlens.cli.client.ApiClient;
import app.testlens.cli.client.api.TestsApi;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.ObjectMapper;

class ExecutionsLoaderTest {

    private static final String EXECUTIONS_PATH = "/v1/repos/some-org/some-repo/tests/1729/executions";

    private static final ObjectMapper OBJECT_MAPPER = ApiClient.createDefaultObjectMapper();

    private static final OffsetDateTime START_TIME = OffsetDateTime.parse("2026-01-02T03:04:05Z");

    private static final String LINK = "<" + EXECUTIONS_PATH + "?page=2>; rel=\"next\"";

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Test
    void fetches_pages_following_the_link_header() throws Exception {
        // page 1 carries a rel="next" link, page 2 is the last page (no link)
        wireMock.stubFor(
            get(urlPathEqualTo(EXECUTIONS_PATH)).inScenario("pages")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(
                    aJsonPage(List.of(execution(Outcome.FAILED)), LINK)
                )
                .willSetStateTo("second")
        );
        wireMock.stubFor(
            get(urlPathEqualTo(EXECUTIONS_PATH)).inScenario("pages")
                .whenScenarioStateIs("second")
                .willReturn(aJsonPage(List.of(execution(Outcome.ABORTED)), null))
        );

        var loader = new ExecutionsLoader(new TestsApi(apiClient()), "some-org", "some-repo", 1729L);

        var first = loader.first();
        assertThat(first.executions()).hasSize(1);
        assertThat(first.hasMore()).isTrue();

        var second = loader.next();
        assertThat(second).isNotNull();
        assertThat(second.executions()).hasSize(1);
        assertThat(second.hasMore()).isFalse();

        // the loader follows the link: the initial fetch excludes successful runs and pages via the page parameter
        wireMock.verify(
            getRequestedFor(urlPathEqualTo(EXECUTIONS_PATH))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("outcome", equalTo("SKIPPED"))
                .withQueryParam("outcome", equalTo("FAILED"))
                .withQueryParam("outcome", equalTo("ABORTED"))
                .withQueryParam("outcome", equalTo("MUTED"))
        );
        wireMock.verify(getRequestedFor(urlPathEqualTo(EXECUTIONS_PATH)).withQueryParam("page", equalTo("2")));

        assertThat(loader.next()).isNull();
    }

    enum Outcome {
        FAILED,
        ABORTED
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder aJsonPage(
        List<app.testlens.cli.client.model.TestExecutionResponse> executions,
        @org.jspecify.annotations.Nullable String link
    ) {
        var response = com.github.tomakehurst.wiremock.client.WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(OBJECT_MAPPER.writeValueAsString(executions));
        return link == null ? response : response.withHeader("Link", link);
    }

    private static app.testlens.cli.client.model.TestExecutionResponse execution(Outcome outcome) {
        return new app.testlens.cli.client.model.TestExecutionResponse()
            .outcome(app.testlens.cli.client.model.TestExecutionResponse.OutcomeEnum.valueOf(outcome.name()))
            .startTime(START_TIME)
            .durationMillis(2000L)
            .sha("0123456789abcdef0123456789abcdef01234567")
            .branch("main")
            .workUnit(":core:test");
    }

    private static ApiClient apiClient() {
        var apiClient = new ApiClient();
        apiClient.updateBaseUri(wireMock.baseUrl());
        apiClient.setRequestInterceptor(request -> request.header("Authorization", "Bearer good"));
        return apiClient;
    }
}
