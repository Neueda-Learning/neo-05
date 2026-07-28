package com.neobank.module.service.decision;

/** Only the non-identifying application values needed by the credit engine. */
public record CreditApplicationInput(
        String productCode,
        Integer annualIncome,
        Integer monthlyHousingCost,
        Integer existingCreditCommitments,
        Integer requestedCreditLimit) {

    public CreditApplicationInput {
        requireNonNegative(annualIncome, "annualIncome");
        requireNonNegative(monthlyHousingCost, "monthlyHousingCost");
        requireNonNegative(existingCreditCommitments, "existingCreditCommitments");
        if (requestedCreditLimit == null || requestedCreditLimit <= 0) {
            throw new IllegalArgumentException("requestedCreditLimit must be positive");
        }
    }

    private static void requireNonNegative(Integer value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
