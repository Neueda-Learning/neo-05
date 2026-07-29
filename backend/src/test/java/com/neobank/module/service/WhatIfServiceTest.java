package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.CreditPolicyRequest;
import com.neobank.module.dto.ProductTermDTO;
import com.neobank.module.dto.WhatIfResponse;
import com.neobank.module.model.CreditRecord;
import com.neobank.module.repository.CreditRecordRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatIfServiceTest {

    private CreditRecordRepository creditRecords;
    private WhatIfService service;

    @BeforeEach
    void setUp() {
        creditRecords = mock(CreditRecordRepository.class);
        service = new WhatIfService(creditRecords);
    }

    @Test
    void identicalDraftProducesNoFlips() {
        CreditRecord accepted = CreditRecord.inProgress("app-1");
        accepted.applyScoring(1, 1L, "CREDIT_CARD_STANDARD", 36_000, 3_000, 1_200,
                new BigDecimal("0.40"), 3_000, 3_000, 5_000, 3_000,
                new BigDecimal("29.9"), "CRE_APPROVED");
        accepted.recordMachineDecision(CreditRecord.STATUS_ACCEPTED, "CRE_APPROVED");

        when(creditRecords.findByOutcomeInOrderBySubmittedAtDescApplicationIdDesc(List.of(
                CreditRecord.STATUS_REJECTED,
                CreditRecord.STATUS_REFERRED,
                CreditRecord.STATUS_ACCEPTED))).thenReturn(List.of(accepted));

        WhatIfResponse result = service.simulate(baseDraft(new BigDecimal("0.45")));

        assertThat(result.evaluated()).isEqualTo(1);
        assertThat(result.flips()).isZero();
        assertThat(result.changes()).isEmpty();
        verify(creditRecords).findByOutcomeInOrderBySubmittedAtDescApplicationIdDesc(List.of(
                CreditRecord.STATUS_REJECTED,
                CreditRecord.STATUS_REFERRED,
                CreditRecord.STATUS_ACCEPTED));
    }

    @Test
    void dtiRelaxationCanFlipReferredToAccepted() {
        CreditRecord referred = CreditRecord.inProgress("app-2");
        referred.applyScoring(1, 1L, "CREDIT_CARD_STANDARD", 48_000, 4_000, 1_840,
                new BigDecimal("0.46"), 4_000, 3_000, 5_000, null,
                new BigDecimal("29.9"), null);
        referred.recordMachineDecision(CreditRecord.STATUS_REFERRED, "CRE_AFFORDABILITY_EXCEEDED");

        when(creditRecords.findByOutcomeInOrderBySubmittedAtDescApplicationIdDesc(List.of(
                CreditRecord.STATUS_REJECTED,
                CreditRecord.STATUS_REFERRED,
                CreditRecord.STATUS_ACCEPTED))).thenReturn(List.of(referred));

        WhatIfResponse result = service.simulate(baseDraft(new BigDecimal("0.50")));

        assertThat(result.evaluated()).isEqualTo(1);
        assertThat(result.flips()).isEqualTo(1);
        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.applicationId()).isEqualTo("app-2");
            assertThat(change.from()).isEqualTo("REFERRED");
            assertThat(change.to()).isEqualTo("ACCEPTED");
        });
    }

    @Test
    void acceptsLowRateAndRewardsAliases() {
        CreditRecord referred = CreditRecord.inProgress("app-3");
        referred.applyScoring(1, 1L, "CREDIT_CARD_REWARDS", 48_000, 4_000, 1_840,
                new BigDecimal("0.46"), 4_000, 3_000, 5_000, null,
                new BigDecimal("24.9"), null);
        referred.recordMachineDecision(CreditRecord.STATUS_REFERRED, "CRE_AFFORDABILITY_EXCEEDED");

        when(creditRecords.findByOutcomeInOrderBySubmittedAtDescApplicationIdDesc(List.of(
                CreditRecord.STATUS_REJECTED,
                CreditRecord.STATUS_REFERRED,
                CreditRecord.STATUS_ACCEPTED))).thenReturn(List.of(referred));

        CreditPolicyRequest draft = new CreditPolicyRequest(
                new BigDecimal("0.50"),
                100,
                7,
                List.of(
                        new ProductTermDTO("CREDIT_CARD_LOW_RATE", 12_000, 5_000,
                                new BigDecimal("29.9")),
                        new ProductTermDTO("CREDIT_CARD_REWARDS", 20_000, 10_000,
                                new BigDecimal("24.9")),
                        new ProductTermDTO("CREDIT_CARD_STUDENT", 0, 1_000,
                                new BigDecimal("34.9"))),
                null);

        WhatIfResponse result = service.simulate(draft);
        assertThat(result.evaluated()).isEqualTo(1);
    }

    @Test
    void invalidDraftFailsBeforeAnyRead() {
        CreditPolicyRequest invalid = new CreditPolicyRequest(
                new BigDecimal("0.45"),
                100,
                7,
                List.of(
                        new ProductTermDTO("CREDIT_CARD_STANDARD", 12_000, 5_000,
                                new BigDecimal("29.9"))),
                null);

        assertThatThrownBy(() -> service.simulate(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("all three products");

        verifyNoInteractions(creditRecords);
    }

    private CreditPolicyRequest baseDraft(BigDecimal dtiLimit) {
        return new CreditPolicyRequest(
                dtiLimit,
                100,
                7,
                List.of(
                        new ProductTermDTO("CREDIT_CARD_STANDARD", 12_000, 5_000,
                                new BigDecimal("29.9")),
                        new ProductTermDTO("CREDIT_CARD_REWARDS", 20_000, 10_000,
                                new BigDecimal("24.9")),
                        new ProductTermDTO("CREDIT_CARD_STUDENT", 0, 1_000,
                                new BigDecimal("34.9"))),
                null);
    }
}
