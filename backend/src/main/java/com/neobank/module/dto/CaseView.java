package com.neobank.module.dto;

import com.neobank.module.model.CreditRecord;
import java.math.BigDecimal;

/**
 * The read model for GET /cases/{applicationId} — every number behind the decision,
 * assembled once from the stored row so the view never recalculates.
 */
public record CaseView(
        String outcome,
        String machineOutcome,
        String reference,
        int creditConfigVersion,
        WorkingsView workings,
        SamplingView sampling) {

    public record WorkingsView(
            int annualIncome,
            int monthlyIncome,
            int monthlyOutgoings,
            BigDecimal dti,
            BigDecimal dtiLimit,
            int incomeBasisLimit,
            int productMaxLimit,
            int requestedLimit,
            Integer grantedLimit,
            BigDecimal apr,
            String capReason) {}

    public record SamplingView(boolean sampled) {}

    public static CaseView of(CreditRecord row, BigDecimal dtiLimit) {
        return new CaseView(
                row.getOutcome(),
                row.getMachineOutcome(),
                row.getReference(),
                row.getCreditConfigVersion(),
                new WorkingsView(
                        row.getAnnualIncome(),
                        row.getMonthlyIncome(),
                        row.getMonthlyOutgoings(),
                        row.getDti(),
                        dtiLimit,
                        row.getIncomeBasisLimit(),
                        row.getProductMaxLimit(),
                        row.getRequestedLimit(),
                        row.getGrantedLimit(),
                        row.getApr(),
                        row.getCapReason()),
                new SamplingView(row.isSampled()));
    }
}
