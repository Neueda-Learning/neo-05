package com.neobank.module.integrations.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class OrchestratorClientTest {

    private MockRestServiceServer server;
    private OrchestratorClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OrchestratorClient(builder.build(), "neo05", "http://orchestrator:8080");
    }

    @Test
    void resolvesApplicantNameToApplicationIds() {
        server.expect(once(), requestTo(
                        "http://orchestrator:8080/api/v1/applications?name=Maria%20Nowak"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[\"app-1234\",\"app-5678\"]", MediaType.APPLICATION_JSON));

        List<String> result = client.resolveApplicationIdsByName("  Maria Nowak  ");

        assertThat(result).containsExactly("app-1234", "app-5678");
        server.verify();
    }

    @Test
    void blankNameReturnsNoIdsWithoutCallingOrchestrator() {
        assertThat(client.resolveApplicationIdsByName("  ")).isEmpty();
        server.verify();
    }

    @Test
    void orchestratorFailureDegradesToNoMatches() {
        server.expect(once(), requestTo(
                        "http://orchestrator:8080/api/v1/applications?name=Maria"))
                .andExpect(method(GET))
                .andRespond(withServerError());

        assertThat(client.resolveApplicationIdsByName("Maria")).isEmpty();
        server.verify();
    }

    @Test
    void fetchesWholeApplicationByUnchangedId() {
        server.expect(once(), requestTo(
                        "http://orchestrator:8080/api/v1/applications/app-1301"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "applicationId": "app-1301",
                          "applicant": {"fullName": "Daniel Osei", "dateOfBirth": "1987-05-12"},
                          "employment": {"status": "PERMANENT"},
                          "finances": {
                            "annualIncome": 48000,
                            "monthlyHousingCost": 1200,
                            "existingCreditCommitments": 900
                          },
                          "product": {"requestedCreditLimit": 5000}
                        }
                        """, MediaType.APPLICATION_JSON));

        Application application = client.fetchApplication("app-1301");

        assertThat(application.applicationId()).isEqualTo("app-1301");
        assertThat(application.applicant().fullName()).isEqualTo("Daniel Osei");
        assertThat(application.finances().annualIncome()).isEqualTo(48000);
        server.verify();
    }
}
