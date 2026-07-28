package com.neobank.module.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Request body for {@code POST /config} — a complete credit-policy document.
 *
 * <p>All three product codes ({@code PREMIUM}, {@code PLATINUM}, {@code STUDENT}) are required;
 * the service rejects a payload that omits any of them with {@code 400}.</p>
 */
public record CreateConfigCommand(

        @NotNull(message = "must not be null")
        @Valid
        Map<String, ProductTermsDto> productTerms,

        @NotNull(message = "must not be null")
        @DecimalMin(value = "0", inclusive = false, message = "must be greater than 0")
        @DecimalMax(value = "1", inclusive = false, message = "must be less than 1")
        BigDecimal dtiLimit,

        @Positive(message = "must be positive")
        int roundingStep,

        @Min(value = 1, message = "must be at least 1")
        int sampleEvery
) {

    public record ProductTermsDto(

            @Min(value = 0, message = "must be 0 or greater")
            int minIncome,

            @Positive(message = "must be positive")
            int maxLimit,

            @NotNull(message = "must not be null")
            @DecimalMin(value = "0", inclusive = false, message = "must be greater than 0")
            @Digits(integer = 3, fraction = 1, message = "must have at most 1 decimal place")
            BigDecimal apr
    ) {}
}
