package com.neobank.module.controller;

import com.neobank.module.dto.CaseSearchItem;
import com.neobank.module.dto.CaseSearchResponse;
import com.neobank.module.service.CaseSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseSearchController.class)
class CaseSearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CaseSearchService caseSearch;

    @Test
    void defaultSearchIsEmptyAndUsesLimitTen() throws Exception {
        when(caseSearch.search("", 10))
                .thenReturn(new CaseSearchResponse(List.of(), false));

        mvc.perform(get("/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases").isEmpty())
                .andExpect(jsonPath("$.more").value(false));

        verify(caseSearch).search("", 10);
    }

    @Test
    void apiPrefixedRouteIsAvailableThroughTheFrontendProxy() throws Exception {
        when(caseSearch.search("APP-1234", 10))
                .thenReturn(new CaseSearchResponse(List.of(), false));

        mvc.perform(get("/api/v1/cases").param("q", "APP-1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases").isEmpty());

        verify(caseSearch).search("APP-1234", 10);
    }

    @Test
    void queryReturnsLocalDecisionFieldsAndMoreFlag() throws Exception {
        CaseSearchItem item = new CaseSearchItem(
                "APP-1234",
                Instant.parse("2026-07-21T21:40:00Z"),
                "ACCEPTED",
                2800,
                false);
        when(caseSearch.search("Maria", 5))
                .thenReturn(new CaseSearchResponse(List.of(item), true));

        mvc.perform(get("/cases").param("q", "Maria").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].applicationId").value("APP-1234"))
                .andExpect(jsonPath("$.cases[0].submittedAt")
                        .value("2026-07-21T21:40:00Z"))
                .andExpect(jsonPath("$.cases[0].outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.cases[0].grantedLimit").value(2800))
                .andExpect(jsonPath("$.cases[0].sampled").value(false))
                .andExpect(jsonPath("$.cases[0].applicantName").doesNotExist())
                .andExpect(jsonPath("$.more").value(true));

        verify(caseSearch).search("Maria", 5);
    }

    @Test
    void nonPositiveLimitReturnsBadRequest() throws Exception {
        when(caseSearch.search("Maria", 0))
                .thenThrow(new IllegalArgumentException("limit must be positive"));

        mvc.perform(get("/cases").param("q", "Maria").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be positive"));
    }

}
