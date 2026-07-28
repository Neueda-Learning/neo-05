package com.neobank.module.dto;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request to create or update a credit policy version.
 * A version is the WHOLE config: all three products' terms plus dtiLimit, roundingStep, sampleEvery.
 *
 * Validation:
 * - all three catalogue products (CREDIT_CARD_REWARDS, CREDIT_CARD_LOW_RATE, CREDIT_CARD_STUDENT) present
 * - 0 < dtiLimit < 1
 * - sampleEvery ≥ 1
 */
public record CreditPolicyRequest(
        @JsonProperty("dti_limit")
        @NotNull(message = "dti_limit required")
        @DecimalMin(value = "0.01", message = "dti_limit must be > 0")
        @DecimalMax(value = "0.99", message = "dti_limit must be < 1")
        BigDecimal dtiLimit,
        
        @JsonProperty("rounding_step")
        @NotNull(message = "rounding_step required")
        @Min(value = 1, message = "rounding_step must be > 0")
        Integer roundingStep,
        
        @JsonProperty("sample_every")
        @NotNull(message = "sample_every required")
        @Min(value = 1, message = "sample_every must be >= 1")
        Integer sampleEvery,
        
        @JsonProperty("product_terms")
        @NotNull(message = "product_terms required")
        @Valid
        List<ProductTermDTO> productTerms) {
}
