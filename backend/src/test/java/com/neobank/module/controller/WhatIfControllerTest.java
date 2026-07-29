package com.neobank.module.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.CreditPolicyRequest;
import com.neobank.module.dto.WhatIfChangeView;
import com.neobank.module.dto.WhatIfResponse;
import com.neobank.module.service.WhatIfService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WhatIfController.class)
class WhatIfControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private WhatIfService whatIfService;

    @Test
    void returnsSimulationResult() throws Exception {
        when(whatIfService.simulate(any(CreditPolicyRequest.class))).thenReturn(
                new WhatIfResponse(
                        160,
                        2,
                        List.of(
                                new WhatIfChangeView("app-1", "REFERRED", "ACCEPTED"),
                                new WhatIfChangeView("app-2", "ACCEPTED", "REFERRED"))));

        mvc.perform(post("/api/v1/what-if")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "draft": {
                                    "dti_limit": 0.50,
                                    "rounding_step": 100,
                                    "sample_every": 7,
                                    "product_terms": [
                                      {"productCode": "CREDIT_CARD_STANDARD", "minIncome": 12000, "maxLimit": 5000, "apr": 29.9},
                                      {"productCode": "CREDIT_CARD_REWARDS", "minIncome": 20000, "maxLimit": 10000, "apr": 24.9},
                                      {"productCode": "CREDIT_CARD_STUDENT", "minIncome": 0, "maxLimit": 1000, "apr": 34.9}
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluated").value(160))
                .andExpect(jsonPath("$.flips").value(2))
                .andExpect(jsonPath("$.changes[0].applicationId").value("app-1"));

        ArgumentCaptor<CreditPolicyRequest> sent = ArgumentCaptor.forClass(CreditPolicyRequest.class);
        verify(whatIfService).simulate(sent.capture());
        CreditPolicyRequest draft = sent.getValue();
        assert draft.dtiLimit().equals(new BigDecimal("0.50"));
    }

    @Test
    void rejectsMissingDraft() throws Exception {
        mvc.perform(post("/api/v1/what-if")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
