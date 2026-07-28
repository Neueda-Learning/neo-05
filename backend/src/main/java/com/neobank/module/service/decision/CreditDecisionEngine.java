package com.neobank.module.service.decision;

import com.neobank.module.model.Decision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Pure, side-effect-free implementation of the three currently enabled credit rules. */
public final class CreditDecisionEngine {

    private static final int DTI_SCALE = 2;

    public CreditDecisionResult evaluate(CreditApplicationInput input,
                                         CreditConfiguration configuration) {
        Objects.requireNonNull(input, "input is required");
        Objects.requireNonNull(configuration, "configuration is required");

        CreditProduct product = CreditProduct.fromCode(input.productCode());
        ProductTerms terms = configuration.termsFor(product);

        if (input.annualIncome() < terms.minimumAnnualIncome()) {
            return new CreditDecisionResult(
                    configuration.version(), product, Decision.REJECTED,
                    DecisionReason.CRE_INCOME_BELOW_MINIMUM,
                    input.annualIncome(), null, null, null, null,
                    input.requestedCreditLimit(), terms.maximumCreditLimit(),
                    null, null, terms.apr(), null);
        }

        int monthlyIncome = input.annualIncome() / 12;
        int monthlyOutgoings = Math.addExact(
                input.monthlyHousingCost(), input.existingCreditCommitments());
        BigDecimal dti = monthlyIncome == 0
                ? null
                : BigDecimal.valueOf(monthlyOutgoings)
                        .divide(BigDecimal.valueOf(monthlyIncome), DTI_SCALE,
                                RoundingMode.HALF_UP);

        int rawLimit = Math.min(monthlyIncome,
                Math.min(terms.maximumCreditLimit(), input.requestedCreditLimit()));
        int calculatedLimit = floorToStep(rawLimit, configuration.roundingStep());
        LimitCapReason capReason = capReason(monthlyIncome, terms.maximumCreditLimit(),
                input.requestedCreditLimit(), rawLimit);

        if (dti == null || dti.compareTo(configuration.dtiLimit()) > 0) {
            return new CreditDecisionResult(
                    configuration.version(), product, Decision.REFERRED,
                    DecisionReason.CRE_AFFORDABILITY_EXCEEDED,
                    input.annualIncome(), monthlyIncome, monthlyOutgoings, dti, monthlyIncome,
                    input.requestedCreditLimit(), terms.maximumCreditLimit(),
                    calculatedLimit, null, terms.apr(), capReason);
        }

        return new CreditDecisionResult(
                configuration.version(), product, Decision.ACCEPTED,
                decisionReason(capReason),
                input.annualIncome(), monthlyIncome, monthlyOutgoings, dti, monthlyIncome,
                input.requestedCreditLimit(), terms.maximumCreditLimit(),
                calculatedLimit, calculatedLimit, terms.apr(), capReason);
    }

    private static int floorToStep(int value, int step) {
        return (value / step) * step;
    }

    private static LimitCapReason capReason(int incomeLimit, int productLimit,
                                            int requestedLimit, int rawLimit) {
        // An equal income basis is treated as the natural result, not as an external cap.
        if (rawLimit == incomeLimit) {
            return null;
        }
        if (rawLimit == requestedLimit) {
            return LimitCapReason.TO_REQUEST;
        }
        if (rawLimit == productLimit) {
            return LimitCapReason.TO_BAND_MAX;
        }
        throw new IllegalStateException("No limit candidate matched the calculated minimum");
    }

    private static DecisionReason decisionReason(LimitCapReason capReason) {
        if (capReason == LimitCapReason.TO_REQUEST) {
            return DecisionReason.CRE_LIMIT_CAPPED_TO_REQUEST;
        }
        if (capReason == LimitCapReason.TO_BAND_MAX) {
            return DecisionReason.CRE_LIMIT_CAPPED_TO_BAND_MAX;
        }
        return DecisionReason.CRE_APPROVED;
    }
}
