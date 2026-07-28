package com.neobank.module.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Single product's terms within a credit policy version.
 * Validation: minIncome ≥ 0, maxLimit > 0, apr > 0 with one decimal place.
 */
public record ProductTermDTO(
        @JsonProperty("productCode")
        @NotNull(message = "productCode required")
        String productCode,
        
        @JsonProperty("minIncome")
        @NotNull(message = "minIncome required")
        @Min(value = 0, message = "minIncome must be >= 0")
        Integer minIncome,
        
        @JsonProperty("maxLimit")
        @NotNull(message = "maxLimit required")
        @Min(value = 1, message = "maxLimit must be > 0")
        Integer maxLimit,
        
        @JsonProperty("apr")
        @NotNull(message = "apr required")
        @DecimalMin(value = "0.01", message = "apr must be > 0")
        BigDecimal apr) {

    public ProductTermDTO {
        // Validate apr has exactly one decimal place
        if (apr != null && apr.scale() != 1) {
            throw new IllegalArgumentException("apr must have exactly one decimal place");
        }
    }
}
