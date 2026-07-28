package com.neobank.module.service.decision;

/** Explainable reason codes produced by the three currently enabled rules. */
public enum DecisionReason {
    CRE_APPROVED,
    CRE_INCOME_BELOW_MINIMUM,
    CRE_AFFORDABILITY_EXCEEDED,
    CRE_LIMIT_CAPPED_TO_REQUEST,
    CRE_LIMIT_CAPPED_TO_BAND_MAX
}
