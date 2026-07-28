package com.neobank.module.service.decision;

import java.util.Locale;

/** The three products supported by the current credit policy. */
public enum CreditProduct {
    PREMIUM,
    PLATINUM,
    STUDENT;

    /**
     * Resolve either the internal catalogue name or the product code carried by the application.
     */
    public static CreditProduct fromCode(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            throw new IllegalArgumentException("productCode is required");
        }

        String normalized = productCode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PREMIUM", "CREDIT_CARD_PREMIUM" -> PREMIUM;
            case "PLATINUM", "CREDIT_CARD_PLATINUM" -> PLATINUM;
            case "STUDENT", "CREDIT_CARD_STUDENT" -> STUDENT;
            default -> throw new IllegalArgumentException(
                    "Unsupported credit product: " + productCode);
        };
    }
}
