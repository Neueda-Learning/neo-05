package com.neobank.module.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.module.dto.CreditPolicyView;
import com.neobank.module.model.CreditConfig;
import com.neobank.module.repository.CreditConfigRepository;

@ExtendWith(MockitoExtension.class)
class CreditPolicyServiceLegacyParseTest {

    @Mock
    private CreditConfigRepository repository;

    @Test
    void getCurrentPolicyParsesLegacySeededObjectShape() {
        CreditPolicyService service = new CreditPolicyService(repository, new ObjectMapper());

        CreditConfig legacy = new CreditConfig(
                2,
                """
                {"PREMIUM":{"minIncome":20000,"maxLimit":5500,"apr":13.4},
                 "PLATINUM":{"minIncome":26000,"maxLimit":8500,"apr":15.2},
                 "STUDENT":{"minIncome":12000,"maxLimit":1800,"apr":10.2}}
                """,
                new BigDecimal("0.42"),
                new BigDecimal("100"),
                5,
                Instant.now()
        );

        when(repository.findTopByOrderByVersionDesc()).thenReturn(Optional.of(legacy));

        CreditPolicyView current = service.getCurrentPolicy();

        assertThat(current.version()).isEqualTo(2);
        assertThat(current.productTerms()).hasSize(3);
        assertThat(current.productTerms()).extracting(t -> t.productCode())
                .containsExactly(
                        "CREDIT_CARD_REWARDS",
                        "CREDIT_CARD_LOW_RATE",
                        "CREDIT_CARD_STUDENT"
                );
    }
}
