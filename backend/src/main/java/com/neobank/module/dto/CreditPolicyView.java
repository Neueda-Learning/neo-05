package com.neobank.module.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    private static final Map<String, String> POLICY_CODE_TO_NAME = Map.of(
            "CREDIT_CARD_REWARDS", "REWARDS",
            "CREDIT_CARD_STANDARD", "STANDARD",
            "CREDIT_CARD_LOW_RATE", "STANDARD",
            "CREDIT_CARD_STUDENT", "STUDENT",
            "PLATINUM", "REWARDS",
            "PREMIUM", "STANDARD",
            "STUDENT", "STUDENT"
    );

    public static CreditPolicyView of(CreditConfig config, List<ProductTermDTO> parsedTerms) {
        return new CreditPolicyView(
                config.getVersion(),
                config.getDtiLimit(),
                config.getRoundingStep().intValue(),
                config.getSampleEvery(),
                parsedTerms,
                derivePolicyName(config.getProductTerms(), config.getProductCode()),
                config.getEffectiveFrom()
        );
    }

    private static String derivePolicyName(String rawProductTerms, String productCode) {
        if (rawProductTerms == null) {
            String fromCode = normalizePolicyName(productCode);
            return fromCode;
        }

        String trimmed = rawProductTerms.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            String fromCode = normalizePolicyName(productCode);
            return fromCode;
        }

        return normalizePolicyName(trimmed);
    }

    private static String normalizePolicyName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return null;
        }
        if ("PLATIUM".equals(normalized)) {
            return "REWARDS";
        }
        return POLICY_CODE_TO_NAME.getOrDefault(normalized, normalized);
    }
}
