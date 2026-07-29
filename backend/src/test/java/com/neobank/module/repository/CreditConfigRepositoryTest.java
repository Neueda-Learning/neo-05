package com.neobank.module.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CreditConfigRepositoryTest {

    @Autowired
    private CreditConfigRepository creditConfigs;

    @Test
    void currentConfigurationIsTheMaximumVersion() {
        assertThat(creditConfigs.findTopByOrderByVersionDesc())
                .hasValueSatisfying(config -> assertThat(config.getVersion()).isEqualTo(3));
    }
}
