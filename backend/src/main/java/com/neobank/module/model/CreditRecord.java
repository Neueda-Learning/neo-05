package com.neobank.module.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

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

    @Column(name = "reference", length = 32)
    private String reference;

    @Column(name = "credit_config_version")
    private Integer creditConfigVersion;

    @Column(name = "credit_config_id")
    private Long creditConfigId;

    @Column(name = "product_code", length = 32)
    private String productCode;

    @Column(name = "annual_income")
    private Integer annualIncome;

    @Column(name = "monthly_income")
    private Integer monthlyIncome;

    @Column(name = "monthly_outgoings")
    private Integer monthlyOutgoings;

    @Column(name = "dti", precision = 4, scale = 2)
    private BigDecimal dti;

    @Column(name = "income_basis_limit")
    private Integer incomeBasisLimit;

    @Column(name = "requested_limit")
    private Integer requestedLimit;

    @Column(name = "product_max_limit")
    private Integer productMaxLimit;

    @Column(name = "granted_limit")
    private Integer grantedLimit;

    @Column(name = "apr", precision = 3, scale = 1)
    private BigDecimal apr;

    @Column(name = "cap_reason", length = 32)
    private String capReason;

    @Column(name = "sampled", nullable = false, columnDefinition = "TINYINT")
    private boolean sampled;

    @Column(name = "sample_position")
    private Integer samplePosition;

    @Column(name = "claimed_by", length = 128)
    private String claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "decided_by", length = 128)
    private String decidedBy;

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
        this.decidedBy = null;
    }

    public void applyManualOverride(String status,
                                    Integer grantedLimit,
                                    String reason,
                                    String operator) {
        this.outcome = status;
        if (STATUS_ACCEPTED.equals(status)) {
            this.grantedLimit = grantedLimit;
        }
        if (STATUS_REFERRED.equals(status)) {
            this.claimedBy = null;
            this.claimedAt = null;
        }
        this.decisionReason = reason;
        this.decidedBy = operator;
        this.decidedAt = Instant.now();
    }

    public void applyManualOverride(String status, String reason) {
        this.outcome = status;
        this.decisionReason = reason;
        this.decidedAt = Instant.now();
    }

    public void applyScoring(Integer configVersion,
                             Long configId,
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
                    this.creditConfigId = configId;
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

    public String getMachineOutcome() {
        return machineOutcome;
    }

    public String getReference() {
        return reference;
    }

    public Integer getCreditConfigVersion() {
        return creditConfigVersion;
    }

    public Long getCreditConfigId() {
        return creditConfigId;
    }

    public String getProductCode() {
        return productCode;
    }

    public Integer getAnnualIncome() {
        return annualIncome;
    }

    public Integer getMonthlyIncome() {
        return monthlyIncome;
    }

    public Integer getMonthlyOutgoings() {
        return monthlyOutgoings;
    }

    public java.math.BigDecimal getDti() {
        return dti;
    }

    public Integer getIncomeBasisLimit() {
        return incomeBasisLimit;
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

    public java.math.BigDecimal getApr() {
        return apr;
    }

    public boolean isSampled() {
        return sampled;
    }

    public Integer getSamplePosition() {
        return samplePosition;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getCapReason() {
        return capReason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
