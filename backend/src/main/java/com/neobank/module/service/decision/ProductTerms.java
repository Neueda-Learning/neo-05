package com.neobank.module.service.decision;

import java.math.BigDecimal;
import java.util.Objects;

/** Product-specific policy values stored inside a versioned credit configuration. */
public record ProductTerms(
        int minimumAnnualIncome,
        int maximumCreditLimit,
        BigDecimal apr) {

    public ProductTerms {
        if (minimumAnnualIncome < 0) {
            throw new IllegalArgumentException("minimumAnnualIncome must not be negative");
        }
        if (maximumCreditLimit <= 0) {
            throw new IllegalArgumentException("maximumCreditLimit must be positive");
        }
        Objects.requireNonNull(apr, "apr is required");
        if (apr.signum() <= 0) {
            throw new IllegalArgumentException("apr must be positive");
        }
    }
}
