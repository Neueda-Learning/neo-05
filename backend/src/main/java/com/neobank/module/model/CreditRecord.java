package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "credit_record")
public class CreditRecord {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_REFERRED = "REFERRED";

    @Id
    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "machine_outcome", nullable = false, length = 16)
    private String machineOutcome;

    @Column(name = "reference", nullable = false, length = 32)
    private String reference;

    @Column(name = "credit_config_version", nullable = false)
    private Integer creditConfigVersion;

    @Column(name = "product_code", nullable = false, length = 32)
    private String productCode;

    @Column(name = "annual_income", nullable = false)
    private Integer annualIncome;

    @Column(name = "monthly_income", nullable = false)
    private Integer monthlyIncome;

    @Column(name = "monthly_outgoings", nullable = false)
    private Integer monthlyOutgoings;

    @Column(name = "dti", precision = 4, scale = 2)
    private BigDecimal dti;

    @Column(name = "income_basis_limit", nullable = false)
    private Integer incomeBasisLimit;

    @Column(name = "requested_limit", nullable = false)
    private Integer requestedLimit;

    @Column(name = "product_max_limit", nullable = false)
    private Integer productMaxLimit;

    @Column(name = "granted_limit")
    private Integer grantedLimit;

    @Column(name = "apr", nullable = false, precision = 3, scale = 1)
    private BigDecimal apr;

    @Column(name = "cap_reason", length = 32)
    private String capReason;

    @Column(name = "sampled", nullable = false, columnDefinition = "TINYINT")
    private boolean sampled;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "decision_reason", length = 512)
    private String decisionReason;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected CreditRecord() {
        // JPA
    }

    public static CreditRecord inProgress(String applicationId) {
        CreditRecord row = new CreditRecord();
        row.applicationId = applicationId;
        row.outcome = STATUS_IN_PROGRESS;
        row.machineOutcome = STATUS_IN_PROGRESS;
        row.reference = "pending-" + applicationId;
        row.creditConfigVersion = 1;
        row.productCode = "PENDING";
        row.annualIncome = 0;
        row.monthlyIncome = 0;
        row.monthlyOutgoings = 0;
        row.dti = null;
        row.incomeBasisLimit = 0;
        row.requestedLimit = 0;
        row.productMaxLimit = 0;
        row.grantedLimit = null;
        row.apr = BigDecimal.ZERO;
        row.capReason = null;
        row.sampled = false;
        return row;
    }

    @PrePersist
    void onCreate() {
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(outcome);
    }

    public boolean hasFinalOutcome() {
        return STATUS_ACCEPTED.equals(outcome)
                || STATUS_REJECTED.equals(outcome)
                || STATUS_REFERRED.equals(outcome);
    }

    public void markFinal(String status, String reason) {
        this.outcome = status;
        this.machineOutcome = status;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
    }

    public void applyScoring(Integer configVersion,
                             String productCode,
                             Integer annualIncome,
                             Integer monthlyIncome,
                             Integer monthlyOutgoings,
                             BigDecimal dti,
                             Integer incomeBasisLimit,
                             Integer requestedLimit,
                             Integer productMaxLimit,
                             Integer grantedLimit,
                             BigDecimal apr,
                             String capReason) {
        this.creditConfigVersion = configVersion;
        this.productCode = productCode;
        this.annualIncome = annualIncome;
        this.monthlyIncome = monthlyIncome;
        this.monthlyOutgoings = monthlyOutgoings;
        this.dti = dti;
        this.incomeBasisLimit = incomeBasisLimit;
        this.requestedLimit = requestedLimit;
        this.productMaxLimit = productMaxLimit;
        this.grantedLimit = grantedLimit;
        this.apr = apr;
        this.capReason = capReason;
    }

    public String apiStatus() {
        if (STATUS_IN_PROGRESS.equals(outcome)) {
            return "in-progress";
        }
        return outcome;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Integer getMonthlyIncome() {
        return monthlyIncome;
    }

    public BigDecimal getDti() {
        return dti;
    }

    public Integer getRequestedLimit() {
        return requestedLimit;
    }

    public Integer getProductMaxLimit() {
        return productMaxLimit;
    }

    public Integer getGrantedLimit() {
        return grantedLimit;
    }

    public String getCapReason() {
        return capReason;
    }
}
