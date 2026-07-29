package com.neobank.module.dto;

import com.neobank.module.model.CreditRecord;
import com.neobank.module.model.OverrideLog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        SamplingView sampling,
        List<OverrideView> overrides) {

    public CaseView(String outcome,
                    String machineOutcome,
                    String reference,
                    int creditConfigVersion,
                    WorkingsView workings,
                    SamplingView sampling) {
        this(outcome, machineOutcome, reference, creditConfigVersion, workings, sampling, List.of());
    }

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
            String capReason,
            String decisionReason,
            String productCode,
            Integer minIncome) {
    }

    public record SamplingView(boolean sampled) {
    }

    public record OverrideView(
            String oldOutcome,
            String newOutcome,
            Integer grantedLimit,
            String reason,
            String operator,
            Instant overriddenAt) {
        public static OverrideView of(OverrideLog row) {
            return new OverrideView(
                    row.getOldOutcome(),
                    row.getNewOutcome(),
                    row.getGrantedLimit(),
                    row.getReason(),
                    row.getOperator(),
                    row.getOverriddenAt());
        }
    }

    public static CaseView of(CreditRecord row, BigDecimal dtiLimit) {
        return of(row, dtiLimit, null, List.of());
    }

    public static CaseView of(CreditRecord row, BigDecimal dtiLimit, Integer minIncome) {
        return of(row, dtiLimit, minIncome, List.of());
    }

    public static CaseView of(CreditRecord row,
                              BigDecimal dtiLimit,
                              Integer minIncome,
                              List<OverrideView> overrides) {
        return new CaseView(
                row.getOutcome(),
                row.getMachineOutcome(),
                row.getReference(),
                nz(row.getCreditConfigVersion()),
                new WorkingsView(
                        nz(row.getAnnualIncome()),
                        nz(row.getMonthlyIncome()),
                        nz(row.getMonthlyOutgoings()),
                        row.getDti(),
                        dtiLimit,
                        nz(row.getIncomeBasisLimit()),
                        nz(row.getProductMaxLimit()),
                        nz(row.getRequestedLimit()),
                        row.getGrantedLimit(),
                        row.getApr(),
                        row.getCapReason(),
                        row.getDecisionReason(),
                        row.getProductCode(),
                        minIncome),
                new SamplingView(row.isSampled()),
                overrides == null ? List.of() : List.copyOf(overrides));
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
