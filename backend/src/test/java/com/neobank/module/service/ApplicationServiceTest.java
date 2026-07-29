package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.neobank.module.model.OverrideLog;
import com.neobank.module.repository.CreditConfigRepository;
import com.neobank.module.repository.CreditRecordRepository;
import com.neobank.module.repository.OverrideLogRepository;
import com.neobank.module.dto.OverrideCaseRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

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
        private OverrideLogRepository overrideLogs;
    private OrchestratorClient orchestrator;
    private CreditRecordAcceptanceService acceptance;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        creditRecords = mock(CreditRecordRepository.class);
        creditConfigs = mock(CreditConfigRepository.class);
        overrideLogs = mock(OverrideLogRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        acceptance = mock(CreditRecordAcceptanceService.class);
        // Runnable::run — the work happens inline, so there is nothing to wait for.
        service = new ApplicationService(
                Runnable::run, acceptance, creditRecords, creditConfigs, overrideLogs, orchestrator);
        when(creditRecords.save(any(CreditRecord.class))).thenAnswer(call -> call.getArgument(0));
        when(overrideLogs.save(any(OverrideLog.class))).thenAnswer(call -> call.getArgument(0));
        when(overrideLogs.findByApplicationIdOrderByOverriddenAtDescIdDesc(any()))
                .thenReturn(List.of());
        when(overrideLogs.findFirstByApplicationIdOrderByOverriddenAtDescIdDesc(any()))
                .thenReturn(Optional.empty());
        when(creditRecords.findById(any())).thenReturn(Optional.empty());
        when(creditConfigs.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(any(Instant.class)))
                .thenReturn(Optional.of(CreditConfig.of(
                        1,
                        "{\"REWARDS\":{\"minIncome\":18000,\"maxLimit\":5000,\"apr\":12.9}}",
                        BigDecimal.valueOf(0.45),
                        BigDecimal.valueOf(100),
                        7,
                        Instant.now().minusSeconds(24 * 60 * 60))));
        when(creditConfigs.findFirstByVersionOrderByEffectiveFromDescConfigIdDesc(1))
                .thenReturn(Optional.of(CreditConfig.of(
                        1,
                        "{\"REWARDS\":{\"minIncome\":18000,\"maxLimit\":5000,\"apr\":12.9}}",
                        BigDecimal.valueOf(0.45),
                        BigDecimal.valueOf(100),
                        7,
                        Instant.now().minusSeconds(24 * 60 * 60))));
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
    void commitsTheInProgressHandoffBeforeProcessingAndReportsAccepted() {
        CreditRecord inProgress = CreditRecord.inProgress("SIM-01");
        when(creditRecords.findById("SIM-01"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(inProgress));

        service.processApplicationAsync(request("SIM-01"));

        ArgumentCaptor<CreditRecord> saved = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecords, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        verify(acceptance).insertInProgress("SIM-01");
        assertThat(saved.getAllValues().getLast().getApplicationId()).isEqualTo("SIM-01");
        assertThat(saved.getAllValues().getLast().getOutcome()).isEqualTo("ACCEPTED");

        verify(orchestrator).applicationStatusUpdate("SIM-01", Decision.ACCEPTED,
            "CRE_APPROVED");
    }

    @Test
    void policyEditorProfileRowsAreUsedWithoutParsingTheProfileNameAsJson() {
        CreditRecord inProgress = CreditRecord.inProgress("SIM-PROFILE");
        CreditConfig selected = CreditConfig.of(
                4,
                "PLATINUM",
                new BigDecimal("0.45"),
                new BigDecimal("100"),
                7,
                Instant.now()).withProductTermColumns(
                        "CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("9.9"));
        CreditConfig rewardsTerms = CreditConfig.of(
                4,
                "PLATINUM",
                new BigDecimal("0.45"),
                new BigDecimal("100"),
                7,
                Instant.now()).withProductTermColumns(
                        "CREDIT_CARD_REWARDS", 24000, 8000, new BigDecimal("14.9"));

        when(creditRecords.findById("SIM-PROFILE"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(inProgress));
        when(creditConfigs.findFirstByProductTermsOrderByVersionDescConfigIdDesc("PLATINUM"))
                .thenReturn(Optional.of(selected));
        when(creditConfigs.findAllByVersionAndProductTermsOrderByConfigIdDesc(4, "PLATINUM"))
                .thenReturn(java.util.List.of(selected, rewardsTerms));

        service.processApplicationAsync(request("SIM-PROFILE"));

        ArgumentCaptor<CreditRecord> saved = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecords, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        CreditRecord decided = saved.getAllValues().getLast();
        assertThat(decided.getOutcome()).isEqualTo(CreditRecord.STATUS_ACCEPTED);
        assertThat(decided.getProductMaxLimit()).isEqualTo(8000);
        assertThat(decided.getApr()).isEqualByComparingTo("14.9");
        verify(orchestrator).applicationStatusUpdate(
                "SIM-PROFILE", Decision.ACCEPTED, "CRE_APPROVED");
    }

    @Test
    void namedSeedProfileFallsBackToItsCatalogueTerms() {
        CreditRecord inProgress = CreditRecord.inProgress("SIM-SEED-PROFILE");
        CreditConfig selected = CreditConfig.of(
                1,
                "PLATINUM",
                new BigDecimal("0.45"),
                new BigDecimal("100"),
                7,
                Instant.now());

        when(creditRecords.findById("SIM-SEED-PROFILE"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(inProgress));
        when(creditConfigs.findFirstByProductTermsOrderByVersionDescConfigIdDesc("PLATINUM"))
                .thenReturn(Optional.of(selected));
        when(creditConfigs.findAllByVersionAndProductTermsOrderByConfigIdDesc(1, "PLATINUM"))
                .thenReturn(java.util.List.of(selected));

        service.processApplicationAsync(request("SIM-SEED-PROFILE"));

        ArgumentCaptor<CreditRecord> saved = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecords, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        CreditRecord decided = saved.getAllValues().getLast();
        assertThat(decided.getOutcome()).isEqualTo(CreditRecord.STATUS_ACCEPTED);
        assertThat(decided.getProductMaxLimit()).isEqualTo(8000);
        assertThat(decided.getApr()).isEqualByComparingTo("14.9");
    }

    @Test
    void repeatedRequestsDoNotCreateAnotherRow() {
        CreditRecord existing = CreditRecord.inProgress("SIM-02");
        when(creditRecords.findById("SIM-02")).thenReturn(Optional.of(existing));

        service.processApplicationAsync(request("SIM-02"));

        verify(creditRecords, never()).save(any(CreditRecord.class));
        verify(acceptance, never()).insertInProgress(any());
        verify(orchestrator, never()).applicationStatusUpdate(eq("SIM-02"), any(), any());
    }

    @Test
    void duplicateKeyFromTheShortInsertTransactionIsHandledIdempotently() {
        CreditRecord existing = CreditRecord.inProgress("SIM-RACE");
        when(creditRecords.findById("SIM-RACE"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate"))
                .when(acceptance).insertInProgress("SIM-RACE");

        service.processApplicationAsync(request("SIM-RACE"));

        verify(creditRecords, never()).save(any(CreditRecord.class));
        verify(orchestrator, never()).applicationStatusUpdate(eq("SIM-RACE"), any(), any());
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
        when(creditConfigs.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(any(Instant.class)))
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

        verify(orchestrator).applicationStatusUpdate(
                "SIM-DTI", Decision.REFERRED, "CRE_AFFORDABILITY_EXCEEDED");
    }

    @Test
    void zeroMonthlyIncomeIsReferredWithoutDividingByZero() {
        CreditRecord inProgress = CreditRecord.inProgress("SIM-ZERO-INCOME");
        when(creditRecords.findById("SIM-ZERO-INCOME"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(inProgress));
        when(creditConfigs.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(any(Instant.class)))
                .thenReturn(Optional.of(CreditConfig.of(
                        2,
                        "{\"REWARDS\":{\"minIncome\":0,\"maxLimit\":5000,\"apr\":12.9}}",
                        BigDecimal.valueOf(0.45),
                        BigDecimal.valueOf(100),
                        7,
                        Instant.now().minusSeconds(24 * 60 * 60))));

        service.processApplicationAsync(request("SIM-ZERO-INCOME", 0, 0, 0, 1000));

        ArgumentCaptor<CreditRecord> saved = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecords, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        CreditRecord decided = saved.getAllValues().getLast();

        assertThat(decided.getMonthlyIncome()).isZero();
        assertThat(decided.getDti()).isNull();
        assertThat(decided.getGrantedLimit()).isNull();
        assertThat(decided.getOutcome()).isEqualTo(CreditRecord.STATUS_REFERRED);
        assertThat(decided.getDecisionReason()).isEqualTo("CRE_AFFORDABILITY_EXCEEDED");
        verify(orchestrator).applicationStatusUpdate(
                "SIM-ZERO-INCOME", Decision.REFERRED, "CRE_AFFORDABILITY_EXCEEDED");
    }

    @Test
    void grantedLimitUsesMonthlyIncomeWhenItIsSmallestCandidate() {
        CreditRecord inProgress = CreditRecord.inProgress("SIM-MONTHLY");
        when(creditRecords.findById("SIM-MONTHLY"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(inProgress));

        service.processApplicationAsync(request("SIM-MONTHLY", 36000, 100, 100, 4000));

        ArgumentCaptor<CreditRecord> saved = ArgumentCaptor.forClass(CreditRecord.class);
        verify(creditRecords, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
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
        verify(creditRecords, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
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
        verify(creditRecords, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        CreditRecord decided = saved.getAllValues().getLast();

        assertThat(decided.getMonthlyIncome()).isEqualTo(6000);
        assertThat(decided.getRequestedLimit()).isEqualTo(9000);
        assertThat(decided.getProductMaxLimit()).isEqualTo(5000);
        assertThat(decided.getGrantedLimit()).isEqualTo(5000);
        assertThat(decided.getCapReason()).isEqualTo("CRE_LIMIT_CAPPED_TO_BAND_MAX");
        verify(orchestrator).applicationStatusUpdate("SIM-MAX", Decision.ACCEPTED,
                "CRE_LIMIT_CAPPED_TO_BAND_MAX");
    }

        @Test
        void manualOverrideWritesAuditAndReportsOnce() {
                CreditRecord decided = decidedCase("SIM-OVERRIDE", CreditRecord.STATUS_REJECTED, null);
                when(creditRecords.findById("SIM-OVERRIDE")).thenReturn(Optional.of(decided));

                OverrideCaseRequest request = new OverrideCaseRequest(
                                "APPROVED", 2800, "income evidenced at 34k", "b.dimovski");

                service.overrideCase("SIM-OVERRIDE", request);

                ArgumentCaptor<OverrideLog> savedLog = ArgumentCaptor.forClass(OverrideLog.class);
                verify(overrideLogs).save(savedLog.capture());
                assertThat(savedLog.getValue().getApplicationId()).isEqualTo("SIM-OVERRIDE");
                assertThat(savedLog.getValue().getOldOutcome()).isEqualTo(CreditRecord.STATUS_REJECTED);
                assertThat(savedLog.getValue().getNewOutcome()).isEqualTo(CreditRecord.STATUS_ACCEPTED);
                assertThat(savedLog.getValue().getGrantedLimit()).isEqualTo(2800);
                verify(orchestrator).applicationStatusUpdate(
                                "SIM-OVERRIDE",
                                Decision.ACCEPTED,
                                "local-manual CRE_MANUAL_APPROVED limit=2800 apr=12.9 reason=income evidenced at 34k");
        }

        @Test
        void manualOverrideOnlyAllowedFromRejectedCases() {
                CreditRecord decided = decidedCase("SIM-OVERRIDE-NOT-REJECTED", CreditRecord.STATUS_ACCEPTED, 2000);
                when(creditRecords.findById("SIM-OVERRIDE-NOT-REJECTED")).thenReturn(Optional.of(decided));

                OverrideCaseRequest request = new OverrideCaseRequest(
                                "REFERRED", null, "manual review requested", "b.dimovski");

                assertThatThrownBy(() -> service.overrideCase("SIM-OVERRIDE-NOT-REJECTED", request))
                                .isInstanceOf(UnprocessableCaseOverrideException.class)
                                .hasMessage("only REJECTED cases can be overridden");
        }

        @Test
        void manualOverrideOnlyAllowsApprovedOrReferredTargets() {
                CreditRecord decided = decidedCase("SIM-OVERRIDE-BAD-TARGET", CreditRecord.STATUS_REJECTED, null);
                when(creditRecords.findById("SIM-OVERRIDE-BAD-TARGET")).thenReturn(Optional.of(decided));

                OverrideCaseRequest request = new OverrideCaseRequest(
                                "DECLINED", null, "invalid target", "b.dimovski");

                assertThatThrownBy(() -> service.overrideCase("SIM-OVERRIDE-BAD-TARGET", request))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessage("newOutcome must be one of APPROVED or REFERRED");
        }

        private CreditRecord decidedCase(String applicationId, String outcome, Integer grantedLimit) {
                CreditRecord row = CreditRecord.inProgress(applicationId);
                row.applyScoring(
                                1,
                                null,
                                "REWARDS",
                                34000,
                                2833,
                                1180,
                                new BigDecimal("0.42"),
                                2833,
                                3000,
                                5000,
                                grantedLimit,
                                new BigDecimal("12.9"),
                                null);
                row.markFinal(outcome, "machine decision");
                return row;
        }
}
