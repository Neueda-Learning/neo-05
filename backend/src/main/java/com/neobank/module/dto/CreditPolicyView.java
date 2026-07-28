package com.neobank.module.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.neobank.module.model.CreditConfig;

/**
 * The current credit policy version returned to the frontend for viewing or editing.
 * Includes parsed product terms.
 */
public record CreditPolicyView(
        Integer version,
        
        @JsonProperty("dti_limit")
        BigDecimal dtiLimit,
        
        @JsonProperty("rounding_step")
        Integer roundingStep,
        
        @JsonProperty("sample_every")
        Integer sampleEvery,
        
        @JsonProperty("product_terms")
        List<ProductTermDTO> productTerms,

        @JsonProperty("policy_name")
        String policyName,
        
        @JsonProperty("effective_from")
        Instant effectiveFrom) {

    public static CreditPolicyView of(CreditConfig config, List<ProductTermDTO> parsedTerms) {
        return new CreditPolicyView(
                config.getVersion(),
                config.getDtiLimit(),
                config.getRoundingStep().intValue(),
                config.getSampleEvery(),
                parsedTerms,
                derivePolicyName(config.getProductTerms()),
                config.getEffectiveFrom()
        );
    }

    private static String derivePolicyName(String rawProductTerms) {
        if (rawProductTerms == null) {
            return null;
        }

        String trimmed = rawProductTerms.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return null;
        }

        String normalized = trimmed.toUpperCase();
        if ("PLATIUM".equals(normalized)) {
            return "PLATINUM";
        }
        return normalized;
    }
}
