package com.neobank.module.controller;

import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.CreditRecord;
import com.neobank.module.repository.CreditRecordRepository;
import com.neobank.module.service.ApplicantService;
import com.neobank.module.service.CaseSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level UC-01 acceptance tests with the real search service and mocked boundaries. */
@WebMvcTest(CaseSearchController.class)
@Import(CaseSearchService.class)
class CaseSearchEndpointTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CreditRecordRepository creditRecords;

    @MockBean
    private OrchestratorClient orchestrator;

    @MockBean
    private ApplicantService applicants;

    @Test
    void emptyQueryReturnsNoRowsAndTouchesNoBoundary() throws Exception {
        mvc.perform(get("/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases").isEmpty())
                .andExpect(jsonPath("$.more").value(false));

        verifyNoInteractions(creditRecords, orchestrator);
    }

    @Test
    void exactlyTenRowsAreReturnedWithoutMore() throws Exception {
        List<CreditRecord> tenRows = records(10);
        when(creditRecords.searchByApplicationId("SIM-", 10)).thenReturn(tenRows);

        mvc.perform(get("/cases").param("q", "SIM-").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases.length()").value(10))
                .andExpect(jsonPath("$.more").value(false));
    }

    @Test
    void eleventhRowSetsMoreButResponseRemainsCappedAtTen() throws Exception {
        when(creditRecords.searchByApplicationId("SIM-", 10)).thenReturn(records(11));

        mvc.perform(get("/cases").param("q", "SIM-").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases.length()").value(10))
                .andExpect(jsonPath("$.more").value(true));

        verify(creditRecords).searchByApplicationId("SIM-", 10);
    }

    @Test
    void applicationIdSearchIsEntirelyLocal() throws Exception {
        when(creditRecords.searchByApplicationId("app-1234", 10))
                .thenReturn(List.of(CreditRecord.inProgress("APP-1234")));

        mvc.perform(get("/cases").param("q", "app-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].applicationId").value("APP-1234"));

        verifyNoInteractions(orchestrator);
    }

    @Test
    void applicantNameResolvesIdsBeforeLocalLookup() throws Exception {
        List<String> ids = List.of("APP-1234");
        when(orchestrator.resolveApplicationIdsByName("Maria")).thenReturn(ids);
        when(creditRecords.searchByApplicationIds(ids, 10))
                .thenReturn(List.of(CreditRecord.inProgress("APP-1234")));

        mvc.perform(get("/cases").param("q", "Maria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].applicationId").value("APP-1234"));

        verify(orchestrator).resolveApplicationIdsByName("Maria");
        verify(creditRecords).searchByApplicationIds(ids, 10);
        verify(creditRecords, never()).searchByApplicationId("Maria", 10);
    }

    @Test
    void orchestratorFailureDegradesNameSearchToHttp200EmptyResult() throws Exception {
        when(orchestrator.resolveApplicationIdsByName("Maria"))
                .thenThrow(new RuntimeException("connection refused"));

        mvc.perform(get("/cases").param("q", "Maria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases").isEmpty())
                .andExpect(jsonPath("$.more").value(false));

        verifyNoInteractions(creditRecords);
    }

    private List<CreditRecord> records(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> CreditRecord.inProgress("SIM-%02d".formatted(index)))
                .toList();
    }
}
