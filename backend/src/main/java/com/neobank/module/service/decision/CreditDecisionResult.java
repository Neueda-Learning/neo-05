package com.neobank.module.service.decision;

import com.neobank.module.model.Decision;
import java.math.BigDecimal;

/** The decision plus every calculation needed to explain it later. */
public record CreditDecisionResult(
        int configurationVersion,
        CreditProduct product,
        Decision outcome,
        DecisionReason reason,
        int annualIncome,
        Integer monthlyIncome,
        Integer monthlyOutgoings,
        BigDecimal dti,
        Integer incomeBasisLimit,
        int requestedLimit,
        int productMaximumLimit,
        Integer calculatedLimit,
        Integer grantedLimit,
        BigDecimal apr,
        LimitCapReason capReason) {
}
