package com.neobank.module.dto;

import com.neobank.module.model.CreditConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * API view of one credit_config version.
 */
public record CreditConfigResponse(
        int version,
        Map<String, ProductTerms> productTerms,
        BigDecimal dtiLimit,
        BigDecimal roundingStep,
        int sampleEvery,
        Instant effectiveFrom) {

    public record ProductTerms(int minIncome, int maxLimit, BigDecimal apr) {}

    public static CreditConfigResponse of(CreditConfig row, Map<String, ProductTerms> terms) {
        return new CreditConfigResponse(
                row.getVersion(),
                terms,
                row.getDtiLimit(),
                row.getRoundingStep(),
                row.getSampleEvery(),
                row.getEffectiveFrom()
        );
    }
}
