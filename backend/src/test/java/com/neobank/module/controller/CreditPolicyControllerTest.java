package com.neobank.module.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.CreditPolicyRequest;
import com.neobank.module.dto.CreditPolicyView;
import com.neobank.module.dto.ProductTermDTO;
import com.neobank.module.service.CreditPolicyService;

/**
 * UC06: Credit Policy Controller endpoints
 * - GET /api/v1/credit-policy — fetch current policy for editor prefill
 * - POST /api/v1/credit-policy — create new version
 */
@WebMvcTest(CreditPolicyController.class)
class CreditPolicyControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CreditPolicyService policies;

    @Test
    void getEndpointReturnsCurcurrentPolicy() throws Exception {
        CreditPolicyView current = new CreditPolicyView(
                1,
                new BigDecimal("0.45"),
                100,
                10,
                List.of(
                        new ProductTermDTO("CREDIT_CARD_REWARDS", 24000, 5000, new BigDecimal("18.9")),
                        new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 3000, new BigDecimal("12.9")),
                        new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("22.9"))
                ),
                Instant.now()
        );

        when(policies.getCurrentPolicy()).thenReturn(current);

        mvc.perform(get("/api/v1/credit-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.dti_limit").value(0.45))
                .andExpect(jsonPath("$.rounding_step").value(100))
                .andExpect(jsonPath("$.sample_every").value(10))
                .andExpect(jsonPath("$.product_terms").isArray())
                .andExpect(jsonPath("$.product_terms[0].productCode").value("CREDIT_CARD_REWARDS"));
    }

                @Test
                void listEndpointReturnsAllVersionsNewestFirst() throws Exception {
              CreditPolicyView v2 = new CreditPolicyView(
                2,
                new BigDecimal("0.42"),
                100,
                5,
                List.of(
                  new ProductTermDTO("CREDIT_CARD_REWARDS", 26000, 8500, new BigDecimal("15.2")),
                  new ProductTermDTO("CREDIT_CARD_LOW_RATE", 20000, 5500, new BigDecimal("13.4")),
                  new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1800, new BigDecimal("10.2"))
                ),
                Instant.now()
              );
              CreditPolicyView v1 = new CreditPolicyView(
                1,
                new BigDecimal("0.45"),
                100,
                7,
                List.of(
                  new ProductTermDTO("CREDIT_CARD_REWARDS", 24000, 5000, new BigDecimal("18.9")),
                  new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 3000, new BigDecimal("12.9")),
                  new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("22.9"))
                ),
                Instant.now()
              );

              when(policies.listPolicies()).thenReturn(List.of(v2, v1));

              mvc.perform(get("/api/v1/credit-policy/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[1].version").value(1));
                }

                @Test
                void getVersionEndpointReturnsRequestedPolicyVersion() throws Exception {
              CreditPolicyView v1 = new CreditPolicyView(
                1,
                new BigDecimal("0.45"),
                100,
                7,
                List.of(
                  new ProductTermDTO("CREDIT_CARD_REWARDS", 24000, 5000, new BigDecimal("18.9")),
                  new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 3000, new BigDecimal("12.9")),
                  new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("22.9"))
                ),
                Instant.now()
              );

              when(policies.getPolicyVersion(1)).thenReturn(v1);

              mvc.perform(get("/api/v1/credit-policy/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.sample_every").value(7));
                }

    @Test
    void postEndpointCreatesNewPolicyVersion() throws Exception {
        CreditPolicyView created = new CreditPolicyView(
                2,
                new BigDecimal("0.50"),
                150,
                15,
                List.of(
                        new ProductTermDTO("CREDIT_CARD_REWARDS", 25000, 5500, new BigDecimal("18.9")),
                        new ProductTermDTO("CREDIT_CARD_LOW_RATE", 19000, 3500, new BigDecimal("12.9")),
                        new ProductTermDTO("CREDIT_CARD_STUDENT", 13000, 2000, new BigDecimal("22.9"))
                ),
                Instant.now()
        );

        when(policies.createVersion(any(CreditPolicyRequest.class))).thenReturn(created);

        mvc.perform(post("/api/v1/credit-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dti_limit": 0.50,
                                  "rounding_step": 150,
                                  "sample_every": 15,
                                  "product_terms": [
                                    {
                                      "productCode": "CREDIT_CARD_REWARDS",
                                      "minIncome": 25000,
                                      "maxLimit": 5500,
                                      "apr": 18.9
                                    },
                                    {
                                      "productCode": "CREDIT_CARD_LOW_RATE",
                                      "minIncome": 19000,
                                      "maxLimit": 3500,
                                      "apr": 12.9
                                    },
                                    {
                                      "productCode": "CREDIT_CARD_STUDENT",
                                      "minIncome": 13000,
                                      "maxLimit": 2000,
                                      "apr": 22.9
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.dti_limit").value(0.50))
                .andExpect(jsonPath("$.rounding_step").value(150));

        ArgumentCaptor<CreditPolicyRequest> sent = ArgumentCaptor.forClass(CreditPolicyRequest.class);
        verify(policies).createVersion(sent.capture());

        CreditPolicyRequest request = sent.getValue();
        assert request.dtiLimit().equals(new BigDecimal("0.50"));
        assert request.roundingStep().equals(150);
        assert request.productTerms().size() == 3;
    }

    @Test
    void rejectsInvalidDtiLimit() throws Exception {
        mvc.perform(post("/api/v1/credit-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dti_limit": 1.5,
                                  "rounding_step": 100,
                                  "sample_every": 10,
                                  "product_terms": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("dtiLimit")));
    }

    @Test
    void rejectsInvalidSampleEvery() throws Exception {
        mvc.perform(post("/api/v1/credit-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dti_limit": 0.45,
                                  "rounding_step": 100,
                                  "sample_every": 0,
                                  "product_terms": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("sampleEvery")));
    }

    @Test
    void rejectsMissingProductTerms() throws Exception {
        mvc.perform(post("/api/v1/credit-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dti_limit": 0.45,
                                  "rounding_step": 100,
                                  "sample_every": 10
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
