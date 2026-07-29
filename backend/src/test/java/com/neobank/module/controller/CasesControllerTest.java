package com.neobank.module.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.ApplicantViewDto;
import com.neobank.module.dto.CaseView;
import com.neobank.module.service.UnprocessableCaseOverrideException;
import com.neobank.module.service.ApplicationService;
import com.neobank.module.service.ApplicantUnavailableException;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CasesController.class)
class CasesControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ApplicationService applications;

    @Test
    void returnsFullWorkingsForAKnownCase() throws Exception {
        CaseView view = new CaseView(
                "ACCEPTED",
                "ACCEPTED",
                "cre-000517",
                1,
                new CaseView.WorkingsView(
                        34000, 2833, 1180,
                        new BigDecimal("0.42"), new BigDecimal("0.45"),
                        2833, 10000, 3000, 2800,
                        new BigDecimal("24.9"), null, "CRE_APPROVED", "PREMIUM", 20000),
                new CaseView.SamplingView(false));

        when(applications.getCase("app-1234")).thenReturn(view);

        mvc.perform(get("/cases/app-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.machineOutcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.reference").value("cre-000517"))
                .andExpect(jsonPath("$.creditConfigVersion").value(1))
                .andExpect(jsonPath("$.workings.annualIncome").value(34000))
                .andExpect(jsonPath("$.workings.monthlyIncome").value(2833))
                .andExpect(jsonPath("$.workings.monthlyOutgoings").value(1180))
                .andExpect(jsonPath("$.workings.dti").value(0.42))
                .andExpect(jsonPath("$.workings.dtiLimit").value(0.45))
                .andExpect(jsonPath("$.workings.incomeBasisLimit").value(2833))
                .andExpect(jsonPath("$.workings.productMaxLimit").value(10000))
                .andExpect(jsonPath("$.workings.requestedLimit").value(3000))
                .andExpect(jsonPath("$.workings.grantedLimit").value(2800))
                .andExpect(jsonPath("$.workings.apr").value(24.9))
                .andExpect(jsonPath("$.workings.capReason").doesNotExist())
                .andExpect(jsonPath("$.sampling.sampled").value(false));
    }

    @Test
    void returns404WithJsonBodyForUnknownCase() throws Exception {
        when(applications.getCase("unknown-id"))
                .thenThrow(new NoSuchElementException("case not found: unknown-id"));

        mvc.perform(get("/cases/unknown-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("case not found: unknown-id"));
    }

    @Test
    void applicantEndpointReturnsFlatViewDto() throws Exception {
        ApplicantViewDto dto = new ApplicantViewDto(
                "Daniel Osei", "1987-05-12", "PERMANENT",
                new ApplicantViewDto.FinancesView(48000, 1200, 900),
                5000);
        when(applications.getApplicant("app-1301")).thenReturn(dto);

        mvc.perform(get("/cases/app-1301/applicant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Daniel Osei"))
                .andExpect(jsonPath("$.dateOfBirth").value("1987-05-12"))
                .andExpect(jsonPath("$.employmentStatus").value("PERMANENT"))
                .andExpect(jsonPath("$.finances.annualIncome").value(48000))
                .andExpect(jsonPath("$.finances.monthlyHousingCost").value(1200))
                .andExpect(jsonPath("$.finances.existingCreditCommitments").value(900))
                .andExpect(jsonPath("$.requestedCreditLimit").value(5000));
    }

    @Test
    void unavailableOrchestratorReturnsServiceUnavailable() throws Exception {
        when(applications.getApplicant("app-1301"))
                .thenThrow(new ApplicantUnavailableException(
                        "app-1301", new RuntimeException("connection refused")));

        mvc.perform(get("/cases/app-1301/applicant"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("applicant data is temporarily unavailable for app-1301"));
    }

    @Test
    void overrideReturnsUpdatedCase() throws Exception {
        CaseView view = new CaseView(
                "ACCEPTED",
                "REJECTED",
                "cre-000517",
                1,
                new CaseView.WorkingsView(
                        34000, 2833, 1180,
                        new BigDecimal("0.42"), new BigDecimal("0.45"),
                        2833, 10000, 3000, 2800,
                        new BigDecimal("24.9"), null,
                        "income evidenced at 34k", "PREMIUM", 20000),
                new CaseView.SamplingView(false));

        when(applications.overrideCase(org.mockito.ArgumentMatchers.eq("app-1234"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(view);

        mvc.perform(post("/cases/app-1234/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newOutcome":"APPROVED","grantedLimit":2800,
                                "reason":"income evidenced at 34k","operator":"b.dimovski"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.machineOutcome").value("REJECTED"))
                .andExpect(jsonPath("$.workings.grantedLimit").value(2800))
                .andExpect(jsonPath("$.workings.decisionReason").value("income evidenced at 34k"));
    }

    @Test
    void overrideRequiresReason() throws Exception {
        mvc.perform(post("/cases/app-1234/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newOutcome":"APPROVED","grantedLimit":2800,
                                "reason":"","operator":"b.dimovski"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("reason must not be blank"));
    }

    @Test
    void overrideReturns404ForUnknownCase() throws Exception {
        when(applications.overrideCase(org.mockito.ArgumentMatchers.eq("unknown-id"), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new NoSuchElementException("case not found: unknown-id"));

        mvc.perform(post("/cases/unknown-id/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newOutcome":"DECLINED",
                                "reason":"new information contradicts the original approval",
                                "operator":"b.dimovski"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("case not found: unknown-id"));
    }

    @Test
    void overrideReturns422WhenApprovalExceedsStoredBasis() throws Exception {
        when(applications.overrideCase(org.mockito.ArgumentMatchers.eq("app-1234"), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new UnprocessableCaseOverrideException(
                        "grantedLimit must not exceed stored three-way minimum of 2800"));

        mvc.perform(post("/cases/app-1234/override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newOutcome":"APPROVED","grantedLimit":2900,
                                "reason":"income evidenced at 34k","operator":"b.dimovski"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value("grantedLimit must not exceed stored three-way minimum of 2800"));
    }
}
