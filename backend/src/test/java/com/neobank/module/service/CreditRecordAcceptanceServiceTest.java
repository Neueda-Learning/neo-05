package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.model.CreditRecord;
import com.neobank.module.repository.CreditRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreditRecordAcceptanceServiceTest {

    @Test
    void flushesAMinimalInProgressRecord() {
        CreditRecordRepository records = mock(CreditRecordRepository.class);
        when(records.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> call.getArgument(0));

        new CreditRecordAcceptanceService(records).insertInProgress("SIM-01");

        ArgumentCaptor<CreditRecord> saved = ArgumentCaptor.forClass(CreditRecord.class);
        verify(records).saveAndFlush(saved.capture());
        CreditRecord row = saved.getValue();
        assertThat(row.getApplicationId()).isEqualTo("SIM-01");
        assertThat(row.getOutcome()).isEqualTo(CreditRecord.STATUS_IN_PROGRESS);
        assertThat(row.getReference()).isNull();
        assertThat(row.getCreditConfigVersion()).isNull();
        assertThat(row.getAnnualIncome()).isNull();
        assertThat(row.getProductMaxLimit()).isNull();
        assertThat(row.getApr()).isNull();
    }
}
