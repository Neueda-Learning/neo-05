package com.neobank.module.service;

import com.neobank.module.dto.ApplicantView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicantServiceTest {

    private OrchestratorClient orchestrator;
    private ApplicantService service;

    @BeforeEach
    void setUp() {
        orchestrator = mock(OrchestratorClient.class);
        service = new ApplicantService(orchestrator);
    }

    @Test
    void mapsOnlyFieldsRequiredByTheApplicantSidebar() {
        Application application = new Application(
                "app-1301", "WEB", "2026-07-21T21:40:00Z",
                new Application.Applicant(
                        "Daniel Osei", "1987-05-12", "private@example.com", "+441234",
                        "GB", "GB", null, "OWNER", null, 24, 1),
                null,
                new Application.Employment("PERMANENT", "Private Employer", 60),
                new Application.Finances(48000, 1200, 900),
                new Application.Product("CREDIT_CARD_REWARDS", 5000),
                null,
                null);
        when(orchestrator.fetchApplication("app-1301")).thenReturn(application);

        ApplicantView view = service.getApplicant("app-1301");

        assertThat(view.applicant().fullName()).isEqualTo("Daniel Osei");
        assertThat(view.applicant().dateOfBirth()).isEqualTo("1987-05-12");
        assertThat(view.employment().status()).isEqualTo("PERMANENT");
        assertThat(view.finances().annualIncome()).isEqualTo(48000);
        assertThat(view.product().requestedCreditLimit()).isEqualTo(5000);
        verify(orchestrator).fetchApplication("app-1301");
    }

    @Test
    void orchestratorFailureBecomesRetryableApplicantFailure() {
        when(orchestrator.fetchApplication("app-1301"))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> service.getApplicant("app-1301"))
                .isInstanceOf(ApplicantUnavailableException.class)
                .hasMessage("applicant data is temporarily unavailable for app-1301")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
