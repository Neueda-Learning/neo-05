package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.CreditConfig;
import com.neobank.module.model.CreditRecord;
import com.neobank.module.model.Decision;
import com.neobank.module.repository.CreditConfigRepository;
import com.neobank.module.repository.CreditRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The three things the placeholder does, and the guard that keeps a failure reportable.
 *
 * <p>No Spring, no database, no HTTP — the service takes a request and calls two collaborators, so
 * the test is a handful of lines. Keep it that way as you replace the body: logic that needs a
 * running container to test is logic you will stop testing.</p>
 */
class ApplicationServiceTest {

    private CreditRecordRepository creditRecords;
    private CreditConfigRepository creditConfigs;
    private OrchestratorClient orchestrator;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        creditRecords = mock(CreditRecordRepository.class);
        creditConfigs = mock(CreditConfigRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        // Runnable::run — the work happens inline, so there is nothing to wait for.
        service = new ApplicationService(Runnable::run, creditRecords, creditConfigs, orchestrator);
        when(creditRecords.save(any(CreditRecord.class))).thenAnswer(call -> call.getArgument(0));
        when(creditRecords.findById(any())).thenReturn(Optional.empty());
        when(creditConfigs.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(any()))
                .thenReturn(Optional.of(CreditConfig.of(
                        1,
                        "{\"REWARDS\":{\"minIncome\":18000,\"maxLimit\":5000,\"apr\":12.9}}",
                        BigDecimal.valueOf(0.45),
                        BigDecimal.valueOf(100),
                        7,
                        LocalDateTime.now().minusDays(1))));
    }

    private static ApplicationRequest request(String id) {
        return request(id, 36000, 700, 200, 3000);
    }

    private static ApplicationRequest request(String id,
                                              int annualIncome,
                                              int monthlyHousingCost,
                                              int existingCommitments,
                                              int requestedLimit) {
        Application application = new Application(
                id, "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Maria Nowak", "1996-04-11", null, null, null, null,
                        null, null, null, null, null),
                null,
                null,
                new Application.Finances(annualIncome, monthlyHousingCost, existingCommitments),
                new Application.Product("CREDIT_CARD_REWARDS", requestedLimit),
                null, null);
        return new ApplicationRequest(id, "corr-1", "process-application", application);
    }

    @Test
    void storesOneInProgressRowAndReportsAccepted() {
        CreditRecord inProgress = CreditRecord.inProgress("SIM-01");
        when(creditRecords.findById("SIM-01"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(inProgress));

        service.processApplicationAsync(request("SIM-01"));

        ArgumentCaptor<CreditRecord> saved = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecords, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().getFirst().getApplicationId()).isEqualTo("SIM-01");
        assertThat(saved.getAllValues().getFirst().getOutcome()).isEqualTo("IN_PROGRESS");

        verify(orchestrator).applicationStatusUpdate("SIM-01", Decision.ACCEPTED,
            "CRE_APPROVED");
    }

    @Test
    void repeatedRequestsDoNotCreateAnotherRow() {
        CreditRecord existing = CreditRecord.inProgress("SIM-02");
        when(creditRecords.findById("SIM-02")).thenReturn(Optional.of(existing));

        service.processApplicationAsync(request("SIM-02"));

        verify(creditRecords, never()).save(any(CreditRecord.class));
        verify(orchestrator, never()).applicationStatusUpdate(eq("SIM-02"), any(), any());
    }

    @Test
    void aFailureIsStillReportedRatherThanLeavingTheJourneyToTimeOut() {
        // The failure mode this guard exists for: a module that throws never reports, and the
        // orchestrator then waits out its 30s timeout and ends the journey FAILED with nothing to
        // explain it. REFERRED with a reason is far more useful than silence.
        CreditRecord inProgress = CreditRecord.inProgress("SIM-03");
        when(creditRecords.findById("SIM-03"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(inProgress))
            .thenReturn(Optional.of(inProgress));
        when(creditConfigs.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(any()))
            .thenThrow(new IllegalStateException("database on fire"));

        service.processApplicationAsync(request("SIM-03"));

        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).applicationStatusUpdate(eq("SIM-03"), eq(Decision.REFERRED),
                comment.capture());
        assertThat(comment.getValue()).contains("database on fire");
        verifyNoMoreInteractions(orchestrator);
    }

    @Test
    void theBoardShowsWhatWasStored() {
        CreditRecord row = CreditRecord.inProgress("SIM-01");
        when(creditRecords.findAllByOrderBySubmittedAtDescApplicationIdDesc())
                .thenReturn(java.util.List.of(row));

        assertThat(service.findAll())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.applicationId()).isEqualTo("SIM-01");
                    assertThat(view.status()).isEqualTo("in-progress");
                });
    }

    @Test
    void duplicateAfterDecisionReplaysStoredOutcome() {
        CreditRecord decided = CreditRecord.inProgress("SIM-04");
        decided.markFinal(CreditRecord.STATUS_ACCEPTED, "stored outcome");
        when(creditRecords.findById("SIM-04")).thenReturn(Optional.of(decided));

        service.processApplicationAsync(request("SIM-04"));

        verify(orchestrator).applicationStatusUpdate("SIM-04", Decision.ACCEPTED, "stored outcome");
        verify(creditRecords, never()).save(any(CreditRecord.class));
    }

    @Test
    void lowIncomeIsRejectedOffThread() {
        CreditRecord inProgress = CreditRecord.inProgress("SIM-LOW");
        when(creditRecords.findById("SIM-LOW"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(inProgress));

        service.processApplicationAsync(request("SIM-LOW", 12000, 200, 50, 1500));

        verify(orchestrator).applicationStatusUpdate("SIM-LOW", Decision.REJECTED, "CRE_REJECTED_MIN_INCOME");
    }

    @Test
    void highDtiIsReferredOffThread() {
        CreditRecord inProgress = CreditRecord.inProgress("SIM-DTI");
        when(creditRecords.findById("SIM-DTI"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(inProgress));

        service.processApplicationAsync(request("SIM-DTI", 36000, 2500, 1000, 2000));

        verify(orchestrator).applicationStatusUpdate("SIM-DTI", Decision.REFERRED, "CRE_REFERRED_DTI");
    }

    @Test
    void grantedLimitUsesMonthlyIncomeWhenItIsSmallestCandidate() {
        CreditRecord inProgress = CreditRecord.inProgress("SIM-MONTHLY");
        when(creditRecords.findById("SIM-MONTHLY"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(inProgress));

        service.processApplicationAsync(request("SIM-MONTHLY", 36000, 100, 100, 4000));

        ArgumentCaptor<CreditRecord> saved = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecords, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        CreditRecord decided = saved.getAllValues().getLast();

        assertThat(decided.getMonthlyIncome()).isEqualTo(3000);
        assertThat(decided.getRequestedLimit()).isEqualTo(4000);
        assertThat(decided.getProductMaxLimit()).isEqualTo(5000);
        assertThat(decided.getGrantedLimit()).isEqualTo(3000);
        assertThat(decided.getCapReason()).isEqualTo("CRE_APPROVED");
        verify(orchestrator).applicationStatusUpdate("SIM-MONTHLY", Decision.ACCEPTED, "CRE_APPROVED");
    }

    @Test
    void grantedLimitUsesRequestedLimitWhenItIsSmallestCandidate() {
        CreditRecord inProgress = CreditRecord.inProgress("SIM-REQUEST");
        when(creditRecords.findById("SIM-REQUEST"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(inProgress));

        service.processApplicationAsync(request("SIM-REQUEST", 72000, 100, 100, 2500));

        ArgumentCaptor<CreditRecord> saved = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecords, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        CreditRecord decided = saved.getAllValues().getLast();

        assertThat(decided.getMonthlyIncome()).isEqualTo(6000);
        assertThat(decided.getRequestedLimit()).isEqualTo(2500);
        assertThat(decided.getProductMaxLimit()).isEqualTo(5000);
        assertThat(decided.getGrantedLimit()).isEqualTo(2500);
        assertThat(decided.getCapReason()).isEqualTo("CRE_LIMIT_CAPPED_TO_REQUEST");
        verify(orchestrator).applicationStatusUpdate("SIM-REQUEST", Decision.ACCEPTED,
                "CRE_LIMIT_CAPPED_TO_REQUEST");
    }

    @Test
    void grantedLimitUsesProductMaxWhenItIsSmallestCandidate() {
        CreditRecord inProgress = CreditRecord.inProgress("SIM-MAX");
        when(creditRecords.findById("SIM-MAX"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(inProgress));

        service.processApplicationAsync(request("SIM-MAX", 72000, 100, 100, 9000));

        ArgumentCaptor<CreditRecord> saved = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecords, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        CreditRecord decided = saved.getAllValues().getLast();

        assertThat(decided.getMonthlyIncome()).isEqualTo(6000);
        assertThat(decided.getRequestedLimit()).isEqualTo(9000);
        assertThat(decided.getProductMaxLimit()).isEqualTo(5000);
        assertThat(decided.getGrantedLimit()).isEqualTo(5000);
        assertThat(decided.getCapReason()).isEqualTo("CRE_LIMIT_CAPPED_TO_BAND_MAX");
        verify(orchestrator).applicationStatusUpdate("SIM-MAX", Decision.ACCEPTED,
                "CRE_LIMIT_CAPPED_TO_BAND_MAX");
    }
}
