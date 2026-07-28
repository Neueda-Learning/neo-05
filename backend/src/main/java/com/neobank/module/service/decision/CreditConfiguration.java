package com.neobank.module.service.decision;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** One immutable credit-policy version. */
public record CreditConfiguration(
        int version,
        Map<CreditProduct, ProductTerms> productTerms,
        BigDecimal dtiLimit,
        int roundingStep) {

    public CreditConfiguration {
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        productTerms = Map.copyOf(Objects.requireNonNull(productTerms,
                "productTerms is required"));
        for (CreditProduct product : CreditProduct.values()) {
            if (!productTerms.containsKey(product)) {
                throw new IllegalArgumentException("Missing terms for " + product);
            }
        }
        Objects.requireNonNull(dtiLimit, "dtiLimit is required");
        if (dtiLimit.signum() <= 0 || dtiLimit.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("dtiLimit must be between zero and one");
        }
        if (roundingStep <= 0) {
            throw new IllegalArgumentException("roundingStep must be positive");
        }
    }

    public ProductTerms termsFor(CreditProduct product) {
        return productTerms.get(Objects.requireNonNull(product, "product is required"));
    }
}
