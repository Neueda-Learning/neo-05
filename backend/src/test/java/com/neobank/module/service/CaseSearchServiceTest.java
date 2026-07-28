package com.neobank.module.service;

import com.neobank.module.dto.CaseSearchResponse;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.CreditRecord;
import com.neobank.module.repository.CreditRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CaseSearchServiceTest {

    private CreditRecordRepository creditRecords;
    private OrchestratorClient orchestrator;
    private CaseSearchService service;

    @BeforeEach
    void setUp() {
        creditRecords = mock(CreditRecordRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        service = new CaseSearchService(creditRecords, orchestrator);
    }

    @Test
    void blankQueryReturnsEmptyWithoutReadingAnything() {
        assertThat(service.search("  ", 10).cases()).isEmpty();
        verifyNoInteractions(creditRecords, orchestrator);
    }

    @Test
    void applicationIdSearchStaysEntirelyLocal() {
        CreditRecord record = CreditRecord.inProgress("APP-1234");
        when(creditRecords.searchByApplicationId("app-1234", 10))
                .thenReturn(List.of(record));

        CaseSearchResponse response = service.search(" app-1234 ", 10);

        assertThat(response.cases()).hasSize(1);
        assertThat(response.cases().getFirst().applicationId()).isEqualTo("APP-1234");
        assertThat(response.more()).isFalse();
        verifyNoInteractions(orchestrator);
    }

    @Test
    void nameSearchResolvesIdsBeforeReadingLocalCases() {
        List<String> ids = List.of("APP-1", "APP-2");
        when(orchestrator.resolveApplicationIdsByName("Maria")).thenReturn(ids);
        when(creditRecords.searchByApplicationIds(ids, 10))
                .thenReturn(List.of(CreditRecord.inProgress("APP-2")));

        CaseSearchResponse response = service.search("Maria", 10);

        assertThat(response.cases()).extracting(item -> item.applicationId())
                .containsExactly("APP-2");
        verify(orchestrator).resolveApplicationIdsByName("Maria");
        verify(creditRecords).searchByApplicationIds(ids, 10);
        verify(creditRecords, never()).searchByApplicationId("Maria", 10);
    }

    @Test
    void overflowRowSetsMoreAndIsNotReturned() {
        List<CreditRecord> elevenRows = IntStream.rangeClosed(1, 11)
                .mapToObj(index -> CreditRecord.inProgress("SIM-%02d".formatted(index)))
                .toList();
        when(creditRecords.searchByApplicationId("SIM-", 10)).thenReturn(elevenRows);

        CaseSearchResponse response = service.search("SIM-", 20);

        assertThat(response.cases()).hasSize(10);
        assertThat(response.more()).isTrue();
        verify(creditRecords).searchByApplicationId("SIM-", 10);
    }

    @Test
    void nonPositiveLimitIsRejected() {
        assertThatThrownBy(() -> service.search("Maria", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        verifyNoInteractions(creditRecords, orchestrator);
    }
}
